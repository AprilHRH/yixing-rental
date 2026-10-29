import hashlib
import json
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
BASE = 'http://127.0.0.1:5173'
ARTIFACTS = ROOT / 'docs/previews'


def contrast(foreground, background):
    def luminance(value):
        channels = [float(channel) / 255 for channel in re.findall(r'[\d.]+', value)[:3]]
        linear = [channel / 12.92 if channel <= 0.04045 else ((channel + 0.055) / 1.055) ** 2.4 for channel in channels]
        return sum(channel * weight for channel, weight in zip(linear, [.2126, .7152, .0722]))
    values = sorted([luminance(foreground), luminance(background)])
    return (values[1] + .05) / (values[0] + .05)


def fingerprint(data):
    return hashlib.sha256(json.dumps({key: data[key] for key in ['vehicles', 'customers', 'orders', 'payments', 'dispatch', 'maintenance']}, sort_keys=True).encode()).hexdigest()


def main():
    with sync_playwright() as runtime:
        browser = runtime.chromium.launch(channel='msedge', headless=True)
        page = browser.new_page(viewport={'width': 1451, 'height': 900})
        errors = []
        failures = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.on('requestfailed', lambda request: failures.append(request.url))
        page.on('response', lambda response: failures.append(str(response.status) + ' ' + response.url) if response.status >= 400 and not (response.status == 401 and response.url.endswith('/api/auth/me')) else None)
        page.goto(BASE + '/#/login', wait_until='networkidle')
        page.get_by_role('button', name='管理员 admin', exact=True).click()
        page.get_by_role('button', name='登录工作空间', exact=True).click()
        expect(page.locator('.revenue-total')).to_be_visible()
        before = fingerprint(page.request.get(BASE + '/api/bootstrap').json())
        results = []
        for route, selector, filename in [('/', '.kpi-number', 'bright-dashboard.png'), ('/orders', '.module-overview strong', 'bright-orders.png'), ('/reports', '.report-stats .stat-value', 'bright-reports.png'), ('/dispatch', '.branch-count strong', 'bright-dispatch.png'), ('/vehicles', '.daily-price', 'bright-vehicles.png')]:
            page.goto(BASE + '/#' + route, wait_until='networkidle')
            expect(page.locator(selector).first).to_be_visible()
            page.wait_for_function('Array.from(document.querySelectorAll(".vehicle-thumbnail img")).every(image => image.complete && image.naturalWidth > 0)')
            page.wait_for_timeout(350)
            styles = page.locator(selector).evaluate_all('(elements) => elements.map(element => ({ color: getComputedStyle(element).color, size: parseFloat(getComputedStyle(element).fontSize), weight: parseInt(getComputedStyle(element).fontWeight) }))')
            for style in styles:
                threshold = 3 if style['size'] >= 24 or (style['size'] >= 18.66 and style['weight'] >= 700) else 4.5
                assert contrast(style['color'], 'rgb(255, 255, 255)') >= threshold, (route, style)
            if route in ['/', '/orders']:
                assert len({style['color'] for style in styles}) >= 3
            heading = page.locator('h1').first
            heading_color = heading.evaluate('element => getComputedStyle(element).color')
            assert contrast(heading_color, 'rgb(247, 249, 252)') >= 7
            if route == '/':
                button = page.locator('.shift-create')
                colors = button.evaluate('element => ({text:getComputedStyle(element).color, bg:getComputedStyle(element).backgroundColor})')
                assert contrast(colors['text'], colors['bg']) >= 4.5, colors
                page.get_by_role('button', name='本月', exact=True).click()
                expect(page.get_by_role('button', name='本月', exact=True)).to_have_attribute('aria-pressed', 'true')
                page.get_by_role('button', name='近7天', exact=True).click()
            for button in page.locator('.el-button--primary:not(.is-link):not(.is-text):not(.is-plain):not(.is-disabled)').all():
                colors = button.evaluate('element => ({text:getComputedStyle(element.querySelector("span") || element).color, bg:getComputedStyle(element).backgroundColor})')
                assert contrast(colors['text'], colors['bg']) >= 4.5, (route, colors)
            for width in [1451, 1024, 768, 390]:
                page.set_viewport_size({'width': width, 'height': 900})
                page.wait_for_timeout(180)
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), (route, width)
                if width == 1451:
                    page.evaluate('window.scrollTo(0, 0)')
                    page.wait_for_timeout(120)
                    page.screenshot(path=str(ARTIFACTS / filename), full_page=False)
            page.set_viewport_size({'width': 1451, 'height': 900})
            results.append({'route': route, 'numbers': styles, 'responsiveWidths': [1451, 1024, 768, 390]})
        assert before == fingerprint(page.request.get(BASE + '/api/bootstrap').json())
        assert not errors, errors
        assert not failures, failures
        print(json.dumps({'passed': True, 'unchangedBusinessData': True, 'pages': results}, ensure_ascii=False, indent=2))
        browser.close()


if __name__ == '__main__':
    main()
