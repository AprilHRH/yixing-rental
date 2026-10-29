import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.request import build_opener, ProxyHandler
import webbrowser


ROOT = Path(__file__).resolve().parents[1]
URL = 'http://127.0.0.1:5173/'
HTTP = build_opener(ProxyHandler({}))


def occupied(port):
    with socket.socket() as connection:
        connection.settimeout(.5)
        return connection.connect_ex(('127.0.0.1', port)) == 0


def read_url(url):
    try:
        with HTTP.open(url, timeout=2) as response:
            return response.status, response.read(1048576).decode('utf-8', errors='replace')
    except HTTPError as error:
        return error.code, error.read(4096).decode('utf-8', errors='replace')
    except (URLError, TimeoutError, OSError):
        return 0, ''


def backend_ready():
    status, body = read_url('http://127.0.0.1:8080/api/auth/me')
    try:
        return status == 401 and json.loads(body).get('message') == '请先登录'
    except (ValueError, AttributeError):
        return False


def frontend_ready():
    status, body = read_url(URL)
    return status == 200 and '<title>易行租车 · 车辆租赁管理系统</title>' in body


def service_state():
    return {'backend': backend_ready(), 'frontend': frontend_ready()}


def resolve_tools():
    tools = ROOT / '.tools'
    local_jdk = next((path for path in sorted(tools.glob('jdk*')) if (path / 'bin/java.exe').exists()), None)
    if local_jdk:
        os.environ['JAVA_HOME'] = str(local_jdk)
        os.environ['PATH'] = str(local_jdk / 'bin') + os.pathsep + os.environ.get('PATH', '')
    elif os.environ.get('JAVA_HOME'):
        os.environ['PATH'] = str(Path(os.environ['JAVA_HOME']) / 'bin') + os.pathsep + os.environ.get('PATH', '')
    maven = tools / 'apache-maven-3.9.9/bin' / ('mvn.cmd' if os.name == 'nt' else 'mvn')
    return {'java': shutil.which('java'), 'node': shutil.which('node'),
            'npm': shutil.which('npm.cmd' if os.name == 'nt' else 'npm'),
            'maven': str(maven) if maven.exists() else shutil.which('mvn.cmd' if os.name == 'nt' else 'mvn')}


def terminate_owned(process):
    if process.poll() is not None:
        return
    if os.name == 'nt':
        subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW, check=False)
    else:
        process.terminate()
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)


class Runner:
    def __init__(self, arguments):
        self.arguments = arguments
        self.stopping = threading.Event()
        self.children = []
        self.runtime = ROOT / 'data/runtime'
        self.flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0

    def announce(self, text):
        print(text, flush=True)

    def wait(self, process):
        while process.poll() is None:
            if self.stopping.wait(.15):
                terminate_owned(process)
                raise InterruptedError('启动已取消。')
        if process.returncode:
            raise RuntimeError('构建命令执行失败，请查看启动日志中的详细原因。')

    def build(self, command, directory):
        with (self.runtime / 'build.log').open('a', encoding='utf-8') as log:
            process = subprocess.Popen(command, cwd=directory, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace', creationflags=self.flags)
            self.children.append(process)
            def output():
                for line in process.stdout:
                    log.write(line)
                    log.flush()
                    self.announce(line.rstrip())
            reader = threading.Thread(target=output, daemon=True)
            reader.start()
            try:
                self.wait(process)
            finally:
                reader.join(timeout=3)

    def prepare(self, tools, existing):
        missing = []
        if not existing['backend'] and not tools['java']:
            missing.append('Java 21（安装后设置 JAVA_HOME）')
        if not existing['frontend'] and (not tools['node'] or not tools['npm']):
            missing.append('Node.js 22（包含 npm）')
        if missing:
            raise RuntimeError('缺少运行环境：' + '、'.join(missing) + '。安装后重新打开启动器。')
        jar = ROOT / 'backend/target/rental-system-1.0.0.jar'
        backend_build = not existing['backend'] and (self.arguments.build or not jar.exists())
        if backend_build and not tools['maven']:
            raise RuntimeError('首次构建需要 Maven 3.9+。请安装并加入 PATH，或放入 .tools/apache-maven-3.9.9。')
        if not existing['frontend']:
            if not (ROOT / 'frontend/node_modules/vite/bin/vite.js').exists():
                self.announce('正在安装前端依赖，首次运行需要联网……')
                self.build([tools['npm'], 'ci', '--no-audit', '--no-fund'], ROOT / 'frontend')
            if not self.arguments.dev and (self.arguments.build or not (ROOT / 'frontend/dist/index.html').exists()):
                self.announce('正在构建前端……')
                self.build([tools['npm'], 'run', 'build'], ROOT / 'frontend')
        if backend_build:
            self.announce('正在测试并构建后端，首次运行可能需要几分钟……')
            self.build([tools['maven'], '-f', str(ROOT / 'backend/pom.xml'), 'package'], ROOT)

    def serve(self, arguments, directory, log):
        process = subprocess.Popen(arguments, cwd=directory, stdout=log, stderr=subprocess.STDOUT, creationflags=self.flags)
        self.children.append(process)
        return process

    def ready(self, owned):
        self.announce('系统已就绪：' + URL)
        if self.arguments.managed:
            self.announce('::ready::' + ('owned' if owned else 'external'))
        if self.arguments.open:
            webbrowser.open(URL)

    def listen(self):
        def controls():
            pending = b''
            try:
                while True:
                    chunk = os.read(sys.stdin.fileno(), 128)
                    pending += chunk
                    if not chunk or b'stop\n' in pending.replace(b'\r', b''):
                        break
            except OSError:
                pass
            self.stopping.set()
        threading.Thread(target=controls, daemon=True).start()

    def run(self):
        existing = service_state()
        for name, port in [('backend', 8080), ('frontend', 5173)]:
            if occupied(port) and not existing[name]:
                raise RuntimeError('端口 ' + str(port) + ' 被其他程序占用；请手动检查，不会自动关闭该程序。')
        if self.arguments.build and any(existing.values()):
            raise RuntimeError('重新构建前请先停止正在运行的本项目服务，避免覆盖运行中的文件。')
        if all(existing.values()):
            self.announce('检测到系统已在运行，直接复用，不会重复启动或停止原有服务。')
            self.ready(False)
            return
        if self.arguments.managed:
            self.listen()
        tools = resolve_tools()
        self.runtime.mkdir(parents=True, exist_ok=True)
        with (self.runtime / 'startup.lock').open('a+b') as lock:
            if os.name == 'nt':
                import msvcrt
                lock.seek(0)
                try:
                    msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
                except OSError as error:
                    raise RuntimeError('另一个启动器正在启动或管理系统，请使用原启动器。') from error
            else:
                import fcntl
                try:
                    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                except OSError as error:
                    raise RuntimeError('另一个启动器正在启动或管理系统。') from error
            self.prepare(tools, existing)
            if self.stopping.is_set():
                raise InterruptedError('启动已取消。')
            with (self.runtime / 'backend.log').open('a', encoding='utf-8') as backend_log, (self.runtime / 'frontend.log').open('a', encoding='utf-8') as frontend_log:
                services = []
                if not existing['backend']:
                    self.announce('正在启动后端服务……')
                    arguments = [tools['java'], '-jar', str(ROOT / 'backend/target/rental-system-1.0.0.jar')]
                    if self.arguments.mysql:
                        arguments.append('--spring.profiles.active=mysql')
                    services.append(self.serve(arguments, ROOT, backend_log))
                if not existing['frontend']:
                    self.announce('正在启动前端服务……')
                    arguments = [tools['node'], str(ROOT / 'frontend/node_modules/vite/bin/vite.js')]
                    if not self.arguments.dev:
                        arguments.append('preview')
                    arguments.extend(['--host', '127.0.0.1', '--port', '5173', '--strictPort'])
                    services.append(self.serve(arguments, ROOT / 'frontend', frontend_log))
                deadline = time.monotonic() + 120
                while not all(service_state().values()):
                    if self.stopping.wait(.4):
                        raise InterruptedError('启动已取消。')
                    if any(process.poll() is not None for process in services):
                        raise RuntimeError('服务意外退出，请查看 data/runtime 中的前后端日志。')
                    if time.monotonic() > deadline:
                        raise RuntimeError('等待服务就绪超时，请查看 data/runtime 中的日志。')
                self.ready(True)
                self.announce('停止按钮或 Ctrl+C 只会关闭本次启动的服务。日志：data/runtime/')
                while not self.stopping.wait(.5):
                    if any(process.poll() is not None for process in services):
                        raise RuntimeError('服务已退出，请查看 data/runtime 中的日志。')

    def close(self):
        for process in reversed(self.children):
            terminate_owned(process)


def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    parser = argparse.ArgumentParser(description='易行租车快速启动')
    parser.add_argument('--build', action='store_true', help='测试并重新构建前后端')
    parser.add_argument('--mysql', action='store_true', help='使用配置的 MySQL 数据库')
    parser.add_argument('--dev', action='store_true', help='使用前端热更新模式')
    parser.add_argument('--open', action='store_true', help='就绪后自动打开浏览器')
    parser.add_argument('--managed', action='store_true', help='由桌面启动器管理，stdin 接收 stop 指令')
    parser.add_argument('--check', action='store_true', help='只检查服务状态，不安装、启动或停止服务')
    arguments = parser.parse_args()
    if arguments.check:
        status = service_state()
        print(json.dumps({'url': URL, 'ready': all(status.values()), **status}, ensure_ascii=False))
        return 0 if all(status.values()) else 1
    runner = Runner(arguments)
    try:
        runner.run()
        return 0
    except (KeyboardInterrupt, InterruptedError):
        runner.announce('正在停止本次启动的服务……')
        return 0
    except Exception as error:
        runner.announce('启动失败：' + str(error))
        return 1
    finally:
        runner.close()


if __name__ == '__main__':
    raise SystemExit(main())
