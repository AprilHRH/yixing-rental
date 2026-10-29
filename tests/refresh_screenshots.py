import hashlib
import json
from datetime import datetime
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'docs/previews/current'
BASE = 'http://127.0.0.1:5173'


def fingerprint(data):
    fields = ['vehicles', 'customers', 'orders', 'payments', 'dispatch', 'maintenance']
    return hashlib.sha256(json.dumps({key: data[key] for key in fields}, sort_keys=True).encode()).hexdigest()


def main():
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    screenshots = []
    with sync_playwright() as runtime:
        browser = runtime.chromium.launch(channel='msedge', headless=True)
        page = browser.new_page(viewport={'width': 1451, 'height': 900}, device_scale_factor=1)
        errors, resources = [], []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.on('requestfailed', lambda request: resources.append(request.url))
        page.on('response', lambda response: resources.append(str(response.status) + ' ' + response.url) if response.status >= 400 and not (response.status == 401 and response.url.endswith('/api/auth/me')) else None)

        def capture(filename, full=False):
            page.wait_for_load_state('networkidle')
            page.wait_for_function('Array.from(document.images).every(image => image.complete && image.naturalWidth > 0)')
            page.evaluate('window.scrollTo(0, 0)')
            page.mouse.move(700, 20)
            page.wait_for_timeout(400)
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), filename
            page.screenshot(path=str(ARTIFACTS / filename), full_page=full)
            screenshots.append({'file': filename, 'url': page.url, 'viewport': page.viewport_size})

        page.goto(BASE + '/#/login', wait_until='networkidle')
        capture('login.png')
        page.get_by_text('客户登录', exact=True).click()
        capture('customer-login.png')
        page.get_by_role('button', name='注册客户账户', exact=True).click()
        capture('registration.png', True)
        page.get_by_text('管理员登录', exact=True).click()
        page.get_by_role('button', name='管理员 admin', exact=True).click()
        page.get_by_role('button', name='登录工作空间', exact=True).click()
        expect(page.locator('.revenue-total')).to_be_visible()
        before = fingerprint(page.request.get(BASE + '/api/bootstrap').json())
        capture('dashboard.png')
        capture('dashboard-full.png', True)
        for route, title in [('vehicles', '车辆信息'), ('customers', '客户管理'), ('orders', '租赁订单'), ('pricing', '租金计算'), ('dispatch', '车辆调度'), ('maintenance', '维修保养'), ('reports', '统计报表'), ('settings', '系统管理')]:
            page.goto(BASE + '/#/' + route, wait_until='networkidle')
            expect(page.get_by_role('heading', name=title, exact=True)).to_be_visible()
            capture(route + '.png')
            if route == 'orders':
                page.locator('.order-listing .el-table__row').first.get_by_role('button', name='查看 / 办理', exact=True).click()
                expect(page.locator('.el-drawer:visible')).to_be_visible()
                capture('order-detail.png')
                page.keyboard.press('Escape')
            if route == 'pricing':
                page.get_by_text('包月', exact=True).click()
                capture('pricing-monthly.png')
            if route == 'reports':
                page.get_by_text('车辆使用', exact=True).click()
                page.get_by_text('租金收入', exact=True).click()
                capture('report-usage.png')
            if route == 'settings':
                page.get_by_role('tab', name='系统参数', exact=True).click()
                capture('settings-parameters.png')
        page.set_viewport_size({'width': 390, 'height': 844})
        for route in ['', 'vehicles', 'orders']:
            page.goto(BASE + '/#/' + route, wait_until='networkidle')
            capture('mobile-' + (route or 'dashboard') + '.png', True)
        assert before == fingerprint(page.request.get(BASE + '/api/bootstrap').json())
        assert not errors, errors
        assert not resources, resources
        result = {'captured_at': datetime.now().isoformat(timespec='seconds'), 'source': 'Current production build and existing persistent database; read-only capture, no fabricated records', 'unchanged_business_data': True, 'screenshots': screenshots, 'page_errors': errors, 'failed_resources': resources}
        (ARTIFACTS / 'manifest.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        print('PASS:', len(screenshots), 'current screenshots; no broken images, page overflow or script errors; business records unchanged.')
        browser.close()


if __name__ == '__main__':
    main()
