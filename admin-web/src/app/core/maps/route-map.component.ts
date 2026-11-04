import { AfterViewInit, Component, ElementRef, Input, OnChanges, ViewChild } from '@angular/core';
import { loadGoogleMaps } from './google-maps-loader';

export interface MapRoute {
  id: string;
  /** Shown on the start marker, e.g. the trip number. */
  label: string;
  color: string;
  /** [latitude, longitude] pairs. */
  path: [number, number][];
  start?: { latitude: number; longitude: number } | null;
  dashed?: boolean;
}

export interface RouteMapPin {
  id: string;
  latitude: number;
  longitude: number;
  title: string;
  kind: 'shop' | 'job';
}

interface LatLngLiteral {
  lat: number;
  lng: number;
}

interface GoogleOverlay {
  setMap: (map: GoogleMap | null) => void;
}

interface GoogleMap {
  setCenter: (pos: object) => void;
  setZoom: (zoom: number) => void;
  fitBounds: (bounds: object, padding?: number) => void;
}

interface GoogleMapsApi {
  maps: {
    Map: new (el: HTMLElement, opts: object) => GoogleMap;
    Marker: new (opts: object) => GoogleOverlay;
    Polyline: new (opts: object) => GoogleOverlay;
    LatLngBounds: new () => { extend: (pos: object) => void };
    SymbolPath: { CIRCLE: number };
  };
}

@Component({
  selector: 'app-route-map',
  standalone: true,
  template: `<div #host class="map-host" [style.height.px]="height"></div>`,
  styles: [
    `
      .map-host {
        width: 100%;
        min-height: 200px;
        border-radius: 12px;
        background: #e8eef6;
      }
    `,
  ],
})
export class RouteMapComponent implements AfterViewInit, OnChanges {
  @ViewChild('host', { static: true }) host!: ElementRef<HTMLElement>;

  @Input() routes: MapRoute[] = [];
  @Input() pins: RouteMapPin[] = [];
  @Input() height = 360;

  private map?: GoogleMap;
  private overlays: GoogleOverlay[] = [];
  private ready = false;
  private lastKey = '';

  ngAfterViewInit(): void {
    loadGoogleMaps()
      .then(() => {
        const google = (window as unknown as { google: GoogleMapsApi }).google;
        this.map = new google.maps.Map(this.host.nativeElement, {
          center: { lat: 19.076, lng: 72.8777 },
          zoom: 12,
          mapTypeControl: false,
          streetViewControl: false,
          fullscreenControl: true,
          clickableIcons: false,
        });
        this.ready = true;
        this.draw();
      })
      .catch(() => {
        this.host.nativeElement.textContent = 'Map could not be loaded.';
      });
  }

  ngOnChanges(): void {
    const key = [
      ...this.routes.map((r) => `${r.id}:${r.color}:${r.path.length}:${r.start?.latitude ?? ''}`),
      ...this.pins.map((p) => `${p.id}:${p.latitude}:${p.longitude}`),
    ].join('|');
    if (key === this.lastKey) return;
    this.lastKey = key;
    if (this.ready) this.draw();
  }

  private draw(): void {
    if (!this.map) return;
    const google = (window as unknown as { google: GoogleMapsApi }).google;
    for (const overlay of this.overlays) overlay.setMap(null);
    this.overlays = [];
    const bounds = new google.maps.LatLngBounds();
    const framed: LatLngLiteral[] = [];
    const extend = (pos: LatLngLiteral) => {
      if (!Number.isFinite(pos.lat) || !Number.isFinite(pos.lng)) return false;
      bounds.extend(pos);
      framed.push(pos);
      return true;
    };

    for (const route of this.routes) {
      const path = route.path
        .map(([lat, lng]) => ({ lat, lng }))
        .filter((p) => Number.isFinite(p.lat) && Number.isFinite(p.lng));
      path.forEach(extend);
      if (path.length >= 2) {
        this.overlays.push(
          new google.maps.Polyline({
            map: this.map,
            path,
            strokeColor: route.color,
            strokeOpacity: route.dashed ? 0 : 0.85,
            strokeWeight: 4,
            icons: route.dashed
              ? [{ icon: { path: 'M 0,-1 0,1', strokeOpacity: 0.8, scale: 3 }, offset: '0', repeat: '12px' }]
              : undefined,
          }),
        );
      }
      const start = route.start ? { lat: route.start.latitude, lng: route.start.longitude } : path[0];
      if (start && extend(start)) {
        this.overlays.push(
          new google.maps.Marker({
            map: this.map,
            position: start,
            title: `Trip ${route.label} start`,
            zIndex: 20,
            label: { text: route.label, color: '#ffffff', fontSize: '12px', fontWeight: '700' },
            icon: {
              path: google.maps.SymbolPath.CIRCLE,
              scale: 11,
              fillColor: route.color,
              fillOpacity: 1,
              strokeColor: '#ffffff',
              strokeWeight: 2,
            },
          }),
        );
      }
    }

    for (const pin of this.pins) {
      const position = { lat: pin.latitude, lng: pin.longitude };
      if (!extend(position)) continue;
      this.overlays.push(
        new google.maps.Marker({
          map: this.map,
          position,
          title: pin.title,
          zIndex: pin.kind === 'shop' ? 10 : 5,
          label: pin.kind === 'shop' ? 'O' : undefined,
        }),
      );
    }

    if (!framed.length) return;
    if (framed.length === 1) {
      this.map.setCenter(framed[0]);
      this.map.setZoom(15);
    } else {
      this.map.fitBounds(bounds, 40);
    }
  }
}
