import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserProfile, UserService } from '../../../core/services/user';
import { ProfileStats } from '../profile-stats/profile-stats';

@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule, ProfileStats],
  templateUrl: './profile.html',
  styleUrl: './profile.css',
})
export class Profile implements OnInit {
  private readonly userService = inject(UserService);
  private readonly fb = inject(FormBuilder);

  readonly avatarIds = ['1', '2', '3', '4', '5'];

  profile = signal<UserProfile | null>(null);
  selectedAvatar = signal<string>('1');
  loading = signal(true);
  saving = signal(false);
  message = signal('');
  error = signal('');

  form = this.fb.group({
    name: ['', [Validators.required]],
  });

  ngOnInit(): void {
    this.userService.getMe().subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.form.patchValue({ name: profile.name });
        this.selectedAvatar.set(profile.avatar ?? '1');
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load your profile');
        this.loading.set(false);
      },
    });
  }

  avatarSrc(id: string): string {
    return `avatars/avatar-${id}.svg`;
  }

  selectAvatar(id: string): void {
    this.selectedAvatar.set(id);
  }

  /** Picks a different avatar than the one currently shown. */
  randomizeAvatar(): void {
    const others = this.avatarIds.filter((id) => id !== this.selectedAvatar());
    const [random] = crypto.getRandomValues(new Uint32Array(1));
    this.selectedAvatar.set(others[random % others.length]);
  }

  roleLabel(): string {
    return this.profile()?.role === 'SELLER' ? 'Seller' : 'Buyer';
  }

  initials(): string {
    const name = this.profile()?.name ?? '';
    return name
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0].toUpperCase())
      .join('');
  }

  onSave(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.message.set('');
    this.error.set('');
    this.saving.set(true);

    this.userService
      .updateProfile({ name: this.form.value.name!, avatar: this.selectedAvatar() })
      .subscribe({
        next: (profile) => {
          this.profile.set(profile);
          this.saving.set(false);
          this.message.set('Profile updated');
          setTimeout(() => this.message.set(''), 3200);
        },
        error: () => {
          this.saving.set(false);
          this.error.set('Failed to update your profile');
        },
      });
  }
}
