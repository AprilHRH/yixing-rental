import argparse
from contextlib import redirect_stdout
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import MagicMock, patch
from urllib.error import HTTPError

SPEC = importlib.util.spec_from_file_location('rental_runner', Path(__file__).resolve().parents[1] / 'scripts/run.py')
runner_module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner_module)


def arguments(**changes):
    values = dict(build=False, mysql=False, dev=False, open=False, managed=False)
    values.update(changes)
    return argparse.Namespace(**values)


class LauncherTests(unittest.TestCase):
    def test_system_reserved_port_uses_fallback(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(runner_module, 'ROOT', Path(directory)), patch.object(runner_module, 'occupied', return_value=False), patch.object(runner_module, 'can_bind', side_effect=[False, True]):
            self.assertEqual(runner_module.choose_frontend_port(), 15173)

    def test_explicit_reserved_port_has_actionable_error(self):
        with patch.object(runner_module, 'occupied', return_value=False), patch.object(runner_module, 'can_bind', return_value=False):
            with self.assertRaisesRegex(RuntimeError, '--frontend-port'):
                runner_module.choose_frontend_port(5173)

    def test_occupied_default_is_checked_not_silently_switched(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(runner_module, 'ROOT', Path(directory)), patch.object(runner_module, 'occupied', return_value=True), patch.object(runner_module, 'can_bind') as bind:
            self.assertEqual(runner_module.choose_frontend_port(), 5173)
            bind.assert_not_called()

    def test_running_fallback_is_reused(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(runner_module, 'ROOT', Path(directory)):
            runtime = Path(directory) / 'data/runtime'
            runtime.mkdir(parents=True)
            (runtime / 'frontend-port.json').write_text('{"port":15173}', encoding='utf-8')
            with patch.object(runner_module, 'occupied', return_value=True), patch.object(runner_module, 'frontend_ready', return_value=True):
                self.assertEqual(runner_module.choose_frontend_port(), 15173)
            with patch.object(runner_module, 'occupied', return_value=False), patch.object(runner_module, 'can_bind', return_value=True):
                self.assertEqual(runner_module.choose_frontend_port(), 5173)

    def test_untrusted_saved_port_is_ignored(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(runner_module, 'ROOT', Path(directory)):
            runtime = Path(directory) / 'data/runtime'
            runtime.mkdir(parents=True)
            (runtime / 'frontend-port.json').write_text('{"port":true}', encoding='utf-8')
            with patch.object(runner_module, 'occupied', return_value=False), patch.object(runner_module, 'can_bind', return_value=True):
                self.assertEqual(runner_module.choose_frontend_port(), 5173)

    def test_backend_origin_probe_handles_allowed_and_denied(self):
        for status, expected in [(400, True), (403, False)]:
            error = HTTPError('http://localhost', status, '', {}, io.BytesIO(b'{}'))
            with patch.object(runner_module.HTTP, 'open', side_effect=error):
                self.assertEqual(runner_module.backend_accepts_origin(), expected)

    def test_backend_requires_expected_auth_response(self):
        with patch.object(runner_module, 'read_url', return_value=(401, '{"message":"请先登录"}')):
            self.assertTrue(runner_module.backend_ready())
        with patch.object(runner_module, 'read_url', return_value=(401, '{"message":"another app"}')):
            self.assertFalse(runner_module.backend_ready())

    def test_invalid_backend_response_is_not_ready(self):
        for status, body in [(200, '{}'), (401, '<html>error</html>'), (401, '[]')]:
            with patch.object(runner_module, 'read_url', return_value=(status, body)):
                self.assertFalse(runner_module.backend_ready())

    def test_frontend_requires_project_title(self):
        with patch.object(runner_module, 'read_url', return_value=(200, '<title>易行租车 · 车辆租赁管理系统</title>')):
            self.assertTrue(runner_module.frontend_ready())
        with patch.object(runner_module, 'read_url', return_value=(200, '<title>Another project</title>')):
            self.assertFalse(runner_module.frontend_ready())

    def test_http_error_body_is_read_for_auth_probe(self):
        error = HTTPError('http://localhost', 401, '', {}, io.BytesIO(b'{"message":"auth"}'))
        with patch.object(runner_module.HTTP, 'open', side_effect=error):
            self.assertEqual(runner_module.read_url('http://localhost'), (401, '{"message":"auth"}'))

    def test_connection_failure_is_not_ready(self):
        with patch.object(runner_module.HTTP, 'open', side_effect=OSError('offline')):
            self.assertEqual(runner_module.read_url('http://localhost'), (0, ''))

    def test_running_services_are_reused_without_tools_or_processes(self):
        instance = runner_module.Runner(arguments(managed=True))
        with patch.object(runner_module, 'service_state', return_value={'frontend': True, 'backend': True}), patch.object(runner_module, 'occupied', return_value=True), patch.object(runner_module, 'resolve_tools') as tools, redirect_stdout(io.StringIO()) as output:
            instance.run()
        tools.assert_not_called()
        self.assertEqual(instance.children, [])
        self.assertIn('::ready::external', output.getvalue())

    def test_foreign_port_never_kills_or_starts_process(self):
        instance = runner_module.Runner(arguments())
        with patch.object(runner_module, 'service_state', return_value={'frontend': False, 'backend': False}), patch.object(runner_module, 'occupied', return_value=True), patch.object(runner_module.subprocess, 'Popen') as process:
            with self.assertRaisesRegex(RuntimeError, '端口 8080'):
                instance.run()
        process.assert_not_called()

    def test_rebuild_rejects_running_services(self):
        instance = runner_module.Runner(arguments(build=True))
        with patch.object(runner_module, 'service_state', return_value={'frontend': True, 'backend': True}), patch.object(runner_module, 'occupied', return_value=True):
            with self.assertRaisesRegex(RuntimeError, '重新构建前'):
                instance.run()

    def test_missing_environment_has_actionable_message(self):
        instance = runner_module.Runner(arguments())
        with self.assertRaisesRegex(RuntimeError, 'Java 21.*Node.js 22'):
            instance.prepare(dict(java=None, node=None, npm=None, maven=None), dict(backend=False, frontend=False))

    def test_missing_maven_fails_before_dependency_install(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(runner_module, 'ROOT', Path(directory)):
            instance = runner_module.Runner(arguments())
            with patch.object(instance, 'build') as build:
                with self.assertRaisesRegex(RuntimeError, 'Maven 3.9'):
                    instance.prepare(dict(java='java', node='node', npm='npm', maven=None), dict(backend=False, frontend=False))
            build.assert_not_called()

    def test_fresh_project_installs_and_builds(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(runner_module, 'ROOT', Path(directory)):
            instance = runner_module.Runner(arguments())
            with patch.object(instance, 'build') as build, redirect_stdout(io.StringIO()):
                instance.prepare(dict(java='java', node='node', npm='npm', maven='mvn'), dict(backend=False, frontend=False))
            self.assertEqual(build.call_count, 3)
            self.assertEqual(build.call_args_list[0].args[0][:2], ['npm', 'ci'])
            self.assertEqual(build.call_args_list[1].args[0], ['npm', 'run', 'build'])
            self.assertEqual(build.call_args_list[2].args[0][-1], 'package')

    def test_cancel_build_stops_only_owned_process(self):
        instance = runner_module.Runner(arguments())
        instance.stopping.set()
        process = MagicMock()
        process.poll.return_value = None
        with patch.object(runner_module, 'terminate_owned') as stop:
            with self.assertRaises(InterruptedError):
                instance.wait(process)
        stop.assert_called_once_with(process)

    def test_cleanup_tracks_only_spawned_children(self):
        instance = runner_module.Runner(arguments())
        first, second = MagicMock(), MagicMock()
        instance.children = [first, second]
        with patch.object(runner_module, 'terminate_owned') as stop:
            instance.close()
        self.assertEqual([call.args[0] for call in stop.call_args_list], [second, first])

    def test_services_do_not_read_launcher_control_pipe(self):
        instance = runner_module.Runner(arguments(managed=True))
        with patch.object(runner_module.subprocess, 'Popen') as process:
            child = instance.serve(['node', 'vite.js'], Path('.'), io.StringIO())
        self.assertEqual(process.call_args.kwargs['stdin'], runner_module.subprocess.DEVNULL)
        self.assertEqual(instance.children, [child])

    def test_exited_process_is_never_terminated(self):
        process = MagicMock()
        process.poll.return_value = 0
        with patch.object(runner_module.subprocess, 'run') as command:
            runner_module.terminate_owned(process)
        command.assert_not_called()
        process.terminate.assert_not_called()


if __name__ == '__main__':
    unittest.main()
