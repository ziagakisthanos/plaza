import { Component, inject } from '@angular/core';
import { NotificationService } from '../../core/services/notification';

@Component({
  selector: 'app-toasts',
  templateUrl: './toasts.html',
  styleUrl: './toasts.css',
})
export class Toasts {
  protected readonly notificationService = inject(NotificationService);
}
