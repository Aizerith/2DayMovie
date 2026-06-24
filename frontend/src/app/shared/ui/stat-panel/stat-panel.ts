import {Component, input} from '@angular/core';

export interface StatPanelItem {
  label: string;
  value: string;
}

@Component({
  selector: 'app-stat-panel',
  templateUrl: './stat-panel.html',
})
export class StatPanel {
  readonly title = input.required<string>();
  readonly subtitle = input('');
  readonly items = input<StatPanelItem[]>([]);
  readonly vertical = input(false);
}
