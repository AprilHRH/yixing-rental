import argparse
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time


ROOT = Path(__file__).resolve().parents[1]


def occupied(port):
    with socket.socket() as connection:
        return connection.connect_ex(('127.0.0.1', port)) == 0


def main():
    parser = argparse.ArgumentParser(description='Run the Yixing rental project')
    parser.add_argument('--build', action='store_true', help='Run tests and rebuild both applications')
    parser.add_argument('--mysql', action='store_true', help='Use the configured MySQL database')
    parser.add_argument('--dev', action='store_true', help='Use source hot reload instead of the production frontend build')
    arguments = parser.parse_args()
    os.chdir(ROOT)
    tools = ROOT / '.tools'
    local_jdk = next((path for path in tools.glob('jdk*') if path.is_dir()), None) if tools.exists() else None
    if local_jdk and local_jdk.is_dir():
        os.environ['JAVA_HOME'] = str(local_jdk)
        os.environ['PATH'] = str(local_jdk / 'bin') + os.pathsep + os.environ['PATH']
    java = shutil.which('java')
    npm = shutil.which('npm.cmd' if os.name == 'nt' else 'npm')
    node = shutil.which('node')
    maven = tools / 'apache-maven-3.9.9' / 'bin' / ('mvn.cmd' if os.name == 'nt' else 'mvn')
    if not maven.exists():
        maven = shutil.which('mvn.cmd' if os.name == 'nt' else 'mvn')
    if not java or not npm or not node:
        raise SystemExit('Install Java 21 and Node.js 22 first; set JAVA_HOME if necessary.')
    for port in (8080, 5173):
        if occupied(port):
            raise SystemExit(f'Port {port} is already in use. If this project is running, open http://127.0.0.1:5173/')
    if not (ROOT / 'frontend/node_modules').exists():
        subprocess.run([npm, 'ci', '--no-audit', '--no-fund'], cwd=ROOT / 'frontend', check=True)
    jar = ROOT / 'backend/target/rental-system-1.0.0.jar'
    if arguments.build or not jar.exists():
        if not maven:
            raise SystemExit('Maven 3.9+ is required for the first build.')
        subprocess.run([str(maven), '-f', str(ROOT / 'backend/pom.xml'), 'package'], check=True)
        subprocess.run([npm, 'run', 'build'], cwd=ROOT / 'frontend', check=True)
    elif not arguments.dev and not (ROOT / 'frontend/dist/index.html').exists():
        subprocess.run([npm, 'run', 'build'], cwd=ROOT / 'frontend', check=True)
    log_directory = ROOT / 'data/runtime'
    log_directory.mkdir(parents=True, exist_ok=True)
    children = []
    with (log_directory / 'backend.log').open('a', encoding='utf-8') as backend_log, (log_directory / 'frontend.log').open('a', encoding='utf-8') as frontend_log:
        try:
            backend_arguments = [java, '-jar', str(jar)]
            if arguments.mysql:
                backend_arguments.append('--spring.profiles.active=mysql')
            flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
            children.append(subprocess.Popen(backend_arguments, cwd=ROOT, stdout=backend_log, stderr=subprocess.STDOUT, creationflags=flags))
            frontend_arguments = [node, str(ROOT / 'frontend/node_modules/vite/bin/vite.js')]
            if not arguments.dev:
                frontend_arguments.append('preview')
            frontend_arguments.extend(['--host', '127.0.0.1'])
            children.append(subprocess.Popen(frontend_arguments, cwd=ROOT / 'frontend', stdout=frontend_log, stderr=subprocess.STDOUT, creationflags=flags))
            for attempt in range(80):
                if any(child.poll() is not None for child in children):
                    raise RuntimeError(f'A service exited. Check logs in {log_directory}')
                if occupied(8080) and occupied(5173):
                    break
                time.sleep(.5)
            else:
                raise RuntimeError(f'Startup timed out. Check logs in {log_directory}')
            print('\nYixing Rental: http://127.0.0.1:5173/', flush=True)
            print('Admin: admin / Admin@12345   Staff: staff / Staff@12345', flush=True)
            print('Press Ctrl+C to stop both services. Logs: data/runtime/', flush=True)
            while all(child.poll() is None for child in children):
                time.sleep(1)
        except KeyboardInterrupt:
            print('\nStopping project services...')
        finally:
            for child in children:
                if child.poll() is None:
                    child.terminate()
            for child in children:
                try:
                    child.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    child.kill()
                    child.wait()


if __name__ == '__main__':
    main()
