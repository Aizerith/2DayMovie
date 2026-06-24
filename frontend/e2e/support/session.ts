import {expect, type Page} from '@playwright/test';

const authEmail = process.env['PLAYWRIGHT_AUTH_EMAIL'] ?? 'admin@local.dev';
const authPassword = process.env['PLAYWRIGHT_AUTH_PASSWORD'] ?? 'Admin123!';

export async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.getByTestId('login-email').fill(authEmail);
  await page.getByTestId('login-password').fill(authPassword);
  await page.getByTestId('login-submit').click();
  await expect(page).toHaveURL(/\/profile$/);
}

export function uniqueName(prefix: string): string {
  return `${prefix} ${Date.now()}`;
}
