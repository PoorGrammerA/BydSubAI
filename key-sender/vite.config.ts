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
    },
  },
  build: {
    // 1. 빌드 파일이 구형/신형 브라우저에서 모두 꼬이지 않도록 타겟 지정
    target: 'esnext',
    rollupOptions: {
      // 2. 핵심: ggwave 내부의 fs, path 임포트 시도를 번들링 타겟에서 제외하고 
      // 글로벌 변수로 강제 바인딩하여 브라우저용 순수 아티팩트로 만들어 줍니다.
      external: ['fs', 'path'],
      output: {
        globals: {
          fs: '{}',
          path: '{}',
        },
      },
    },
  },
});
