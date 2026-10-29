import json
from pathlib import Path
from PIL import Image
from playwright.sync_api import expect


ROOT = Path(__file__).resolve().parents[1]


def verify_order_photos(page, base, data, results, artifacts=None):
    catalog = json.loads((ROOT / 'frontend/src/vehiclePhotoCatalog.js').read_text(encoding='utf-8').split('=', 1)[1])
    for photo in catalog:
        with Image.open(ROOT / ('frontend/public' + photo['src'])) as image:
            assert image.size == (960, 520)
            assert 'A' in image.getbands()
            alpha = image.getchannel('A')
            assert alpha.getextrema() == (0, 255)
            for point in [(0, 0), (959, 0), (0, 519), (959, 519)]:
                assert alpha.getpixel(point) == 0
        assert photo['view'] == 'front-left-three-quarter' and photo['direction'] == 'left'
    results.append('18张车辆素材均为真实透明通道、统一960×520画布与前侧朝左视角')

    page.goto(base + '/#/orders', wait_until='networkidle')
    overview = page.locator('.order-overview')
    expected = [len(data['orders'])] + [sum(order['status'] == status for order in data['orders']) for status in ['RESERVED', 'ACTIVE', 'RETURNED']]
    expect(overview.locator('.order-metric > strong')).to_have_text([str(value) for value in expected])
    styles = overview.locator('.order-metric > strong').evaluate_all('(elements) => elements.map(element => ({color:getComputedStyle(element).color,size:parseFloat(getComputedStyle(element).fontSize),weight:parseInt(getComputedStyle(element).fontWeight)}))')
    assert len({style['color'] for style in styles}) == 4
    assert all(style['size'] >= 32 and style['weight'] >= 650 for style in styles)
    results.append('订单四项统计数值与接口一致，使用四种区分色及加粗数字字体')

    seen = set()
    while True:
        page.wait_for_function('Array.from(document.querySelectorAll(".order-vehicle-cell img")).every(image => image.complete && image.naturalWidth > 0)')
        rows = page.locator('.order-listing .el-table__body-wrapper .el-table__row')
        expect(rows.locator('.order-vehicle-cell img')).to_have_count(rows.count())
        for row in rows.all():
            number = row.locator('.cell-title').first.inner_text()
            order = next(order for order in data['orders'] if order['order_no'] == number)
            car = next(car for car in data['vehicles'] if car['id'] == order['vehicle_id'])
            identity = row.locator('.order-vehicle-cell')
            expect(identity).to_contain_text(car['brand'] + ' ' + car['model'])
            expect(identity).to_contain_text(car['plate'])
            photo = next(photo for photo in catalog if photo['brand'] == car['brand'] and photo['model'] == car['model'])
            assert identity.locator('img').get_attribute('src') == (car.get('image') or photo['src'])
            seen.add(number)
        if artifacts and len(seen) <= 10:
            page.mouse.move(0, 0)
            page.wait_for_timeout(250)
            page.screenshot(path=str(artifacts / 'ui-orders-final.png'), full_page=False)
        next_button = page.locator('.order-listing .el-pagination .btn-next')
        if next_button.is_disabled():
            break
        previous = rows.first.locator('.cell-title').first.inner_text()
        next_button.click()
        expect(rows.first.locator('.cell-title').first).not_to_have_text(previous)
    assert seen == {order['order_no'] for order in data['orders']}
    results.append(f'全部{len(seen)}笔订单逐页核对：每行均有对应车辆图片、品牌车型及业务车牌')

    first = page.locator('.order-listing .el-table__body-wrapper .el-table__row').first
    expected_image = first.locator('.order-vehicle-cell img').get_attribute('src')
    first.get_by_role('button', name='查看 / 办理', exact=True).click()
    drawer = page.locator('.el-drawer:visible')
    expect(drawer.locator('.order-detail-vehicle img')).to_have_attribute('src', expected_image)
    page.wait_for_function('document.querySelector(".el-drawer").getBoundingClientRect().right <= innerWidth + 1')
    if artifacts:
        page.screenshot(path=str(artifacts / 'ui-order-detail-final.png'), full_page=False)
    page.keyboard.press('Escape')
    expect(drawer).not_to_be_visible()

    page.get_by_role('button', name='新建订单', exact=True).click()
    dialog = page.locator('.el-dialog:visible')
    dialog.locator('.el-form-item').filter(has=page.locator('.el-form-item__label', has_text='租赁车辆')).locator('.el-select').click()
    options = page.locator('.order-vehicle-options:visible')
    expect(options.locator('.order-vehicle-option')).to_have_count(len(data['vehicles']))
    page.wait_for_function('Array.from(document.querySelectorAll(".order-vehicle-option img")).every(image => image.complete && image.naturalWidth > 0)')
    page.wait_for_timeout(300)
    if artifacts:
        page.screenshot(path=str(artifacts / 'ui-order-vehicle-picker.png'), full_page=False)
    page.keyboard.press('Escape')
    dialog.get_by_role('button', name='取消', exact=True).click()
    expect(dialog).not_to_be_visible()
    results.append('订单详情与新建订单车辆选择器复用透明车辆图，未提交或修改业务记录')

    for width in [1440, 768, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        assert not page.evaluate('document.documentElement.scrollWidth > innerWidth'), width
        if artifacts and width == 390:
            page.screenshot(path=str(artifacts / 'ui-orders-mobile-final.png'), full_page=True)
    page.set_viewport_size({'width': 1440, 'height': 1000})
    results.append('订单页1440/768/390px无整页横向溢出，表格保留独立横向滚动')
