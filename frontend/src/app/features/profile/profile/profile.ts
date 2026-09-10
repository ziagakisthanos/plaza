import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { UserService } from '../../../core/services/user';

@Component({
  selector: 'app-profile',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  templateUrl: './profile.html',
  styleUrl: './profile.css',
})
export class Profile implements OnInit {
  private userService = inject(UserService);
  private fb = inject(FormBuilder);

  private readonly avatarIds = ['1', '2', '3', '4', '5'];
  selectedAvatar = signal<string>('1');
  message = signal('');
  error = signal('');

  form = this.fb.group({
    name: ['', [Validators.required]],
  });

  ngOnInit(): void {
    this.userService.getMe().subscribe({
      next: (profile) => {
        this.form.patchValue({ name: profile.name });
        this.selectedAvatar.set(profile.avatar ?? '1');
      },
      error: () => this.error.set('Failed to load profile'),
    });
  }

  // pick a random avatar, avoiding current
  randomizeAvatar(): void {
    let next = this.selectedAvatar();
    while (next === this.selectedAvatar()) {
      next = this.avatarIds[Math.floor(Math.random() * this.avatarIds.length)];
    }
    this.selectedAvatar.set(next);
  }

  onSave(): void {
    if (this.form.invalid) return;
    this.message.set('');
    this.error.set('');

    this.userService.updateProfile({
      name: this.form.value.name!,
      avatar: this.selectedAvatar(),
    }).subscribe({
      next: () => this.message.set('Profile updated!'),
      error: () => this.error.set('Failed to update profile'),
    });
  }
}