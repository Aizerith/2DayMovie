import {Component, input} from '@angular/core';

@Component({
  selector: 'app-page-intro',
  templateUrl: './page-intro.html',
})
export class PageIntro {
  readonly badge = input('');
  readonly badgeClass = input('badge badge-lg badge-primary badge-outline');
  readonly title = input.required<string>();
  readonly description = input('');
  readonly compact = input(false);
}
