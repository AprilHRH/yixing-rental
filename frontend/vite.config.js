import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: { port: Number(process.env.FRONTEND_PORT || 5173), strictPort: true, proxy: { '/api': process.env.API_TARGET || 'http://127.0.0.1:8080', '/uploads': process.env.API_TARGET || 'http://127.0.0.1:8080' } },
  preview: { port: Number(process.env.FRONTEND_PORT || 5173), strictPort: true, proxy: { '/api': process.env.API_TARGET || 'http://127.0.0.1:8080', '/uploads': process.env.API_TARGET || 'http://127.0.0.1:8080' } },
  build: { outDir: 'dist', rollupOptions: { output: { manualChunks: { charts: ['echarts/core', 'echarts/charts', 'echarts/components', 'echarts/renderers'], components: ['element-plus', '@element-plus/icons-vue'], framework: ['vue', 'vue-router', 'axios'] } } } },
})
