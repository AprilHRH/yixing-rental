from pathlib import Path
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
BASE = 'http://127.0.0.1:5173'


def main():
    with sync_playwright() as runtime:
        browser = runtime.chromium.launch(channel='msedge', headless=True)
        page = browser.new_page(viewport={'width': 1451, 'height': 783})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(BASE + '/#/login', wait_until='networkidle')
        page.get_by_role('button', name='管理员 admin', exact=True).click()
        page.get_by_role('button', name='登录工作空间', exact=True).click()
        expect(page.locator('.revenue-total')).to_be_visible()
        page.goto(BASE + '/#/vehicles', wait_until='networkidle')
        sidebar = page.locator('.sidebar')
        expect(page.locator('.vehicle-grid')).to_be_visible()
        page.wait_for_function('Array.from(document.querySelectorAll(".vehicle-thumbnail img")).every(image => image.complete && image.naturalWidth > 0)')
        initial = sidebar.evaluate('element => ({width:element.getBoundingClientRect().width, thumb:getComputedStyle(element,"::-webkit-scrollbar-thumb").backgroundColor, track:getComputedStyle(element,"::-webkit-scrollbar-track").backgroundColor, scrollbar:getComputedStyle(element,"::-webkit-scrollbar").width})')
        assert initial['scrollbar'] == '6px', initial
        assert initial['track'] == 'rgba(0, 0, 0, 0)', initial
        sidebar.hover(position={'x': 100, 'y': 300})
        hover = sidebar.evaluate('element => getComputedStyle(element,"::-webkit-scrollbar-thumb").backgroundColor')
        assert hover != initial['thumb'], (initial, hover)
        assert sidebar.evaluate('element => element.getBoundingClientRect().width') == initial['width']
        page.screenshot(path=str(ROOT / 'docs/previews/sidebar-scrollbar.png'), full_page=False)
        for height in [783, 560]:
            page.set_viewport_size({'width': 1451, 'height': height})
            sidebar.evaluate('element => element.scrollTop = 0')
            assert sidebar.evaluate('element => element.scrollHeight > element.clientHeight')
            sidebar.hover(position={'x': 100, 'y': 250})
            document_scroll = page.evaluate('window.scrollY')
            page.mouse.wheel(0, 1200)
            page.wait_for_timeout(250)
            assert sidebar.evaluate('element => element.scrollTop > 0')
            assert page.evaluate('window.scrollY') == document_scroll
            settings = page.locator('nav a[href="#/settings"]')
            settings.focus()
            expect(settings).to_be_in_viewport()
            page.keyboard.press('Enter')
            expect(page.get_by_role('heading', name='系统管理', exact=True)).to_be_visible()
            page.goto(BASE + '/#/vehicles', wait_until='networkidle')
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        page.emulate_media(forced_colors='active')
        assert sidebar.evaluate('element => getComputedStyle(element).scrollbarColor') == 'auto'
        assert not errors, errors
        browser.close()
        print('PASS: 6px transparent-track scrollbar, hover feedback without layout shift, independent wheel scroll at 783/560px, keyboard navigation, forced-colors fallback; no page errors.')


if __name__ == '__main__':
    main()
