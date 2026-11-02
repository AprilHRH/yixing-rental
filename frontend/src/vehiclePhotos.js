import { vehiclePhotoCatalog } from './vehiclePhotoCatalog.js'

function normalize(value) { return String(value || '').toLowerCase().replace(/\s+/g, '') }

export function vehiclePhotoSources(vehicle = {}) {
  // 优先展示档案上传的实车照片，再匹配同品牌、同车型的参考图，不拿其他车型冒充。
  const photos = []
  if (vehicle.image) photos.push({ src: vehicle.image, reference: false })
  const model = normalize(vehicle.model)
  const reference = vehiclePhotoCatalog.find(item =>
    (!vehicle.brand || normalize(item.brand) === normalize(vehicle.brand)) &&
    [item.model, ...(item.aliases || [])].some(alias => normalize(alias) === model)
  )
  if (reference) photos.push({ src: reference.src, reference: true })
  return photos
}
