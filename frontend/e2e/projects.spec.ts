import {expect, test} from '@playwright/test';
import {login, uniqueName} from './support/session';

test.describe('projects CRUD', () => {
  test('creates a project from the UI', async ({page}) => {
    const projectName = uniqueName('E2E Project');

    await login(page);
    await page.goto('/projects');

    await page.getByTestId('projects-start-create').click();
    await page.getByTestId('project-name').fill(projectName);
    await page.getByTestId('project-description').fill('Projet cree automatiquement par Playwright.');
    await page.getByTestId('project-status').selectOption('ACTIVE');
    await page.getByTestId('project-submit').click();

    await expect(page.getByText(projectName)).toBeVisible();
  });
});
