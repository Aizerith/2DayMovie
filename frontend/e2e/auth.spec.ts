import {expect, test} from '@playwright/test';
import {login} from './support/session';

test.describe('auth flow', () => {
  test('redirects unauthenticated users to login on protected route', async ({page}) => {
    await page.goto('/projects');

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByTestId('login-submit')).toBeVisible();
  });

  test('logs in and shows private navigation', async ({page}) => {
    await login(page);

    await expect(page.getByTestId('nav-projects')).toBeVisible();
    await expect(page.getByTestId('nav-tasks')).toBeVisible();
  });
});
