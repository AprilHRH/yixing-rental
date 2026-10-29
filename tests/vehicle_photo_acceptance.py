from playwright.sync_api import expect


def verify_vehicle_photos(page, base, vehicles, results, artifacts=None):
    page.goto(base + '/#/vehicles', wait_until='networkidle')
    expect(page.get_by_placeholder('全部门店', exact=True)).to_have_count(0)
    expect(page.locator('.filter-bar .el-select')).to_have_count(1)
    expect(page.locator('.vehicle-branch-scope')).to_have_count(0)
    seen = set()
    while True:
        page.wait_for_function('Array.from(document.querySelectorAll(".vehicle-card img")).every(image => image.complete && image.naturalWidth > 0)')
        cards = page.locator('.vehicle-card')
        assert cards.count() > 0
        for card in cards.all():
            identity = card.locator('.vehicle-title > div').inner_text()
            plate = card.locator('.vehicle-title > div > span').inner_text()
            car = next(car for car in vehicles if car['plate'] == plate)
            assert car['brand'] in identity and car['model'] in identity
            photo = card.locator('.vehicle-photo--card img')
            expect(photo).to_be_visible()
            assert car['model'] in photo.get_attribute('alt')
            assert photo.get_attribute('src').startswith('/images/vehicles/') or car.get('image')
            expect(card.locator('svg.car-art')).to_have_count(0)
            seen.add(plate)
        if artifacts:
            filename = 'ui-vehicles-final.png' if len(seen) <= 9 else 'ui-vehicles-page-2.png'
            page.screenshot(path=str(artifacts / filename), full_page=True)
            if len(seen) <= 9:
                page.screenshot(path=str(artifacts / 'ui-vehicles-viewport.png'), full_page=False)
        next_button = page.locator('.el-pagination .btn-next')
        if next_button.is_disabled():
            break
        previous = cards.first.locator('.vehicle-title').inner_text()
        next_button.click()
        expect(cards.first.locator('.vehicle-title')).not_to_have_text(previous)
    assert seen == {car['plate'] for car in vehicles}
    results.append(f'{len(seen)}辆车辆两页图片加载成功，品牌、车型、车牌与业务记录一一对应')

    first_card = page.locator('.vehicle-card').first
    expected_image = first_card.locator('img').get_attribute('src')
    first_card.locator('.vehicle-card-art').focus()
    page.keyboard.press('Enter')
    detail = page.locator('.el-drawer:visible')
    expect(detail).to_be_visible()
    expect(detail.locator('.vehicle-photo--detail img')).to_have_attribute('src', expected_image)
    page.wait_for_function('document.querySelector(".el-drawer").getBoundingClientRect().right <= innerWidth + 1')
    page.wait_for_function('document.querySelector(".vehicle-photo--detail img").complete && document.querySelector(".vehicle-photo--detail img").naturalWidth > 0')
    if artifacts:
        page.screenshot(path=str(artifacts / 'ui-vehicle-detail-final.png'), full_page=False)
    page.keyboard.press('Escape')
    expect(detail).not_to_be_visible()
    page.get_by_title('列表视图', exact=True).click()
    page.wait_for_function('Array.from(document.querySelectorAll(".vehicle-table-identity img")).every(image => image.complete && image.naturalWidth > 0)')
    expect(page.locator('.vehicle-table-identity img')).to_have_count(page.locator('.el-table__body-wrapper .el-table__row').count())
    results.append('车辆档案与列表复用同一图片，卡片支持键盘Enter查看详情')

    for width in [1440, 768, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        page.get_by_title('卡片视图', exact=True).click()
        assert not page.evaluate('document.documentElement.scrollWidth > innerWidth'), width
        if artifacts and width == 390:
            page.screenshot(path=str(artifacts / 'ui-vehicles-mobile-final.png'), full_page=True)
    page.set_viewport_size({'width': 1440, 'height': 1000})
    results.append('车辆页1440/768/390px无整页溢出，已删除门店下拉筛选')
