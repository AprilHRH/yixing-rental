from datetime import date
from uuid import uuid4
from playwright.sync_api import expect


def verify_accounts(page, base, browser, results, artifacts):
    """Operate the actual account dialogs against an isolated acceptance database."""
    page.goto(base + '/#/settings', wait_until='networkidle')
    created = []
    for kind, label in [('ADMIN', '管理员'), ('STAFF', '业务人员'), ('CUSTOMER', '客户')]:
        username = 'ui_' + uuid4().hex[:10]
        page.get_by_role('button', name='新增账号', exact=True).click()
        dialog = page.locator('.el-dialog:visible')
        for field, value in [('登录账号', username), ('姓名', '账号验收' + label), ('初始密码', 'Account@12345')]:
            dialog.get_by_label(field, exact=True).fill(value)
        dialog.locator('.el-form-item').filter(has=page.locator('.el-form-item__label', has_text='账号类型')).locator('.el-select').click()
        page.get_by_role('option', name=label, exact=True).click()
        if kind == 'CUSTOMER':
            for field, value in [('手机号', '13800009999'), ('证件号', 'UI-ID-' + username.replace('_', '-')), ('驾驶证号', 'UI-DL-' + username)]:
                dialog.get_by_label(field, exact=True).fill(value)
            expiry = dialog.get_by_label('驾驶证有效期至', exact=True)
            expiry.fill(f'{date.today().year + 3}-12-31')
            expiry.press('Enter')
        dialog.get_by_role('button', name='确认保存', exact=True).click()
        expect(dialog).not_to_be_visible()
        page.get_by_role('textbox', name='搜索账号', exact=True).fill(username)
        row = page.locator('#pane-users .el-table__row').filter(has_text=username)
        expect(row).to_have_count(1)
        row.get_by_role('button', name='编辑', exact=True).click()
        dialog = page.locator('.el-dialog:visible')
        dialog.get_by_label('姓名', exact=True).fill('修改后的' + label)
        dialog.get_by_label('重置密码（留空不修改）', exact=True).fill('Updated@12345')
        dialog.get_by_role('button', name='确认保存', exact=True).click()
        expect(dialog).not_to_be_visible()
        expect(row).to_contain_text('修改后的' + label)
        account = next(user for user in page.request.get(base + '/api/system').json()['users'] if user['username'] == username)
        assert account['account_type'] == kind
        created.append(account)
    results.append('实际表单创建和编辑管理员、业务人员、客户三类账号；客户档案同步建档')

    page.get_by_role('textbox', name='搜索账号', exact=True).clear()
    page.locator('.el-message').last.wait_for(state='hidden')
    page.get_by_role('heading', name='账号管理', exact=True).click()
    page.screenshot(path=str(artifacts / 'account-management-desktop.png'), full_page=False)
    for account in created[:2]:
        page.get_by_role('textbox', name='搜索账号', exact=True).fill(account['username'])
        row = page.locator('#pane-users .el-table__row').filter(has_text=account['username'])
        row.get_by_role('button', name='删除', exact=True).click()
        confirmation = page.locator('.el-message-box')
        confirmation.locator('input').fill('wrong')
        confirmation.get_by_role('button', name='确认删除', exact=True).click()
        expect(confirmation).to_be_visible()
        confirmation.locator('input').fill(account['username'])
        confirmation.get_by_role('button', name='确认删除', exact=True).click()
        expect(confirmation).not_to_be_visible()
        expect(row).to_have_count(0)
    results.append('管理员与业务人员账号可删除，必须正确输入目标账号进行确认')

    page.set_viewport_size({'width': 390, 'height': 844})
    page.get_by_role('textbox', name='搜索账号', exact=True).clear()
    assert not page.evaluate('document.documentElement.scrollWidth > innerWidth')
    page.get_by_role('button', name='新增账号', exact=True).click()
    dialog = page.locator('.el-dialog:visible')
    expect(dialog.get_by_role('heading', name='新增账号', exact=True)).to_be_visible()
    page.wait_for_timeout(300)
    box = dialog.bounding_box()
    assert box['x'] >= 0 and box['x'] + box['width'] <= 390
    page.screenshot(path=str(artifacts / 'account-create-mobile.png'), full_page=False)
    dialog.get_by_role('button', name='取消', exact=True).click()
    page.set_viewport_size({'width': 1440, 'height': 1080})

    customer = created[-1]
    context = browser.new_context(viewport={'width': 390, 'height': 844})
    client = context.new_page()
    try:
        client.goto(base + '/#/login', wait_until='networkidle')
        client.get_by_text('客户登录', exact=True).click()
        client.get_by_label('账号', exact=True).fill(customer['username'])
        client.get_by_label('密码', exact=True).fill('Updated@12345')
        client.get_by_role('button', name='登录我的易行', exact=True).click()
        expect(client.get_by_role('heading', name='选车出行', exact=True)).to_be_visible()
        client.goto(base + '/#/portal/profile', wait_until='networkidle')
        assert not client.evaluate('document.documentElement.scrollWidth > innerWidth')
        client.get_by_role('button', name='注销账号', exact=True).click()
        dialog = client.locator('.el-dialog:visible')
        expect(dialog.get_by_role('button', name='确认注销', exact=True)).to_be_disabled()
        dialog.get_by_label('当前登录密码', exact=True).fill('wrong-password')
        dialog.get_by_text('我已了解并确认注销此账号', exact=True).click()
        expect(dialog.get_by_role('checkbox', name='我已了解并确认注销此账号', exact=True)).to_be_checked()
        dialog.get_by_role('button', name='确认注销', exact=True).click()
        expect(client.locator('.el-message').filter(has_text='当前密码不正确')).to_be_visible()
        box = dialog.bounding_box()
        assert box['x'] >= 0 and box['x'] + box['width'] <= 390
        client.wait_for_timeout(300)
        client.screenshot(path=str(artifacts / 'account-closure-mobile.png'), full_page=False)
        dialog.get_by_label('当前登录密码', exact=True).fill('Updated@12345')
        dialog.get_by_role('button', name='确认注销', exact=True).click()
        expect(client).to_have_url(base + '/#/login')
        expect(client.get_by_role('radiogroup', name='登录身份', exact=True)).to_be_visible()
        response = client.request.post(base + '/api/auth/login', data={'username': customer['username'], 'password': 'Updated@12345', 'portal': 'CUSTOMER'}, headers={'X-Requested-With': 'XMLHttpRequest'})
        assert response.status == 400
        users = page.request.get(base + '/api/system').json()['users']
        assert not any(user['id'] == customer['id'] for user in users)
        profiles = page.request.get(base + '/api/bootstrap').json()['customers']
        assert any(profile['id'] == customer['customer_id'] for profile in profiles)
    finally:
        context.close()
    results.append('390px账号表单与注销弹窗可用，无横向溢出；校验密码和二次确认，注销后不能登录且客户档案保留')
