// Generates app/src/main/java/com/sallo/hifz/quran/QuranData.kt from the Tanzil files.
//
// Usage:
//   node tools/gen_quran_data.mjs <quran-data.js> <quran-simple-clean.txt> <out.kt> [passages.txt ...] [--allow-gaps]
//
// Passage files hold the division of «موسوعة التفسير» (dorar.net), one surah per line:
//   70: 1-7, 8-18, 19-35, 36-44
// collected by the user with tools/dorar_passages_snippet.js. Lines starting with '#' are ignored, and a
// later file replaces the lines of an earlier one. Every surah must be covered exactly; --allow-gaps
// (for development only) turns each gap into one passage instead of failing.
//
// Sources (Tanzil Project, https://tanzil.net, Creative Commons Attribution 3.0):
//   https://tanzil.net/res/text/metadata/quran-data.js
//   https://tanzil.net/pub/download/index.php?quranType=simple-clean&outType=txt-2&agree=true
//
// The metadata file is parsed with regular expressions (never evaluated).
// Only per-ayah letter counts are taken from the text; the text itself is not shipped.

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

const args = process.argv.slice(2);
const allowGaps = args.includes('--allow-gaps');
const [metaPath, textPath, outPath, ...passageFiles] = args.filter((a) => a !== '--allow-gaps');
if (!metaPath || !textPath || !outPath) {
  console.error('usage: node tools/gen_quran_data.mjs <quran-data.js> <quran-simple-clean.txt> <out.kt> [passages.txt ...] [--allow-gaps]');
  process.exit(1);
}

// Surah names with standard hamza spelling (Tanzil writes «ابراهيم»).
const NAMES = [
  'الفاتحة', 'البقرة', 'آل عمران', 'النساء', 'المائدة', 'الأنعام', 'الأعراف', 'الأنفال', 'التوبة', 'يونس',
  'هود', 'يوسف', 'الرعد', 'إبراهيم', 'الحجر', 'النحل', 'الإسراء', 'الكهف', 'مريم', 'طه',
  'الأنبياء', 'الحج', 'المؤمنون', 'النور', 'الفرقان', 'الشعراء', 'النمل', 'القصص', 'العنكبوت', 'الروم',
  'لقمان', 'السجدة', 'الأحزاب', 'سبأ', 'فاطر', 'يس', 'الصافات', 'ص', 'الزمر', 'غافر',
  'فصلت', 'الشورى', 'الزخرف', 'الدخان', 'الجاثية', 'الأحقاف', 'محمد', 'الفتح', 'الحجرات', 'ق',
  'الذاريات', 'الطور', 'النجم', 'القمر', 'الرحمن', 'الواقعة', 'الحديد', 'المجادلة', 'الحشر', 'الممتحنة',
  'الصف', 'الجمعة', 'المنافقون', 'التغابن', 'الطلاق', 'التحريم', 'الملك', 'القلم', 'الحاقة', 'المعارج',
  'نوح', 'الجن', 'المزمل', 'المدثر', 'القيامة', 'الإنسان', 'المرسلات', 'النبأ', 'النازعات', 'عبس',
  'التكوير', 'الانفطار', 'المطففين', 'الانشقاق', 'البروج', 'الطارق', 'الأعلى', 'الغاشية', 'الفجر', 'البلد',
  'الشمس', 'الليل', 'الضحى', 'الشرح', 'التين', 'العلق', 'القدر', 'البينة', 'الزلزلة', 'العاديات',
  'القارعة', 'التكاثر', 'العصر', 'الهمزة', 'الفيل', 'قريش', 'الماعون', 'الكوثر', 'الكافرون', 'النصر',
  'المسد', 'الإخلاص', 'الفلق', 'الناس',
];

function fail(msg) {
  console.error('ERROR: ' + msg);
  process.exit(2);
}

const meta = readFileSync(metaPath, 'utf8');

function block(name) {
  const marker = `QuranData.${name} = [`;
  const start = meta.indexOf(marker);
  if (start < 0) fail('missing block ' + name);
  const end = meta.indexOf('];', start);
  if (end < 0) fail('unterminated block ' + name);
  return meta.slice(start + marker.length, end);
}

// [start, ayas, order, rukus, 'name', "tname", 'ename', 'type']
const suras = [...block('Sura').matchAll(/\[(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*'([^']*)'/g)]
  .map((m) => ({ start: +m[1], ayas: +m[2], name: m[5] }));
const pairs = (name) => [...block(name).matchAll(/\[(\d+),\s*(\d+)\]/g)].map((m) => [+m[1], +m[2]]);
const pagePairs = pairs('Page');
const juzPairs = pairs('Juz');
const quarterPairs = pairs('HizbQaurter');

if (suras.length !== 114) fail(`expected 114 suras, got ${suras.length}`);
let running = 0;
suras.forEach((s, i) => {
  if (s.start !== running) fail(`sura ${i + 1} start ${s.start} != ${running}`);
  running += s.ayas;
});
if (running !== 6236) fail(`expected 6236 ayahs, got ${running}`);

const index = (sura, aya) => {
  if (sura === 115 && aya === 1) return 6236; // end sentinel
  const s = suras[sura - 1];
  if (!s || aya < 1 || aya > s.ayas) fail(`bad position ${sura}:${aya}`);
  return s.start + aya - 1;
};

if (pagePairs.length !== 605) fail(`expected 604 pages + sentinel, got ${pagePairs.length}`);
if (juzPairs.length !== 31) fail(`expected 30 juz + sentinel, got ${juzPairs.length}`);
const pageStarts = pagePairs.slice(0, 604).map(([s, a]) => index(s, a));
const juzStarts = juzPairs.slice(0, 30).map(([s, a]) => index(s, a));
if (quarterPairs.length !== 241) fail(`expected 240 hizb quarters + sentinel, got ${quarterPairs.length}`);
const quarterStarts = quarterPairs.slice(0, 240).map(([s, a]) => index(s, a));
for (let i = 1; i < quarterStarts.length; i++) {
  if (quarterStarts[i] <= quarterStarts[i - 1]) fail(`quarter ${i + 1} does not advance`);
}
juzStarts.forEach((start, j) => {
  if (quarterStarts[j * 8] !== start) fail(`juz ${j + 1} does not start at quarter ${j * 8 + 1}`);
});
for (let i = 1; i < pageStarts.length; i++) {
  if (pageStarts[i] <= pageStarts[i - 1]) fail(`page ${i + 1} does not advance`);
}
if (index(...pagePairs[604]) !== 6236) fail('page sentinel mismatch');

// Letters per ayah from the simple-clean text: "sura|aya|text", comments start with '#'.
const letters = new Array(6236).fill(-1);
for (const line of readFileSync(textPath, 'utf8').split(/\r?\n/)) {
  const m = /^(\d+)\|(\d+)\|(.*)$/.exec(line);
  if (!m) continue;
  const i = index(+m[1], +m[2]);
  const count = (m[3].match(/[ء-ي]/g) || []).length;
  if (count === 0) fail(`ayah ${m[1]}:${m[2]} has no letters`);
  letters[i] = count;
}
const missing = letters.findIndex((n) => n < 0);
if (missing >= 0) fail(`no text for global ayah index ${missing}`);

// Tafsir passages: start index of every passage, in mushaf order.
const passageLines = new Map();
for (const file of passageFiles) {
  for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
    const m = /^\s*(\d+)\s*:\s*(.+?)\s*$/.exec(line);
    if (!m || line.trim().startsWith('#')) continue;
    passageLines.set(+m[1], m[2]);
  }
}
let passageStarts = [];
if (passageFiles.length) {
  const problems = [];
  suras.forEach((sura, i) => {
    const n = i + 1;
    const text = passageLines.get(n);
    const ranges = text
      ? text.split(',').map((t) => t.trim()).filter(Boolean).map((t) => {
          const [a, b] = t.split('-').map(Number);
          return [a, Number.isNaN(b) || b === undefined ? a : b];
        })
      : [];
    ranges.sort((x, y) => x[0] - y[0]);
    let next = 1;
    const starts = [];
    for (const [a, b] of ranges) {
      if (b < a || a < next || b > sura.ayas) {
        problems.push(`${n}: bad or overlapping passage ${a}-${b}`);
        continue;
      }
      if (a > next) {
        if (!allowGaps) problems.push(`${n}: ayahs ${next}-${a - 1} are in no passage`);
        starts.push(next);
      }
      starts.push(a);
      next = b + 1;
    }
    if (next <= sura.ayas) {
      if (!allowGaps) problems.push(`${n}: ayahs ${next}-${sura.ayas} are in no passage`);
      starts.push(next);
    }
    passageStarts.push(...starts.map((a) => index(n, a)));
  });
  if (problems.length) fail(`passages incomplete in ${new Set(problems.map((p) => p.split(':')[0])).size} surahs:\n  ` + problems.join('\n  '));
  for (let i = 1; i < passageStarts.length; i++) {
    if (passageStarts[i] <= passageStarts[i - 1]) fail(`passage ${i + 1} does not advance`);
  }
  if (passageStarts[0] !== 0) fail('passages do not start at the first ayah');
}

// Name check against Tanzil (ignoring hamza forms on alef).
const norm = (s) => s.replace(/[أإآ]/g, 'ا');
suras.forEach((s, i) => {
  if (norm(s.name) !== norm(NAMES[i])) console.warn(`name differs for sura ${i + 1}: tanzil=${s.name} ours=${NAMES[i]}`);
});

const kt = `// GENERATED by tools/gen_quran_data.mjs. Do not edit by hand.
// Source: Tanzil Quran metadata and simple-clean text (https://tanzil.net),
// Creative Commons Attribution 3.0. Only counts are kept; no Quran text is shipped.
package com.sallo.hifz.quran

internal object QuranData {
    val SURAH_NAMES: Array<String> = arrayOf(
${NAMES.map((n) => `        "${n}",`).join('\n')}
    )

    /** Number of ayahs in each surah (114 entries). */
    val SURAH_AYAHS: IntArray = intArrayOf(${suras.map((s) => s.ayas).join(', ')})

    /** Global 0-based ayah index where each juz starts (30 entries). */
    val JUZ_STARTS: IntArray = intArrayOf(${juzStarts.join(', ')})

    /** Global 0-based ayah index where each hizb quarter (rub') starts (240 entries; a hizb is 4, a juz 8). */
    const val QUARTER_STARTS: String = "${quarterStarts.join(',')}"

    /** Global 0-based ayah index where each page of the Madani mushaf starts (604 entries). */
    const val PAGE_STARTS: String = "${pageStarts.join(',')}"

    /** Arabic letters in each ayah of the simple-clean text (6236 entries). */
    const val AYAH_LETTERS: String = "${letters.join(',')}"

    /**
     * Global 0-based ayah index where each passage of «موسوعة التفسير» (dorar.net) starts
     * (${passageStarts.length} entries; empty when no passage file was given). Passages never cross a surah.
     */
    const val PASSAGE_STARTS: String = "${passageStarts.join(',')}"
}
`;

mkdirSync(dirname(outPath), { recursive: true });
writeFileSync(outPath, kt, 'utf8');
const totalLetters = letters.reduce((a, b) => a + b, 0);
console.log(`ok: 114 suras, 6236 ayahs, ${pageStarts.length} pages, ${juzStarts.length} juz, ${passageStarts.length} passages${allowGaps ? ' (gaps filled)' : ''}, ${totalLetters} letters -> ${outPath}`);
console.log('anchors: p1=' + pageStarts[0] + ' p2=' + pageStarts[1] + ' p50=' + pageStarts[49] + ' p582=' + pageStarts[581] + ' p604=' + pageStarts[603]);
