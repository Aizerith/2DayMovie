import {Component, input} from '@angular/core';

@Component({
  selector: 'app-toolbar',
  templateUrl: './toolbar.html',
})
export class Toolbar {
  readonly title = input.required<string>();
  readonly subtitle = input('');
}
