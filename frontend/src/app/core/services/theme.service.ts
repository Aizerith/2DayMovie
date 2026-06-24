import {Injectable, signal} from '@angular/core';

export type AppTheme = 'boilerplate' | 'boilerplate-dark';

@Injectable({
  providedIn: 'root',
})
export class ThemeService {
  private readonly themeKey = '2daymovie_theme';
  readonly theme = signal<AppTheme>(this.readTheme());

  setTheme(theme: AppTheme): void {
    this.theme.set(theme);
    localStorage.setItem(this.themeKey, theme);
  }

  toggleTheme(): void {
    this.setTheme(this.theme() === 'boilerplate' ? 'boilerplate-dark' : 'boilerplate');
  }

  private readTheme(): AppTheme {
    const storedTheme = localStorage.getItem(this.themeKey);
    return storedTheme === 'boilerplate' ? 'boilerplate' : 'boilerplate-dark';
  }
}
