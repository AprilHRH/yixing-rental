import hashlib
import json
from datetime import datetime
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
from vehicle_photo_acceptance import verify_vehicle_photos
from order_photo_acceptance import verify_order_photos


ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'docs/previews'
BASE = 'http://127.0.0.1:5173'


def fingerprint(data):
    fields = ['vehicles', 'customers', 'orders', 'payments', 'dispatch', 'maintenance']
    content = json.dumps({key: data[key] for key in fields}, sort_keys=True)
    return hashlib.sha256(content.encode()).hexdigest()


def main():
    with sync_playwright() as runtime:
        browser = runtime.chromium.launch(executable_path='C:/Program Files (x86)/Microsoft/Edge/Application/157.0.4341.24/msedge.exe', headless=True)
        context = browser.new_context(viewport={'width': 1440, 'height': 1000}, device_scale_factor=1)
        page = context.new_page()
        errors, failed_requests = [], []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.on('requestfailed', lambda request: failed_requests.append(request.url))
        page.on('response', lambda response: failed_requests.append(f'{response.status} {response.url}') if response.status >= 400 and not (response.status == 401 and response.url.endswith('/api/auth/me')) else None)
        page.goto(BASE, wait_until='networkidle')
        expect(page.locator('.login-photograph img')).to_be_visible()
        page.wait_for_function('document.querySelector(".login-photograph img").complete && document.querySelector(".login-photograph img").naturalWidth > 0')
        page.screenshot(path=str(ARTIFACTS / 'ui-login-final.png'), full_page=False)
        page.get_by_role('button', name='管理员 admin').click()
        page.get_by_role('button', name='登录工作空间').click()
        expect(page.locator('.revenue-total')).to_be_visible()
        baseline = fingerprint(page.request.get(BASE + '/api/bootstrap').json())
        page.wait_for_function('Array.from(document.querySelectorAll(".operation-row .vehicle-thumbnail img")).every(image => image.complete && image.naturalWidth > 0)')
        assert page.locator('.operation-row .vehicle-thumbnail img').count() > 0
        assert page.locator('.dashboard-source, .page-footer').count() == 0

        page.mouse.move(0, 0)
        page.wait_for_timeout(400)
        assert page.locator('.shift-photograph img').evaluate('image => image.complete && image.naturalWidth > 0')
        page.screenshot(path=str(ARTIFACTS / 'ui-dashboard-viewport.png'), full_page=False)
        page.screenshot(path=str(ARTIFACTS / 'ui-dashboard-final.png'), full_page=True)
        assert page.locator('.fleet-ring canvas').evaluate('canvas => canvas.width > 0')
        assert 'rental-forest.webp' in page.locator('.sidebar').evaluate('element => getComputedStyle(element).backgroundImage')
        assert page.locator('.revenue-panel canvas').evaluate('canvas => canvas.width > 0 && canvas.toDataURL().length > 2000')
        page.locator('.task-tabs button').first.focus()
        page.keyboard.press('Tab')
        assert page.evaluate('document.activeElement.textContent.includes("今日交还")')
        page.keyboard.press('Enter')
        expect(page.locator('.task-tabs button').nth(1)).to_have_attribute('aria-pressed', 'true')
        for route, title, filename in [('/dispatch', '车辆调度', 'ui-dispatch-final.png'), ('/vehicles', '车辆信息', 'ui-vehicles-final.png')]:
            page.locator(f'nav a[href="#{route}"]').click()
            expect(page.get_by_role('heading', name=title, exact=True)).to_be_visible()
            page.mouse.move(0, 0)
            page.wait_for_timeout(250)
            page.screenshot(path=str(ARTIFACTS / filename), full_page=False)
        vehicle_checks = []
        verify_vehicle_photos(page, BASE, page.request.get(BASE + '/api/bootstrap').json()['vehicles'], vehicle_checks, ARTIFACTS)
        order_checks = []
        verify_order_photos(page, BASE, page.request.get(BASE + '/api/bootstrap').json(), order_checks, ARTIFACTS)
        page.set_viewport_size({'width': 390, 'height': 844})
        page.locator('nav a[href="#/"]').click()
        expect(page.locator('.revenue-total')).to_be_visible()
        expect(page.locator('.recent-order-mobile')).to_be_visible()
        page.wait_for_timeout(400)
        assert not page.evaluate('document.documentElement.scrollWidth > innerWidth')
        page.screenshot(path=str(ARTIFACTS / 'ui-mobile-final.png'), full_page=True)
        after = fingerprint(page.request.get(BASE + '/api/bootstrap').json())
        assert baseline == after, 'Read-only preview must not change business records'
        assert not errors, errors
        assert not failed_requests, failed_requests
        result = {'order_checks': order_checks, 'vehicle_checks': vehicle_checks, 'url': BASE, 'source': 'built production preview; existing persistent database', 'business_data_unchanged': baseline == after, 'keyboard_filter': 'Tab and Enter verified', 'photography': 'local WebP assets; login and dashboard loaded', 'task_photos': 'vehicle records first, clearly labeled local model references as fallback; loaded', 'homepage_footer': 'removed from DOM', 'chart_canvas': 'centered fleet donut, lower legend and revenue bars rendered', 'design_reference': 'updated user direction; photographic blue-slate navigation, clear blue-white surfaces and burnt-orange actions' , 'mobile': '390px; responsive order cards; no page overflow', 'browser_errors': errors, 'failed_resources': failed_requests, 'screenshots': ['ui-login-final.png', 'ui-dashboard-final.png', 'ui-dispatch-final.png', 'ui-vehicles-final.png', 'ui-vehicles-page-2.png', 'ui-vehicles-viewport.png', 'ui-vehicle-detail-final.png', 'ui-vehicles-mobile-final.png', 'ui-orders-final.png', 'ui-order-detail-final.png', 'ui-order-vehicle-picker.png', 'ui-orders-mobile-final.png', 'vehicle-photo-catalog.png', 'ui-mobile-final.png']}
        (ROOT / 'tests/ui-preview-results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        storage = context.storage_state()
        context.close()

        recording = browser.new_context(viewport={'width': 1440, 'height': 1000}, storage_state=storage, record_video_dir=str(ARTIFACTS / 'video'), record_video_size={'width': 1440, 'height': 1000})
        demo = recording.new_page()
        demo.goto(BASE + '/#/', wait_until='networkidle')
        expect(demo.locator('.revenue-total')).to_be_visible()
        demo.wait_for_timeout(800)
        demo.locator('.kpi-card').first.click()
        expect(demo.get_by_role('heading', name='租赁订单', exact=True)).to_be_visible()
        demo.wait_for_timeout(700)
        demo.locator('nav a[href="#/orders"]').click()
        expect(demo.locator('.order-overview')).to_be_visible()
        demo.locator('.order-listing .el-table__row').first.get_by_role('button', name='查看 / 办理', exact=True).click()
        expect(demo.locator('.order-detail-vehicle img')).to_be_visible()
        demo.wait_for_timeout(900)
        demo.keyboard.press('Escape')
        demo.locator('nav a[href="#/vehicles"]').click()
        expect(demo.locator('.vehicle-grid')).to_be_visible()
        demo.wait_for_timeout(1000)
        demo.locator('nav a[href="#/"]').click()
        expect(demo.locator('.revenue-total')).to_be_visible()
        demo.locator('.task-tabs').get_by_role('button', name='维修保养', exact=False).click()
        demo.wait_for_timeout(700)
        demo.locator('.task-tabs').get_by_role('button', name='全部待办', exact=False).click()
        demo.locator('.revenue-panel').scroll_into_view_if_needed()
        demo.get_by_role('button', name='近7天', exact=True).click()
        demo.locator('.chart-data summary').click()
        expect(demo.locator('.chart-data li')).to_have_count(7)
        demo.wait_for_timeout(900)
        demo.locator('nav a[href="#/dispatch"]').click()
        expect(demo.get_by_role('heading', name='车辆调度', exact=True)).to_be_visible()
        demo.wait_for_timeout(900)
        video_path = Path(demo.video.path())
        recording.close()
        video_name = 'ui-operations-walkthrough-' + datetime.now().strftime('%Y%m%d-%H%M%S') + '.webm'
        video_path.rename(ARTIFACTS / 'video' / video_name)
        result['video'] = 'video/' + video_name
        (ROOT / 'tests/ui-preview-results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        browser.close()
        print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
