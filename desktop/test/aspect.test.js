import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fitWindowToVideo } from '../src/core/aspect.js';

const ecran = { width: 1440, height: 900 };
const ratio = ({ width, height }) => width / height;

test('aspect: portrait 1080x2400 sur un écran 1440x900 reste portrait et tient dans 80 %', () => {
  const r = fitWindowToVideo({ width: 1080, height: 2400 }, ecran);
  assert.ok(r.height > r.width);
  assert.ok(r.width <= 0.8 * ecran.width && r.height <= 0.8 * ecran.height);
  assert.ok(Math.abs(ratio(r) - 1080 / 2400) < 0.01);
});

test('aspect: paysage 2400x1080 reste paysage', () => {
  const r = fitWindowToVideo({ width: 2400, height: 1080 }, ecran);
  assert.ok(r.width > r.height);
  assert.ok(r.width <= 0.8 * ecran.width && r.height <= 0.8 * ecran.height);
  assert.ok(Math.abs(ratio(r) - 2400 / 1080) < 0.01);
});

test('aspect: jamais plus petit que 240 px de large', () => {
  const r = fitWindowToVideo({ width: 1080, height: 2400 }, { width: 400, height: 600 });
  assert.equal(r.width, 240);
  assert.ok(Math.abs(ratio(r) - 1080 / 2400) < 0.01);
  assert.ok(r.height <= 600);
});

test('aspect: dimensions invalides renvoient une taille par défaut sûre', () => {
  for (const v of [{ width: 0, height: 100 }, { width: NaN, height: 100 }, { width: 100, height: -5 }]) {
    assert.deepEqual(fitWindowToVideo(v, ecran), { width: 420, height: 640 });
  }
});
