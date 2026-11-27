const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8');
const manifest = read('app/src/main/AndroidManifest.xml');
const mainActivity = read('app/src/main/java/com/rutinitas/islamic/NativeMainActivity.java');
const previewActivity = read('app/src/main/java/com/rutinitas/islamic/LockScreenPreviewActivity.java');
const strings = read('app/src/main/res/values/strings.xml');

assert.match(manifest, /android:name="\.LockScreenPreviewActivity"[\s\S]*?android:exported="false"/);
assert.match(manifest, /android:showWhenLocked="true"/);
assert.match(manifest, /android:turnScreenOn="true"/);
assert.match(mainActivity, /new Intent\(this, LockScreenPreviewActivity\.class\)/);
assert.match(mainActivity, /FLAG_ACTIVITY_NEW_TASK \| Intent\.FLAG_ACTIVITY_CLEAR_TASK/);
assert.match(mainActivity, /LockScreenPreviewActivity\.EXTRA_TITLE, title/);
assert.match(mainActivity, /LockScreenPreviewActivity\.EXTRA_TEXT, content/);
assert.match(previewActivity, /setShowWhenLocked\(true\)/);
assert.match(previewActivity, /setTurnScreenOn\(true\)/);
assert.match(previewActivity, /FLAG_SHOW_WHEN_LOCKED/);
assert.match(previewActivity, /FLAG_TURN_SCREEN_ON/);
assert.match(strings, /lockscreen_preview_eyebrow">PRATINJAU · TANPA MEMBUKA APLIKASI<\/string>/);
assert.match(previewActivity, /close\.setOnClickListener\(view -> finish\(\)\)/);
assert.match(manifest, /android:name="\.NativeMainActivity"[\s\S]*?android:name="android\.intent\.action\.MAIN"/);
assert.doesNotMatch(`${mainActivity}\n${previewActivity}`, /requestDismissKeyguard|FLAG_DISMISS_KEYGUARD/);

console.log('Lock screen preview checks passed.');
