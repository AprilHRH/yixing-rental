import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time

from playwright.sync_api import sync_playwright, expect
from dashboard_acceptance import verify_dashboard
from revision_acceptance import verify_revision


ROOT = Path(__file__).resolve().parents[1]
BASE = 'http://127.0.0.1:5174'
ARTIFACTS = ROOT / 'docs/previews'


def wait_port(port, process):
    for attempt in range(120):
        if process.poll() is not None:
            raise RuntimeError(f'Test service exited on port {port}')
        with socket.socket() as connection:
            if connection.connect_ex(('127.0.0.1', port)) == 0:
                return
        time.sleep(.25)
    raise RuntimeError(f'Test service did not start on port {port}')


def select(page, dialog, label, option):
    dialog.locator('.el-form-item').filter(has=page.locator('.el-form-item__label', has_text=label)).locator('.el-select').click()
    page.get_by_role('option', name=option, exact=True).click()


def visit(page, route, title):
    page.goto(BASE + '/#' + route, wait_until='networkidle')
    expect(page.get_by_role('heading', name=title, exact=True)).to_be_visible()
    assert not page.evaluate('document.documentElement.scrollWidth > innerWidth'), route


def main():
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    tools = ROOT / '.tools'
    jdk = next((path for path in tools.glob('jdk*') if path.is_dir()), None)
    java = str(jdk / 'bin/java.exe') if jdk else shutil.which('java')
    node = shutil.which('node')
    assert java and node, 'Java and Node are required'
    browser_candidates = [
        Path('C:/Program Files (x86)/Microsoft/Edge/Application/157.0.4341.24/msedge.exe'),
        Path('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'),
        Path('C:/Program Files/Google/Chrome/Application/chrome.exe'),
    ]
    browser_path = next((str(path) for path in browser_candidates if path.exists()), None)
    children = []
    results = []
    with tempfile.TemporaryDirectory(prefix='yixing-acceptance-') as temporary:
        directory = Path(temporary)
        with (directory / 'backend.log').open('w', encoding='utf-8') as backend_log, (directory / 'frontend.log').open('w', encoding='utf-8') as frontend_log:
            try:
                flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
                children.append(subprocess.Popen([java, '-jar', str(ROOT / 'backend/target/rental-system-1.0.0.jar'), '--server.port=8081', '--spring.datasource.url=jdbc:h2:mem:browser;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1', '--app.allowed-origins=' + BASE], cwd=directory, stdout=backend_log, stderr=subprocess.STDOUT, creationflags=flags))
                environment = {**os.environ, 'FRONTEND_PORT': '5174', 'API_TARGET': 'http://127.0.0.1:8081'}
                children.append(subprocess.Popen([node, str(ROOT / 'frontend/node_modules/vite/bin/vite.js')], cwd=ROOT / 'frontend', env=environment, stdout=frontend_log, stderr=subprocess.STDOUT, creationflags=flags))
                wait_port(8081, children[0]); wait_port(5174, children[1])
                with sync_playwright() as playwright:
                    launch = {'headless': True, 'args': ['--disable-gpu']}
                    if browser_path:
                        launch['executable_path'] = browser_path
                    browser = playwright.chromium.launch(**launch)
                    context = browser.new_context(viewport={'width': 1440, 'height': 1080}, record_video_dir=str(ARTIFACTS / 'video'), record_video_size={'width': 1280, 'height': 960})
                    page = context.new_page()
                    errors = []
                    page.on('pageerror', lambda error: errors.append(str(error)))
                    page.goto(BASE, wait_until='networkidle')
                    page.get_by_role('button', name='登录工作空间').click()
                    expect(page.get_by_role('heading', name='工作台', exact=True)).to_be_visible()
                    results.append('管理员登录及工作台加载')
                    verify_dashboard(page, BASE, results)
                    for route, title in [('/vehicles', '车辆信息'), ('/customers', '客户管理'), ('/orders', '租赁订单'), ('/pricing', '租金计算'), ('/dispatch', '车辆调度'), ('/maintenance', '维修保养'), ('/reports', '统计报表'), ('/settings', '系统管理')]:
                        visit(page, route, title)
                        results.append(title + '页面加载及布局检查')
                    verify_revision(page, BASE, browser, results, ARTIFACTS)
                    visit(page, '/pricing', '租金计算')
                    page.get_by_role('button', name='计算预计租金').click()
                    expect(page.locator('.calculator-result')).to_be_visible()
                    results.append('后端真实报价接口接入')
                    visit(page, '/customers', '客户管理')
                    page.get_by_role('button', name='新增客户').click()
                    dialog = page.locator('.el-dialog:visible')
                    for label, value in [('客户姓名', '浏览器验收客户'), ('联系电话', '13800008888'), ('身份证 / 证件号', 'DEMO-E2E-CUSTOMER'), ('驾驶证编号', 'DEMO-E2E-DRIVER')]:
                        dialog.get_by_label(label, exact=True).fill(value)
                    date_input = dialog.get_by_label('驾驶证有效期', exact=True)
                    date_input.fill('2030-12-31'); date_input.press('Enter')
                    dialog.get_by_role('button', name='确认保存').click()
                    expect(dialog).not_to_be_visible()
                    page.locator('input[placeholder="搜索姓名、联系方式"]').fill('浏览器验收客户')
                    expect(page.get_by_role('cell', name='浏览器验收客户', exact=False)).to_be_visible()
                    results.append('客户登记及真实数据库查询')
                    visit(page, '/vehicles', '车辆信息')
                    page.get_by_role('button', name='新增车辆').click()
                    dialog = page.locator('.el-dialog:visible')
                    for label, value in [('车牌号', '沪TEST888'), ('品牌', '测试品牌'), ('车型名称', '浏览器验收车'), ('颜色', '银灰'), ('行驶证编号', 'DEMO-E2E-VEHICLE')]:
                        dialog.get_by_label(label, exact=True).fill(value)
                    date_input = dialog.get_by_label('购置日期', exact=True)
                    date_input.fill('2025-01-01'); date_input.press('Enter')
                    dialog.get_by_role('button', name='确认保存').click()
                    expect(dialog).not_to_be_visible()
                    visit(page, '/orders', '租赁订单')
                    page.get_by_role('button', name='新建订单', exact=True).click()
                    dialog = page.locator('.el-dialog:visible')
                    select(page, dialog, '租赁客户', '浏览器验收客户')
                    select(page, dialog, '租赁车辆', '沪TEST888 · 浏览器验收车')
                    expect(dialog.locator('.quote-strip')).to_be_visible()
                    dialog.get_by_role('button', name='确认创建订单').click()
                    expect(dialog).not_to_be_visible()
                    drawer = page.locator('.el-drawer:visible')
                    expect(drawer.get_by_text('浏览器验收客户', exact=True)).to_be_visible()
                    drawer.get_by_role('button', name='登记收款', exact=True).click()
                    dialog = page.locator('.el-dialog:visible')
                    dialog.get_by_role('button', name='确认保存').click()
                    expect(dialog).not_to_be_visible()
                    drawer.get_by_role('button', name='办理交车', exact=True).click()
                    dialog = page.locator('.el-dialog:visible')
                    dialog.get_by_role('button', name='确认保存').click()
                    expect(dialog).not_to_be_visible()
                    expect(drawer.locator('.order-detail-heading')).to_contain_text('租赁中')
                    drawer.get_by_role('button', name='办理还车', exact=True).click()
                    dialog = page.locator('.el-dialog:visible')
                    dialog.get_by_label('还车里程（km）', exact=True).fill('120')
                    dialog.get_by_role('button', name='确认保存').click()
                    expect(dialog).not_to_be_visible()
                    expect(drawer.locator('.order-detail-heading')).to_contain_text('待结算')
                    drawer.get_by_role('button', name='确认收退款并结算').click()
                    page.get_by_role('button', name='已处理，确认', exact=True).click()
                    expect(drawer.locator('.order-detail-heading')).to_contain_text('已完成')
                    page.wait_for_timeout(3500)
                    page.screenshot(path=str(ARTIFACTS / 'order-settlement-verified.png'), full_page=False)
                    results.append('创建订单、收押金、交车、还车、退款结算完整浏览器闭环')
                    page.reload(wait_until='networkidle')
                    data = page.request.get(BASE + '/api/bootstrap').json()
                    order = next(order for order in data['orders'] if any(customer['id'] == order['customer_id'] and customer['name'] == '浏览器验收客户' for customer in data['customers']))
                    assert order['status'] == 'COMPLETED'
                    net = sum(payment['amount'] * (1 if payment['direction'] == 'IN' else -1) for payment in data['payments'] if payment['order_id'] == order['id'])
                    assert abs(net - order['actual_rent']) < .001
                    results.append('刷新后订单持久化、费用与流水一致')
                    page.set_viewport_size({'width': 390, 'height': 844})
                    visit(page, '/', '工作台')
                    page.set_viewport_size({'width': 1440, 'height': 1080})
                    results.append('390px 窄屏工作台无横向溢出')
                    staff = browser.new_context(viewport={'width': 1440, 'height': 1080})
                    staff_page = staff.new_page()
                    staff_requests = []
                    staff_page.on('request', lambda request: staff_requests.append(request.url))
                    staff_page.goto(BASE, wait_until='networkidle')
                    staff_page.get_by_role('button', name='业务人员 staff').click()
                    with staff_page.expect_response(lambda response: response.url.endswith('/api/dashboard')) as staff_dashboard:
                        staff_page.get_by_role('button', name='登录工作空间').click()
                    expect(staff_page.get_by_role('heading', name='工作台', exact=True)).to_be_visible()
                    assert staff_page.locator('nav a[href="#/settings"]').count() == 1
                    assert staff_page.request.get(BASE + '/api/system').status == 200
                    expect(staff_page.locator('.revenue-panel')).to_be_visible()
                    staff_page.wait_for_load_state('networkidle')
                    assert staff_dashboard.value.ok
                    assert 'revenue' in staff_dashboard.value.json()
                    assert staff_page.locator('.staff-guide').count() == 0
                    results.append('业务人员与管理员权限合并，系统管理接口可访问')
                    results.append('业务人员与管理员使用同一完整工作台和经营报表')
                    assert not errors, errors
                    staff.close(); context.close(); browser.close()
                report = {'passed': len(results), 'checks': results, 'browser_errors': errors, 'database': 'isolated in-memory H2; demo database unchanged'}
                (ROOT / 'tests/browser-results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
                print(json.dumps(report, ensure_ascii=False, indent=2))
            except Exception:
                print((directory / 'backend.log').read_text(encoding='utf-8', errors='replace')[-3000:])
                raise
            finally:
                for child in children:
                    if child.poll() is None:
                        child.terminate()
                for child in children:
                    child.wait(timeout=15)


if __name__ == '__main__':
    main()
