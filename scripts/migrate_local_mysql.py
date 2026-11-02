"""Copy a protected H2 snapshot via the application's verified backup format."""
import io
import json
import os
import shutil
import subprocess
import time
import zipfile
from pathlib import Path

import requests
from local_mysql import ROOT, HOME, ensure, environment, client, private


def main():
    config, _ = ensure()
    # 只向空库迁移，目标已有数据就停止，不允许覆盖现有 MySQL 业务数据。
    if client(config, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='yixing';").strip() != '0':
        raise RuntimeError('Migration requires an empty yixing database; refusing to overwrite.')
    stamp = str(time.time_ns())
    backup = HOME / ('h2-before-migration-' + stamp)
    backup.mkdir()
    source = ROOT / 'data/yixing.mv.db'
    # 在原数据库副本上启动旧后端，原始文件保留不动，便于失败后恢复。
    shutil.copy2(source, backup / source.name)
    private(backup / source.name)
    jdk = next(path for path in (ROOT / '.tools').glob('jdk*') if path.is_dir())
    jar = ROOT / 'backend/target/rental-system-1.0.0.jar'
    children, logs = [], []
    try:
        sessions = []
        for port, args, env in [
            (8082, ['--spring.datasource.url=jdbc:h2:file:' + (backup / 'yixing').as_posix() + ';MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH'], os.environ),
            (8083, ['--spring.profiles.active=mysql'], {**os.environ, **environment(config)})]:
            log = (HOME / f'migration-{port}.log').open('wb')
            logs.append(log)
            process = subprocess.Popen([str(jdk / 'bin/java.exe'), '-jar', str(jar),
                                        '--server.port=' + str(port), *args],
                                       cwd=ROOT, env=env, stdout=log, stderr=log,
                                       creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
            children.append(process)
            base = f'http://127.0.0.1:{port}'
            session = requests.Session()
            session.trust_env = False
            session.headers['X-Requested-With'] = 'XMLHttpRequest'
            for _ in range(120):
                if process.poll() is not None:
                    raise RuntimeError('Migration backend exited; inspect migration log.')
                try:
                    response = session.get(base + '/api/auth/me', timeout=1)
                    if response.status_code == 401:
                        break
                except requests.RequestException:
                    pass
                time.sleep(.5)
            response = session.post(base + '/api/auth/login', json={'username': 'admin', 'password': 'Admin@12345'}, timeout=10)
            response.raise_for_status()
            sessions.append((base, session))
        base, session = sessions[0]
        # 通过应用自己的备份/恢复接口搬运数据，而不是直接转换两种数据库的文件。
        response = session.get(base + '/api/system/backup', timeout=30)
        response.raise_for_status()
        archive = backup / 'h2-backup.zip'
        archive.write_bytes(response.content)
        private(archive)
        snapshot = json.loads(zipfile.ZipFile(io.BytesIO(response.content)).read('database.json'))
        base, session = sessions[1]
        response = session.post(base + '/api/system/restore',
                                files={'file': ('backup.zip', archive.read_bytes(), 'application/zip')},
                                data={'confirmation': 'RESTORE'}, timeout=60)
        response.raise_for_status()
        tables = [name for name, value in snapshot.items() if isinstance(value, list)]
        # 先比行数，再逐表比字段值；导出操作本身会增加日志，因此日志只校验行数。
        counts = {}
        for table in tables:
            expected = snapshot[table]
            actual = int(client(config, f'SELECT COUNT(*) FROM `{table}`;', 'yixing').strip())
            if actual != len(expected):
                raise RuntimeError('Migrated row count mismatch: ' + table)
            counts[table] = actual
        session.post(base + '/api/auth/login', json={'username': 'admin', 'password': 'Admin@12345'}, timeout=10).raise_for_status()
        response = session.get(base + '/api/system/backup', timeout=30)
        response.raise_for_status()
        copied = json.loads(zipfile.ZipFile(io.BytesIO(response.content)).read('database.json'))
        for table in tables:
            # 两次导出各自增加操作日志，不能要求日志字段完全相同。
            if table == 'operation_log':
                continue
            if copied[table] != snapshot[table]:
                raise RuntimeError('Migrated field values mismatch: ' + table)
        (HOME / 'migration-results.json').write_text(json.dumps(
            {'counts': counts, 'all_business_fields_equal': True, 'original_h2_unchanged': True,
             'backup': str(archive)}, indent=2), encoding='utf-8')
        print(json.dumps(counts, indent=2))
    finally:
        # 无论成功还是失败，只结束本脚本创建的两个临时后端。
        for child in children:
            if child.poll() is None:
                child.terminate()
        for child in children:
            child.wait(timeout=20)
        for log in logs:
            log.close()


if __name__ == '__main__':
    main()
