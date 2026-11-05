import test from 'node:test'
import assert from 'node:assert/strict'
import { webcrypto } from 'node:crypto'
import { createRequestId } from './requestId.js'

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

test('HTTPS 和 localhost 优先使用原生请求编号', () => {
  const crypto = { randomUUID() { assert.equal(this, crypto); return 'native-request-id' } }
  assert.equal(createRequestId(crypto), 'native-request-id')
})

test('HTTP 缺少 randomUUID 时仍生成格式正确且不重复的编号', () => {
  const crypto = { getRandomValues: bytes => webcrypto.getRandomValues(bytes) }
  const ids = Array.from({ length: 10000 }, () => createRequestId(crypto))
  assert.equal(new Set(ids).size, ids.length)
  assert.ok(ids.every(id => uuid.test(id)))
})

test('兼容编号正确补零并设置 UUID 版本与变体', () => {
  assert.equal(createRequestId({ getRandomValues: bytes => bytes.fill(0) }), '00000000-0000-4000-8000-000000000000')
})

test('随机接口完全不可用时给出可读错误，不降级为弱随机数', () => {
  for (const crypto of [null, {}]) {
    assert.throws(() => createRequestId(crypto), /当前浏览器无法生成操作编号/)
  }
})
