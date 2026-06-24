import {Component} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {AppShell} from '../../shared/ui/app-shell/app-shell';

@Component({
  selector: 'app-private-layout',
  imports: [
    RouterOutlet,
    AppShell
  ],
  templateUrl: './private-layout.html',
})
export class PrivateLayout {
}
