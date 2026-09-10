import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink, Router } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from './core/services/auth';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, MatToolbarModule, MatButtonModule],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private authService = inject(AuthService);
  private router = inject(Router);

  isLoggedIn(): boolean { return this.authService.isLoggedIn(); }
  isSeller(): boolean { return this.authService.getRole() === 'SELLER'; }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}