import { environment } from '../../../environments/environment';

let loading: Promise<void> | null = null;

/** Loads the Maps JavaScript API once. The key is restricted by website address. */
export function loadGoogleMaps(): Promise<void> {
  const win = window as Window & { google?: { maps?: unknown } };
  if (win.google?.maps) return Promise.resolve();
  if (loading) return loading;
  loading = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src =
      'https://maps.googleapis.com/maps/api/js?key=' +
      encodeURIComponent(environment.googleMapsApiKey);
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      loading = null;
      reject(new Error('Google Maps failed to load'));
    };
    document.head.appendChild(script);
  });
  return loading;
}
