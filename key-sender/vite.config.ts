import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import { fileURLToPath } from 'url';
import { defineConfig } from 'vite';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default defineConfig({
  base: './',
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, '.'),
      // 핵심: 브라우저가 실행될 때 fs와 path를 요구하면 에러 대신 빈 객({})을 주도록 가상 매핑
      'fs': 'unenv/runtime/mock/empty',
      'path': 'unenv/runtime/mock/empty',
    },
  },
  build: {
    // 이전의 external 옵션은 브라우저 배포 시 에러를 유발하므로 과감히 삭제합니다.
    rollupOptions: {},
  },
});
