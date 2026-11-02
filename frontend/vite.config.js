import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  // 开发和本地预览都把接口、上传图片转发给后端，浏览器统一访问前端地址即可。
  server: { port: Number(process.env.FRONTEND_PORT || 5173), strictPort: true, proxy: { '/api': process.env.API_TARGET || 'http://127.0.0.1:8080', '/uploads': process.env.API_TARGET || 'http://127.0.0.1:8080' } },
  preview: { port: Number(process.env.FRONTEND_PORT || 5173), strictPort: true, proxy: { '/api': process.env.API_TARGET || 'http://127.0.0.1:8080', '/uploads': process.env.API_TARGET || 'http://127.0.0.1:8080' } },
  // 将框架、组件库和图表拆成独立文件，业务代码更新时可继续使用未变化的浏览器缓存。
  build: { outDir: 'dist', rollupOptions: { output: { manualChunks: { charts: ['echarts/core', 'echarts/charts', 'echarts/components', 'echarts/renderers'], components: ['element-plus', '@element-plus/icons-vue'], framework: ['vue', 'vue-router', 'axios'] } } } },
})
