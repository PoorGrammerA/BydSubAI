import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import { fileURLToPath } from 'url';
import { defineConfig } from 'vite';

// ESM 환경(최신 Node/Vite)에서 __dirname을 안전하게 가져오기 위한 코드
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default defineConfig({
  base: './',
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, '.'),
    },
  },
  build: {
    // GitHub Pages 배포 러너가 결함이 있는 파일로 오인하지 않도록 빌드 옵션 강제 지정
    rollupOptions: {
      external: ['fs', 'path'],
    },
  },
});
