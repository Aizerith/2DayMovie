import {Component, inject} from '@angular/core';
import {RouterLink, RouterOutlet} from '@angular/router';
import {ThemeService} from '../../core/services/theme.service';

@Component({
  selector: 'app-public-layout',
  imports: [
    RouterLink,
    RouterOutlet
  ],
  templateUrl: './public-layout.html',
})
export class PublicLayout {
  private readonly themeService = inject(ThemeService);
  readonly theme = this.themeService.theme;

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }
}
