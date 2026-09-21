import { Injectable, signal } from '@angular/core';

export interface Notification {
  id: number;
  kind: 'success' | 'error';
  text: string;
}

export const NOTIFICATION_MS = 6000;

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly items = signal<Notification[]>([]);
  private lastId = 0;

  readonly notifications = this.items.asReadonly();

  show(kind: Notification['kind'], text: string): void {
    if (this.items().some((item) => item.text === text)) return;

    const id = ++this.lastId;
    this.items.update((list) => [...list, { id, kind, text }]);
    setTimeout(() => this.dismiss(id), NOTIFICATION_MS);
  }

  dismiss(id: number): void {
    this.items.update((list) => list.filter((item) => item.id !== id));
  }
}
