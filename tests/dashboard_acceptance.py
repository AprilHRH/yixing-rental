from datetime import datetime, timedelta
from playwright.sync_api import expect
from vehicle_photo_acceptance import verify_vehicle_photos
from order_photo_acceptance import verify_order_photos


def verify_dashboard(page, base, results):
    page.goto(base + '/#/', wait_until='networkidle')
    expect(page.locator('.kpi-card')).to_have_count(4)
    expect(page.locator('.revenue-total')).to_be_visible()
    data = page.request.get(base + '/api/bootstrap').json()
    report = page.request.get(base + '/api/dashboard').json()
    today = page.evaluate('new Date().toLocaleDateString("sv-SE")')
    pickups = sum(order['status'] == 'RESERVED' and order['planned_start'][:10] == today for order in data['orders'])
    returns = sum(order['status'] == 'ACTIVE' and order['planned_end'][:10] == today for order in data['orders'])
    rented = sum(car['status'] == 'RENTED' for car in data['vehicles'])
    for index, amount in enumerate([pickups, returns, rented]):
        assert int(page.locator('.kpi-number').nth(index).evaluate('element => element.firstChild.textContent.trim()')) == amount
    income = float(page.locator('.kpi-number').nth(3).inner_text().replace(',', '').replace('元', '').strip())
    assert abs(income - report['revenue']) < .001
    counts = page.locator('.fleet-state-list a strong').all_text_contents()
    assert sum(int(value.replace('辆', '').strip()) for value in counts) == len(data['vehicles'])
    assert sum(report['vehicle_statuses'].values()) == len(data['vehicles'])
    results.append('首页四项指标、五种车辆状态与业务接口统计一致')
    assert 'rental-forest.webp' in page.locator('.sidebar').evaluate('element => getComputedStyle(element).backgroundImage')
    assert page.locator('.brand-mark').evaluate('element => getComputedStyle(element).backgroundColor') == 'rgb(255, 255, 255)'
    assert page.locator('.shift-create').evaluate('element => getComputedStyle(element).backgroundColor') == 'rgb(207, 75, 10)'
    results.append('摄影蓝灰导航、橙色操作与高对比文字使用统一设计规范')
    ring = page.locator('.fleet-ring canvas')
    assert ring.evaluate('canvas => canvas.width > 0 && canvas.toDataURL().length > 2000')
    expect(page.locator('.fleet-ring-label strong')).to_have_text(str(len(data['vehicles'])))
    results.append('车辆状态环图实际渲染，总数与状态明细、车辆接口一致')
    ring_box = page.locator('.fleet-ring').bounding_box()
    legend_box = page.locator('.fleet-state-list').bounding_box()
    assert legend_box['y'] >= ring_box['y'] + ring_box['height']
    assert abs((ring_box['x'] + ring_box['width'] / 2) - (legend_box['x'] + legend_box['width'] / 2)) < 3
    results.append('车队状态恢复居中环图、下方双列图例的早期布局')
    thumbnails = page.locator('.operation-row .vehicle-thumbnail')
    expect(thumbnails).to_have_count(page.locator('.operation-row').count())
    assert page.locator('.operation-row .vehicle-thumbnail img').count() > 0
    assert page.locator('.operation-row .vehicle-thumbnail img').evaluate_all('images => images.every(image => image.complete && image.naturalWidth > 0)')
    results.append('每条业务待办都有车辆缩略图，现有车型参考图片加载正常')
    colors = page.locator('.kpi-grid .kpi-number').evaluate_all('elements => elements.map(element => getComputedStyle(element).color)')
    assert len(set(colors)) == 3
    results.append('三项运营数字分别采用橙、蓝、青绿色区分')
    expect(page.locator('.dashboard-source')).to_have_count(0)
    expect(page.locator('.page-footer')).to_have_count(0)
    results.append('首页底部数据说明和品牌时间页脚已移除，非隐藏留白')

    category_rows = page.locator('.category-capacity-row')
    expected_categories = {car.get('category') or '未分类' for car in data['vehicles']}
    expect(category_rows).to_have_count(len(expected_categories))
    assert sum(int(value) for value in category_rows.locator('strong').evaluate_all('elements => elements.map(element => element.dataset.total)')) == len(data['vehicles'])
    assert sum(int(value) for value in category_rows.locator('strong').evaluate_all('elements => elements.map(element => element.dataset.available)')) == sum(car['status'] == 'AVAILABLE' for car in data['vehicles'])
    results.append('分类运力按实际车辆类别聚合，可用数量与总数逐项一致')

    task_box = page.locator('.task-panel').bounding_box()
    summary_box = page.locator('.kpi-grid').bounding_box()
    assert 0 <= task_box['y'] - summary_box['y'] - summary_box['height'] <= 24
    assert page.locator('.revenue-panel').bounding_box()['y'] < 900
    results.append('桌面运营摘要与待办无异常留白，收入趋势在首屏下缘可见')

    assert page.locator('.shift-photograph img').evaluate('image => image.complete && image.naturalWidth > 0')
    assert page.locator('.shift-brief').bounding_box()['height'] < 210
    page.get_by_role('button', name='查看今日安排', exact=False).click()
    expect(page.locator('.task-tabs button').nth(1)).to_have_attribute('aria-pressed', 'true')
    results.append('本地品牌摄影加载，紧凑欢迎区与今日安排入口可用')

    page.locator('.task-tabs').get_by_role('button', name='逾期未还', exact=False).click()
    expect(page.locator('.business-empty')).to_contain_text('暂无逾期未还订单')
    page.locator('.task-tabs').get_by_role('button', name='维修保养', exact=False).click()
    work = [task for task in data['maintenance'] if task['status'] in ['PENDING', 'IN_PROGRESS']]
    expect(page.locator('.operation-row')).to_have_count(len(work))
    page.get_by_role('button', name='查看工单', exact=False).first.click()
    expect(page.get_by_role('heading', name='维修保养', exact=True)).to_be_visible()
    expect(page.locator('.el-dialog:visible')).to_contain_text('维修保养详情')
    results.append('待办筛选、空状态及维修工单详情深链接可用')

    page.goto(base + '/#/', wait_until='networkidle')
    page.locator('.kpi-card').first.click()
    expect(page.get_by_role('heading', name='租赁订单', exact=True)).to_be_visible()
    expect(page.locator('.order-scope-note')).to_contain_text('今日待取车')
    expect(page.locator('.el-table__body-wrapper .el-table__row')).to_have_count(pickups)
    results.append('今日待取车统计卡进入同口径订单筛选')

    page.goto(base + '/#/', wait_until='networkidle')
    expect(page.locator('.operation-row')).not_to_have_count(0)
    page.get_by_role('button', name='办理取车', exact=False).first.click()
    expect(page.locator('.el-drawer:visible .order-detail-heading')).to_contain_text('YX-DEMO')
    results.append('待取车操作直接打开对应订单办理抽屉')

    page.goto(base + '/#/', wait_until='networkidle')
    page.locator('.fleet-state-list a').nth(2).click()
    expect(page.get_by_role('heading', name='车辆信息', exact=True)).to_be_visible()
    expect(page.locator('.filter-bar')).to_contain_text('维修中')
    results.append('车队状态入口进入对应车辆筛选')

    page.goto(base + '/#/dispatch', wait_until='networkidle')
    page.locator('.branch-available-link').first.click()
    expect(page.get_by_role('heading', name='车辆信息', exact=True)).to_be_visible()
    expect(page.locator('.filter-bar')).to_contain_text(data['branches'][0]['name'])
    expect(page.locator('.filter-bar')).to_contain_text('可用')
    expected = sum(car['branch_id'] == data['branches'][0]['id'] and car['status'] == 'AVAILABLE' for car in data['vehicles'])
    expect(page.locator('.vehicle-card')).to_have_count(min(expected, 9))
    results.append('门店运力进入同门店可用车辆，不混入其他门店')
    expect(page.locator('.vehicle-branch-scope')).to_contain_text(data['branches'][0]['name'])
    page.locator('.vehicle-branch-scope .el-tag__close').click()
    expect(page.locator('.vehicle-branch-scope')).to_have_count(0)
    assert 'branch=' not in page.url
    expected_all = sum(car['status'] == 'AVAILABLE' for car in data['vehicles'])
    expect(page.locator('.vehicle-card')).to_have_count(min(expected_all, 9))
    results.append('调度深链接保留门店范围，可清除范围且不恢复门店下拉框')
    verify_vehicle_photos(page, base, data['vehicles'], results)
    verify_order_photos(page, base, data, results)


    page.goto(base + '/#/', wait_until='networkidle')
    page.get_by_role('button', name='近7天', exact=True).click()
    expect(page.locator('.revenue-total')).to_be_visible()
    start = (datetime.fromisoformat(today) - timedelta(days=6)).date().isoformat()
    week = page.request.get(base + '/api/reports', params={'from': start, 'to': today}).json()
    expect(page.locator('.revenue-total > span')).to_have_text(start + ' 至 ' + today)
    actual = float(page.locator('.revenue-total > strong').inner_text().replace('¥', '').replace(',', ''))
    assert abs(actual - week['revenue']) < .001
    page.locator('.chart-data summary').click()
    expect(page.locator('.chart-data li')).to_have_count(7)
    page.get_by_role('button', name='本月', exact=True).click()
    expect(page.locator('.chart-data li')).to_have_count(len(report['trend']))
    results.append('近7天与本月收入、日期范围和可访问数据明细一致')

    def fail_report(route):
        route.fulfill(status=503, content_type='application/json', body='{"message":"验收用临时服务异常"}')

    page.route('**/api/dashboard', fail_report)
    page.route('**/api/reports?*', fail_report)
    page.get_by_role('button', name='刷新业务数据', exact=True).click()
    expect(page.locator('.revenue-panel [role="alert"]')).to_contain_text('收入数据加载失败')
    expect(page.locator('.kpi-number').nth(3)).to_contain_text('加载失败')
    expect(page.locator('.task-list')).to_be_visible()
    page.unroute('**/api/dashboard', fail_report)
    page.unroute('**/api/reports?*', fail_report)
    page.get_by_role('button', name='重新加载', exact=True).click()
    expect(page.locator('.revenue-total')).to_be_visible()
    results.append('收入失败不伪装为零，保留业务待办且重试恢复')

    routes = [('/', '工作台'), ('/vehicles', '车辆信息'), ('/customers', '客户管理'), ('/orders', '租赁订单'), ('/pricing', '租金计算'), ('/dispatch', '车辆调度'), ('/maintenance', '维修保养'), ('/reports', '统计报表'), ('/settings', '系统管理')]
    for width in [1440, 1280, 768, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        for route, title in routes:
            page.goto(base + '/#' + route, wait_until='networkidle')
            expect(page.get_by_role('heading', name=title, exact=True)).to_be_visible()
            assert not page.evaluate('document.documentElement.scrollWidth > innerWidth'), (width, route)
            active = page.locator('nav a.active')
            expect(active).to_have_count(1)
            expect(active).to_have_attribute('href', '#' + route)
        results.append(f'{width}px 下九个页面无整页横向溢出，导航选中态准确')
    page.goto(base + '/#/', wait_until='networkidle')
    expect(page.locator('.recent-order-mobile')).to_be_visible()
    expect(page.locator('.recent-order-table')).not_to_be_visible()
    assert page.locator('.task-panel').bounding_box()['y'] < page.locator('.fleet-summary').bounding_box()['y'] < page.locator('.recent-panel').bounding_box()['y']
    page.set_viewport_size({'width': 1440, 'height': 1080})
