import { fileURLToPath } from 'node:url'
import { defineConfig, mergeConfig } from 'vite'
import appConfig from '../../vite.config.ts'

// Keep the synthetic fixture out of the real dist deployment directory.
export default mergeConfig(appConfig, defineConfig({
  build: {
    outDir: '.ui-test-dist',
    rollupOptions: { input: fileURLToPath(new URL('./notifications-editor.html', import.meta.url)) },
  },
  preview: { host: '127.0.0.1', port: 4173, strictPort: true },
}))
