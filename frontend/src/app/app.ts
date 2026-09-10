import { Component, HostListener, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from './core/services/auth';
import { UserProfile, UserService } from './core/services/user';
import { BrandMark } from './shared/brand-mark/brand-mark';

/** Routes that render their own full-screen layout without the app chrome. */
const BARE_ROUTES = ['/login', '/register'];

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, BrandMark],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private authService = inject(AuthService);
  private userService = inject(UserService);
  private router = inject(Router);

  profile = signal<UserProfile | null>(null);
  menuOpen = signal(false);
  mobileNavOpen = signal(false);
  chromeless = signal(false);

  constructor() {
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe((e) => {
      this.chromeless.set(BARE_ROUTES.some((r) => e.urlAfterRedirects.startsWith(r)));
      this.menuOpen.set(false);
      this.mobileNavOpen.set(false);
      this.syncProfile();
    });
  }

  /** Keeps the header identity in step with the session. */
  private syncProfile(): void {
    if (!this.isLoggedIn()) {
      this.profile.set(null);
      return;
    }
    if (this.profile()) return;
    this.userService.getMe().subscribe({
      next: (profile) => this.profile.set(profile),
      error: () => this.profile.set(null),
    });
  }

  isLoggedIn(): boolean {
    return this.authService.isLoggedIn();
  }

  isSeller(): boolean {
    return this.authService.getRole() === 'SELLER';
  }

  avatarUrl(): string {
    return `avatars/avatar-${this.profile()?.avatar ?? '1'}.svg`;
  }

  displayName(): string {
    return this.profile()?.name ?? 'My account';
  }

  roleLabel(): string {
    return this.isSeller() ? 'Seller' : 'Buyer';
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  toggleMobileNav(): void {
    this.mobileNavOpen.update((open) => !open);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (this.menuOpen() && !target.closest('.account')) this.menuOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.menuOpen.set(false);
    this.mobileNavOpen.set(false);
  }

  logout(): void {
    this.authService.logout();
    this.profile.set(null);
    this.menuOpen.set(false);
    this.router.navigate(['/login']);
  }
}
