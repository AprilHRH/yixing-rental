import test from 'node:test'
import assert from 'node:assert/strict'
import { buildOperations, categorySummary, dayKey, fleetSummary, matchesOrderScope, recentOrders, revenueSeries, weekStart } from './dashboard.js'

import { vehiclePhotoSources } from './vehiclePhotos.js'
import { vehiclePhotoCatalog } from './vehiclePhotoCatalog.js'
import { readFileSync } from 'node:fs'

const now = new Date('2026-10-28T12:00:00')

test('今日交还车按计划日期与未完成状态统计，不混入已还车订单', () => {
  const pickup = { status: 'RESERVED', planned_start: '2026-10-28T16:00:00' }
  assert.equal(matchesOrderScope(pickup, 'pickup-today', now), true)
  assert.equal(matchesOrderScope({ ...pickup, status: 'CANCELLED' }, 'pickup-today', now), false)
  assert.equal(matchesOrderScope({ status: 'ACTIVE', planned_end: '2026-10-28T18:00:00' }, 'return-today', now), true)
  assert.equal(matchesOrderScope({ status: 'RETURNED', planned_end: '2026-10-28T18:00:00' }, 'return-today', now), false)
})

test('逾期依据精确时间判断，包含昨日未归还但不包含今日尚未到期', () => {
  assert.equal(matchesOrderScope({ status: 'ACTIVE', planned_end: '2026-10-27T18:00:00' }, 'overdue', now), true)
  assert.equal(matchesOrderScope({ status: 'ACTIVE', planned_end: '2026-10-28T18:00:00' }, 'overdue', now), false)
  assert.equal(matchesOrderScope({ status: 'COMPLETED', planned_end: '2026-10-27T18:00:00' }, 'overdue', now), false)
})

test('业务待办优先逾期归还，覆盖待结算与有效维修，排除已完成业务', () => {
  const orders = [
    { id: 1, status: 'RESERVED', planned_start: '2026-10-29T09:00:00' },
    { id: 2, status: 'ACTIVE', planned_end: '2026-10-27T18:00:00' },
    { id: 3, status: 'RETURNED', actual_end: '2026-10-28T09:00:00' },
    { id: 4, status: 'COMPLETED' },
    { id: 5, status: 'ACTIVE', planned_end: '2026-10-29T18:00:00' },
  ]
  const work = [{ id: 1, status: 'PENDING' }, { id: 2, status: 'IN_PROGRESS' }, { id: 3, status: 'COMPLETED' }, { id: 4, status: 'CANCELLED' }]
  const result = buildOperations(orders, work, now)
  assert.equal(result.length, 5)
  assert.equal(result[0].id, 'order-2')
  assert.equal(result[0].level, 'danger')
  assert.equal(result[1].type, 'settlement')
  assert.equal(result.filter(task => task.type === 'maintenance').length, 2)
  assert.equal(result.find(task => task.id === 'maintenance-1').path, '/maintenance?detail=1')
})

test('车队统计覆盖所有五种状态，保留零值且总数与档案相同', () => {
  const vehicles = [{ status: 'AVAILABLE' }, { status: 'AVAILABLE' }, { status: 'RENTED' }, { status: 'MAINTENANCE' }, { status: 'DISPATCHING' }, { status: 'DISABLED' }]
  const result = fleetSummary(vehicles)
  assert.equal(result.length, 5)
  assert.equal(result.reduce((sum, item) => sum + item.count, 0), vehicles.length)
  assert.equal(result[0].count, 2)
  assert.equal(fleetSummary([]).every(item => item.count === 0), true)
})

test('最近订单依据创建时间而不是接口数组顺序，不修改原数组', () => {
  const orders = [{ id: 1, created_at: '2026-10-27T09:00:00' }, { id: 2, created_at: '2026-10-28T09:00:00' }]
  assert.equal(recentOrders(orders)[0].id, 2)
  assert.equal(orders[0].id, 1)
})

test('七天范围跨月、跨年仍为本地日历日期，不伪造趋势或补入虚假收入', () => {
  assert.equal(weekStart(new Date('2026-11-02T12:00:00')), '2026-10-27')
  assert.equal(weekStart(new Date('2027-01-02T12:00:00')), '2026-12-27')
  assert.equal(dayKey(now), '2026-10-28')
  const trend = Array.from({ length: 10 }, (_, index) => ({ date: `day-${index}`, revenue: index === 9 ? 100 : 0 }))
  assert.equal(revenueSeries(trend, 'week').length, 7)
  assert.equal(revenueSeries(trend, 'week').reduce((sum, point) => sum + point.revenue, 0), 100)
  assert.equal(revenueSeries(trend, 'month'), trend)
})

test('分类运力完全基于车辆档案，区分可用与总数并保留未分类车辆', () => {
  const vehicles = [
    { category: 'SUV', status: 'AVAILABLE' },
    { category: 'SUV', status: 'RENTED' },
    { category: '经济轿车', status: 'MAINTENANCE' },
    { status: 'AVAILABLE' },
  ]
  const groups = categorySummary(vehicles)
  assert.deepEqual(groups.find(group => group.category === 'SUV'), { category: 'SUV', total: 2, available: 1 })
  assert.deepEqual(groups.find(group => group.category === '未分类'), { category: '未分类', total: 1, available: 1 })
  assert.equal(groups.reduce((total, group) => total + group.total, 0), vehicles.length)
  assert.equal(groups.reduce((total, group) => total + group.available, 0), 2)
  assert.deepEqual(categorySummary([]), [])
  assert.equal(vehicles.length, 4)
})

test('待办车辆优先使用档案照片，车型参考图只作为明确的后备', () => {
  assert.deepEqual(vehiclePhotoSources({ model: '海豹', image: '/uploads/actual.jpg' }), [
    { src: '/uploads/actual.jpg', reference: false },
    { src: '/images/vehicles/byd-seal.webp', reference: true },
  ])
  assert.equal(vehiclePhotoSources({ model: ' GL8 ' })[0].src, '/images/vehicles/buick-gl8.webp')
  assert.equal(vehiclePhotoSources({ model: '雅阁' })[0].src, '/images/vehicles/honda-accord.webp')
})

test('没有对应照片的车型不冒用其他车辆图片', () => {
  assert.deepEqual(vehiclePhotoSources({ model: '未知车型' }), [])
  assert.deepEqual(vehiclePhotoSources(), [])
  assert.deepEqual(vehiclePhotoSources({ model: '未知车型', image: '/uploads/real.png' }), [{ src: '/uploads/real.png', reference: false }])
})


test('18款车型各自匹配独立本地图片，来源完整且WebP文件存在', () => {
  assert.equal(vehiclePhotoCatalog.length, 18)
  assert.equal(new Set(vehiclePhotoCatalog.map(item => item.src)).size, 18)
  for (const item of vehiclePhotoCatalog) {
    assert.deepEqual(vehiclePhotoSources({ brand: item.brand, model: item.model }), [{ src: item.src, reference: true }])
    assert.match(item.source, /^https:\/\//)
    assert.match(item.original, /^https:\/\//)
    const bytes = readFileSync(new URL('../public' + item.src, import.meta.url))
    assert.equal(bytes.toString('ascii', 0, 4), 'RIFF')
    assert.equal(bytes.toString('ascii', 8, 12), 'WEBP')
    assert.ok(item.width >= 300 && item.height >= 100)
  }
})

test('车型匹配忽略空格和大小写，但不冒用其他品牌的同名图片', () => {
  assert.equal(vehiclePhotoSources({ brand: '长安', model: 'cs75plus' })[0].src, '/images/vehicles/changan-cs75-plus.webp')
  assert.equal(vehiclePhotoSources({ brand: '丰田', model: ' RAV4荣放 ' })[0].src, '/images/vehicles/toyota-rav4.webp')
  assert.deepEqual(vehiclePhotoSources({ brand: '大众', model: 'ET5' }), [])
})

test('所有车型优先实车上传，未知车型也保留档案图片', () => {
  for (const item of vehiclePhotoCatalog) {
    const sources = vehiclePhotoSources({ brand: item.brand, model: item.model, image: '/uploads/actual.webp' })
    assert.deepEqual(sources[0], { src: '/uploads/actual.webp', reference: false })
    assert.equal(sources[1].src, item.src)
  }
})


test('全车队参考图统一前侧向左视角、透明背景和画布尺寸', () => {
  for (const photo of vehiclePhotoCatalog) {
    assert.equal(photo.transparent, true)
    assert.equal(photo.view, 'front-left-three-quarter')
    assert.equal(photo.direction, 'left')
    assert.deepEqual([photo.width, photo.height], [960, 520])
    assert.ok(photo.provider && photo.sourceModel)
  }
})
