import {Component, input} from '@angular/core';

@Component({
  selector: 'app-section-card',
  templateUrl: './section-card.html',
  host: {
    class: 'block w-full'
  }
})
export class SectionCard {
  readonly title = input.required<string>();
  readonly subtitle = input('');
  readonly actions = input(false);
}
