import { Component, OnInit, inject, input, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ClientStats, SellerStats, StatsService } from '../../../core/services/stats';
import { apiErrorMessage } from '../../../core/utils/api-error';

@Component({
  selector: 'app-profile-stats',
  imports: [CurrencyPipe, RouterLink],
  templateUrl: './profile-stats.html',
  styleUrl: './profile-stats.css',
})
export class ProfileStats implements OnInit {
  private readonly statsService = inject(StatsService);

  readonly role = input.required<string>();

  loading = signal(true);
  error = signal('');
  clientStats = signal<ClientStats | null>(null);
  sellerStats = signal<SellerStats | null>(null);

  ngOnInit(): void {
    if (this.role() === 'SELLER') {
      this.statsService.getSellerStats().subscribe({
        next: (stats) => this.done(() => this.sellerStats.set(stats)),
        error: (error) => this.failed(error),
      });
    } else {
      this.statsService.getClientStats().subscribe({
        next: (stats) => this.done(() => this.clientStats.set(stats)),
        error: (error) => this.failed(error),
      });
    }
  }

  unitLabel(quantity: number): string {
    return quantity === 1 ? 'unit' : 'units';
  }

  private done(store: () => void): void {
    store();
    this.loading.set(false);
  }

  private failed(error: unknown): void {
    this.error.set(apiErrorMessage(error, 'We could not load your numbers. Please try again later.'));
    this.loading.set(false);
  }
}
