import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.getRegistrations().then((registrations) => {
      registrations.forEach((registration) => {
        registration.unregister().catch(() => {
          // Ignore cleanup failures; app can still run without SW.
        });
      });

      if (!import.meta.env.DEV) {
        navigator.serviceWorker.register('/sw.js').catch((error) => {
          console.error('Service worker registration failed:', error);
        });
      }
    });
  });
}


