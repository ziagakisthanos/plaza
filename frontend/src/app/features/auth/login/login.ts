import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth';
import { BrandMark } from '../../../shared/brand-mark/brand-mark';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, BrandMark],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  errorMessage = signal('');
  loading = signal(false);
  showPassword = signal(false);

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  /** Only surface a field error once the user has engaged with it. */
  invalid(control: string): boolean {
    const field = this.form.get(control);
    return !!field && field.invalid && (field.touched || field.dirty);
  }

  togglePassword(): void {
    this.showPassword.update((shown) => !shown);
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set('');
    this.loading.set(true);

    const credentials = {
      email: this.form.value.email!,
      password: this.form.value.password!,
    };

    this.auth.login(credentials).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/products']);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(
          err.status === 401
            ? 'Invalid email or password'
            : 'Something went wrong. Please try again.',
        );
      },
    });
  }
}
