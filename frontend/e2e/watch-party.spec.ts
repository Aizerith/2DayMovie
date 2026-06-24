import {expect, test} from '@playwright/test';

const room = {
  shareCode: 'ABCD2345',
  shareUrl: 'http://localhost:4200/watch/ABCD2345',
  videoUpload: {
    url: 'https://storage.test/2daymovie-videos/video.mp4',
    objectKey: 'rooms/ABCD2345/video.mp4'
  },
  subtitleUpload: null,
  expiresAt: new Date(Date.now() + 900_000).toISOString()
};

test.describe('watch party', () => {
  test('creates a private room and shows the share link with generated PIN', async ({page}) => {
    await page.route('**/api/rooms', async route => {
      await route.fulfill({json: room, status: 201});
    });
    await page.route(room.videoUpload.url, async route => {
      await route.fulfill({body: '', status: 200});
    });
    await page.route(`**/api/rooms/${room.shareCode}/complete`, async route => {
      await route.fulfill({
        json: {
          shareCode: room.shareCode,
          title: 'Movie night',
          videoUrl: room.videoUpload.url,
          subtitleUrl: null,
          subtitleTracks: [],
          videoContentType: 'video/mp4',
          playbackTimeSeconds: 0,
          playing: false
        },
        status: 200
      });
    });

    await page.goto('/');
    await page.getByPlaceholder('Soiree cinema').fill('Movie night');
    await page.locator('input[type="file"][accept="video/*"]').setInputFiles({
      name: 'movie.mp4',
      mimeType: 'video/mp4',
      buffer: Buffer.from('fake video')
    });
    await page.getByRole('button', {name: 'Creer le lien partage'}).click();

    await expect(page.getByText('Salon pret', {exact: true})).toBeVisible();
    await expect(page.getByText(room.shareUrl)).toBeVisible();
    await expect(page.getByText('PIN', {exact: true})).toBeVisible();
    await expect(page.getByRole('link', {name: 'Ouvrir'})).toHaveAttribute('href', `/watch/${room.shareCode}`);
  });

  test('unlocks a shared room with the PIN and shows subtitle settings', async ({page}) => {
    await page.route(`**/api/rooms/${room.shareCode}/access`, async route => {
      await route.fulfill({
        json: {
          shareCode: room.shareCode,
          title: 'Movie night',
          videoUrl: room.videoUpload.url,
          subtitleUrl: 'https://storage.test/2daymovie-videos/subtitles.vtt',
          subtitleTracks: [
            {
              label: 'Francais',
              language: 'fr',
              url: 'https://storage.test/2daymovie-videos/subtitles.vtt'
            },
            {
              label: 'English',
              language: 'en',
              url: 'https://storage.test/2daymovie-videos/subtitles-en.vtt'
            }
          ],
          videoContentType: 'video/mp4',
          playbackTimeSeconds: 12,
          playing: false
        },
        status: 200
      });
    });

    await page.goto(`/watch/${room.shareCode}`);
    await page.getByPlaceholder('0000').fill('1234');
    await page.getByRole('button', {name: 'Regarder'}).click();

    await expect(page.getByRole('heading', {name: 'Movie night'})).toBeVisible();
    await expect(page.getByText('Reglage de l')).toBeVisible();
    await expect(page.locator('video')).toBeVisible();
  });
});
