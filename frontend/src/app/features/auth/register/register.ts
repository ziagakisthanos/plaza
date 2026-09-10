import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth';
import { BrandMark } from '../../../shared/brand-mark/brand-mark';

type Role = 'CLIENT' | 'SELLER';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, BrandMark],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  errorMessage = signal('');
  loading = signal(false);
  showPassword = signal(false);

  form = this.fb.group({
    name: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(20)]],
    role: ['' as Role | '', [Validators.required]],
  });

  selectRole(role: Role): void {
    this.form.get('role')!.setValue(role);
    this.form.get('role')!.markAsTouched();
  }

  isRole(role: Role): boolean {
    return this.form.value.role === role;
  }

  invalid(control: string): boolean {
    const field = this.form.get(control);
    return !!field && field.invalid && (field.touched || field.dirty);
  }

  togglePassword(): void {
    this.showPassword.update((shown) => !shown);
  }

  /** 0–3, drives the password strength meter. */
  passwordStrength(): number {
    const value = this.form.value.password ?? '';
    if (value.length < 6) return 0;
    let score = 1;
    if (value.length >= 10) score++;
    if (/[^A-Za-z0-9]/.test(value) || (/[A-Z]/.test(value) && /[0-9]/.test(value))) score++;
    return Math.min(score, 3);
  }

  strengthLabel(): string {
    return ['Too short', 'Weak', 'Good', 'Strong'][this.passwordStrength()];
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set('');
    this.loading.set(true);

    const data = {
      name: this.form.value.name!,
      email: this.form.value.email!,
      password: this.form.value.password!,
      role: this.form.value.role!,
    };

    this.auth.register(data).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/login']);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 409) {
          this.errorMessage.set('An account with this email already exists');
        } else if (err.status === 400 && err.error?.fieldErrors) {
          this.errorMessage.set('Please check your input and try again');
        } else {
          this.errorMessage.set('Something went wrong. Please try again.');
        }
      },
    });
  }
}
