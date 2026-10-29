import argparse
import importlib.util
import json
from pathlib import Path
import shutil
import socket
import tempfile
import threading
import time
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('cold_runner', ROOT / 'scripts/run.py')
runner_module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner_module)


def free_port():
    with socket.socket() as connection:
        connection.bind(('127.0.0.1', 0))
        return connection.getsockname()[1]


def main():
    backend_port, frontend_port = free_port(), free_port()
    while backend_port == frontend_port:
        frontend_port = free_port()
    tools = runner_module.resolve_tools()
    with tempfile.TemporaryDirectory(prefix='yixing-launcher-') as directory:
        root = Path(directory)
        (root / 'backend/target').mkdir(parents=True)
        (root / 'frontend').mkdir()
        shutil.copy2(ROOT / 'backend/target/rental-system-1.0.0.jar', root / 'backend/target/rental-system-1.0.0.jar')
        shutil.copytree(ROOT / 'frontend/dist', root / 'frontend/dist')
        config = 'export default ' + json.dumps({'preview': {'proxy': {'/api': 'http://127.0.0.1:' + str(backend_port)}}})
        (root / 'frontend/vite.config.mjs').write_text(config, encoding='utf-8')
        arguments = argparse.Namespace(build=False, mysql=False, dev=False, open=False, managed=False)
        original_state = runner_module.service_state
        original_occupied = runner_module.occupied
        ready = threading.Event()
        errors = []
        with patch.object(runner_module, 'ROOT', root), patch.object(runner_module, 'URL', 'http://127.0.0.1:' + str(frontend_port) + '/'):
            instance = runner_module.Runner(arguments)
            original_serve = instance.serve
            original_ready = instance.ready

            def state():
                status, body = runner_module.read_url('http://127.0.0.1:' + str(backend_port) + '/api/auth/me')
                return {'backend': status == 401 and '请先登录' in body, 'frontend': runner_module.frontend_ready()}

            def serve(command, working_directory, log):
                command = list(command)
                if '-jar' in command:
                    command.extend(['--server.port=' + str(backend_port), '--spring.datasource.url=jdbc:h2:mem:launcher_smoke;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH'])
                else:
                    command[1] = str(ROOT / 'frontend/node_modules/vite/bin/vite.js')
                    command[command.index('--port') + 1] = str(frontend_port)
                return original_serve(command, working_directory, log)

            def on_ready(owned):
                assert owned
                original_ready(owned)
                ready.set()

            def worker():
                try:
                    instance.run()
                except Exception as error:
                    errors.append(str(error))
                finally:
                    instance.close()

            with patch.object(runner_module, 'service_state', side_effect=state), patch.object(runner_module, 'occupied', side_effect=lambda port: original_occupied({8080: backend_port, 5173: frontend_port}[port])), patch.object(runner_module, 'resolve_tools', return_value=tools), patch.object(instance, 'prepare'), patch.object(instance, 'serve', side_effect=serve), patch.object(instance, 'ready', side_effect=on_ready):
                thread = threading.Thread(target=worker, daemon=True)
                thread.start()
                try:
                    deadline = time.monotonic() + 90
                    while not ready.wait(.2) and thread.is_alive() and time.monotonic() < deadline:
                        pass
                    assert ready.is_set(), errors
                    status, body = runner_module.read_url('http://127.0.0.1:' + str(frontend_port) + '/api/auth/me')
                    assert status == 401 and '请先登录' in body, (status, body)
                    assert len(instance.children) == 2
                finally:
                    instance.stopping.set()
                    thread.join(timeout=25)
                assert not thread.is_alive()
                assert not errors, errors
                assert all(process.poll() is not None for process in instance.children)
                assert not original_occupied(backend_port) and not original_occupied(frontend_port)
        assert all(original_state().values()), 'Original project services must remain running'
    print('PASS: cold start real Java + Vite on isolated ports and in-memory H2; verified frontend API proxy, stopped both owned processes, original services untouched.')


if __name__ == '__main__':
    main()
