import {Component, input} from '@angular/core';

@Component({
  selector: 'app-form-field',
  templateUrl: './form-field.html',
  host: {
    class: 'block w-full'
  }
})
export class FormField {
  readonly label = input.required<string>();
  readonly hint = input('');
  readonly error = input('');
  readonly required = input(false);
}
