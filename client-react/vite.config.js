import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
function fromHere(rel) {
    var url = new URL(rel, import.meta.url);
    return decodeURIComponent(url.pathname).replace(/^\/([A-Za-z]:)/, '$1');
}
export default defineConfig({
    base: './',
    plugins: [react()],
    resolve: {
        dedupe: ['react', 'react-dom'],
        alias: {
            '@aisuper/applet-web-runtime': fromHere('../packages/applet-web-runtime/src/index.ts'),
            react: fromHere('./node_modules/react'),
            'react-dom': fromHere('./node_modules/react-dom'),
        },
    },
    server: {
        fs: {
            allow: [fromHere('..')],
        },
    },
});
