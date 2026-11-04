import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  ViewChild,
  output,
} from '@angular/core';
import { loadGoogleMaps } from './google-maps-loader';

export interface MapPin {
  id: string;
  latitude: number;
  longitude: number;
  title: string;
  kind: 'office' | 'job' | 'technician';
}

interface GoogleLatLng {
  lat: () => number;
  lng: () => number;
}

interface GoogleMarker {
  setMap: (map: GoogleMap | null) => void;
  setPosition: (pos: { lat: number; lng: number }) => void;
  addListener: (event: string, handler: (e: { latLng?: GoogleLatLng }) => void) => void;
}

interface GoogleMapsApi {
  maps: {
    Map: new (el: HTMLElement, opts: object) => GoogleMap;
    Marker: new (opts: object) => GoogleMarker;
    LatLngBounds: new () => { extend: (pos: object) => void };
  };
}

interface GoogleMap {
  setCenter: (pos: object) => void;
  setZoom: (zoom: number) => void;
  getZoom: () => number | undefined;
  fitBounds: (bounds: object) => void;
  addListener: (event: string, handler: (e: { latLng?: GoogleLatLng }) => void) => void;
}

@Component({
  selector: 'app-pin-map',
  standalone: true,
  template: `<div #host class="map-host" [style.height.px]="height"></div>`,
  styles: [
    `
      .map-host {
        width: 100%;
        min-height: 220px;
        border-radius: 12px;
        background: #e8eef6;
      }
    `,
  ],
})
export class PinMapComponent implements AfterViewInit, OnChanges {
  @ViewChild('host', { static: true }) host!: ElementRef<HTMLElement>;

  @Input() pins: MapPin[] = [];
  @Input() height = 280;
  /** When set, a click moves a single editable pin and emits the new point. */
  @Input() clickToSet = false;

  readonly positionChange = output<{ latitude: number; longitude: number }>();

  private map?: GoogleMap;
  private markers: GoogleMarker[] = [];
  private ready = false;
  private lastKey = '';
  private framed = false;
  private suppressClick = false;

  ngAfterViewInit(): void {
    loadGoogleMaps()
      .then(() => {
        const google = (window as unknown as { google: GoogleMapsApi }).google;
        this.map = new google.maps.Map(this.host.nativeElement, {
          center: { lat: 19.076, lng: 72.8777 },
          zoom: 12,
          mapTypeControl: false,
          streetViewControl: false,
          fullscreenControl: false,
          clickableIcons: false,
          draggableCursor: this.clickToSet ? 'crosshair' : undefined,
        });
        if (this.clickToSet) {
          this.map.addListener('click', (event) => {
            if (this.suppressClick) return;
            const latLng = event.latLng;
            if (!latLng) return;
            this.moveEditablePin(latLng.lat(), latLng.lng());
          });
        }
        this.ready = true;
        this.draw();
      })
      .catch(() => {
        this.host.nativeElement.textContent = 'Map could not be loaded.';
      });
  }

  ngOnChanges(): void {
    const key = this.pins.map((pin) => `${pin.id}:${pin.latitude}:${pin.longitude}`).join('|');
    if (key === this.lastKey) return;
    this.lastKey = key;
    if (this.ready) this.draw();
  }

  private draw(): void {
    if (!this.map) return;
    const google = (window as unknown as { google: GoogleMapsApi }).google;
    for (const marker of this.markers) marker.setMap(null);
    this.markers = [];
    const points = this.pins.filter((pin) => Number.isFinite(pin.latitude) && Number.isFinite(pin.longitude));
    if (!points.length) return;
    const bounds = new google.maps.LatLngBounds();
    for (const pin of points) {
      const position = { lat: pin.latitude, lng: pin.longitude };
      bounds.extend(position);
      const marker = new google.maps.Marker({
        map: this.map,
        position,
        title: this.clickToSet ? 'Drag to move the pin' : pin.title,
        draggable: this.clickToSet,
        label: pin.kind === 'office' ? 'O' : pin.kind === 'technician' ? 'T' : undefined,
      });
      if (this.clickToSet) {
        marker.addListener('dragstart', () => {
          this.suppressClick = true;
        });
        marker.addListener('dragend', (event) => {
          const latLng = event.latLng;
          window.setTimeout(() => {
            this.suppressClick = false;
          }, 400);
          if (!latLng) return;
          this.positionChange.emit({ latitude: latLng.lat(), longitude: latLng.lng() });
        });
      }
      this.markers.push(marker);
    }
    if (points.length === 1) {
      this.map.setCenter({ lat: points[0].latitude, lng: points[0].longitude });
      if (!this.framed) {
        this.map.setZoom(16);
        this.framed = true;
      }
    } else {
      this.map.fitBounds(bounds);
    }
  }

  /** Moves the pin on screen immediately, then tells the parent to save it. */
  private moveEditablePin(latitude: number, longitude: number): void {
    const position = { lat: latitude, lng: longitude };
    this.markers[0]?.setPosition(position);
    this.map?.setCenter(position);
    this.positionChange.emit({ latitude, longitude });
  }
}
