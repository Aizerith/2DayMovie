import {Component, input} from '@angular/core';
import {RouterLink} from '@angular/router';
import {TranslocoPipe} from '@jsverse/transloco';

@Component({
  selector: 'app-empty-state',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './empty-state.html',
})
export class EmptyState {
  readonly title = input.required<string>();
  readonly description = input.required<string>();
  readonly actionLabel = input('');
  readonly actionLink = input('/');
}
