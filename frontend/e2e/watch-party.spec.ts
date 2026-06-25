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
          audioTracks: [],
          videoContentType: 'video/mp4',
          status: 'READY',
          preparationProgressPercent: 100,
          preparationMessage: 'Pret',
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

    await expect(page.getByText('Lien pret', {exact: true})).toBeVisible();
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
          audioTracks: [
            {
              label: 'Francais',
              language: 'fr',
              url: 'https://storage.test/2daymovie-videos/audio-fr.m4a'
            },
            {
              label: 'English',
              language: 'en',
              url: 'https://storage.test/2daymovie-videos/audio-en.m4a'
            }
          ],
          videoContentType: 'video/mp4',
          status: 'READY',
          preparationProgressPercent: 100,
          preparationMessage: 'Pret',
          playbackTimeSeconds: 12,
          playing: false
        },
        status: 200
      });
    });
    await page.route('**/*', async route => {
      if (!route.request().url().includes('/close')) {
        await route.fallback();
        return;
      }

      if (route.request().method() === 'OPTIONS') {
        await route.fulfill({
          body: '',
          status: 204,
          headers: {
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'POST, OPTIONS',
            'Access-Control-Allow-Headers': 'content-type'
          }
        });
        return;
      }

      await route.fulfill({
        json: {shareCode: room.shareCode, closed: true},
        status: 200,
        headers: {'Access-Control-Allow-Origin': '*'}
      });
    });

    await page.goto(`/watch/${room.shareCode}`);
    await page.getByPlaceholder('0000').fill('1234');
    await page.getByRole('button', {name: 'Regarder'}).click();

    await expect(page.getByRole('heading', {name: 'Movie night'})).toBeVisible();
    await expect(page.getByText('Reglages lecture')).toBeVisible();
    await expect(page.getByText('Piste audio')).toBeVisible();
    await expect(page.getByText('AAC compatible navigateur')).toBeVisible();
    await expect(page.locator('video')).toBeVisible();
    await expect(page.locator('select').first()).toContainText('Francais');
    await page.getByRole('button', {name: 'Clore le salon'}).click();
    await expect(page.getByRole('heading', {name: 'Clore le salon ?'})).toBeVisible();
    await page.getByRole('button', {name: 'Confirmer la fermeture'}).click();
    await expect(page).toHaveURL(/\/$/);
  });
});
