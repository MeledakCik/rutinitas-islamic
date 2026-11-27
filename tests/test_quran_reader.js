const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const assets = path.join(root, 'app', 'src', 'main', 'assets');
const quranPath = path.join(assets, 'quran-uthmani.txt');
const metadataPath = path.join(assets, 'quran-data.xml');
const htmlPath = path.join(assets, 'Rutinitas.html');
const textBytes = fs.readFileSync(quranPath);
const text = textBytes.toString('utf8');
const sourceHash = crypto.createHash('sha256').update(textBytes).digest('hex');

assert.equal(
  sourceHash,
  '6933e133dd56db778c801bf738848454e43648105a151e8d84d86a7cae39ec5f',
  'Bundled Uthmani text must remain byte-for-byte unchanged from the approved source download.'
);

const allLines = text.split(/\r?\n/u).filter(Boolean);
const lines = allLines.filter((line) => /^\d+\|\d+\|/u.test(line));
assert.ok(allLines.every((line) => /^\d+\|\d+\|/u.test(line) || /^#/u.test(line)), 'Source contains only ayah records and its original copyright notice.');
const surahs = Array.from({ length: 114 }, () => []);
for (const [rowIndex, line] of lines.entries()) {
  const firstPipe = line.indexOf('|');
  const secondPipe = firstPipe < 0 ? -1 : line.indexOf('|', firstPipe + 1);
  assert.ok(firstPipe > 0 && secondPipe > firstPipe + 1, `Bad record at line ${rowIndex + 1}`);
  const surah = Number(line.slice(0, firstPipe));
  const ayah = Number(line.slice(firstPipe + 1, secondPipe));
  const ayahText = line.slice(secondPipe + 1);
  assert.ok(Number.isInteger(surah) && surah >= 1 && surah <= 114, `Bad surah at line ${rowIndex + 1}`);
  assert.equal(ayah, surahs[surah - 1].length + 1, `Ayah sequence gap in surah ${surah}`);
  assert.ok(ayahText.length > 0, `Empty ayah at ${surah}:${ayah}`);
  surahs[surah - 1].push(ayahText);
}

const html = fs.readFileSync(htmlPath, 'utf8');
const surahList = html.match(/const SURAHS_114 = \[([\s\S]*?)\n    \];/u);
assert.ok(surahList, 'Surah metadata is present in the app.');
const expectedCounts = [...surahList[1].matchAll(/ayahs:\s*(\d+)/gu)].map((match) => Number(match[1]));
assert.equal(expectedCounts.length, 114);
assert.equal(expectedCounts.reduce((sum, count) => sum + count, 0), 6236);
assert.equal(lines.length, 6236);
assert.equal(surahs.length, 114);
for (let index = 0; index < 114; index++) {
  assert.equal(surahs[index].length, expectedCounts[index], `Surah ${index + 1} ayah count mismatch`);
}

const parserDeclaration = html.match(/function parseQuranTextAsset\(rawText\) \{[\s\S]*?\n    \}/u);
assert.ok(parserDeclaration, 'The app has a dedicated Quran asset parser.');
const appParser = vm.runInNewContext(`(${parserDeclaration[0]})`, {
  SURAHS_114: expectedCounts.map((ayahs, index) => ({ n: index + 1, ayahs }))
});
const parsedByApp = appParser(text);
assert.equal(parsedByApp.length, 114);
assert.equal(parsedByApp.reduce((sum, rows) => sum + rows.length, 0), 6236);
assert.equal(parsedByApp[1][0], surahs[1][0], 'The app parser preserves exact verse characters.');

function splitBasmala(sourceText) {
  const match = sourceText.match(/^(\S+(?:\s+\S+){3})([\s\S]*)$/u);
  const words = match && match[1].split(/\s+/u);
  if (!match || !words[0].startsWith('ب') || !words[1].includes('للَّه')) return null;
  return { prefix: match[1], remainder: match[2] };
}

assert.ok(surahs[0][0].startsWith('بِسْمِ'), 'Al-Fatihah 1:1 retains its basmala as the ayah text.');
assert.ok(!surahs[8][0].startsWith('بِسْمِ'), 'At-Tawbah does not receive a basmala prefix.');
for (let surah = 2; surah <= 114; surah++) {
  if (surah === 9) continue;
  const original = surahs[surah - 1][0];
  const split = splitBasmala(original);
  assert.ok(split, `Surah ${surah} has the source basmala prefix`);
  assert.equal(split.prefix + split.remainder, original, `Basmala display split changed text in surah ${surah}`);
}

const metadata = fs.readFileSync(metadataPath, 'utf8');
const metadataHash = crypto.createHash('sha256').update(fs.readFileSync(metadataPath)).digest('hex');
assert.equal(
  metadataHash,
  '8867c1d88191472adec9db694b3cd9f135b1a2ef580574d32cf888dcb22c5c7a',
  'Bundled Mushaf page/juz metadata must remain byte-for-byte unchanged from its source download.'
);
const pages = [...metadata.matchAll(/<page index="(\d+)" sura="(\d+)" aya="(\d+)"\s*\/>/gu)];
const juz = [...metadata.matchAll(/<juz index="(\d+)" sura="(\d+)" aya="(\d+)"\s*\/>/gu)];
assert.equal(pages.length, 604, 'Mushaf page metadata must cover all 604 pages.');
assert.equal(juz.length, 30, 'Juz metadata must cover all 30 juz.');
assert.deepEqual(pages[0].slice(1).map(Number), [1, 1, 1]);
assert.deepEqual(juz[0].slice(1).map(Number), [1, 1, 1]);
assert.ok(metadata.includes('copyright="(C) 2008-2009 Tanzil.info"'));

const license = fs.readFileSync(path.join(assets, 'TANZIL-LICENSE.txt'), 'utf8');
assert.ok(license.includes('Copyright (C) 2007-2021 Tanzil Project'));
assert.ok(license.includes('CHANGING IT IS NOT ALLOWED'));
assert.ok(license.includes('link is made to tanzil.net'));
assert.ok(html.includes('href="https://tanzil.net/updates/"'));
assert.ok(html.includes("data-nav-view=\"quran\""));
assert.ok(html.includes("fetch('quran-uthmani.txt'"));
assert.ok(html.includes("fetch('quran-data.xml'"));

const scripts = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/giu)];
assert.ok(scripts.length > 0, 'The application has an inline script.');
for (const [index, script] of scripts.entries()) {
  new vm.Script(script[1], { filename: `${path.basename(htmlPath)} inline script ${index + 1}` });
}

console.log('PASS Quran source SHA-256, 114 surahs / 6,236 ayahs, basmala preservation, 604-page / 30-juz metadata, license credit, and inline JavaScript syntax.');
