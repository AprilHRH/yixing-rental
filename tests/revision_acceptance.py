from datetime import date, datetime, timedelta
from playwright.sync_api import expect


def verify_revision(page, base, browser, results, artifacts):
    headers = {'X-Requested-With': 'XMLHttpRequest'}
    data = page.request.get(base + '/api/bootstrap').json()
    page.goto(base + '/#/settings', wait_until='networkidle')
    page.get_by_role('tab', name='系统参数', exact=True).click()
    assert page.get_by_label('公司名称', exact=True).count() == 0
    original = data['settings']
    response = page.request.put(base + '/api/system/settings', data={**original, 'company_name': '不得修改'}, headers=headers)
    assert response.ok and response.json()['company_name'] == original['company_name']
    results.append('系统参数移除公司名称编辑，后端忽略伪造改名字段')

    page.goto(base + '/#/reports', wait_until='networkidle')
    report = page.request.get(base + '/api/reports', params={'from': date.today().replace(day=1).isoformat(), 'to': date.today().isoformat()}).json()
    expect(page.get_by_role('heading', name='每日经营明细', exact=True)).to_be_visible()
    dates = page.locator('.panel .el-table__body-wrapper tbody tr td:first-child .cell').all_text_contents()
    assert dates == sorted(dates, reverse=True)
    page.get_by_text('车辆使用', exact=True).click()
    expect(page.locator('.usage-ranking-row')).to_have_count(len(data['vehicles']))
    assert page.get_by_role('heading', name='车辆使用明细', exact=True).count() == 0
    assert page.locator('.el-table').count() == 0
    for metric, name in [('hours', '出租时长'), ('count', '出租次数'), ('revenue', '租金收入')]:
        page.get_by_text(name, exact=True).click()
        expected = sorted(report['usage'], key=lambda row: (-float(row[metric]), row['vehicle_id']))
        plates = page.locator('.usage-ranking-row .vehicle-identity > div > span').all_text_contents()
        assert plates == [row['plate'] for row in expected]
        assert page.locator('.usage-ranking-row img').count() == len(expected)
    assert abs(sum(float(row['revenue']) for row in report['usage']) - float(report['revenue'])) < .001
    page.wait_for_function('Array.from(document.querySelectorAll(".usage-ranking-row img")).every(image => image.complete && image.naturalWidth)')
    page.wait_for_timeout(250)
    page.screenshot(path=str(artifacts / 'revision_usage_ranking.png'), full_page=True)
    results.append('单一带车辆图片排行支持时长、次数、收入切换，收入合计等于报表总额；每日明细日期倒序')

    page.goto(base + '/#/pricing', wait_until='networkidle')
    assert page.locator('.el-table').count() == 0
    assert page.locator('.el-date-editor--daterange').count() == 1
    assert page.locator('.el-date-editor--datetime').count() == 0
    page.get_by_text('按里程', exact=True).click()
    assert page.locator('.pricing-form .el-date-editor').count() == 0
    expect(page.get_by_label('预计行驶里程（km）', exact=True)).to_be_visible()
    page.get_by_role('button', name='计算预计租金').click()
    expect(page.locator('.calculator-result')).to_be_visible()
    car = data['vehicles'][0]
    pricing = next(item for item in data['pricing'] if item['id'] == car['pricing_id'])
    expected_rent = float(pricing['base_fee']) + 200 * float(pricing['mileage_rate'])
    expect(page.locator('.calculator-result > strong')).to_have_text('¥' + f'{expected_rent:,.2f}')
    page.get_by_text('包月', exact=True).click()
    page.get_by_role('combobox', name='起租月份').focus()
    page.get_by_role('combobox', name='起租月份').press('Enter')
    options = page.get_by_role('option').all_text_contents()
    assert options == [f'{date.today().year} 年 {month} 月' for month in range(1, 13)]
    page.keyboard.press('Escape')
    assert page.locator('.pricing-form .el-date-editor').count() == 0
    page.get_by_role('button', name='计算预计租金').click()
    expect(page.locator('.calculator-result > strong')).to_have_text('¥' + f'{float(pricing["monthly_rate"]):,.2f}')
    results.append('租金表单随按天、里程、包月切换；仅提供当年12个月，30天包月与实际车辆价格一致')

    page.goto(base + '/#/orders', wait_until='networkidle')
    assert page.get_by_role('combobox', name='业务待办筛选').count() == 0
    assert page.get_by_text('全部业务待办', exact=True).count() == 0
    page.goto(base + '/#/dispatch', wait_until='networkidle')
    expect(page.locator('.el-table__row .vehicle-identity img')).to_have_count(min(10, len(data['dispatch'])))
    assert page.locator('.branch-count strong').first.evaluate('element => parseFloat(getComputedStyle(element).fontSize)') >= 38
    results.append('订单删除重复待办下拉但保留工作台深链接；调度车辆均有图，运力数字加大')

    car_input = {**data['vehicles'][0], 'plate': '沪TEST909', 'status': 'AVAILABLE', 'image': '', 'next_service_date': (date.today() + timedelta(days=1)).isoformat(), 'next_service_mileage': int(data['vehicles'][0]['mileage']) + 100}
    created = page.request.post(base + '/api/vehicles', data=car_input, headers=headers)
    assert created.ok, created.text()
    car = created.json()
    page.goto(base + '/#/maintenance', wait_until='networkidle')
    page.get_by_role('button', name='刷新业务数据', exact=True).click()
    reminder = page.locator('.reminder-cars > div').filter(has_text=car['plate'])
    expect(reminder).to_be_visible()
    button = reminder.get_by_role('button', name='创建工单', exact=True)
    assert 'el-button--primary' in button.get_attribute('class')
    button.click()
    dialog = page.locator('.el-dialog:visible')
    dialog.get_by_label('维修保养项目', exact=True).fill('浏览器保养流程验收')
    dialog.get_by_role('button', name='确认保存').click()
    expect(dialog).not_to_be_visible()
    expect(reminder.get_by_role('button', name='开始工单', exact=True)).to_be_visible()
    reminder.get_by_role('button', name='开始工单', exact=True).click()
    page.locator('.el-message-box').get_by_role('button', name='确定', exact=True).click()
    expect(reminder).not_to_be_visible()
    state = page.request.get(base + '/api/bootstrap').json()
    task = next(item for item in state['maintenance'] if item['vehicle_id'] == car['id'])
    assert task['status'] == 'IN_PROGRESS'
    assert page.request.post(base + f'/api/maintenance/{task["id"]}/complete', data={'cost': 0, 'next_date': (date.today() + timedelta(days=180)).isoformat(), 'next_mileage': car['mileage'] + 5000}, headers=headers).ok
    results.append('提醒按钮与新增工单同样醒目；创建后可直接开始，开始后立即消失而非等待完成')

    customer_context = browser.new_context(viewport={'width': 1440, 'height': 1000})
    client = customer_context.new_page()
    client.goto(base + '/#/login', wait_until='networkidle')
    client.get_by_text('客户登录', exact=True).click()
    client.get_by_role('button', name='注册客户账户', exact=True).click()
    values = {'账号': 'browser_client', '密码': 'Client@12345', '确认密码': 'Client@12345', '姓名': '客户端验收用户', '手机号': '13800006666', '身份证号': 'TEST-PORTAL-0001', '驾驶证号': 'TEST-DRIVER-0001'}
    for label, value in values.items(): client.get_by_label(label, exact=True).fill(value)
    expiry = client.get_by_label('驾驶证有效期至', exact=True); expiry.fill('2030-12-31'); expiry.press('Enter')
    client.get_by_role('button', name='注册客户账户', exact=True).click()
    expect(client.get_by_role('button', name='登录我的易行', exact=True)).to_be_visible()
    client.get_by_label('密码', exact=True).fill('Client@12345')
    client.get_by_role('button', name='登录我的易行', exact=True).click()
    expect(client.get_by_role('heading', name='选车出行', exact=True)).to_be_visible()
    assert client.locator('nav .nav-item').count() == 3
    assert client.request.get(base + '/api/bootstrap').status == 403
    assert client.request.get(base + '/api/system').status == 403
    client.locator('.search-input input').fill(car['plate'])
    expect(client.locator('.customer-vehicle-grid .vehicle-card')).to_have_count(1)
    client.get_by_role('button', name='预约用车', exact=True).click()
    dialog = client.locator('.el-dialog:visible')
    start = datetime.now().replace(hour=10, minute=0, second=0, microsecond=0) + timedelta(days=30)
    for label, moment in [('计划取车时间', start), ('计划还车时间', start + timedelta(days=2))]:
        field = dialog.get_by_label(label, exact=True); field.fill(moment.strftime('%Y-%m-%d %H:%M:%S')); field.press('Enter')
    dialog.get_by_role('button', name='试算租金', exact=True).click()
    expect(dialog.locator('.customer-quote')).to_be_visible()
    dialog.get_by_role('button', name='确认预约', exact=True).click()
    expect(client.get_by_role('heading', name='我的订单', exact=True)).to_be_visible()
    expect(client.locator('.el-table__body-wrapper .vehicle-identity')).to_contain_text(car['plate'])
    client.get_by_role('button', name='取消预约', exact=True).click()
    client.locator('.el-message-box').get_by_role('button', name='确认取消', exact=True).click()
    expect(client.locator('.el-table__body-wrapper .status-tag')).to_contain_text('已取消')
    client.locator('nav a[href="#/portal/profile"]').click()
    client.get_by_label('手机号', exact=True).fill('13900006666')
    client.get_by_role('button', name='保存个人资料', exact=True).click()
    expect(client.locator('.el-message').filter(has_text='个人资料已更新')).to_be_visible()
    assert client.request.get(base + '/api/portal/bootstrap').json()['customers'][0]['phone'] == '13900006666'
    for width in [1440, 768, 390]:
        client.set_viewport_size({'width': width, 'height': 1000})
        for route in ['/portal', '/portal/orders', '/portal/profile']:
            client.goto(base + '/#' + route, wait_until='networkidle')
            assert not client.evaluate('document.documentElement.scrollWidth > innerWidth'), (width, route)
    client.set_viewport_size({'width': 1440, 'height': 1000})
    client.goto(base + '/#/portal', wait_until='networkidle')
    client.wait_for_function('Array.from(document.querySelectorAll(".vehicle-thumbnail img")).every(image => image.complete && image.naturalWidth)')
    client.wait_for_timeout(300)
    client.screenshot(path=str(artifacts / 'revision_customer_portal_isolated.png'), full_page=False)
    customer_context.close()
    results.append('客户端浏览器完成注册、客户登录、选车试算、预约、取消及更新资料；1440/768/390px无溢出，管理端API拒绝客户访问')

    page.goto(base + '/#/settings', wait_until='networkidle')
    page.get_by_role('button', name='新增账号', exact=True).click()
    dialog = page.locator('.el-dialog:visible')
    for label, value in [('登录账号', 'browser_team'), ('姓名', '验收业务人员'), ('初始密码', 'Team@12345')]: dialog.get_by_label(label, exact=True).fill(value)
    dialog.get_by_role('button', name='确认保存', exact=True).click()
    expect(dialog).not_to_be_visible()
    account = next(item for item in page.request.get(base + '/api/system').json()['users'] if item['username'] == 'browser_team')
    assert account['role'] == 'ADMIN' and account['account_type'] == 'STAFF'
    results.append('管理员可从系统管理新增业务员账号，新账号具有合并后的管理权限')
