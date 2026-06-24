import {expect, test} from '@playwright/test';
import {login, uniqueName} from './support/session';

const assigneeLabel = process.env['PLAYWRIGHT_ASSIGNEE_LABEL'] ?? 'Manager Local (MANAGER)';

test.describe('tasks CRUD', () => {
  test('creates a task linked to a project and assigns it', async ({page}) => {
    const projectName = uniqueName('E2E Task Project');
    const taskName = uniqueName('E2E Task');

    await login(page);

    await page.goto('/projects');
    await page.getByTestId('projects-start-create').click();
    await page.getByTestId('project-name').fill(projectName);
    await page.getByTestId('project-description').fill('Projet de support pour la creation E2E de tache.');
    await page.getByTestId('project-status').selectOption('ACTIVE');
    await page.getByTestId('project-submit').click();
    await expect(page.getByText(projectName)).toBeVisible();

    await page.goto('/tasks');
    await page.getByTestId('tasks-start-create').click();
    await page.getByTestId('task-project').selectOption({label: projectName});
    await page.getByTestId('task-assignee').selectOption({label: assigneeLabel});
    await page.getByTestId('task-title').fill(taskName);
    await page.getByTestId('task-description').fill('Tache creee automatiquement par Playwright.');
    await page.getByTestId('task-status').selectOption('IN_PROGRESS');
    await page.getByTestId('task-priority').selectOption('HIGH');
    await page.getByTestId('task-submit').click();

    await expect(page.getByText(taskName)).toBeVisible();
    await expect(page.getByRole('cell', {name: projectName})).toBeVisible();
  });
});
