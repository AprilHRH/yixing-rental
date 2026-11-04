"""Explicit local MySQL maintenance: back up, then rebuild demo accounts."""
import argparse
from datetime import datetime
import json
import os
import socket
import subprocess
from local_mysql import ROOT, HOME, BIN, ensure, environment, client, private

CONFIRMATION = 'RESET-CUSTOMERS-AND-ORDERS'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--confirm', required=True, choices=[CONFIRMATION])
    parser.add_argument('--backend-port', type=int, default=8080)
    args = parser.parse_args()
    # 不与正在运行的后端争抢业务写入；先通过原启动器停止服务。
    with socket.socket() as connection:
        if connection.connect_ex(('127.0.0.1', args.backend_port)) == 0:
            raise RuntimeError('Stop the project backend before resetting accounts.')
    config, _ = ensure()
    jar = ROOT / 'backend/target/rental-system-1.0.0.jar'
    jdk = next((ROOT / '.tools').glob('jdk*'))
    if not jar.is_file():
        raise RuntimeError('Build the backend JAR before maintenance.')
    stamp = datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    backup_dir = HOME / 'backups' / stamp
    backup_dir.mkdir(parents=True)
    dump = backup_dir / 'before-account-reset.sql'
    with dump.open('xb') as output:
        private(dump)
        subprocess.run([str(BIN / 'mysqldump.exe'), '--host=127.0.0.1', '--port=3307', '--user=root',
                        '--single-transaction', '--no-tablespaces', '--set-gtid-purged=OFF',
                        '--default-character-set=utf8mb4', 'yixing'], stdout=output, stderr=subprocess.PIPE,
                       env={**os.environ, 'MYSQL_PWD': config['root_password']}, check=True,
                       creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    if dump.stat().st_size < 100:
        raise RuntimeError('Database backup is empty; reset cancelled.')
    tables = ('branch', 'pricing_rule', 'vehicle', 'dispatch_task', 'maintenance_record', 'settings')
    before = {table: int(client(config, f'SELECT COUNT(*) FROM {table};', 'yixing')) for table in tables}
    log = backup_dir / 'maintenance.log'
    with log.open('x', encoding='utf-8') as output:
        result = subprocess.run([str(jdk / 'bin/java.exe'), '-jar', str(jar),
                                 '--spring.profiles.active=mysql', '--spring.main.web-application-type=none',
                                 '--app.reset-accounts=' + args.confirm], cwd=ROOT,
                                env={**os.environ, **environment(config)}, stdin=subprocess.DEVNULL,
                                stdout=output, stderr=subprocess.STDOUT,
                                creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    if result.returncode:
        raise RuntimeError(f'Reset failed; see {log}. Database backup: {dump}')
    after = {table: int(client(config, f'SELECT COUNT(*) FROM {table};', 'yixing')) for table in tables}
    assert before == after, 'Unexpected change in fleet/configuration counts'
    counts = {table: int(client(config, f'SELECT COUNT(*) FROM {table};', 'yixing'))
              for table in ('sys_user', 'customer', 'rental_order', 'payment_record', 'password_reset_ticket')}
    assert counts == {'sys_user': 7, 'customer': 5, 'rental_order': 0, 'payment_record': 0, 'password_reset_ticket': 0}
    receipt = {'backup': str(dump), 'preserved': after, 'counts': counts}
    (backup_dir / 'receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    main()
