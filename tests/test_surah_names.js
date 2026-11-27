'use strict';
// Memastikan nama Latin surah (transliterasi Kemenag) dipasangkan dengan surah yang benar:
// jumlah ayat di tabel Java = fixture Kemenag = teks Tanzil, dan nama Arab Tanzil tetap berurutan.
const fs = require('fs');
const path = require('path');
const assert = require('assert');

const root = path.join(__dirname, '..');
const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'surah-names-kemenag.json'), 'utf8'));
const java = fs.readFileSync(path.join(root, 'app/src/main/java/com/rutinitas/islamic/QuranSurahNames.java'), 'utf8');
const text = fs.readFileSync(path.join(root, 'app/src/main/assets/quran-uthmani.txt'), 'utf8');
const meta = fs.readFileSync(path.join(root, 'app/src/main/assets/quran-data.xml'), 'utf8');

const block = (name) => java.match(new RegExp(`${name} = \\{([\\s\\S]*?)\\};`))[1];
const javaNames = [...block('LATIN').matchAll(/"((?:[^"\\]|\\.)*)"/g)].map((m) => m[1]);
const javaCounts = block('AYAH_COUNTS').split(',').map((v) => Number(v.trim()));

const tanzilCounts = new Array(114).fill(0);
for (const line of text.split(/\r?\n/)) {
  const parts = line.split('|');
  if (parts.length < 3) continue;
  const surah = Number(parts[0]);
  if (surah >= 1 && surah <= 114) tanzilCounts[surah - 1]++;
}
const xmlSuras = [...meta.matchAll(/<sura index="(\d+)" ayas="(\d+)"[^>]*? name="([^"]+)"/g)];

assert.equal(fixture.length, 114);
assert.equal(javaNames.length, 114, 'Tabel Java harus memuat 114 nama');
assert.equal(javaCounts.length, 114, 'Tabel Java harus memuat 114 jumlah ayat');
assert.equal(xmlSuras.length, 114);
for (let i = 0; i < 114; i++) {
  const n = i + 1;
  assert.equal(fixture[i].number, n, `Urutan fixture salah di ${n}`);
  assert.equal(javaNames[i], fixture[i].latin, `Nama surah ${n} berbeda dari Kemenag`);
  assert.equal(javaCounts[i], fixture[i].ayahs, `Jumlah ayat surah ${n} berbeda dari Kemenag`);
  assert.equal(tanzilCounts[i], fixture[i].ayahs, `Surah ${n} (${fixture[i].latin}): Tanzil ${tanzilCounts[i]} ayat, Kemenag ${fixture[i].ayahs}`);
  assert.equal(Number(xmlSuras[i][1]), n);
  assert.equal(Number(xmlSuras[i][2]), fixture[i].ayahs, `Metadata Tanzil surah ${n} berbeda`);
}
assert.equal(tanzilCounts.reduce((a, b) => a + b, 0), 6236);
// Titik uji manual: nama, nomor, dan nama Arab yang mudah dikenali.
const spot = { 1: ['Al-Fatihah', 'الفاتحة'], 2: ['Al-Baqarah', 'البقرة'], 18: ['Al-Kahf', 'الكهف'],
  36: ['Yasin', 'يس'], 67: ['Al-Mulk', 'الملك'], 112: ['Al-Ikhlas', 'الإخلاص'], 114: ['An-Nas', 'الناس'] };
for (const [n, [latin, arabic]] of Object.entries(spot)) {
  assert.equal(javaNames[n - 1], latin);
  assert.equal(xmlSuras[n - 1][3], arabic, `Nama Arab surah ${n}`);
}
console.log('PASS 114 nama surah Kemenag cocok dengan jumlah ayat Tanzil (6.236 ayat) dan urutan nama Arab.');
