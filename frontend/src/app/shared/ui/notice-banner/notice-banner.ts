import {Component, input} from '@angular/core';

@Component({
  selector: 'app-notice-banner',
  host: {
    class: 'block w-full'
  },
  templateUrl: './notice-banner.html',
})
export class NoticeBanner {
  readonly tone = input<'info' | 'success' | 'warning' | 'error'>('info');
  readonly message = input.required<string>();
}
