"""Project-local MySQL, bound to loopback with private runtime credentials."""
import argparse
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / 'data/mysql'
CONFIG = HOME / 'local.json'
BIN = ROOT / '.tools/mysql-8.4.13-winx64/bin'
FLAGS = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0


def private(path):
    # 密码配置只允许当前用户和系统读取，不把本地凭据开放给其他普通用户。
    if os.name == 'nt':
        user = subprocess.check_output(['whoami'], text=True).strip()
        subprocess.run(['icacls', str(path), '/inheritance:r', '/grant:r',
                        user + ':(F)', 'SYSTEM:(F)'], check=True, stdout=subprocess.DEVNULL)
    else:
        path.chmod(0o600)


def client(config, sql, database=None):
    # 密码通过子进程环境传入，避免直接出现在命令行参数中。
    environment = {**os.environ, 'MYSQL_PWD': config['root_password']}
    command = [str(BIN / 'mysql.exe'), '--host=127.0.0.1', '--port=3307',
               '--user=root', '--batch', '--skip-column-names', '--default-character-set=utf8mb4']
    if database:
        command.append(database)
    return subprocess.check_output(command, input=sql, text=True, encoding='utf-8',
                                   env=environment, stderr=subprocess.PIPE, creationflags=FLAGS)


def ensure():
    if not (BIN / 'mysqld.exe').exists():
        raise RuntimeError('Missing project-local MySQL under .tools.')
    HOME.mkdir(parents=True, exist_ok=True)
    if CONFIG.exists():
        config = json.loads(CONFIG.read_text(encoding='utf-8'))
    else:
        # 首次运行随机生成密码，存入被 Git 忽略的 data/mysql/local.json。
        config = {'root_password': secrets.token_hex(24), 'password': secrets.token_hex(24)}
        with CONFIG.open('x', encoding='utf-8') as file:
            json.dump(config, file)
        private(CONFIG)
    data = HOME / 'db'
    if not (data / 'mysql').exists():
        subprocess.run([str(BIN / 'mysqld.exe'), '--no-defaults', '--initialize-insecure',
                        '--basedir=' + str(BIN.parent), '--datadir=' + str(data)],
                       check=True, creationflags=FLAGS, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    with socket.socket() as connection:
        occupied = connection.connect_ex(('127.0.0.1', 3307)) == 0
    if not occupied:
        # 初始化 SQL 含密码，限制读取权限；启动并验证成功后删除该临时文件。
        bootstrap = HOME / 'bootstrap.sql'
        bootstrap.write_text(
            "ALTER USER 'root'@'localhost' IDENTIFIED BY '" + config['root_password'] + "';\n"
            "CREATE DATABASE IF NOT EXISTS yixing CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n"
            "CREATE USER IF NOT EXISTS 'yixing'@'localhost' IDENTIFIED BY '" + config['password'] + "';\n"
            "GRANT ALL ON yixing.* TO 'yixing'@'localhost';\n", encoding='utf-8')
        private(bootstrap)
        log = (HOME / 'console.log').open('ab')
        try:
            process = subprocess.Popen(
                [str(BIN / 'mysqld.exe'), '--no-defaults', '--basedir=' + str(BIN.parent),
                 '--datadir=' + str(data), '--port=3307', '--bind-address=127.0.0.1',
                 '--mysqlx=OFF', '--local-infile=OFF', '--secure-file-priv=NULL',
                 '--init-file=' + str(bootstrap), '--log-error=' + str(HOME / 'server.log')],
                stdin=subprocess.DEVNULL, stdout=log, stderr=log, creationflags=FLAGS)
        finally:
            log.close()
        for _ in range(120):
            if process.poll() is not None:
                raise RuntimeError('Local MySQL exited; inspect data/mysql/server.log.')
            try:
                client(config, 'SELECT 1;')
                break
            except subprocess.CalledProcessError:
                time.sleep(.5)
        else:
            raise RuntimeError('Local MySQL startup timed out.')
        bootstrap.unlink()
    # 端口有人监听不代表就是本项目的数据库，必须用本地凭据连接成功才算就绪。
    version = client(config, 'SELECT VERSION();').strip()
    return config, version


def environment(config):
    # 只监听本机 3307 端口；用环境变量把连接信息交给后端，不写入源码。
    return {'MYSQL_URL': 'jdbc:mysql://127.0.0.1:3307/yixing?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai',
            'MYSQL_USER': 'yixing', 'MYSQL_PASSWORD': config['password']}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['start', 'stop', 'status'], nargs='?', default='start')
    args = parser.parse_args()
    if args.action == 'start':
        _, version = ensure()
        print('Local MySQL ' + version + ' ready on 127.0.0.1:3307')
    else:
        config = json.loads(CONFIG.read_text(encoding='utf-8'))
        print(client(config, 'SHUTDOWN;' if args.action == 'stop' else 'SELECT VERSION();').strip())


if __name__ == '__main__':
    main()
