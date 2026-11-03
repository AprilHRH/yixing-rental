"""Run the same backend suite against isolated, real MySQL databases."""
import argparse
import json
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from local_mysql import ROOT, HOME, ensure, environment, client


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--browser', action='store_true', help='Also run browser acceptance (requires Playwright).')
    args = parser.parse_args()
    config, version = ensure()
    stamp = str(time.time_ns())
    # 每次新建带时间标记的独立测试库，测试中的清空和恢复操作不会碰日常演示库。
    rental, portal, account = 'yixing_test_r_' + stamp, 'yixing_test_p_' + stamp, 'yixing_test_a_' + stamp
    for name in (rental, portal, account):
        client(config, f"CREATE DATABASE `{name}` CHARACTER SET utf8mb4; GRANT ALL ON `{name}`.* TO 'yixing'@'localhost';")
    env = {**os.environ, **environment(config)}
    jdk = next(path for path in (ROOT / '.tools').glob('jdk*') if path.is_dir())
    env.update(JAVA_HOME=str(jdk), SPRING_PROFILES_ACTIVE='mysql')
    env['PATH'] = str(jdk / 'bin') + os.pathsep + env['PATH']
    for key, name in [('TEST_RENTAL_DATABASE_URL', rental), ('TEST_PORTAL_DATABASE_URL', portal), ('TEST_ACCOUNT_DATABASE_URL', account)]:
        env[key] = f'jdbc:mysql://127.0.0.1:3307/{name}?serverTimezone=Asia/Shanghai'
    result = subprocess.run([str(ROOT / '.tools/apache-maven-3.9.9/bin/mvn.cmd'),
                             '-f', str(ROOT / 'backend/pom.xml'), 'clean', 'package'],
                            cwd=ROOT, env=env, stdin=subprocess.DEVNULL,
                            creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    suites = []
    # 汇总本轮 Maven 生成的测试结果，存到被忽略的运行目录，便于本地复核。
    for path in (ROOT / 'backend/target/surefire-reports').glob('TEST-*.xml'):
        root = ET.parse(path).getroot()
        suites.append({key: root.get(key) for key in ('name', 'tests', 'failures', 'errors', 'skipped')})
    report = {'mysql_version': version, 'databases': [rental, portal, account],
              'suites': suites, 'exit_code': result.returncode}
    (ROOT / 'data/mysql/test-results.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report, indent=2))
    if result.returncode == 0 and args.browser:
        # 浏览器验收再用第三个独立数据库，避免与后端用例的数据互相干扰。
        name = 'yixing_browser_' + stamp
        client(config, f"CREATE DATABASE `{name}` CHARACTER SET utf8mb4; GRANT ALL ON `{name}`.* TO 'yixing'@'localhost';")
        env.update(BROWSER_DATABASE_URL=f'jdbc:mysql://127.0.0.1:3307/{name}?serverTimezone=Asia/Shanghai',
                   BROWSER_ARTIFACTS=str(HOME / 'browser'), BROWSER_RESULTS=str(HOME / 'browser-results.json'),
                   PYTHONIOENCODING='utf-8')
        raise SystemExit(subprocess.call([sys.executable, str(ROOT / 'tests/browser_acceptance.py')], env=env, cwd=ROOT))
    raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
