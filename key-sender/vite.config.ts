import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import { fileURLToPath } from 'url';
import { defineConfig } from 'vite';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// 브라우저 환경에서 fs와 path를 호출할 때 에러가 나지 않도록 빈 가상 모듈을 제공하는 미니 플러그인
const nodeMockPlugin = () => {
  return {
    name: 'node-mock-plugin',
    resolveId(id: string) {
      if (id === 'virtual:node-mock') {
        return id;
      }
      return null;
    },
    load(id: string) {
      if (id === 'virtual:node-mock') {
        return 'export default {};'; // 빈 객체를 내보냄
      }
      return null;
    }
  };
};

export default defineConfig({
  base: './',
  plugins: [react(), tailwindcss(), nodeMockPlugin()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, '.'),
      // ggwave가 fs와 path를 찾으면 위에서 만든 빈 가상 모듈로 연결해줍니다.
      'fs': 'virtual:node-mock',
      'path': 'virtual:node-mock',
    },
  },
});
