import json
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
from ui_preview import fingerprint

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'docs/previews'
BASE = 'http://127.0.0.1:5173'


def ready(page):
    page.wait_for_load_state('networkidle')
    page.wait_for_function('Array.from(document.querySelectorAll(".vehicle-thumbnail img")).every(image => image.complete && image.naturalWidth > 0)')
    page.wait_for_timeout(350)


def main():
    with sync_playwright() as runtime:
        browser = runtime.chromium.launch(channel='msedge', headless=True)
        context = browser.new_context(viewport={'width': 1451, 'height': 1000})
        page = context.new_page()
        errors, resources = [], []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.on('requestfailed', lambda request: resources.append(request.url))
        page.goto(BASE, wait_until='networkidle')
        page.get_by_text('客户登录', exact=True).click()
        page.get_by_role('button', name='注册客户账户', exact=True).click()
        for width in [1451, 768, 390]:
            page.set_viewport_size({'width': width, 'height': 1000})
            assert not page.evaluate('document.documentElement.scrollWidth > innerWidth'), ('registration', width)
        page.set_viewport_size({'width': 1451, 'height': 1000})
        page.screenshot(path=str(ARTIFACTS / 'revision-registration.png'), full_page=True)
        page.get_by_text('管理员登录', exact=True).click()
        page.get_by_role('button', name='管理员 admin', exact=True).click()
        page.get_by_role('button', name='登录工作空间', exact=True).click()
        expect(page.locator('.revenue-total')).to_be_visible()
        baseline = fingerprint(page.request.get(BASE + '/api/bootstrap').json())
        checks = []
        for route, title in [('/', '工作台'), ('/maintenance', '维修保养'), ('/dispatch', '车辆调度'), ('/pricing', '租金计算'), ('/reports', '统计报表'), ('/settings', '系统管理')]:
            page.goto(BASE + '/#' + route, wait_until='networkidle')
            expect(page.get_by_role('heading', name=title, exact=True)).to_be_visible()
            ready(page)
            if route == '/maintenance':
                state = page.request.get(BASE + '/api/bootstrap').json()
                assert page.locator('.maintenance-reminders').count() == (1 if state['reminders'] else 0)
                expect(page.locator('.el-table__row .vehicle-identity img')).to_have_count(min(10, len(state['maintenance'])))
            if route == '/reports':
                page.get_by_text('车辆使用', exact=True).click()
                page.get_by_text('租金收入', exact=True).click()
                ready(page)
                assert page.get_by_role('heading', name='车辆使用明细', exact=True).count() == 0
            if route == '/settings':
                page.get_by_role('tab', name='系统参数', exact=True).click()
                assert page.get_by_label('公司名称', exact=True).count() == 0
            page.screenshot(path=str(ARTIFACTS / ('revision-' + (route.strip('/') or 'dashboard') + '-final.png')), full_page=True)
            for width in [1451, 1280, 768, 390]:
                page.set_viewport_size({'width': width, 'height': 1000})
                ready(page)
                assert not page.evaluate('document.documentElement.scrollWidth > innerWidth'), (route, width)
            page.set_viewport_size({'width': 1451, 'height': 1000})
            checks.append(title + ' 1451/1280/768/390px 无整页溢出、图片加载正常')
        page.goto(BASE + '/#/pricing', wait_until='networkidle')
        page.get_by_text('包月', exact=True).click()
        ready(page)
        assert page.locator('.pricing-form .el-date-editor').count() == 0
        page.screenshot(path=str(ARTIFACTS / 'revision-pricing-monthly-final.png'), full_page=True)
        assert baseline == fingerprint(page.request.get(BASE + '/api/bootstrap').json())
        assert not errors, errors
        assert not resources, resources
        result = {'url': BASE, 'source': 'built production preview, real persistent business database', 'checks': checks, 'business_data_unchanged': True, 'errors': errors, 'failed_resources': resources, 'note': 'Customer registration form previewed without submitting; full customer workflows run against isolated H2 in browser_acceptance.py'}
        (ROOT / 'tests/revision-preview-results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(result, ensure_ascii=False, indent=2))
        context.close()
        browser.close()


if __name__ == '__main__':
    main()
