#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');

const candidatePaths = [
  process.argv[2],
  path.join(__dirname, 'Rutinitas.html'),
  path.join(__dirname, 'Rutinitas_2.html')
].filter(Boolean);

const htmlPath = candidatePaths.find((p) => fs.existsSync(p));
if (!htmlPath) {
  console.error('Tidak dapat menemukan Rutinitas.html. Berikan path sebagai argv[2].');
  process.exit(1);
}

const htmlSource = fs.readFileSync(htmlPath, 'utf8');
const scriptMatch = htmlSource.match(/<script>([\s\S]*?)<\/script>/);
if (!scriptMatch) {
  console.error('Tag <script> tidak ditemukan di', htmlPath);
  process.exit(1);
}
const appScriptCode = scriptMatch[1];

const STORAGE_KEY = 'rutin_hifz_state_v10';

function parseHtmlTagDescriptors(html) {
  const descriptors = [];
  const tagRe = /<([a-zA-Z0-9-]+)\b([^>]*)>/g;
  let m;
  while ((m = tagRe.exec(html)) !== null) {
    const tagName = m[1].toUpperCase();
    const rawAttrs = m[2] || '';
    const attrs = {};
    const attrRe = /([a-zA-Z0-9_-]+)(?:=(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g;
    let am;
    while ((am = attrRe.exec(rawAttrs)) !== null) {
      const k = am[1];
      const v = am[2] !== undefined ? am[2] : (am[3] !== undefined ? am[3] : (am[4] !== undefined ? am[4] : ''));
      attrs[k] = v;
    }
    descriptors.push({ tagName, attrs });
  }
  return descriptors;
}

function extractHtmlIds(html) {
  return parseHtmlTagDescriptors(html).map((d) => d.attrs.id).filter(Boolean);
}

function createAppHarness({
  initialStorage = {},
  initialWindowName = '',
  fixedNowIso = '2026-11-16T05:00:00+07:00',
  isSecureContext = true,
  protocol = null,
  geolocationBehavior = null,
  throwOnSetItem = false,
  initialIdbRaw = undefined
} = {}) {
  const storageMap = new Map(
    Object.entries(initialStorage).map(([k, v]) => [k, typeof v === 'string' ? v : JSON.stringify(v)])
  );

  const localStorageMock = {
    getItem: (k) => (storageMap.has(k) ? storageMap.get(k) : null),
    setItem: (k, v) => {
      if (throwOnSetItem) throw new Error('QuotaExceededError');
      storageMap.set(k, String(v));
    },
    removeItem: (k) => { storageMap.delete(k); },
    clear: () => { storageMap.clear(); }
  };

  let currentNowMs = new Date(fixedNowIso).getTime();
  const RealDate = Date;
  class MockDate extends RealDate {
    constructor(...args) {
      if (args.length === 0) super(currentNowMs);
      else super(...args);
    }
    static now() { return currentNowMs; }
  }

  const allElements = [];
  const elementsById = new Map();
  let documentMock = null;

  function matchesAtomicSelector(el, token) {
    if (!el || !token) return false;
    let rest = token.trim();
    if (!rest) return false;

    const attrRe = /\[([^=\]]+)(?:=["']?([^"'\]]*)["']?)?\]/g;
    const attrChecks = [];
    rest = rest.replace(attrRe, (_, k, v) => { attrChecks.push([k, v]); return ''; });

    const tagMatch = rest.match(/^[a-zA-Z0-9-]+/);
    if (tagMatch) {
      if (el.tagName !== tagMatch[0].toUpperCase()) return false;
      rest = rest.slice(tagMatch[0].length);
    }

    const idMatches = rest.match(/#[a-zA-Z0-9_-]+/g) || [];
    for (const idTok of idMatches) {
      if (el.id !== idTok.slice(1)) return false;
    }
    rest = rest.replace(/#[a-zA-Z0-9_-]+/g, '');

    const classMatches = rest.match(/\.[a-zA-Z0-9_-]+/g) || [];
    for (const clsTok of classMatches) {
      if (!el.classList.contains(clsTok.slice(1))) return false;
    }

    for (const [k, expectedVal] of attrChecks) {
      const actualVal = el.getAttribute(k);
      if (expectedVal === undefined) {
        if (actualVal === null) return false;
      } else if (actualVal !== expectedVal) {
        return false;
      }
    }
    return true;
  }

  function matchesSelector(el, selector) {
    const raw = String(selector || '').trim();
    if (!raw || !el) return false;
    if (raw.includes(',')) return raw.split(',').some((part) => matchesSelector(el, part));
    const parts = raw.split(/\s+/).filter(Boolean);
    if (parts.length === 0) return false;
    if (!matchesAtomicSelector(el, parts[parts.length - 1])) return false;

    let cur = el.parentElement;
    for (let i = parts.length - 2; i >= 0; i -= 1) {
      let matchedAncestor = false;
      while (cur) {
        if (matchesAtomicSelector(cur, parts[i])) {
          matchedAncestor = true;
          cur = cur.parentElement;
          break;
        }
        cur = cur.parentElement;
      }
      if (!matchedAncestor) return false;
    }
    return true;
  }

  function makeElement({ id = '', tagName = 'DIV', attrs = {} } = {}) {
    const classSet = new Set(String(attrs.class || '').split(/\s+/).map((s) => s.trim()).filter(Boolean));
    const listeners = {};
    const children = [];
    const attrMap = { ...attrs };
    if (id) attrMap.id = id;

    const dataset = {};
    Object.entries(attrMap).forEach(([k, v]) => {
      if (k.startsWith('data-')) {
        const camel = k.slice(5).replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
        dataset[camel] = String(v);
      }
    });

    const el = {
      id,
      tagName: String(tagName || 'DIV').toUpperCase(),
      value: attrMap.value !== undefined ? String(attrMap.value) : '',
      textContent: '',
      _innerHTML: '',
      get innerHTML() { return this._innerHTML; },
      set innerHTML(val) {
        this._innerHTML = String(val);
        if (val === '') {
          children.forEach((ch) => { if (ch) ch.parentElement = null; });
          children.length = 0;
        }
      },
      disabled: 'disabled' in attrMap,
      style: {},
      dataset,
      files: [],
      scrollTop: 0,
      parentElement: null,
      children,
      get options() { return children.filter((c) => c.tagName === 'OPTION'); },
      get className() { return Array.from(classSet).join(' '); },
      set className(val) {
        classSet.clear();
        String(val || '').split(/\s+/).filter(Boolean).forEach((c) => classSet.add(c));
      },
      classList: {
        add: (...cls) => cls.forEach((c) => c && classSet.add(c)),
        remove: (...cls) => cls.forEach((c) => c && classSet.delete(c)),
        toggle: (c, force) => {
          if (force === true) { classSet.add(c); return true; }
          if (force === false) { classSet.delete(c); return false; }
          if (classSet.has(c)) { classSet.delete(c); return false; }
          classSet.add(c);
          return true;
        },
        contains: (c) => classSet.has(c)
      },
      getAttribute(k) { return Object.prototype.hasOwnProperty.call(attrMap, k) ? attrMap[k] : null; },
      setAttribute(k, v) {
        attrMap[k] = String(v);
        if (k === 'class') el.className = v;
        if (k === 'id') { this.id = String(v); elementsById.set(this.id, this); }
        if (k.startsWith('data-')) {
          const camel = k.slice(5).replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
          dataset[camel] = String(v);
        }
      },
      removeAttribute(k) {
        delete attrMap[k];
        if (k === 'disabled') el.disabled = false;
      },
      appendChild(child) {
        if (child && typeof child === 'object') { child.parentElement = el; children.push(child); }
        return child;
      },
      removeChild(child) {
        const idx = children.indexOf(child);
        if (idx >= 0) { children.splice(idx, 1); if (child) child.parentElement = null; }
        return child;
      },
      addEventListener(type, fn) {
        if (!listeners[type]) listeners[type] = [];
        listeners[type].push(fn);
      },
      dispatchEvent(evt) {
        const eventObj = typeof evt === 'string' ? { type: evt } : { ...(evt || {}) };
        if (!eventObj.target) eventObj.target = el;
        if (!eventObj.preventDefault) eventObj.preventDefault = () => {};
        if (!eventObj.stopPropagation) eventObj.stopPropagation = () => {};
        (listeners[eventObj.type] || []).forEach((fn) => fn.call(el, eventObj));
        return true;
      },
      click(evOverrides = {}) {
        if (el.disabled) return;
        if (documentMock) documentMock.activeElement = el;
        el.dispatchEvent({ type: 'click', target: el, ...evOverrides });
      },
      focus() { if (documentMock) documentMock.activeElement = el; },
      blur() {
        if (documentMock && documentMock.activeElement === el) documentMock.activeElement = null;
        el.dispatchEvent({ type: 'blur', target: el });
      },
      closest(selector) {
        let cur = el;
        while (cur) {
          if (matchesSelector(cur, selector)) return cur;
          cur = cur.parentElement;
        }
        return null;
      },
      querySelector(selector) {
        let found = null;
        const walk = (node) => {
          for (const ch of node.children || []) {
            if (matchesSelector(ch, selector)) { found = ch; return; }
            walk(ch);
            if (found) return;
          }
        };
        walk(el);
        return found || makeElement();
      },
      querySelectorAll(selector) {
        const out = [];
        const walk = (node) => {
          (node.children || []).forEach((ch) => {
            if (matchesSelector(ch, selector)) out.push(ch);
            walk(ch);
          });
        };
        walk(el);
        return out;
      }
    };

    allElements.push(el);
    if (id && !elementsById.has(id)) elementsById.set(id, el);
    return el;
  }

  const descriptors = parseHtmlTagDescriptors(htmlSource);
  const bodyEl = makeElement({ id: 'body', tagName: 'BODY' });
  descriptors.forEach((d) => {
    if (['HTML', 'HEAD', 'BODY', 'META', 'LINK', 'TITLE', 'STYLE', 'SCRIPT'].includes(d.tagName)) return;
    const el = makeElement({ id: d.attrs.id || '', tagName: d.tagName, attrs: d.attrs });
    bodyEl.appendChild(el);
  });

  const docListeners = {};
  documentMock = {
    hidden: false,
    visibilityState: 'visible',
    activeElement: null,
    documentElement: makeElement({ tagName: 'HTML' }),
    body: bodyEl,
    getElementById(id) {
      if (elementsById.has(id)) return elementsById.get(id);
      const fallback = makeElement({ id, tagName: 'DIV' });
      bodyEl.appendChild(fallback);
      return fallback;
    },
    querySelector(selector) { return bodyEl.querySelector(selector); },
    querySelectorAll(selector) { return bodyEl.querySelectorAll(selector); },
    createElement(tag) { return makeElement({ tagName: tag }); },
    addEventListener(type, fn) {
      if (!docListeners[type]) docListeners[type] = [];
      docListeners[type].push(fn);
    },
    dispatchEvent(evt) {
      const eventObj = typeof evt === 'string' ? { type: evt } : { ...(evt || {}) };
      (docListeners[eventObj.type] || []).forEach((fn) => fn(eventObj));
    }
  };

  const intervals = [];
  const timeouts = [];
  let mockAudioState = 'running';
  const audioLog = { contexts: 0, beeps: 0, resumes: 0 };
  const wakeLockLog = { requests: 0, releases: 0, active: false };
  const vibrateLog = [];
  let lastExportedText = null;

  class MockAudioContext {
    constructor() { audioLog.contexts += 1; this.currentTime = 0; this.destination = {}; }
    get state() { return mockAudioState; }
    resume() { audioLog.resumes += 1; mockAudioState = 'running'; return Promise.resolve(); }
    createOscillator() {
      return { type: 'sine', frequency: { setValueAtTime() {} }, connect() {}, start() { audioLog.beeps += 1; }, stop() {} };
    }
    createGain() { return { gain: { setValueAtTime() {}, exponentialRampToValueAtTime() {} }, connect() {} }; }
  }

  class MockBlob {
    constructor(parts, opts = {}) { this.parts = parts; this._text = parts.map((p) => String(p)).join(''); this.type = opts.type || ''; }
  }

  class MockFileReader {
    readAsText(fileObj) {
      this.result = (fileObj && fileObj._text !== undefined) ? fileObj._text : String((fileObj && fileObj.content) || fileObj || '');
      if (typeof this.onload === 'function') this.onload();
    }
  }

  const navigatorMock = {
    vibrate(pattern) { vibrateLog.push(pattern); return true; },
    wakeLock: {
      async request(kind) {
        assert.strictEqual(kind, 'screen');
        wakeLockLog.requests += 1;
        wakeLockLog.active = true;
        const releaseListeners = [];
        return {
          released: false,
          addEventListener(type, fn) { if (type === 'release') releaseListeners.push(fn); },
          async release() {
            if (!this.released) {
              this.released = true;
              wakeLockLog.releases += 1;
              wakeLockLog.active = false;
              releaseListeners.forEach((fn) => fn());
            }
          }
        };
      }
    }
  };

  if (geolocationBehavior !== null) {
    navigatorMock.geolocation = {
      getCurrentPosition(successCb, errorCb, opts) {
        if (typeof geolocationBehavior === 'function') geolocationBehavior(successCb, errorCb, opts);
      }
    };
  }

  const winListeners = {};
  const windowMock = {
    name: initialWindowName,
    isSecureContext,
    location: { protocol: protocol || (isSecureContext ? 'https:' : 'file:') },
    AudioContext: MockAudioContext,
    localStorage: localStorageMock,
    navigator: navigatorMock,
    document: documentMock,
    addEventListener(type, fn) { if (!winListeners[type]) winListeners[type] = []; winListeners[type].push(fn); },
    dispatchEvent(evt) { (winListeners[evt.type] || []).forEach((fn) => fn(evt)); },
    URL: {
      createObjectURL(blob) { lastExportedText = blob && blob._text !== undefined ? blob._text : null; return 'blob:mock'; },
      revokeObjectURL() {}
    }
  };

  const idbStoreMap = new Map();
  if (typeof initialIdbRaw === 'string') idbStoreMap.set(STORAGE_KEY, initialIdbRaw);
  const indexedDBMock = initialIdbRaw === undefined ? undefined : {
    open() {
      const req = { result: null, onupgradeneeded: null, onsuccess: null, onerror: null };
      const dbObj = {
        objectStoreNames: { contains: () => true },
        createObjectStore() {},
        close() {},
        transaction() {
          const tx = {
            oncomplete: null,
            onerror: null,
            objectStore() {
              return {
                get(k) {
                  const gReq = { result: undefined, onsuccess: null, onerror: null };
                  Promise.resolve().then(() => {
                    gReq.result = idbStoreMap.get(k);
                    if (typeof gReq.onsuccess === 'function') gReq.onsuccess();
                  });
                  return gReq;
                },
                put(v, k) {
                  idbStoreMap.set(k, v);
                  Promise.resolve().then(() => {
                    if (typeof tx.oncomplete === 'function') tx.oncomplete();
                  });
                }
              };
            }
          };
          return tx;
        }
      };
      req.result = dbObj;
      Promise.resolve().then(() => {
        if (typeof req.onupgradeneeded === 'function') req.onupgradeneeded();
        if (typeof req.onsuccess === 'function') req.onsuccess();
      });
      return req;
    }
  };

  const sandbox = {
    window: windowMock,
    isSecureContext,
    document: documentMock,
    navigator: navigatorMock,
    localStorage: localStorageMock,
    ...(indexedDBMock ? { indexedDB: indexedDBMock } : {}),
    Date: MockDate,
    AudioContext: MockAudioContext,
    Blob: MockBlob,
    FileReader: MockFileReader,
    URL: windowMock.URL,
    console, Math, JSON, Number, String, Boolean, Array, Object, RegExp, TypeError, Set, Map, WeakSet, WeakMap, Promise, Error,
    Event: function MockEvent(type) { this.type = type; },
    parseInt, parseFloat, isNaN, isFinite,
    setInterval(fn, ms) { const id = intervals.length + 1; intervals.push({ id, fn, ms, active: true }); return id; },
    clearInterval(id) { const item = intervals.find((x) => x.id === id); if (item) item.active = false; },
    setTimeout(fn, ms) { const id = timeouts.length + 1; timeouts.push({ id, fn, ms, active: true }); return id; },
    clearTimeout(id) { const item = timeouts.find((x) => x.id === id); if (item) item.active = false; }
  };
  sandbox.globalThis = sandbox;
  sandbox.self = sandbox;

  vm.createContext(sandbox);
  vm.runInContext(appScriptCode, sandbox, { filename: 'Rutin.final.js' });

  const engine = sandbox.__ROUTINE_ENGINE__;
  assert.ok(engine, '__ROUTINE_ENGINE__ harus tersedia pada globalThis');

  return {
    ctx: sandbox,
    sandbox,
    engine,
    window: windowMock,
    document: documentMock,
    elements: elementsById,
    storageMap,
    audioLog,
    wakeLockLog,
    el(id) { return elementsById.get(id); },
    click(id, evOverrides) {
      const target = elementsById.get(id);
      assert.ok(target, `Elemen #${id} tidak ditemukan`);
      target.click(evOverrides);
    },
    clickSelector(selector, predicate) {
      const matches = documentMock.querySelectorAll(selector);
      const found = predicate ? matches.find(predicate) : matches[0];
      assert.ok(found, `Elemen tidak ditemukan untuk ${selector}`);
      found.click();
      return found;
    },
    setInput(id, value) {
      const target = elementsById.get(id);
      assert.ok(target, `Input #${id} tidak ditemukan`);
      target.value = String(value);
      target.dispatchEvent({ type: 'change', target });
    },
    getState() { return vm.runInContext('appState', sandbox); },
    getChimeCount() { return audioLog.contexts; },
    getOscillatorCount() { return audioLog.beeps; },
    setAudioState(st) { mockAudioState = st; },
    getResumeCalls() { return audioLog.resumes; },
    getLastExportedText() { return lastExportedText; },
    now() { return new MockDate(); },
    setNow(isoOrMs) { currentNowMs = typeof isoOrMs === 'number' ? isoOrMs : new RealDate(isoOrMs).getTime(); engine.render(); },
    advanceMs(ms) { currentNowMs += ms; engine.render(); },
    elapseClockMs(ms) { currentNowMs += ms; },
    tick() { intervals.filter((x) => x.active).forEach((x) => x.fn()); },
    reload() {
      const persisted = {};
      storageMap.forEach((v, k) => { persisted[k] = v; });
      return createAppHarness({
        initialStorage: persisted,
        initialWindowName: windowMock.name,
        fixedNowIso: new RealDate(currentNowMs).toISOString(),
        isSecureContext,
        protocol,
        geolocationBehavior,
        throwOnSetItem
      });
    }
  };
}

const tests = [];
function test(name, fn) { tests.push({ name, fn }); }

// ===========================================================================
// BAGIAN I: SUITE PENGUJIAN KOMBINATORIAL, ENGINE UMUM & UI TERPADU (01 - 21)
// ===========================================================================

test('01. Matriks jadwal kombinatorial (<7 Juz & >7 Juz) x 5 Mode Sabaq x 3 Sumber Shalat x 3 Profil x Freeze x 7 Hari x 24 Jam (45.360 kasus)', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T05:00:00+07:00' });
  const hours = Array.from({ length: 24 }, (_, hr) => `${String(hr).padStart(2, '0')}:${hr === 3 ? '59' : (hr === 21 ? '14' : '15')}`);
  const hhmmRe = /^([01]\d|2[0-3]):[0-5]\d$/;
  let checks = 0;

  const configs = [
    { sabaq: { mode: 'quarter_page', currentPage: 1, subSlot: 0, sundayNewSabaq: false }, prior: [], lvPace: 10, lvMag: false, hvJuz: 1, hvMag: false },
    { sabaq: { mode: 'quarter_page', currentPage: 42, subSlot: 3, sundayNewSabaq: true }, prior: [30], lvPace: 20, lvMag: true, hvJuz: 1, hvMag: false },
    { sabaq: { mode: 'half_page', currentPage: 22, subSlot: 1, sundayNewSabaq: false }, prior: [30], lvPace: 10, lvMag: false, hvJuz: 1, hvMag: false },
    { sabaq: { mode: 'one_page', currentPage: 65, subSlot: 0, sundayNewSabaq: true }, prior: [29, 30], lvPace: 20, lvMag: true, hvJuz: 1, hvMag: false },
    { sabaq: { mode: 'multi_page', currentPage: 105, pagesPerDay: 3, sundayNewSabaq: false }, prior: [1, 30], lvPace: 20, lvMag: true, hvJuz: 1, hvMag: false },
    { sabaq: { mode: 'ayah', surahNum: 6, startAyah: 111, ayahsPerDay: 12, currentPage: 141, sundayNewSabaq: false }, prior: [30], lvPace: 20, lvMag: true, hvJuz: 1, hvMag: false },
    { sabaq: { mode: 'one_page', currentPage: 141, subSlot: 0, sundayNewSabaq: false }, prior: [], lvPace: 10, lvMag: false, hvJuz: 1, hvMag: false },
    { sabaq: { mode: 'one_page', currentPage: 142, subSlot: 0, sundayNewSabaq: false }, prior: [], lvPace: 10, lvMag: false, hvJuz: 1, hvMag: false },
    { sabaq: { mode: 'one_page', currentPage: 165, subSlot: 0, sundayNewSabaq: false }, prior: [30], lvPace: 10, lvMag: false, hvJuz: 1, hvMag: false },
    { sabaq: { mode: 'half_page', currentPage: 220, subSlot: 0, sundayNewSabaq: true }, prior: [29, 30], lvPace: 10, lvMag: false, hvJuz: 2, hvMag: true },
    { sabaq: { mode: 'quarter_page', currentPage: 350, subSlot: 2, sundayNewSabaq: false }, prior: [1, 15, 30], lvPace: 10, lvMag: false, hvJuz: 1, hvMag: true },
    { sabaq: { mode: 'multi_page', currentPage: 480, pagesPerDay: 2, sundayNewSabaq: false }, prior: [28, 29, 30], lvPace: 10, lvMag: false, hvJuz: 2, hvMag: true },
    { sabaq: { mode: 'ayah', surahNum: 78, startAyah: 1, ayahsPerDay: 20, currentPage: 582, sundayNewSabaq: true }, prior: [30], lvPace: 10, lvMag: false, hvJuz: 1, hvMag: false },
    { sabaq: { mode: 'one_page', currentPage: 604, subSlot: 0, sabaqCompleted: false, sundayNewSabaq: false }, prior: [30], lvPace: 10, lvMag: false, hvJuz: 1, hvMag: true },
    { sabaq: { mode: 'one_page', currentPage: 604, subSlot: 0, sabaqCompleted: true, sundayNewSabaq: false }, prior: [], lvPace: 10, lvMag: false, hvJuz: 2, hvMag: true }
  ];

  for (const cfg of configs) {
    for (const pSrc of ['fixed', 'manual', 'gps']) {
      for (const profId of ['profile_1', 'profile_2', 'profile_3']) {
        for (const frozen of [false, true]) {
          h.engine.setState({
            sabaq: cfg.sabaq,
            sabqiHistory: [],
            priorJuzList: cfg.prior,
            phaseDay: 3,
            sabaqFrozen: frozen,
            lvMaghribEnabled: cfg.lvMag,
            lvPace: cfg.lvPace,
            HighVolumeJuzPerDay: cfg.hvJuz,
            HighVolumeMaghribOn: cfg.hvMag,
            activeRoutineProfileId: profId,
            prayerSource: pSrc,
            subuhOffsetMins: 15,
            completedBlocksToday: []
          });
          for (let dayOffset = 0; dayOffset < 7; dayOffset += 1) {
            const dayStr = String(16 + dayOffset).padStart(2, '0');
            for (const hhmm of hours) {
              const iso = `2026-11-${dayStr}T${hhmm}:00+07:00`;
              const dt = new Date(iso);
              const sched = h.engine.getSchedule(dt);
              const sel = h.engine.getSelection(dt);

              assert.ok(sched.blocks.length >= 3);
              assert.ok(sched.hifz.steps.length >= 2 && sched.hifz.steps.length <= 3);
              assert.ok(sched.compressionRatio >= 0 && sched.compressionRatio <= 1);
              assert.ok(sched.slackBeforeGateMins >= 0);
              assert.ok(sel && sel.block && sel.block.id && Number.isFinite(sel.targetSecs) && sel.targetSecs >= 0);

              const pt = sched.prayerTimes;
              assert.ok(h.ctx.toMinutes(pt.subuh) < h.ctx.toMinutes(pt.dzuhur));
              assert.ok(h.ctx.toMinutes(pt.dzuhur) < h.ctx.toMinutes(pt.ashar));
              assert.ok(h.ctx.toMinutes(pt.ashar) < h.ctx.toMinutes(pt.maghrib));
              assert.ok(h.ctx.toMinutes(pt.maghrib) < h.ctx.toMinutes(pt.isya));

              for (const blk of sched.blocks) {
                assert.ok(hhmmRe.test(blk.start));
                assert.ok(hhmmRe.test(blk.end));
                assert.ok(blk.endMins > blk.startMins);
              }
              const activeFlex = sched.blocks.slice(0, -2).filter((b) => b.isFlexible && !b.isDeferred);
              for (let i = 0; i < activeFlex.length; i += 1) {
                for (let j = i + 1; j < activeFlex.length; j += 1) {
                  assert.strictEqual(
                    activeFlex[i].startMins < activeFlex[j].endMins && activeFlex[j].startMins < activeFlex[i].endMins,
                    false
                  );
                }
              }
              checks += 1;
            }
          }
          for (const checkTime of ['03:59', '05:15', '21:14', '22:15']) {
            h.setNow(`2026-11-16T${checkTime}:00+07:00`);
            assert.ok(h.el('blockTitle').textContent.length > 0);
            assert.ok(h.el('liveTimer').textContent.length > 0);
          }
        }
      }
    }
  }
  assert.strictEqual(checks, 45360);
});

test('02. Integritas seluruh ID DOM HTML, bilah atas ringkas (tanpa topStatusPill), 4 tab & grup opsi', () => {
  const htmlIds = extractHtmlIds(htmlSource);
  assert.strictEqual(htmlIds.includes('topStatusPill'), false);
  const htmlIdSet = new Set(htmlIds);
  const queryIdRe = /document\.getElementById\(\s*['"]([^'"]+)['"]\s*\)/g;
  const missingIds = [];
  let m;
  while ((m = queryIdRe.exec(appScriptCode)) !== null) {
    if (!htmlIdSet.has(m[1])) missingIds.push(m[1]);
  }
  assert.deepStrictEqual(missingIds, []);

  const h = createAppHarness({ fixedNowIso: '2026-11-16T08:00:00+07:00' });
  for (const id of htmlIds) assert.ok(h.el(id), `Elemen #${id} hilang`);

  const optionCounts = [
    ['.cat-tab-btn', 4], ['.smode-opt', 5], ['.sslot-opt', 6],
    ['.lvpace-opt', 2], ['.hvpace-opt', 2], ['.psrc-opt', 3], ['.soff-opt', 5],
    ['.uday-opt', 7], ['.uanchor-opt', 3], ['.ucday-opt', 7]
  ];
  for (const [sel, expected] of optionCounts) {
    assert.strictEqual(h.document.querySelectorAll(sel).length, expected, `Jumlah elemen salah untuk ${sel}`);
  }

  h.click('openScheduleSheetBtn');
  for (const cat of ['schedule', 'hifz', 'prayer', 'system']) {
    h.clickSelector('.cat-tab-btn', (btn) => btn.dataset.scat === cat);
    const activeTab = h.document.querySelectorAll('.cat-tab-btn').find((b) => b.dataset.scat === cat);
    assert.ok(activeTab.classList.contains('selected'));
    const visiblePanels = h.document.querySelectorAll('.protocol-cat-panel').filter((p) => !p.classList.contains('hidden'));
    assert.strictEqual(visiblePanels.length, 1);
    assert.strictEqual(visiblePanels[0].dataset.catPanel, cat);
  }

  h.clickSelector('.psrc-opt', (b) => b.getAttribute('data-psrc') === 'manual');
  h.setInput('inpSubuh', '04:18');
  h.setInput('inpDzuhur', '11:44');
  h.setInput('inpAshar', '15:02');
  h.setInput('inpMaghrib', '17:49');
  h.setInput('inpIsya', '19:01');
  assert.deepStrictEqual(h.engine.getState().manualPrayerTimes, {
    subuh: '04:18', dzuhur: '11:44', ashar: '15:02', maghrib: '17:49', isya: '19:01'
  });

  for (const off of ['0', '10', '15', '20', '30']) {
    h.clickSelector('.soff-opt', (b) => b.getAttribute('data-soff') === off);
    assert.strictEqual(h.getState().subuhOffsetMins, Number(off));
  }
  h.clickSelector('.psrc-opt', (b) => b.getAttribute('data-psrc') === 'gps');
  for (const method of ['kemenag', 'mwl', 'isna', 'makkah', 'egypt']) {
    h.setInput('gpsMethodSelect', method);
    assert.strictEqual(h.getState().gpsMethod, method);
  }
  assert.strictEqual(h.el('inpSabaqPage').getAttribute('min'), '1');
  assert.strictEqual(h.el('inpSabaqPage').getAttribute('max'), '604');
  assert.strictEqual(h.el('inpSabaqAyahsPerDay').getAttribute('max'), '150');
  h.click('closeSettingsBtn');
  assert.strictEqual(h.el('settingsOverlay').classList.contains('hidden'), true);
});

test('03. Kompresor binary-search 24 iterasi, rantai & sirkular after_block, deferral >21:15 valid HH:MM, skip_if_busy & strict preemption', () => {
  const h = createAppHarness({
    fixedNowIso: '2026-11-16T18:00:00+07:00',
    initialStorage: {
      [STORAGE_KEY]: {
        schemaVersion: 10,
        completedDate: '2026-11-16',
        completedBlocksToday: ['primary_hifz'],
        customRoutines: [
          { id: 'flex_a', title: 'Fleksibel A', anchor: { type: 'fixed', time: '18:00' }, windowMins: 120, durationMins: 120, isFlexible: true, conflictPolicy: 'shift', daysOfWeek: [1], enabled: true },
          { id: 'strict_mid', title: 'Agenda Ketat', anchor: { type: 'fixed', time: '19:30' }, windowMins: 60, durationMins: 60, isFlexible: false, conflictPolicy: 'strict', daysOfWeek: [1], enabled: true },
          { id: 'flex_b', title: 'Fleksibel B', anchor: { type: 'fixed', time: '20:00' }, windowMins: 120, durationMins: 120, isFlexible: true, conflictPolicy: 'shift', daysOfWeek: [1], enabled: true },
          { id: 'chain_c', title: 'Rantai C', anchor: { type: 'after_block', blockId: 'flex_a', offsetMins: 5 }, windowMins: 20, durationMins: 20, isFlexible: true, conflictPolicy: 'shift', daysOfWeek: [1], enabled: true },
          { id: 'opt_skip', title: 'Opsional Lewati', anchor: { type: 'fixed', time: '19:45' }, windowMins: 30, durationMins: 30, isFlexible: true, conflictPolicy: 'skip_if_busy', daysOfWeek: [1], enabled: true }
        ]
      }
    }
  });
  const sched = h.engine.getSchedule(new Date('2026-11-16T18:00:00+07:00'));
  assert.ok(sched.compressionRatio > 0 && sched.compressionRatio < 1);
  assert.strictEqual(sched.skippedBlocks.some((s) => s.id === 'opt_skip'), true);
  const blockA = sched.blocks.find((b) => b.id === 'flex_a');
  const blockC = sched.blocks.find((b) => b.id === 'chain_c');
  assert.ok(blockA && blockC && blockC.startMins >= blockA.endMins + 5);
  for (const b of sched.blocks.slice(0, -2).filter((x) => !x.isDeferred)) {
    assert.ok(b.endMins <= 21 * 60 + 15);
  }

  const sel = h.engine.getSelection(new Date('2026-11-16T19:40:00+07:00'));
  assert.strictEqual(sel.block.id, 'strict_mid');
  assert.strictEqual(sel.preemptedFlex, true);

  h.setNow('2026-11-16T21:05:00+07:00');
  const schedLate = h.engine.getSchedule(h.now());
  assert.strictEqual(schedLate.compressionRatio, 0);
  assert.ok(schedLate.deferredBlocks.length >= 1);
  for (const defBlk of schedLate.deferredBlocks) {
    assert.ok(/^\d{2}:\d{2}$/.test(defBlk.start), `deferred.start tidak valid: ${defBlk.start}`);
    assert.ok(/^\d{2}:\d{2}$/.test(defBlk.end), `deferred.end tidak valid: ${defBlk.end}`);
    assert.ok(/^\d{2}:\d{2}$/.test(defBlk.origStart) && /^\d{2}:\d{2}$/.test(defBlk.origEnd));
  }
  h.click('openScheduleSheetBtn');
  const deferredRows = h.document.querySelectorAll('#unifiedTimelineList .timeline-row.is-deferred');
  assert.ok(deferredRows.length >= 1);
  const deferredMeta = h.document.querySelectorAll('#unifiedTimelineList .timeline-row.is-deferred .routine-item-meta');
  assert.ok(deferredMeta.some((m) => m.textContent.includes('TUNDA >21:15') && !m.textContent.includes('undefined')));

  // Penjaga ketergantungan after_block sirkular (A -> B -> A) tidak macet
  h.engine.setState({
    customRoutines: [
      { id: 'circ_a', title: 'Sirkular A', anchor: { type: 'after_block', blockId: 'circ_b', offsetMins: 10 }, windowMins: 20, durationMins: 20, isFlexible: true, daysOfWeek: [1], enabled: true },
      { id: 'circ_b', title: 'Sirkular B', anchor: { type: 'after_block', blockId: 'circ_a', offsetMins: 10 }, windowMins: 20, durationMins: 20, isFlexible: true, daysOfWeek: [1], enabled: true }
    ]
  });
  const circSched = h.engine.getSchedule(new Date('2026-11-16T08:00:00+07:00'));
  assert.ok(circSched.blocks.some((b) => b.id === 'circ_a') && circSched.blocks.some((b) => b.id === 'circ_b'));
});

test('04. Simulasi Khatam penuh Hal. 1 -> 604 pada 5 mode Sabaq, batas presisi peringatan pace & 125 mode switch', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T05:00:00+07:00' });

  const expectedSteps = [
    [{ mode: 'one_page', currentPage: 1, subSlot: 0 }, 604],
    [{ mode: 'half_page', currentPage: 1, subSlot: 0 }, 1208],
    [{ mode: 'quarter_page', currentPage: 1, subSlot: 0 }, 2416],
    [{ mode: 'multi_page', currentPage: 1, subSlot: 0, pagesPerDay: 3 }, 202]
  ];
  for (const [init, steps] of expectedSteps) {
    let cur = h.ctx.normalizeSabaqState(init);
    for (let i = 0; i < steps; i += 1) {
      const d = h.ctx.describeCurrentSabaqUnit(cur);
      assert.strictEqual(d.isCompleted, false);
      cur = d.nextState();
    }
    assert.strictEqual(cur.sabaqCompleted, true);
    assert.strictEqual(cur.currentPage, 604);
    const afterDone = h.ctx.describeCurrentSabaqUnit(cur);
    assert.strictEqual(afterDone.isCompleted, true);
    assert.strictEqual(afterDone.nextState().sabaqCompleted, true);
  }

  let ayahCur = h.ctx.normalizeSabaqState({ mode: 'ayah', surahNum: 1, startAyah: 1, ayahsPerDay: 50 });
  let ayahSteps = 0;
  while (!ayahCur.sabaqCompleted && ayahSteps < 300) {
    ayahCur = h.ctx.describeCurrentSabaqUnit(ayahCur).nextState();
    ayahSteps += 1;
  }
  assert.strictEqual(ayahCur.sabaqCompleted, true);
  assert.strictEqual(ayahCur.surahNum, 114);
  assert.strictEqual(h.ctx.describeCurrentSabaqUnit(ayahCur).nextState().sabaqCompleted, true);

  // Uji batas tepat peringatan pace: 1 hal aman vs 2 hal peringatan; 15 ayat aman vs 16 ayat peringatan
  assert.strictEqual(h.ctx.getSabaqPaceWarning({ mode: 'multi_page', pagesPerDay: 1 }), '');
  assert.match(h.ctx.getSabaqPaceWarning({ mode: 'multi_page', pagesPerDay: 2 }), /rawan tidak lancar/i);
  assert.strictEqual(h.ctx.getSabaqPaceWarning({ mode: 'ayah', ayahsPerDay: 15 }), '');
  assert.match(h.ctx.getSabaqPaceWarning({ mode: 'ayah', ayahsPerDay: 16 }), /melebihi ~1 halaman/i);

  h.engine.setState({ sabaq: { mode: 'one_page', currentPage: 30, sabaqCompleted: false } });
  h.clickSelector('.smode-opt', (b) => b.getAttribute('data-smode') === 'multi_page');
  h.setInput('inpSabaqPagesPerDay', 3);
  assert.match(h.engine.getPrescription(3, 1, h.now()).steps[2].cue, /rawan tidak lancar/i);

  const allModes = ['quarter_page', 'half_page', 'one_page', 'multi_page', 'ayah'];
  let switchCount = 0;
  for (const seedPage of [1, 50, 141, 302, 604]) {
    for (const fromMode of allModes) {
      for (const toMode of allModes) {
        h.engine.setState({
          sabaq: {
            mode: fromMode, currentPage: seedPage,
            subSlot: fromMode === 'quarter_page' ? 2 : (fromMode === 'half_page' ? 1 : 0),
            pagesPerDay: 2, surahNum: seedPage === 604 ? 112 : (seedPage === 1 ? 1 : (seedPage === 50 ? 3 : (seedPage === 141 ? 6 : 18))),
            startAyah: 1, ayahsPerDay: 10, sundayNewSabaq: false, sabaqCompleted: false
          }
        });
        h.clickSelector('.smode-opt', (b) => b.getAttribute('data-smode') === toMode);
        assert.strictEqual(h.getState().sabaq.mode, toMode);
        assert.ok(Math.abs(h.getState().sabaq.currentPage - seedPage) <= 1);
        switchCount += 1;
      }
    }
  }
  assert.strictEqual(switchCount, 125);

  // Sapuan menyeluruh unit Sabaq pada seluruh 604 halaman x (4 quarter + 2 half + 1 one_page + 5 multi_page) + 6.236 ayat x 7 kecepatan (50.900+ kasus)
  let unitChecks = 0;
  for (let pg = 1; pg <= 604; pg += 1) {
    for (let q = 0; q < 4; q += 1) {
      const d = h.ctx.describeCurrentSabaqUnit({ mode: 'quarter_page', currentPage: pg, subSlot: q });
      const nxt = d.nextState();
      assert.ok(d.unitShort.length > 0 && (nxt.sabaqCompleted || nxt.currentPage > pg || nxt.subSlot === q + 1));
      unitChecks += 1;
    }
    for (let hf = 0; hf < 2; hf += 1) {
      const d = h.ctx.describeCurrentSabaqUnit({ mode: 'half_page', currentPage: pg, subSlot: hf });
      const nxt = d.nextState();
      assert.ok(d.unitShort.length > 0 && (nxt.sabaqCompleted || nxt.currentPage > pg || nxt.subSlot === hf + 1));
      unitChecks += 1;
    }
    const dOne = h.ctx.describeCurrentSabaqUnit({ mode: 'one_page', currentPage: pg });
    assert.ok(dOne.unitShort.length > 0 && (dOne.nextState().sabaqCompleted || dOne.nextState().currentPage === pg + 1));
    unitChecks += 1;
    for (let ppd = 1; ppd <= 5; ppd += 1) {
      const dMul = h.ctx.describeCurrentSabaqUnit({ mode: 'multi_page', currentPage: pg, pagesPerDay: ppd });
      const nxt = dMul.nextState();
      assert.ok(nxt.currentPage <= 604 && (pg + ppd > 604 ? nxt.sabaqCompleted : nxt.currentPage === pg + ppd));
      unitChecks += 1;
    }
  }
  for (const s of h.sandbox.SURAHS_114) {
    for (let a = 1; a <= s.ayahs; a += 1) {
      for (const apd of [1, 3, 5, 10, 15, 25, 50]) {
        const dAy = h.ctx.describeCurrentSabaqUnit({ mode: 'ayah', surahNum: s.n, startAyah: a, ayahsPerDay: apd });
        const nxt = dAy.nextState();
        assert.ok(dAy.unitShort.length > 0 && dAy.approxPage >= 1 && dAy.approxPage <= 604);
        assert.ok(nxt.sabaqCompleted || nxt.surahNum > s.n || (nxt.surahNum === s.n && nxt.startAyah > a));
        unitChecks += 1;
      }
    }
  }
  assert.strictEqual(unitChecks, 50900);
});

test('05. Konservasi halaman parsial (quarter/half/ayah tunggal & lintas surah), eviksi gatePassBtn & Sabqi 7 hari utuh di 5 mode', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T08:00:00+07:00' });
  let sabaq = h.ctx.normalizeSabaqState({ mode: 'quarter_page', currentPage: 2, subSlot: 0 });
  let history = [];
  const expectedLines = [null, null, null, null, null, null, null, 'Baris 1–4', 'Baris 1–8', 'Baris 1–12', null];

  for (let step = 0; step < expectedLines.length; step += 1) {
    const desc = h.ctx.describeCurrentSabaqUnit(sabaq);
    history = [...history, { unitShort: desc.unitShort, approxPage: desc.approxPage, mode: desc.mode }].slice(-7);
    sabaq = desc.nextState();
    const nextDesc = h.ctx.describeCurrentSabaqUnit(sabaq);
    const partial = h.ctx.getPartialPageSpan(history, nextDesc);
    const maxGrad = h.ctx.getMaxGraduatedNewPage(history, nextDesc);
    assert.strictEqual(partial ? partial.label : null, expectedLines[step]);
    assert.strictEqual(maxGrad, step < 10 ? 1 : 2);
  }

  // Konservasi half_page Atas lewat eviksi nyata gatePassBtn + undo
  h.engine.resetState({
    schemaVersion: 10,
    phaseDay: 1,
    completedDate: '2026-11-16',
    sabaq: { mode: 'half_page', currentPage: 3, subSlot: 0, sundayNewSabaq: true },
    sabqiHistory: []
  });
  h.setNow('2026-11-16T21:20:00+07:00');
  h.click('gatePassBtn');
  h.engine.setState({ sabaq: { mode: 'one_page', currentPage: 4, subSlot: 0, sundayNewSabaq: true } });
  for (let d = 0; d < 7; d += 1) {
    h.setNow(`2026-11-${17 + d}T21:20:00+07:00`);
    h.click('gatePassBtn');
  }
  const halfSpan = h.ctx.getPartialPageSpan(h.getState().sabqiHistory, h.getState().sabaq);
  assert.ok(halfSpan && halfSpan.page === 3 && halfSpan.label === 'Baris 1–8');
  assert.strictEqual(h.ctx.getMaxGraduatedNewPage(h.getState().sabqiHistory, h.getState().sabaq), 2);
  h.click('undoBtn');
  assert.strictEqual(h.getState().sabqiHistory.length, 7);

  // Konservasi lintas surah pada mode ayah (Surah 106:1-4 + Surah 107:1-4 di Hal. 602)
  h.engine.resetState({
    schemaVersion: 10,
    phaseDay: 1,
    completedDate: '2026-11-16',
    sabaq: { mode: 'ayah', surahNum: 107, startAyah: 5, ayahsPerDay: 6, currentPage: 602 },
    sabqiHistory: [],
    conservedPartialSpans: {}
  });
  const crossSurahHist = [
    { unitShort: "Quraysh 106:1–4 + Al-Ma'un 107:1–4 (8 Ayat)", approxPage: 602, mode: 'ayah' }
  ];
  const nextHal602 = h.ctx.describeCurrentSabaqUnit({ mode: 'ayah', surahNum: 107, startAyah: 5, ayahsPerDay: 6, currentPage: 602 });
  h.ctx.getPartialPageSpan(crossSurahHist, nextHal602);
  assert.strictEqual(h.getState().conservedPartialSpans[602].label, "Quraysh 106:1 → Al-Ma'un 107:4");

  const evictedCrossHist = [{ unitShort: 'Halaman 603', approxPage: 603, mode: 'one_page' }];
  const nextHal604 = h.ctx.describeCurrentSabaqUnit({ mode: 'one_page', currentPage: 604 });
  const ayahSpan = h.ctx.getPartialPageSpan(evictedCrossHist, nextHal604);
  assert.ok(ayahSpan && ayahSpan.page === 602 && ayahSpan.label === "Quraysh 106:1 → Al-Ma'un 107:4");

  for (const testMode of ['quarter_page', 'half_page', 'one_page', 'multi_page', 'ayah']) {
    h.engine.resetState({
      schemaVersion: 10,
      phaseDay: 1,
      completedDate: '2026-11-16',
      sabaq: testMode === 'multi_page'
        ? { mode: 'multi_page', currentPage: 1, subSlot: 0, pagesPerDay: 2, sundayNewSabaq: true }
        : (testMode === 'ayah'
          ? { mode: 'ayah', surahNum: 2, startAyah: 1, ayahsPerDay: 15, currentPage: 2, sundayNewSabaq: true }
          : { mode: testMode, currentPage: 1, subSlot: 0, sundayNewSabaq: true }),
      sabqiHistory: []
    });
    for (let d = 0; d < 7; d += 1) {
      h.setNow(`2026-11-${16 + d}T21:20:00+07:00`);
      h.click('gatePassBtn');
    }
    assert.strictEqual(h.getState().sabqiHistory.length, 7, `Sabqi queue terpotong pada mode ${testMode}`);
  }
});

test('06. Kategori > 7 Juz: parser priorJuzList kompleks, auto-deteksi <=141 vs >141, 1 vs 2 Juz, paritas & kursor mundur', () => {
  assert.deepStrictEqual(Array.from(new Set([1, 28, 29, 30])), [1, 28, 29, 30]);
  const h = createAppHarness({
    fixedNowIso: '2026-11-16T21:20:00+07:00',
    initialStorage: {
      [STORAGE_KEY]: {
        schemaVersion: 10,
        completedDate: '2026-11-16',
        phaseDay: 1,
        priorJuzList: [29, 30],
        HighVolumeJuzPerDay: 2,
        HighVolumeMaghribOn: true,
        hvNextJuz: 1,
        sabaq: { mode: 'one_page', currentPage: 149 },
        sabqiHistory: [142, 143, 144, 145, 146, 147, 148].map((p) => ({ unitShort: `Halaman ${p}`, approxPage: p, mode: 'one_page' }))
      }
    }
  });
  assert.deepStrictEqual(Array.from(h.ctx.parsePriorJuzList('30-28; 29, 1  35 0 abc')), [1, 28, 29, 30]);
  assert.strictEqual(h.getState().hifzPhase, 4);
  const seenOdd = [];
  for (let d = 0; d < 6; d += 1) {
    seenOdd.push(h.engine.getPrescription(4, d + 1, h.now()).hvRange.firstJuz);
    h.getState().completedBlocksToday = [];
    h.click('gateFreezeSabaqBtn');
  }
  assert.deepStrictEqual(seenOdd, [1, 3, 5, 7, 30, 2]);

  h.click('openScheduleSheetBtn');
  h.clickSelector('.cat-tab-btn', (b) => b.dataset.scat === 'hifz');
  for (let d = 0; d < 6; d += 1) h.click('decDayBtn');
  assert.strictEqual(h.getState().hvNextJuz, 1);

  h.setInput('inpPriorJuz', '30');
  const seenEven = [];
  for (let d = 0; d < 6; d += 1) {
    seenEven.push(h.engine.getPrescription(4, d + 1, h.now()).hvRange.firstJuz);
    h.getState().completedBlocksToday = [];
    h.click('gateFreezeSabaqBtn');
  }
  assert.deepStrictEqual(seenEven, [1, 3, 5, 7, 1, 3]);

  // Cek mode 1 Juz/hari dengan & tanpa Manzil Maghrib (pada pukul 08:00 sebelum batas 21:15)
  h.setNow('2026-11-16T08:00:00+07:00');
  h.clickSelector('.hvpace-opt', (b) => b.getAttribute('data-hvpace') === '1');
  assert.strictEqual(h.getState().HighVolumeJuzPerDay, 1);
  h.click('hvMaghribToggle');
  assert.strictEqual(h.getState().HighVolumeMaghribOn, false);
  assert.strictEqual(h.engine.getSchedule(h.now()).blocks.some((b) => b.id === 'hv_maghrib_manzil'), false);
  h.click('hvMaghribToggle');
  assert.strictEqual(h.getState().HighVolumeMaghribOn, true);
  assert.strictEqual(h.engine.getSchedule(h.now()).blocks.some((b) => b.id === 'hv_maghrib_manzil'), true);

  // Kembali ke 2 Juz/hari -> hvMaghribToggle terkunci tetap aktif
  h.clickSelector('.hvpace-opt', (b) => b.getAttribute('data-hvpace') === '2');
  h.click('hvMaghribToggle');
  assert.strictEqual(h.getState().HighVolumeMaghribOn, true);

  // Turun ke <142 -> auto-switch ke Fase 3 (< 7 Juz) dan priorJuzList dipertahankan
  h.setInput('inpSabaqPage', 35);
  assert.strictEqual(h.getState().hifzPhase, 3);
  const lvPool = h.ctx.getLVManzilContext().pool;
  assert.strictEqual(lvPool.length, 27 + 23);
});

test('07. CRUD Rutinitas Terpadu (3 tipe jangkar), 10 ID unik milidetik sama, Profil 1-3 Terisolasi & Modal Hapus', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T08:00:00+07:00' });
  h.engine.setState({
    phaseDay: 4,
    sabaq: { mode: 'one_page', currentPage: 44, subSlot: 0, pagesPerDay: 1, surahNum: 2, startAyah: 260, ayahsPerDay: 10, sundayNewSabaq: false, sabaqCompleted: false },
    prayerSource: 'manual'
  });

  h.click('openScheduleSheetBtn');
  h.setInput('selRoutineProfile', 'profile_2');
  assert.strictEqual(h.getState().activeRoutineProfileId, 'profile_2');
  assert.strictEqual(h.getState().customRoutines.length, 0);

  // Buat 10 rutinitas di milidetik yang sama mencakup fixed, prayer, dan after_block
  const seenIds = new Set();
  for (let i = 0; i < 10; i += 1) {
    h.click('openUnifiedBlockEditorBtn');
    h.setInput('inpUnifiedTitle', `Aktivitas P2 #${i}`);
    if (i % 3 === 0) {
      h.clickSelector('.uanchor-opt', (b) => b.dataset.uanchor === 'fixed');
      h.setInput('inpUnifiedFixedTime', `${String(8 + i).padStart(2, '0')}:00`);
    } else if (i % 3 === 1) {
      h.clickSelector('.uanchor-opt', (b) => b.dataset.uanchor === 'prayer');
      h.setInput('selUnifiedPrayer', 'ashar');
      h.setInput('inpUnifiedPrayerOffset', '20');
    } else {
      h.clickSelector('.uanchor-opt', (b) => b.dataset.uanchor === 'after_block');
      h.setInput('selUnifiedAfterBlock', 'primary_hifz');
      h.setInput('inpUnifiedBlockOffset', '10');
    }
    h.setInput('inpUnifiedWindowMins', '30');
    h.setInput('inpUnifiedTimerMins', '30');
    h.click('saveUnifiedBlockBtn');
    seenIds.add(h.getState().customRoutines[i].id);
  }
  assert.strictEqual(h.getState().customRoutines.length, 10);
  assert.strictEqual(seenIds.size, 10);

  // Isolasi antar profil
  h.setInput('selRoutineProfile', 'profile_3');
  assert.strictEqual(h.getState().customRoutines.length, 0);
  h.setInput('selRoutineProfile', 'profile_1');
  assert.strictEqual(h.getState().customRoutines[0].id, 'daily_dzuhur_hifz');
  h.setInput('selRoutineProfile', 'profile_2');
  assert.strictEqual(h.getState().customRoutines.length, 10);

  // Progres hafalan & sumber shalat tetap utuh
  assert.strictEqual(h.getState().sabaq.currentPage, 44);
  assert.strictEqual(h.getState().prayerSource, 'manual');

  // Edit & toggle enable/disable
  const editBtns = h.document.querySelectorAll('#unifiedTimelineList .mini-chip-btn').filter((b) => b.textContent === 'UBAH');
  editBtns[0].click();
  assert.strictEqual(h.el('unifiedBlockEditorCard').classList.contains('hidden'), false);
  h.setInput('inpUnifiedTitle', 'Aktivitas P2 #0 Diedit');
  h.click('saveUnifiedBlockBtn');
  assert.strictEqual(h.getState().customRoutines[0].title, 'Aktivitas P2 #0 Diedit');

  const toggleBtns = h.document.querySelectorAll('#unifiedTimelineList .mini-chip-btn').filter((b) => b.textContent === 'AKTIF' || b.textContent === 'AKTIFKAN');
  toggleBtns[0].click();
  assert.strictEqual(h.getState().customRoutines[0].enabled, false);
  assert.ok(h.document.querySelectorAll('#unifiedTimelineList .timeline-row.is-muted-disabled').length >= 1);

  const delBtns = h.document.querySelectorAll('#unifiedTimelineList .mini-chip-btn').filter((b) => b.textContent === 'HAPUS');
  delBtns[0].click();
  assert.strictEqual(h.el('confirmDeleteOverlay').classList.contains('hidden'), false);
  h.click('confirmDeleteCancelBtn');
  assert.strictEqual(h.el('confirmDeleteOverlay').classList.contains('hidden'), true);
  assert.strictEqual(h.getState().customRoutines.length, 10);

  delBtns[0].click();
  h.el('confirmDeleteOverlay').click();
  assert.strictEqual(h.el('confirmDeleteOverlay').classList.contains('hidden'), true);
  assert.strictEqual(h.getState().customRoutines.length, 10);

  delBtns[0].click();
  h.document.dispatchEvent({ type: 'keydown', key: 'Escape' });
  assert.strictEqual(h.el('confirmDeleteOverlay').classList.contains('hidden'), true);
  assert.strictEqual(h.getState().customRoutines.length, 10);

  editBtns[0].click();
  delBtns[0].click();
  h.click('confirmDeleteOkBtn');
  assert.strictEqual(h.el('confirmDeleteOverlay').classList.contains('hidden'), true);
  assert.strictEqual(h.el('unifiedBlockEditorCard').classList.contains('hidden'), true);
  assert.strictEqual(h.getState().customRoutines.length, 9);
});

test('08. Round-trip Download/Upload JSON lengkap (Profil 1-3 + priorJuzList + conservedPartialSpans + GPS) & 10 penolakan ketat', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T08:00:00+07:00' });
  h.engine.setState({
    priorJuzList: [29, 30],
    lvMaghribEnabled: true,
    lvPace: 20,
    sabaq: { mode: 'one_page', currentPage: 55 },
    conservedPartialSpans: { 3: { page: 3, from: null, to: null, label: 'Baris 1–8' } },
    prayerSource: 'gps',
    gpsMethod: 'mwl',
    subuhOffsetMins: 20
  });
  h.click('openScheduleSheetBtn');
  h.setInput('selRoutineProfile', 'profile_3');
  h.click('openUnifiedBlockEditorBtn');
  h.setInput('inpUnifiedTitle', 'Rutinitas LV');
  h.click('saveUnifiedBlockBtn');

  h.click('exportScheduleBtn');
  const exported = h.getLastExportedText();
  assert.ok(exported && exported.includes('Rutinitas LV'));

  const h2 = createAppHarness({ fixedNowIso: '2026-11-16T08:00:00+07:00' });
  const fi = h2.el('importScheduleFileInput');
  const badVectors = [
    '{broken',
    '[]',
    'null',
    '{"invalid": true}',
    JSON.stringify({ schemaVersion: 9, customRoutines: [] }),
    JSON.stringify({ schemaVersion: 10 }),
    JSON.stringify({ schemaVersion: 10, customRoutines: 'not-array' }),
    JSON.stringify({ schemaVersion: 10, customRoutines: [{ id: 'no_anchor', title: 'X' }] }),
    JSON.stringify({ schemaVersion: 10, customRoutines: [{ id: 'dup', title: 'A', anchor: { type: 'fixed', time: '09:00' } }, { id: 'dup', title: 'B', anchor: { type: 'fixed', time: '10:00' } }] }),
    JSON.stringify({ schemaVersion: 10, routineProfiles: { profile_1: { customRoutines: [{ id: 'empty_days', title: 'X', anchor: { type: 'fixed', time: '09:00' }, daysOfWeek: [] }] } } })
  ];
  for (const bad of badVectors) {
    fi.files = [{ _text: bad }];
    fi.dispatchEvent({ type: 'change', target: fi });
    assert.ok(
      h2.el('drawerFeedback').textContent.startsWith('UPLOAD GAGAL:') || h2.el('drawerFeedback').textContent === 'KESALAHAN PARSING JSON',
      `Feedback tak terduga: ${h2.el('drawerFeedback').textContent}`
    );
  }

  fi.files = [{ _text: exported }];
  fi.dispatchEvent({ type: 'change', target: fi });
  assert.deepStrictEqual(Array.from(h2.getState().priorJuzList), [29, 30]);
  assert.strictEqual(h2.getState().lvPace, 20);
  assert.strictEqual(h2.getState().prayerSource, 'gps');
  assert.strictEqual(h2.getState().gpsMethod, 'mwl');
  assert.strictEqual(h2.getState().subuhOffsetMins, 20);
  assert.strictEqual(h2.getState().conservedPartialSpans[3].label, 'Baris 1–8');
  assert.strictEqual(h2.getState().activeRoutineProfileId, 'profile_3');
  assert.strictEqual(h2.getState().routineProfiles.profile_3.customRoutines[0].title, 'Rutinitas LV');
});

test('09. Nol-migrasi v1-v9, tie-break updatedAt window.name vs localStorage, IndexedDB async hydration, content:// & QuotaExceededError', async () => {
  const hLegacyKeys = createAppHarness({
    fixedNowIso: '2026-11-16T08:00:00+07:00',
    initialStorage: {
      rutinitas_hifz_id_state_v1: { hifzPhase: 4, phaseDay: 5, activeDate: '2026-11-16', completedIds: ['primary_hifz'], sabaq: { mode: 'one_page', currentPage: 160 } },
      minimalist_hifz_routine_state_v9: { hifzPhase: 4, phaseDay: 9, completedDate: '2026-11-16' },
      [STORAGE_KEY]: { schemaVersion: 9, hifzPhase: 4, phaseDay: 7, completedDate: '2026-11-16' }
    }
  });
  assert.strictEqual(hLegacyKeys.getState().schemaVersion, 10);
  assert.strictEqual(hLegacyKeys.getState().hifzPhase, 3);
  assert.strictEqual(hLegacyKeys.getState().sabaq.currentPage, 1);
  assert.deepStrictEqual(Array.from(hLegacyKeys.getState().completedBlocksToday), []);

  const hOldWin = createAppHarness({
    fixedNowIso: '2026-11-16T08:00:00+07:00',
    initialWindowName: `RUTIN_V10:${JSON.stringify({ schemaVersion: 9, sabaq: { currentPage: 180 } })}`
  });
  assert.strictEqual(hOldWin.getState().sabaq.currentPage, 1);

  const olderPayload = { schemaVersion: 10, priorJuzList: [29], sabaq: { mode: 'one_page', currentPage: 25 }, completedDate: '2026-11-16', updatedAt: 1000 };
  const newerPayload = { schemaVersion: 10, priorJuzList: [30], sabaq: { mode: 'one_page', currentPage: 45 }, completedDate: '2026-11-16', updatedAt: 5000 };
  const hTie = createAppHarness({
    fixedNowIso: '2026-11-16T08:00:00+07:00',
    initialStorage: { [STORAGE_KEY]: olderPayload },
    initialWindowName: `RUTIN_V10:${JSON.stringify(newerPayload)}`
  });
  assert.strictEqual(hTie.getState().sabaq.currentPage, 45);
  assert.deepStrictEqual(Array.from(hTie.getState().priorJuzList), [30]);

  // IndexedDB async hydration menang jika updatedAt lebih baru daripada localStorage
  const idbNewer = JSON.stringify({ schemaVersion: 10, priorJuzList: [28, 30], sabaq: { mode: 'one_page', currentPage: 77 }, completedDate: '2026-11-16', updatedAt: 999999 });
  const hIdb = createAppHarness({
    fixedNowIso: '2026-11-16T08:00:00+07:00',
    initialStorage: { [STORAGE_KEY]: olderPayload },
    initialIdbRaw: idbNewer
  });
  for (let i = 0; i < 6; i += 1) await Promise.resolve();
  assert.strictEqual(hIdb.getState().sabaq.currentPage, 77);
  assert.deepStrictEqual(Array.from(hIdb.getState().priorJuzList), [28, 30]);

  for (const raw of ['{broken', 'null', '[]', '"str"']) {
    const hBad = createAppHarness({ fixedNowIso: '2026-11-16T08:00:00+07:00', initialStorage: { [STORAGE_KEY]: raw } });
    assert.strictEqual(hBad.getState().sabaq.currentPage, 1);
  }

  const hContent = createAppHarness({ fixedNowIso: '2026-11-16T08:00:00+07:00', protocol: 'content:' });
  assert.strictEqual(hContent.el('freezeBanner').classList.contains('hidden'), false);
  assert.match(hContent.el('freezeReasonText').textContent, /content:\/\//);

  const hQuota = createAppHarness({ fixedNowIso: '2026-11-16T05:00:00+07:00', throwOnSetItem: true });
  hQuota.click('primaryActionBtn');
  assert.strictEqual(hQuota.el('freezeBanner').classList.contains('hidden'), false);
  assert.match(hQuota.el('freezeReasonText').textContent, /PENYIMPANAN PENUH/);
});

test('10. Multi-step LIFO undoStack seluruh 7 tipe aksi (restorasi mendalam) & batas maksimum 30 aksi', () => {
  const stripMeta = (o) => {
    const c = JSON.parse(JSON.stringify(o));
    delete c.updatedAt;
    return c;
  };
  const h = createAppHarness({
    fixedNowIso: '2026-11-16T05:00:00+07:00',
    initialStorage: {
      [STORAGE_KEY]: {
        schemaVersion: 10,
        completedDate: '2026-11-16',
        sabaq: { mode: 'one_page', currentPage: 15 },
        phaseDay: 2,
        customRoutines: [{ id: 'siang_block', title: 'Siang', anchor: { type: 'fixed', time: '12:30' }, windowMins: 30, durationMins: 30, isFlexible: true, daysOfWeek: [1], enabled: true }]
      }
    }
  });
  const st = h.getState();
  const snap = stripMeta(st);

  h.click('primaryActionBtn'); // 1. start_timer (step 0)
  h.click('resetTimerBtn');     // 2. reset_timer
  h.click('primaryActionBtn'); // 3. start_timer (step 0)
  h.click('primaryActionBtn'); // 4. substep (0 -> 1)
  h.click('sabqiFreezeInlineBtn'); // 5. sabqi_freeze
  h.click('primaryActionBtn'); // 6. start_timer (step 1)
  h.click('primaryActionBtn'); // 7. substep_complete (primary_hifz selesai)
  h.setNow('2026-11-16T12:35:00+07:00');
  h.click('primaryActionBtn'); // 8. start_timer (siang_block)
  h.click('primaryActionBtn'); // 9. block (siang_block selesai)
  h.setNow('2026-11-16T21:20:00+07:00');
  h.click('gatePassBtn');      // 10. gate

  assert.strictEqual(st.undoStack.length, 10);
  const seenTypes = new Set(st.undoStack.map((a) => a.type));
  for (const reqType of ['start_timer', 'reset_timer', 'substep', 'substep_complete', 'block', 'gate', 'sabqi_freeze']) {
    assert.ok(seenTypes.has(reqType), `Tipe undo hilang: ${reqType}`);
  }
  while (st.undoStack.length > 0) h.click('undoBtn');
  assert.deepStrictEqual(stripMeta(st), snap);
  assert.ok(st.updatedAt > 0);

  for (let i = 0; i < 35; i += 1) h.ctx.pushUndoAction({ type: 'block', blockId: `b_${i}` });
  assert.strictEqual(st.undoStack.length, 30);
  assert.strictEqual(st.undoStack[0].blockId, 'b_5');
  assert.strictEqual(st.undoStack[29].blockId, 'b_34');
});

test('11. Batas bangun dinamis (menit-per-menit 03:59..23:59), bangun kustom <04:30 & pratinjau besok 2-step/3-step', () => {
  const h = createAppHarness({
    fixedNowIso: '2026-11-16T04:05:00+07:00',
    initialStorage: {
      [STORAGE_KEY]: {
        schemaVersion: 10,
        completedDate: '2026-11-16',
        customRoutines: [
          { id: 'early_tahajjud', title: 'Tahajjud', anchor: { type: 'fixed', time: '04:00' }, windowMins: 30, durationMins: 30, isFlexible: true, daysOfWeek: [1], enabled: true }
        ]
      }
    }
  });
  h.setNow('2026-11-16T03:59:00+07:00');
  assert.strictEqual(h.engine.getSelection(h.now()).block.id, 'sleep_block');
  h.setNow('2026-11-16T04:00:00+07:00');
  assert.strictEqual(h.engine.getSelection(h.now()).block.id, 'early_tahajjud');

  h.engine.setState({
    prayerSource: 'manual',
    manualPrayerTimes: { subuh: '03:50', dzuhur: '11:45', ashar: '15:05', maghrib: '17:50', isya: '19:00' },
    subuhOffsetMins: 10,
    customRoutines: []
  });
  const selNight = h.engine.getSelection(new Date('2026-11-16T23:00:00+07:00'));
  assert.strictEqual(selNight.block.id, 'sleep_block');
  assert.strictEqual(selNight.targetSecs, 5 * 3600);
  h.engine.setState({ completedBlocksToday: ['primary_hifz'] });
  assert.strictEqual(h.engine.getSelection(new Date('2026-11-16T21:14:00+07:00')).block.id, 'presleep_gate');
  assert.strictEqual(h.engine.getSelection(new Date('2026-11-16T21:14:00+07:00')).status, 'UPCOMING');
  assert.strictEqual(h.engine.getSelection(new Date('2026-11-16T21:15:00+07:00')).status, 'ACTIVE');
  h.engine.setState({ completedBlocksToday: [] });
  assert.strictEqual(h.engine.getSelection(new Date('2026-11-16T22:00:00+07:00')).block.id, 'sleep_block');

  h.setNow('2026-11-16T21:20:00+07:00');
  h.click('gatePassBtn');
  const selTom = h.engine.getSelection(h.now());
  assert.strictEqual(selTom.status, 'TOMORROW');
  assert.strictEqual(h.el('hifzStepperContainer').classList.contains('hidden'), false);
  assert.match(h.el('subStepLabel').textContent, /PRATINJAU 1\/3/);
  h.click('primaryActionBtn');
  assert.match(h.el('subStepLabel').textContent, /PRATINJAU 2\/3/);
  h.click('primaryActionBtn');
  assert.match(h.el('subStepLabel').textContent, /PRATINJAU 3\/3/);
  h.click('primaryActionBtn');
  assert.match(h.el('subStepLabel').textContent, /PRATINJAU 1\/3/);

  // Uji pratinjau 2-step saat sabaqFrozen = true
  h.engine.setState({ sabaqFrozen: true });
  h.engine.render();
  assert.match(h.el('subStepLabel').textContent, /PRATINJAU 1\/2/);
  h.click('substepCard');
  assert.match(h.el('subStepLabel').textContent, /PRATINJAU 2\/2/);
});

test('12. Solver astronomis 366 hari kabisat x 5 metode x 35 lintang x 12 zona (768.600 cek), Bandung preset & semua cabang GPS', () => {
  const h = createAppHarness({ fixedNowIso: '2024-01-01T08:00:00+07:00' });
  const methods = ['kemenag', 'mwl', 'isna', 'makkah', 'egypt'];
  const lats = [
    -89.9, -69.6492, -65, -60, -55, -50, -45, -40, -35, -33.8688,
    -30, -25, -20, -15, -10, -6.9175, -5, 0, 5, 6.9175,
    10, 15, 20, 21.4225, 25, 30, 35, 40, 45, 50,
    51.5074, 55, 60, 69.6492, 89.9
  ];
  const tzZones = [-10, -8, -5, -3, 0, 1, 3, 5, 7, 8, 10, 12];
  const startDay = new Date('2024-01-01T12:00:00Z');
  let astroChecks = 0;
  for (let d = 0; d < 366; d += 1) {
    const dt = new Date(startDay.getTime() + d * 86400000);
    for (const tz of tzZones) {
      const lon = tz * 15 + (tz % 2 === 0 ? 2.5 : -2.5);
      for (const lat of lats) {
        for (const m of methods) {
          const pt = h.ctx.computeAstronomicalPrayerTimes(dt, lat, lon, m, tz);
          const s = h.ctx.toMinutes(pt.subuh), dz = h.ctx.toMinutes(pt.dzuhur), a = h.ctx.toMinutes(pt.ashar), mg = h.ctx.toMinutes(pt.maghrib), i = h.ctx.toMinutes(pt.isya);
          assert.ok(Number.isFinite(s) && Number.isFinite(i));
          assert.ok(s < dz && dz < a && a < mg && mg < i);
          astroChecks += 1;
        }
      }
    }
  }
  assert.strictEqual(astroChecks, 768600);

  const hInsecure = createAppHarness({
    isSecureContext: false,
    geolocationBehavior: () => { throw new Error('Tidak boleh memanggil geolocation pada konteks tidak aman'); }
  });
  hInsecure.click('openScheduleSheetBtn');
  hInsecure.clickSelector('.cat-tab-btn', (b) => b.dataset.scat === 'prayer');
  hInsecure.clickSelector('.psrc-opt', (b) => b.getAttribute('data-psrc') === 'gps');
  hInsecure.click('detectGpsBtn');
  assert.match(hInsecure.el('drawerFeedback').textContent, /DIBLOKIR FILE LOKAL/);

  const hNoGeo = createAppHarness({ isSecureContext: true, geolocationBehavior: null });
  hNoGeo.click('openScheduleSheetBtn');
  hNoGeo.clickSelector('.cat-tab-btn', (b) => b.dataset.scat === 'prayer');
  hNoGeo.clickSelector('.psrc-opt', (b) => b.getAttribute('data-psrc') === 'gps');
  hNoGeo.click('detectGpsBtn');
  assert.match(hNoGeo.el('drawerFeedback').textContent, /GPS TIDAK DIDUKUNG/);

  for (const [errCode, expectedPattern] of [
    [1, /IZIN GPS DITOLAK/],
    [2, /LOKASI TIDAK TERSEDIA · NYALAKAN LOKASI PERANGKAT/],
    [3, /PENCARIAN HABIS WAKTU · COBA LAGI/]
  ]) {
    const hErr = createAppHarness({ isSecureContext: true, geolocationBehavior: (_ok, errCb) => errCb({ code: errCode }) });
    hErr.click('openScheduleSheetBtn');
    hErr.clickSelector('.cat-tab-btn', (b) => b.dataset.scat === 'prayer');
    hErr.clickSelector('.psrc-opt', (b) => b.getAttribute('data-psrc') === 'gps');
    hErr.click('detectGpsBtn');
    assert.match(hErr.el('drawerFeedback').textContent, expectedPattern);
    assert.strictEqual(hErr.el('detectGpsBtn').disabled, false);
    assert.strictEqual(hErr.el('detectGpsBtn').getAttribute('aria-busy'), null);
  }

  let calls = 0;
  const hRetry = createAppHarness({
    isSecureContext: true,
    geolocationBehavior: (okCb, errCb) => {
      calls += 1;
      if (calls === 1) errCb({ code: 3 });
      else okCb({ coords: { latitude: -6.2088, longitude: 106.8456 } });
    }
  });
  hRetry.click('openScheduleSheetBtn');
  hRetry.clickSelector('.cat-tab-btn', (b) => b.dataset.scat === 'prayer');
  hRetry.clickSelector('.psrc-opt', (b) => b.getAttribute('data-psrc') === 'gps');
  hRetry.click('detectGpsBtn');
  assert.strictEqual(calls, 2);
  assert.strictEqual(hRetry.getState().gpsLocation.lat, -6.2088);
  assert.strictEqual(hRetry.el('detectGpsBtn').disabled, false);
  assert.strictEqual(hRetry.el('detectGpsBtn').getAttribute('aria-busy'), null);

  for (const [badLat, badLon] of [['95', '107.6'], ['-91', '0'], ['0', '181'], ['abc', '107.6']]) {
    hRetry.el('inpGpsLat').value = badLat;
    hRetry.el('inpGpsLon').value = badLon;
    hRetry.click('applyManualCoordsBtn');
    assert.strictEqual(hRetry.getState().gpsLocation.lat, -6.2088);
    assert.match(hRetry.el('drawerFeedback').textContent, /KOORDINAT TIDAK VALID/);
  }
  hRetry.click('presetBandungBtn');
  assert.strictEqual(hRetry.getState().gpsLocation.lat, -6.9175);

  const hInvalidGps = createAppHarness({
    isSecureContext: true,
    geolocationBehavior: (okCb) => okCb({ coords: { latitude: 91, longitude: 181 } })
  });
  hInvalidGps.click('openScheduleSheetBtn');
  hInvalidGps.clickSelector('.cat-tab-btn', (b) => b.dataset.scat === 'prayer');
  hInvalidGps.clickSelector('.psrc-opt', (b) => b.getAttribute('data-psrc') === 'gps');
  hInvalidGps.click('detectGpsBtn');
  assert.strictEqual(hInvalidGps.getState().gpsLocation.hasFix, false);
  assert.match(hInvalidGps.el('drawerFeedback').textContent, /LOKASI TIDAK TERSEDIA/);
  assert.strictEqual(hInvalidGps.el('detectGpsBtn').disabled, false);
});

test('13. Progresi kontinu dari Hal. 1 -> Hari 8 lulus Manzil -> >141 ke Fase 4 & aturan Minggu', () => {
  const h = createAppHarness({
    fixedNowIso: '2026-11-16T21:20:00+07:00',
    initialStorage: {
      [STORAGE_KEY]: {
        schemaVersion: 10,
        completedDate: '2026-11-16',
        sabaq: { mode: 'one_page', currentPage: 1, sundayNewSabaq: true }
      }
    }
  });
  for (let d = 1; d <= 7; d += 1) {
    h.setNow(`2026-11-${15 + d}T21:20:00+07:00`);
    h.click('gatePassBtn');
    assert.strictEqual(h.ctx.getLVManzilContext().maxGrad, 0);
  }
  h.setNow('2026-11-24T21:20:00+07:00');
  h.click('gatePassBtn');
  assert.strictEqual(h.ctx.getLVManzilContext().maxGrad, 1);
  assert.deepStrictEqual(Array.from(h.ctx.getLVManzilContext().pool), [1]);

  h.engine.setState({ sabaq: { mode: 'one_page', currentPage: 141, sundayNewSabaq: false } });
  h.setNow('2026-11-25T21:20:00+07:00');
  h.click('gatePassBtn');
  assert.strictEqual(h.getState().hifzPhase, 4);
  assert.strictEqual(h.getState().sabaq.currentPage, 142);

  // Hari Minggu (2026-11-29): sundayNewSabaq false menahan kemajuan, true memajukan
  h.setNow('2026-11-29T21:20:00+07:00');
  h.click('gatePassBtn');
  assert.strictEqual(h.getState().sabaq.currentPage, 142);
  h.click('undoBtn');
  h.engine.setState({ sabaq: { mode: 'one_page', currentPage: 142, sundayNewSabaq: true } });
  h.click('gatePassBtn');
  assert.strictEqual(h.getState().sabaq.currentPage, 143);
});

test('14. Pengosongan 7 unit Sabqi pasca-Khatam (Hal. 604), freeze di subStep 2, auto-thaw & persistensi gerbang', () => {
  const startMs = new Date('2026-11-16T05:00:00+07:00').getTime();
  const h = createAppHarness({
    fixedNowIso: '2026-11-16T06:00:00+07:00',
    initialStorage: {
      [STORAGE_KEY]: {
        schemaVersion: 10,
        completedDate: '2026-11-16',
        sabaq: { mode: 'one_page', currentPage: 20 },
        primarySubStep: 2,
        blockTimers: {
          primary_hifz_window: { startedAtMs: startMs, durSecs: 5400 },
          primary_hifz_step_1: { startedAtMs: startMs + 1800000, durSecs: 1800, alarmFired: true },
          primary_hifz_step_2: { startedAtMs: startMs + 3600000, durSecs: 1800 }
        }
      }
    }
  });
  h.click('openScheduleSheetBtn');
  h.clickSelector('.cat-tab-btn', (b) => b.dataset.scat === 'hifz');
  h.click('sabaqFrozenBtn');
  assert.strictEqual(h.getState().sabaqFrozen, true);
  assert.strictEqual(h.getState().sabaqFrozenForNextDay, true);
  assert.strictEqual(h.getState().primarySubStep, 1);
  assert.strictEqual(h.getState().blockTimers.primary_hifz_step_2, undefined);
  assert.strictEqual(h.getState().blockTimers.primary_hifz_step_1.durSecs, 2700);
  assert.strictEqual(h.getState().blockTimers.primary_hifz_step_1.alarmFired, false);

  h.click('sabaqActiveBtn');
  assert.strictEqual(h.getState().sabaqFrozen, false);
  h.click('closeSettingsBtn');

  // Freeze inline -> auto-thaw keesokan hari
  h.click('sabqiFreezeInlineBtn');
  assert.strictEqual(h.getState().sabaqFrozen, true);
  assert.strictEqual(h.getState().sabaqFrozenForNextDay, false);
  h.setNow('2026-11-17T05:10:00+07:00');
  assert.strictEqual(h.getState().sabaqFrozen, false);

  h.setNow('2026-11-17T21:20:00+07:00');
  h.click('gateFreezeSabaqBtn');
  assert.strictEqual(h.getState().sabaqFrozen, true);
  assert.strictEqual(h.getState().sabaqFrozenForNextDay, true);
  h.setNow('2026-11-18T05:10:00+07:00');
  assert.strictEqual(h.getState().sabaqFrozen, true);

  h.engine.setState({
    sabaqFrozen: false,
    sabaqFrozenForNextDay: false,
    sabaq: { mode: 'one_page', currentPage: 604, sabaqCompleted: true },
    sabqiHistory: [598, 599, 600, 601, 602, 603, 604].map((pg) => ({ unitShort: `Halaman ${pg}`, approxPage: pg, mode: 'one_page' })),
    completedBlocksToday: []
  });
  h.setNow('2026-11-18T21:20:00+07:00');
  for (let rem = 6; rem >= 0; rem -= 1) {
    h.getState().completedBlocksToday = [];
    h.click('gatePassBtn');
    assert.strictEqual(h.getState().sabqiHistory.length, rem);
  }
});

test('15. Kategori < 7 Juz: opsi 10/20 hal/hari, lv_maghrib_manzil, sweep 10 ukuran pool, stepper & setManzilDayDirect', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T05:10:00+07:00' });
  h.engine.setState({
    sabaq: { mode: 'one_page', currentPage: 35 },
    sabqiHistory: [28, 29, 30, 31, 32, 33, 34].map((p) => ({ unitShort: `Halaman ${p}`, approxPage: p, mode: 'one_page' })),
    priorJuzList: [30],
    phaseDay: 1,
    lvManzilPage: 1,
    customRoutines: [
      { id: 'after_mag', title: 'Doa Malam', anchor: { type: 'after_block', blockId: 'hv_maghrib_manzil', offsetMins: 10 }, windowMins: 20, durationMins: 20, isFlexible: true, daysOfWeek: [1], enabled: true }
    ]
  });
  h.click('openScheduleSheetBtn');
  h.clickSelector('.cat-tab-btn', (b) => b.dataset.scat === 'hifz');

  const btn20 = h.document.querySelector('.lvpace-opt[data-lvpace="20"]');
  assert.strictEqual(btn20.disabled, true);
  assert.strictEqual(h.engine.getSchedule().blocks.some((b) => b.id === 'lv_maghrib_manzil'), false);

  h.click('lvMaghribToggle');
  assert.strictEqual(btn20.disabled, false);
  btn20.click();
  assert.strictEqual(h.getState().lvPace, 20);
  const sched = h.engine.getSchedule();
  const lvMag = sched.blocks.find((b) => b.id === 'lv_maghrib_manzil');
  const afterMag = sched.blocks.find((b) => b.id === 'after_mag');
  assert.ok(lvMag && afterMag && afterMag.startMins >= lvMag.endMins + 10);

  // Sapuan lengkap seluruh 141 ukuran pool (maxGrad 1..141) x 2 pace x priorJuz
  let poolSliceChecks = 0;
  for (let maxGrad = 1; maxGrad <= 141; maxGrad += 1) {
    for (const pace of [10, 20]) {
      const prior = maxGrad >= 135 ? [30] : [];
      const hist = Array.from({ length: 7 }, (_, i) => ({ unitShort: `Halaman ${maxGrad + 1 + i}`, approxPage: maxGrad + 1 + i, mode: 'one_page' }));
      h.engine.setState({ lvMaghribEnabled: pace === 20, lvPace: pace, lvManzilPage: 1, priorJuzList: prior, sabaq: { mode: 'one_page', currentPage: maxGrad + 8 }, sabqiHistory: hist });
      const ctx = h.ctx.getLVManzilContext();
      const covered = new Set();
      const cycleDays = Math.ceil(ctx.pool.length / pace);
      for (let d = 0; d < cycleDays; d += 1) {
        h.ctx.getLVManzilSlice(ctx, h.getState().lvManzilPage, 0, pace).pages.forEach((p) => covered.add(p));
        h.ctx.advanceLowVolumeManzilCursor();
        poolSliceChecks += 1;
      }
      assert.strictEqual(covered.size, ctx.pool.length);
    }
  }
  assert.strictEqual(poolSliceChecks, 1660);

  h.engine.setState({ lvMaghribEnabled: false, lvPace: 10, lvManzilPage: 1, priorJuzList: [30], sabaq: { mode: 'one_page', currentPage: 35 }, sabqiHistory: [28,29,30,31,32,33,34].map((p)=>({unitShort:`Halaman ${p}`,approxPage:p,mode:'one_page'})), phaseDay: 1 });
  h.click('incDayBtn');
  assert.strictEqual(h.getState().lvManzilPage, 11);
  h.click('decDayBtn');
  assert.strictEqual(h.getState().lvManzilPage, 1);
  h.setInput('settingsPhaseDayInput', '999');
  assert.strictEqual(h.getState().phaseDay, 5);
});

test('16. Lonceng WebAudio multi-timer, mute saat nonaktif, resume suspended, WakeLock hidden release & deduplikasi reload', async () => {
  const startMs = new Date('2026-11-16T08:00:00+07:00').getTime();
  const h = createAppHarness({
    fixedNowIso: '2026-11-16T08:19:59+07:00',
    initialStorage: {
      [STORAGE_KEY]: {
        schemaVersion: 10,
        completedDate: '2026-11-16',
        timerAlarmEnabled: true,
        blockTimers: {
          primary_hifz_window: { startedAtMs: startMs, durSecs: 5400 },
          primary_hifz_step_0: { startedAtMs: startMs, durSecs: 1800 },
          custom_bg_timer: { startedAtMs: startMs, durSecs: 1200 }
        }
      }
    }
  });
  await Promise.resolve();
  await Promise.resolve();

  assert.strictEqual(h.getChimeCount(), 0);
  assert.strictEqual(h.wakeLockLog.requests, 1);
  assert.strictEqual(h.wakeLockLog.active, true);

  h.setNow('2026-11-16T08:20:00+07:00');
  assert.strictEqual(h.getChimeCount(), 1);
  assert.strictEqual(h.getOscillatorCount(), 6);
  assert.strictEqual(h.getState().blockTimers.custom_bg_timer.alarmFired, true);

  // Timer kedua selesai belakangan di 08:30:00 -> bunyi kedua terpicu
  h.setNow('2026-11-16T08:30:00+07:00');
  assert.strictEqual(h.getOscillatorCount(), 12);
  assert.strictEqual(h.getState().blockTimers.primary_hifz_step_0.alarmFired, true);

  // Matikan alarm -> timer baru tidak berbunyi saat habis
  h.click('toggleTimerAlarmBtn');
  assert.strictEqual(h.getState().timerAlarmEnabled, false);
  h.getState().blockTimers.extra_mute = { startedAtMs: h.now().getTime() - 70000, durSecs: 60 };
  h.engine.evaluateAlarms(h.now());
  assert.strictEqual(h.getOscillatorCount(), 12);

  // Tab hidden melepaskan WakeLock
  h.document.visibilityState = 'hidden';
  h.document.dispatchEvent({ type: 'visibilitychange' });
  await Promise.resolve();
  assert.strictEqual(h.wakeLockLog.releases, 1);

  const hReload = h.reload();
  assert.strictEqual(hReload.getChimeCount(), 0);

  h.setAudioState('suspended');
  h.click('testTimerAlarmBtn');
  assert.strictEqual(h.getResumeCalls(), 1);
});

test('17. Rollover tengah malam (7 kondisi + lompat multi-hari + gateAdvancedDate) & rollback Reset Hari Ini (Pass + Freeze)', () => {
  const cases = [
    { started: true,  gate: 'passed',        expected: null },
    { started: true,  gate: 'failed',        expected: 'failed_prev' },
    { started: true,  gate: 'sabaq_frozen',  expected: null },
    { started: true,  gate: null,            expected: 'unlogged_prev' },
    { started: false, gate: null,            expected: null },
    { started: false, gate: 'unlogged_prev', expected: 'unlogged_prev' },
    { started: false, gate: 'failed_prev',   expected: 'failed_prev' }
  ];
  for (const c of cases) {
    const h = createAppHarness({
      fixedNowIso: '2026-11-17T05:00:00+07:00',
      initialStorage: {
        [STORAGE_KEY]: {
          schemaVersion: 10,
          completedDate: '2026-11-16',
          completedBlocksToday: c.started ? ['primary_hifz'] : [],
          gateResultToday: c.gate
        }
      }
    });
    assert.strictEqual(h.getState().gateResultToday, c.expected);
  }

  // Rekonsiliasi gateAdvancedDate sama dengan hari ini
  const hRec = createAppHarness({
    fixedNowIso: '2026-11-16T21:30:00+07:00',
    initialStorage: { [STORAGE_KEY]: { schemaVersion: 10, completedDate: '2026-11-16', gateAdvancedDate: '2026-11-16', completedBlocksToday: [] } }
  });
  assert.ok(hRec.getState().completedBlocksToday.includes('presleep_gate'));
  assert.strictEqual(hRec.getState().gateResultToday, 'passed');

  // Reset Hari Ini mengembalikan Pass maupun Freeze Gerbang
  const hRst = createAppHarness({
    fixedNowIso: '2026-11-16T21:20:00+07:00',
    initialStorage: { [STORAGE_KEY]: { schemaVersion: 10, completedDate: '2026-11-16', sabaq: { mode: 'one_page', currentPage: 10 }, phaseDay: 4 } }
  });
  hRst.click('gatePassBtn');
  assert.strictEqual(hRst.getState().phaseDay, 5);
  assert.strictEqual(hRst.getState().sabaq.currentPage, 11);
  hRst.click('resetTodayBtn');
  assert.strictEqual(hRst.getState().phaseDay, 4);
  assert.strictEqual(hRst.getState().sabaq.currentPage, 10);
  assert.strictEqual(hRst.getState().gateResultToday, null);

  hRst.click('gateFreezeSabaqBtn');
  assert.strictEqual(hRst.getState().sabaqFrozen, true);
  assert.strictEqual(hRst.getState().sabaqFrozenForNextDay, true);
  hRst.click('resetTodayBtn');
  assert.strictEqual(hRst.getState().phaseDay, 4);
  assert.strictEqual(hRst.getState().sabaqFrozen, false);
  assert.strictEqual(hRst.getState().sabaqFrozenForNextDay, false);
});

test('18. Overrun rentang 90m detik-ke-detik (+MM:SS), kunci UPCOMING semua blok, pratinjau tanpa teks STOP & handle summary', () => {
  const hUp = createAppHarness({ fixedNowIso: '2026-11-16T13:06:00+07:00' });
  hUp.engine.setState({
    completedBlocksToday: ['primary_hifz'],
    customRoutines: [{ id: 'sore_test', title: 'Sesi Sore', anchor: { type: 'fixed', time: '16:00' }, windowMins: 60, durationMins: 60, isFlexible: true, daysOfWeek: [1], enabled: true }]
  });
  hUp.engine.render();
  assert.strictEqual(hUp.el('primaryActionBtn').disabled, true);
  assert.strictEqual(hUp.el('primaryActionBtn').textContent, 'DIKUNCI SAMPAI 16:00');
  assert.ok(hUp.el('scheduleHandleSummary').textContent.includes('PENGATURAN'));
  assert.ok(hUp.el('scheduleHandleSummary').textContent.includes('BERIKUTNYA 16:00'));

  hUp.engine.setState({ completedBlocksToday: ['primary_hifz', 'sore_test'] });
  hUp.setNow('2026-11-16T21:00:00+07:00');
  assert.strictEqual(hUp.engine.getSelection().status, 'UPCOMING');
  assert.strictEqual(hUp.el('gateActionGroup').classList.contains('hidden'), true);
  assert.strictEqual(hUp.el('primaryActionBtn').disabled, true);
  assert.strictEqual(hUp.el('primaryActionBtn').textContent, 'DIKUNCI SAMPAI 21:15');

  hUp.engine.setState({ sabaq: { mode: 'one_page', currentPage: 195 }, completedBlocksToday: [] });
  hUp.setNow('2026-11-16T01:08:00+07:00');
  hUp.click('substepCard');
  assert.strictEqual(hUp.el('sabqiFreezeInlineBtn').classList.contains('hidden'), true);
  assert.strictEqual(hUp.el('subStepCue').textContent.includes('di bawah'), false);

  const startMs = new Date('2026-11-16T05:00:00+07:00').getTime();
  const hOv = createAppHarness({
    fixedNowIso: '2026-11-16T06:29:59+07:00',
    initialStorage: {
      [STORAGE_KEY]: {
        schemaVersion: 10,
        completedDate: '2026-11-16',
        sabaq: { mode: 'one_page', currentPage: 35 },
        phaseDay: 2,
        primarySubStep: 1,
        blockTimers: {
          primary_hifz_window: { startedAtMs: startMs, durSecs: 5400 },
          primary_hifz_step_1: { startedAtMs: startMs + 1800000, durSecs: 1800, alarmFired: true }
        }
      }
    }
  });
  assert.strictEqual(hOv.el('windowSubtimerKicker').textContent, 'SISA RENTANG 90 MENIT');
  assert.strictEqual(hOv.el('windowSubtimerVal').textContent, '00:01');
  hOv.setNow('2026-11-16T06:30:00+07:00');
  assert.strictEqual(hOv.el('windowSubtimerVal').textContent, '00:00');
  hOv.setNow('2026-11-16T06:30:01+07:00');
  assert.strictEqual(hOv.el('windowSubtimerKicker').textContent, 'RENTANG 90 MENIT · LEWAT');
  assert.strictEqual(hOv.el('windowSubtimerVal').textContent, '+00:01');
  assert.strictEqual(hOv.el('windowSubtimerBox').classList.contains('is-overrun'), true);

  hOv.setNow('2026-11-16T06:40:00+07:00');
  assert.strictEqual(hOv.el('windowSubtimerVal').textContent, '+10:00');
  hOv.click('sabqiFreezeInlineBtn');
  assert.strictEqual(hOv.getState().blockTimers.primary_hifz_step_1.durSecs, 2700);
});

test('19. Daftar Sabqi/Manzil atas (<7 & >7 Juz), tata letak Sabaq di atas Manzil, auto-rebuild & XSS escaping', () => {
  assert.ok(htmlSource.indexOf('id="hifzCorpusSection"') < htmlSource.indexOf('id="detachedSabaqSection"'));
  assert.ok(htmlSource.indexOf('id="detachedSabaqSection"') < htmlSource.indexOf('id="settingsPhaseDayInput"'));

  const h = createAppHarness({ fixedNowIso: '2026-11-16T08:00:00+07:00' });
  h.engine.setState({
    phaseDay: 1,
    priorJuzList: [30],
    sabaq: { mode: 'one_page', currentPage: 35, subSlot: 0, pagesPerDay: 1, surahNum: 2, startAyah: 220, ayahsPerDay: 10, sundayNewSabaq: false, sabaqCompleted: false },
    sabqiHistory: h.ctx.buildBackfilledSabqiHistory({ mode: 'one_page', currentPage: 35 }, 7)
  });
  h.click('openScheduleSheetBtn');
  h.clickSelector('.cat-tab-btn', (b) => b.dataset.scat === 'hifz');

  const boxHtml = () => h.el('hifzCorpusListBox').children.map((c) => c.innerHTML || c.textContent || '').join(' ');
  assert.ok(boxHtml().includes('Halaman 34'));
  assert.ok(h.el('hifzCorpusHint').textContent.includes('1-7'));

  h.clickSelector('.smode-opt', (b) => b.getAttribute('data-smode') === 'quarter_page');
  assert.ok(boxHtml().includes('Baris 1–4') || boxHtml().includes('Baris 13–15'));

  h.click('btnShowManzilList');
  assert.ok(boxHtml().includes('SIKLUS HARI'));
  assert.ok(boxHtml().includes('HARI INI'));

  h.setInput('inpSabaqPage', 155);
  assert.ok(boxHtml().includes('JUZ 1') && boxHtml().includes('JUZ 30'));
  assert.ok(boxHtml().includes('HARI INI'));

  // XSS escape pada unitShort
  h.click('btnShowSabqiList');
  h.engine.setState({ sabqiHistory: [{ unitShort: '<img src=x onerror=alert(1)>', approxPage: 5, mode: 'one_page' }] });
  h.engine.syncSettingsDrawerUI();
  assert.strictEqual(boxHtml().includes('<img'), false);
  assert.ok(boxHtml().includes('&lt;img'));
});

test('20. Invarian keamanan jalan (isCommute) & clamping batas input editor (termasuk jeda 0 menit after_block)', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T08:00:00+07:00' });
  h.click('openScheduleSheetBtn');
  h.click('openUnifiedBlockEditorBtn');

  h.click('toggleUnifiedHifzBtn');
  h.click('toggleUnifiedCommuteBtn');
  assert.strictEqual(h.el('toggleUnifiedFlexBtn').disabled, true);
  assert.strictEqual(h.el('toggleUnifiedHifzBtn').disabled, true);
  h.el('inpUnifiedTitle').value = 'Berkendara ke Masjid';
  h.el('inpUnifiedFixedTime').value = '11:20';
  h.el('inpUnifiedWindowMins').value = '25';
  h.click('saveUnifiedBlockBtn');

  const comm = h.getState().customRoutines.find((r) => r.title === 'Berkendara ke Masjid');
  assert.ok(comm);
  assert.strictEqual(comm.isCommute, true);
  assert.strictEqual(comm.isFlexible, false);
  assert.strictEqual(comm.includeHifzTarget, false);
  assert.strictEqual(comm.conflictPolicy, 'strict');

  h.click('openUnifiedBlockEditorBtn');
  const dayBtns = h.document.querySelectorAll('.ucday-opt');
  dayBtns.forEach((b) => b.click());
  assert.strictEqual(dayBtns.filter((b) => b.classList.contains('selected')).length, 1);

  h.clickSelector('.uanchor-opt', (b) => b.dataset.uanchor === 'prayer');
  h.el('inpUnifiedTitle').value = 'Rutinitas Clamped Max';
  h.el('inpUnifiedPrayerOffset').value = '999';
  h.el('inpUnifiedWindowMins').value = '999';
  h.el('inpUnifiedTimerMins').value = '999';
  h.click('saveUnifiedBlockBtn');
  const clampedMax = h.getState().customRoutines.find((r) => r.title === 'Rutinitas Clamped Max');
  assert.strictEqual(clampedMax.anchor.offsetMins, 240);
  assert.strictEqual(clampedMax.windowMins, 480);
  assert.strictEqual(clampedMax.durationMins, 480);

  h.click('openUnifiedBlockEditorBtn');
  h.clickSelector('.uanchor-opt', (b) => b.dataset.uanchor === 'after_block');
  h.el('inpUnifiedTitle').value = 'Setelah Hifz 0 Menit';
  h.el('inpUnifiedBlockOffset').value = '0';
  h.el('inpUnifiedWindowMins').value = '1';
  h.el('inpUnifiedTimerMins').value = '0';
  h.click('saveUnifiedBlockBtn');
  const zeroOff = h.getState().customRoutines.find((r) => r.title === 'Setelah Hifz 0 Menit');
  assert.strictEqual(zeroOff.anchor.offsetMins, 0);
  assert.strictEqual(zeroOff.windowMins, 5);
  assert.strictEqual(zeroOff.durationMins, 5);
});

test('21. Auto-flush seluruh 7 input pada tutup/pagehide/beforeunload/visibilitychange, 604 halaman non-kosong & 6.236 ayat', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T08:00:00+07:00' });
  h.click('openScheduleSheetBtn');
  h.clickSelector('.cat-tab-btn', (b) => b.dataset.scat === 'hifz');

  h.el('inpSabaqPage').value = '48';
  h.el('inpSabaqPage').focus();
  h.el('inpPriorJuz').value = '29, 30';
  h.window.dispatchEvent({ type: 'pagehide' });
  assert.strictEqual(h.getState().sabaq.currentPage, 48);
  assert.deepStrictEqual(Array.from(h.getState().priorJuzList), [29, 30]);

  h.el('inpSabaqPage').value = '52';
  h.el('inpSabaqPage').focus();
  h.document.visibilityState = 'hidden';
  h.document.dispatchEvent({ type: 'visibilitychange' });
  assert.strictEqual(h.getState().sabaq.currentPage, 52);

  h.clickSelector('.smode-opt', (b) => b.getAttribute('data-smode') === 'multi_page');
  h.el('inpSabaqPagesPerDay').value = '4';
  h.el('inpSabaqPagesPerDay').focus();
  h.window.dispatchEvent({ type: 'beforeunload' });
  assert.strictEqual(h.getState().sabaq.pagesPerDay, 4);

  h.clickSelector('.smode-opt', (b) => b.getAttribute('data-smode') === 'ayah');
  h.el('inpSabaqStartAyah').value = '5';
  h.el('inpSabaqStartAyah').focus();
  h.el('inpSabaqAyahsPerDay').value = '12';
  h.click('closeSettingsBtn');
  assert.strictEqual(h.getState().sabaq.startAyah, 5);
  assert.strictEqual(h.getState().sabaq.ayahsPerDay, 12);
  assert.strictEqual(h.el('settingsOverlay').classList.contains('hidden'), true);

  h.click('openScheduleSheetBtn');
  h.setInput('inpSabaqApproxPage', '50');
  assert.strictEqual(h.getState().sabaq.surahNum, 3);

  let prevPg = 1;
  let totalAyahs = 0;
  const perPageCounts = new Array(605).fill(0);
  for (const s of h.sandbox.SURAHS_114) {
    assert.strictEqual(h.sandbox.estimatePageForSurahAyah(s.n, 1), s.page);
    for (let a = 1; a <= s.ayahs; a += 1) {
      const pg = h.sandbox.estimatePageForSurahAyah(s.n, a);
      assert.ok(pg >= prevPg && pg <= 604);
      prevPg = pg;
      perPageCounts[pg] += 1;
      totalAyahs += 1;
    }
  }
  assert.strictEqual(totalAyahs, 6236);
  for (let pg = 1; pg <= 604; pg += 1) {
    assert.ok(perPageCounts[pg] >= 1, `Halaman ${pg} kosong`);
    assert.strictEqual(h.ctx.getAyahsOnPage(pg).length, perPageCounts[pg], `Partisi getAyahsOnPage halaman ${pg} tidak cocok`);
  }

  // 100.000 uji ketahanan parser parsePriorJuzList & 50.400 uji konversi toMinutes
  let pSeed = 0xCAFE1234;
  const pRand = () => { pSeed = (pSeed * 1664525 + 1013904223) >>> 0; return pSeed; };
  const tokens = ['1', '30', '29-30', '0', '31', '-5', 'abc', ';', ',', '15-18', '28..30', ''];
  for (let k = 0; k < 100000; k += 1) {
    const raw = `${tokens[pRand() % tokens.length]} ${tokens[pRand() % tokens.length]} ${tokens[pRand() % tokens.length]}`;
    const parsed = h.ctx.parsePriorJuzList(raw);
    assert.ok(Array.isArray(parsed) && parsed.length <= 30);
    for (let i = 0; i < parsed.length; i += 1) {
      assert.ok(Number.isInteger(parsed[i]) && parsed[i] >= 1 && parsed[i] <= 30);
      if (i > 0) assert.ok(parsed[i] > parsed[i - 1]);
    }
  }
  for (let round = 0; round < 35; round += 1) {
    for (let m = 0; m < 1440; m += 1) {
      const hhmm = `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
      assert.strictEqual(h.ctx.toMinutes(hhmm), m);
    }
  }
});

// ===========================================================================
// BAGIAN II: REGRESI EKSPLISIT BUG-01..BUG-06 & GESTUR/PITA VISUAL (22 - 29)
// ===========================================================================

test('22. [REGRESI BUG-01] Clamping Khatam Hal. 604 pada seluruh 5 mode Sabaq & stabilitas > 7 Juz', () => {
  const modeSeeds = [
    { mode: 'multi_page', currentPage: 602, pagesPerDay: 5, sabaqCompleted: false },
    { mode: 'one_page', currentPage: 604, sabaqCompleted: false },
    { mode: 'half_page', currentPage: 604, subSlot: 1, sabaqCompleted: false },
    { mode: 'quarter_page', currentPage: 604, subSlot: 3, sabaqCompleted: false },
    { mode: 'ayah', surahNum: 114, startAyah: 1, ayahsPerDay: 10, currentPage: 604, sabaqCompleted: false }
  ];
  for (const seed of modeSeeds) {
    const h = createAppHarness({
      fixedNowIso: '2026-11-16T21:20:00+07:00',
      initialStorage: { [STORAGE_KEY]: { schemaVersion: 10, completedDate: '2026-11-16', phaseDay: 10, sabaq: seed } }
    });
    h.click('gatePassBtn');
    assert.strictEqual(h.getState().hifzPhase, 4);
    assert.strictEqual(h.getState().sabaq.currentPage, 604);
    assert.strictEqual(h.getState().sabaq.sabaqCompleted, true);
    assert.strictEqual(h.ctx.describeCurrentSabaqUnit(h.getState().sabaq).nextState().currentPage, 604);
  }
});

test('23. [REGRESI BUG-02] Penjaga kebocoran halaman parsial ke Manzil, eviksi gatePassBtn & konservasi baris/ayat', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T08:00:00+07:00' });
  const partialHist = [0, 1, 2].map((slot) => {
    const d = h.ctx.describeCurrentSabaqUnit({ mode: 'quarter_page', currentPage: 5, subSlot: slot });
    return { unitShort: d.unitShort, approxPage: d.approxPage, mode: d.mode };
  });
  const activeQ4 = h.ctx.describeCurrentSabaqUnit({ mode: 'quarter_page', currentPage: 5, subSlot: 3 });
  assert.strictEqual(h.ctx.getMaxGraduatedNewPage(partialHist, activeQ4), 4);

  const agedHist = [
    ...partialHist,
    ...[6, 7, 8, 9, 10, 11, 12].map((pg) => ({ unitShort: `Halaman ${pg}`, approxPage: pg, mode: 'one_page' }))
  ].slice(-7);
  const nextDesc = h.ctx.describeCurrentSabaqUnit({ mode: 'one_page', currentPage: 13, subSlot: 0 });
  const span = h.ctx.getPartialPageSpan(agedHist, nextDesc);
  assert.ok(span && span.page === 5 && span.label === 'Baris 1–12');
  assert.strictEqual(h.ctx.getMaxGraduatedNewPage(agedHist, nextDesc), 4);

  // Eviksi nyata via gatePassBtn pada Q1..Q3 lalu tuntas saat Q4
  const hGate = createAppHarness({
    fixedNowIso: '2026-11-16T21:20:00+07:00',
    initialStorage: {
      [STORAGE_KEY]: {
        schemaVersion: 10,
        completedDate: '2026-11-16',
        sabaq: { mode: 'quarter_page', currentPage: 5, subSlot: 0, sundayNewSabaq: true },
        sabqiHistory: []
      }
    }
  });
  for (let d = 0; d < 3; d += 1) {
    hGate.setNow(`2026-11-${16 + d}T21:20:00+07:00`);
    hGate.click('gatePassBtn');
  }
  hGate.engine.setState({ sabaq: { mode: 'one_page', currentPage: 6, subSlot: 0, sundayNewSabaq: true } });
  for (let d = 0; d < 7; d += 1) {
    hGate.setNow(`2026-11-${19 + d}T21:20:00+07:00`);
    hGate.click('gatePassBtn');
  }
  const evSpan = hGate.ctx.getPartialPageSpan(hGate.getState().sabqiHistory, hGate.getState().sabaq);
  assert.ok(evSpan && evSpan.page === 5 && evSpan.label === 'Baris 1–12');
  assert.strictEqual(hGate.ctx.getMaxGraduatedNewPage(hGate.getState().sabqiHistory, hGate.getState().sabaq), 4);

  // Jika Q4 diselesaikan pada Hal. 5 -> conservedPartialSpans[5] langsung terhapus dan Hal. 5 lulus
  hGate.engine.setState({
    sabaq: { mode: 'quarter_page', currentPage: 5, subSlot: 3, sundayNewSabaq: true },
    completedBlocksToday: []
  });
  hGate.setNow('2026-11-26T21:20:00+07:00');
  hGate.click('gatePassBtn');
  assert.strictEqual(hGate.getState().conservedPartialSpans[5], undefined);
});

test('24. [REGRESI BUG-03] Siklus penuh Langkah 0->1->2, freeze inline, pelestarian rentang 90m & undo LIFO', () => {
  const startMs = new Date('2026-11-16T05:00:00+07:00').getTime();
  const h = createAppHarness({
    fixedNowIso: '2026-11-16T05:00:00+07:00',
    initialStorage: {
      [STORAGE_KEY]: {
        schemaVersion: 10,
        completedDate: '2026-11-16',
        sabaq: { mode: 'one_page', currentPage: 25 },
        phaseDay: 2,
        primarySubStep: 0,
        sabaqFrozen: false
      }
    }
  });
  const st = h.getState();
  h.click('primaryActionBtn'); // start step 0
  assert.ok(st.blockTimers.primary_hifz_window);
  h.click('resetTimerBtn');
  assert.strictEqual(st.blockTimers.primary_hifz_window, undefined);

  h.click('primaryActionBtn'); // start step 0
  h.click('primaryActionBtn'); // complete step 0 -> subStep 1
  h.advanceMs(1800000);
  h.click('primaryActionBtn'); // start step 1
  h.click('primaryActionBtn'); // complete step 1 -> subStep 2
  h.advanceMs(1800000);
  h.click('primaryActionBtn'); // start step 2
  const winStart = st.blockTimers.primary_hifz_window.startedAtMs;
  const s2Start = st.blockTimers.primary_hifz_step_2.startedAtMs;

  h.click('resetTimerBtn');
  assert.strictEqual(st.blockTimers.primary_hifz_step_2, undefined);
  assert.strictEqual(st.blockTimers.primary_hifz_window.startedAtMs, winStart);

  h.click('primaryActionBtn'); // restart step 2
  const s2Restart = st.blockTimers.primary_hifz_step_2.startedAtMs;
  assert.ok(s2Restart >= s2Start);
  h.click('sabqiFreezeInlineBtn');
  assert.strictEqual(st.sabaqFrozen, true);
  assert.strictEqual(st.primarySubStep, 1);
  assert.strictEqual(st.blockTimers.primary_hifz_step_2, undefined);

  h.click('undoBtn');
  assert.strictEqual(st.sabaqFrozen, false);
  assert.strictEqual(st.primarySubStep, 2);
  assert.strictEqual(st.blockTimers.primary_hifz_step_2.startedAtMs, s2Restart);
  assert.strictEqual(startMs > 0, true);
});

test('25. [REGRESI BUG-04] Batas keras Gerbang 21:15 pada keterlambatan ekstrem & preemption blok ketat atas flex/Hifz', () => {
  const startMs = new Date('2026-11-16T18:50:00+07:00').getTime();
  const h = createAppHarness({
    fixedNowIso: '2026-11-16T19:00:00+07:00',
    initialStorage: {
      [STORAGE_KEY]: {
        schemaVersion: 10,
        completedDate: '2026-11-16',
        completedBlocksToday: ['primary_hifz'],
        blockTimers: { running_flex: { startedAtMs: startMs, durSecs: 3600 } },
        customRoutines: [
          { id: 'running_flex', title: 'Flex Jalan', anchor: { type: 'fixed', time: '18:50' }, windowMins: 60, durationMins: 60, isFlexible: true, conflictPolicy: 'shift', daysOfWeek: [1], enabled: true },
          { id: 'strict_hit', title: 'Kuliah Ketat', anchor: { type: 'fixed', time: '19:00' }, windowMins: 45, durationMins: 45, isFlexible: false, conflictPolicy: 'strict', daysOfWeek: [1], enabled: true },
          { id: 'late_flex', title: 'Sore', anchor: { type: 'fixed', time: '14:00' }, windowMins: 60, durationMins: 60, isFlexible: true, conflictPolicy: 'shift', daysOfWeek: [1], enabled: true }
        ]
      }
    }
  });
  const preemptSel = h.engine.getSelection(h.now());
  assert.strictEqual(preemptSel.block.id, 'strict_hit');
  assert.strictEqual(preemptSel.preemptedFlex, true);

  h.setNow('2026-11-16T21:05:00+07:00');
  const schedLate = h.engine.getSchedule(h.now());
  assert.strictEqual(schedLate.compressionRatio, 0);
  assert.ok(schedLate.deferredBlocks.length >= 1);
  for (const b of schedLate.blocks.filter((x) => x.id !== 'presleep_gate' && x.id !== 'sleep_block')) {
    assert.ok(b.endMins <= 21 * 60 + 15);
  }
});

test('26. [REGRESI BUG-05] Hitung mundur tidur lintas tengah malam, batas bangun 03:30..04:30 & rollover ganti hari', () => {
  for (const [subuhStr, off, expectedHours] of [['03:20', 10, 4.0], ['03:50', 10, 4.5], ['04:45', 15, 5.0]]) {
    const h = createAppHarness({
      fixedNowIso: '2026-11-16T23:30:00+07:00',
      initialStorage: {
        [STORAGE_KEY]: {
          schemaVersion: 10,
          completedDate: '2026-11-16',
          completedBlocksToday: ['primary_hifz'],
          gateResultToday: null,
          sabaqFrozen: true,
          prayerSource: 'manual',
          manualPrayerTimes: { subuh: subuhStr, dzuhur: '11:45', ashar: '15:05', maghrib: '17:50', isya: '19:00' },
          subuhOffsetMins: off
        }
      }
    });
    const nightSel = h.engine.getSelection(h.now());
    assert.strictEqual(nightSel.block.id, 'sleep_block');
    assert.strictEqual(nightSel.targetSecs, expectedHours * 3600);

    h.setNow('2026-11-18T05:05:00+07:00');
    const st = h.getState();
    assert.strictEqual(st.completedDate, '2026-11-18');
    assert.strictEqual(st.gateResultToday, 'unlogged_prev');
    assert.strictEqual(st.sabaqFrozen, false);
    assert.strictEqual(h.engine.getSelection(h.now()).block.id, 'primary_hifz');
  }
});

test('27. [REGRESI BUG-06] Deduplikasi alarm lintas tick & reload, resume AudioContext & auto-commit semua input terfokus', () => {
  const startMs = new Date('2026-11-16T08:00:00+07:00').getTime();
  const h = createAppHarness({
    fixedNowIso: '2026-11-16T08:20:00+07:00',
    initialStorage: {
      [STORAGE_KEY]: {
        schemaVersion: 10,
        completedDate: '2026-11-16',
        priorJuzList: [29, 30],
        phaseDay: 1,
        timerAlarmEnabled: true,
        blockTimers: { custom_bg_timer: { startedAtMs: startMs, durSecs: 1200 } }
      }
    }
  });
  assert.strictEqual(h.getChimeCount(), 1);
  assert.strictEqual(h.getState().blockTimers.custom_bg_timer.alarmFired, true);
  h.tick();
  h.tick();
  assert.strictEqual(h.getChimeCount(), 1);
  const hReload = h.reload();
  assert.strictEqual(hReload.getChimeCount(), 0);

  h.setAudioState('suspended');
  h.click('testTimerAlarmBtn');
  assert.strictEqual(h.getResumeCalls(), 1);

  h.click('openScheduleSheetBtn');
  h.clickSelector('.cat-tab-btn', (b) => b.dataset.scat === 'hifz');
  const dayInp = h.el('settingsPhaseDayInput');
  dayInp.focus();
  dayInp.value = '3';
  const priorInp = h.el('inpPriorJuz');
  priorInp.value = '28, 29, 30';
  h.click('closeSettingsBtn');
  assert.strictEqual(h.getState().phaseDay, 3);
  assert.strictEqual(h.getState().lvManzilPage, 562);
  assert.deepStrictEqual(Array.from(h.getState().priorJuzList), [28, 29, 30]);
});

test('28. Gestur swipe bottom sheet (ambang buka & tutup) dan segmen pita visual aktivitas 04:30–22:00', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T08:00:00+07:00' });
  assert.strictEqual(h.el('settingsOverlay').classList.contains('hidden'), true);

  // Delta <= 28 tidak membuka sheet
  h.el('openScheduleSheetBtn').dispatchEvent({ type: 'touchstart', touches: [{ clientY: 700 }] });
  h.el('openScheduleSheetBtn').dispatchEvent({ type: 'touchend', changedTouches: [{ clientY: 680 }] });
  assert.strictEqual(h.el('settingsOverlay').classList.contains('hidden'), true);

  // Delta > 28 membuka sheet dan merender segmen hifz, custom, gate, & now-pin
  h.el('openScheduleSheetBtn').dispatchEvent({ type: 'touchstart', touches: [{ clientY: 700 }] });
  h.el('openScheduleSheetBtn').dispatchEvent({ type: 'touchend', changedTouches: [{ clientY: 640 }] });
  assert.strictEqual(h.el('settingsOverlay').classList.contains('hidden'), false);
  const trackChildren = h.el('dayBlockTrack').children;
  assert.ok(trackChildren.some((el) => el.classList.contains('is-hifz')));
  assert.ok(trackChildren.some((el) => el.classList.contains('is-custom')));
  assert.ok(trackChildren.some((el) => el.classList.contains('is-gate')));
  assert.ok(trackChildren.some((el) => el.classList.contains('day-block-now-pin')));

  // Tarikan tutup <= 95 snap back (tetap terbuka)
  h.el('sheetDragZone').dispatchEvent({ type: 'touchstart', touches: [{ clientY: 100 }] });
  h.el('sheetDragZone').dispatchEvent({ type: 'touchmove', touches: [{ clientY: 160 }] });
  h.el('sheetDragZone').dispatchEvent({ type: 'touchend', changedTouches: [{ clientY: 160 }] });
  assert.strictEqual(h.el('settingsOverlay').classList.contains('hidden'), false);
  assert.strictEqual(h.el('settingsSheetModal').style.transform, 'translateY(0px)');

  // Tarikan tutup > 95 menutup sheet
  h.el('sheetDragZone').dispatchEvent({ type: 'touchstart', touches: [{ clientY: 100 }] });
  h.el('sheetDragZone').dispatchEvent({ type: 'touchmove', touches: [{ clientY: 220 }] });
  h.el('sheetDragZone').dispatchEvent({ type: 'touchend', changedTouches: [{ clientY: 220 }] });
  assert.strictEqual(h.el('settingsOverlay').classList.contains('hidden'), true);
});

test('29. Fuzzer properti multi-hari (20.000 langkah acak + CRUD + prayerSource + resetToday + reload + 5 mode + Profil 1-3)', () => {
  let seed = 0xBEEF2026;
  const rand = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 0x100000000; };
  const randInt = (lo, hi) => lo + Math.floor(rand() * (hi - lo + 1));

  let h = createAppHarness({ fixedNowIso: '2026-11-16T05:00:00+07:00' });
  const profs = ['profile_1', 'profile_2', 'profile_3'];
  const modes = ['quarter_page', 'half_page', 'one_page', 'multi_page', 'ayah'];
  const pSrcs = ['fixed', 'manual', 'gps'];

  for (let step = 0; step < 20000; step += 1) {
    const r = rand();
    if (r < 0.22) h.advanceMs(randInt(15, 180) * 60 * 1000);
    else if (r < 0.44) {
      const btn = h.el('primaryActionBtn');
      if (!btn.classList.contains('hidden') && !btn.disabled) h.click('primaryActionBtn');
    } else if (r < 0.56) {
      if (!h.el('gateActionGroup').classList.contains('hidden')) {
        const pick = rand();
        if (pick < 0.6) h.click('gatePassBtn');
        else if (pick < 0.85) h.click('gateFreezeSabaqBtn');
        else h.click('gateFailBtn');
      }
    } else if (r < 0.66) {
      if (!h.el('undoBtn').classList.contains('hidden')) h.click('undoBtn');
    } else if (r < 0.82) {
      const sub = rand();
      if (sub < 0.25) {
        h.click('openScheduleSheetBtn');
        h.setInput('selRoutineProfile', profs[randInt(0, 2)]);
        h.click('closeSettingsBtn');
      } else if (sub < 0.45) {
        h.click('sabqiFreezeInlineBtn');
      } else if (sub < 0.65) {
        const nextMode = modes[randInt(0, 4)];
        h.click('openScheduleSheetBtn');
        h.clickSelector('.cat-tab-btn', (b) => b.dataset.scat === 'hifz');
        h.clickSelector('.smode-opt', (b) => b.getAttribute('data-smode') === nextMode);
        h.click('closeSettingsBtn');
      } else if (sub < 0.82) {
        const pickedSrc = pSrcs[randInt(0, 2)];
        h.click('openScheduleSheetBtn');
        h.clickSelector('.cat-tab-btn', (b) => b.dataset.scat === 'prayer');
        h.clickSelector('.psrc-opt', (b) => b.getAttribute('data-psrc') === pickedSrc);
        h.click('closeSettingsBtn');
      } else {
        h.click('openScheduleSheetBtn');
        if (h.getState().customRoutines.length < 4) {
          h.click('openUnifiedBlockEditorBtn');
          h.setInput('inpUnifiedTitle', `Fuzz ${step}`);
          h.setInput('inpUnifiedFixedTime', `${String(randInt(7, 20)).padStart(2, '0')}:00`);
          h.setInput('inpUnifiedWindowMins', String(randInt(15, 60)));
          h.setInput('inpUnifiedTimerMins', '15');
          h.click('saveUnifiedBlockBtn');
        } else {
          h.clickSelector('.cat-tab-btn', (b) => b.dataset.scat === 'system');
          h.click('resetTodayBtn');
        }
        h.click('closeSettingsBtn');
      }
    } else if (r < 0.98) {
      if (!h.el('resetTimerBtn').classList.contains('hidden')) h.click('resetTimerBtn');
      else h.tick();
    } else {
      h.window.dispatchEvent({ type: 'pagehide' });
    }
    if (step > 0 && step % 200 === 0) {
      h = h.reload();
    }

    const st = h.getState();
    assert.strictEqual(st.schemaVersion, 10);
    assert.ok(st.hifzPhase === 3 || st.hifzPhase === 4);
    assert.ok(Number.isInteger(st.phaseDay) && st.phaseDay >= 1);
    assert.ok(st.primarySubStep >= 0 && st.primarySubStep <= 2);
    assert.ok(st.sabqiHistory.length <= 7);
    assert.ok(st.undoStack.length <= 30);
    assert.ok(st.sabaq.currentPage >= 1 && st.sabaq.currentPage <= 604);

    const sel = h.engine.getSelection(h.now());
    assert.ok(sel && sel.block && Number.isFinite(sel.targetSecs) && sel.targetSecs >= 0);
  }
});

test('30. Heartbeat ringan: timer berubah tiap detik tanpa membangun ulang jadwal, render penuh per menit', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T05:00:00+07:00' });
  h.click('primaryActionBtn');
  const timerBefore = h.el('liveTimer').textContent;

  let scheduleBuilds = 0;
  const originalBuildDailySchedule = h.ctx.buildDailySchedule;
  h.ctx.buildDailySchedule = (...args) => {
    scheduleBuilds += 1;
    return originalBuildDailySchedule(...args);
  };

  h.elapseClockMs(1000);
  h.tick();
  assert.notStrictEqual(h.el('liveTimer').textContent, timerBefore);
  assert.strictEqual(scheduleBuilds, 0, 'Heartbeat detik tidak boleh menghitung ulang jadwal');

  h.elapseClockMs(60000);
  h.tick();
  assert.strictEqual(scheduleBuilds, 1, 'Perubahan menit harus menyegarkan jadwal satu kali');
});

test('31. Tema terang/gelap tersimpan, diterapkan langsung, dan mengikuti nilai dari penyimpanan lokal', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T05:00:00+07:00' });
  assert.strictEqual(h.getState().themePreference, 'system');

  h.el('themePreferenceSelect').value = 'dark';
  h.el('themePreferenceSelect').dispatchEvent({ type: 'change' });
  assert.strictEqual(h.getState().themePreference, 'dark');
  assert.strictEqual(h.document.documentElement.dataset.theme, 'dark');

  const restored = h.reload();
  assert.strictEqual(restored.getState().themePreference, 'dark');
  assert.strictEqual(restored.document.documentElement.dataset.theme, 'dark');

  restored.el('themePreferenceSelect').value = 'light';
  restored.el('themePreferenceSelect').dispatchEvent({ type: 'change' });
  assert.strictEqual(restored.document.documentElement.dataset.theme, 'light');
});

test('32. Navbar enam ikon kustom, termasuk pembaca Al-Qur’an, tetap ramping dan responsif', () => {
  const navMatch = htmlSource.match(/<nav id="appBottomNav"[\s\S]*?<\/nav>/);
  assert.ok(navMatch, 'Navbar utama harus ada');
  const buttons = [...navMatch[0].matchAll(/<button\b([^>]*)>([\s\S]*?)<\/button>/g)];
  assert.strictEqual(buttons.length, 6, 'Navbar berisi lima tujuan lama dan satu tujuan pembaca Al-Qur’an');

  const icons = [];
  for (const [, attrs, body] of buttons) {
    assert.match(attrs, /aria-label="/, 'Setiap ikon navigasi harus punya nama aksesibel');
    assert.match(body, /<span class="nav-pill-label">/, 'Setiap tujuan harus punya label teks');
    const icon = body.match(/<svg\b[\s\S]*?<\/svg>/);
    assert.ok(icon, 'Setiap tujuan harus memakai ikon vektor inline');
    icons.push(icon[0]);
  }
  assert.strictEqual(new Set(icons).size, 6, 'Keenam ikon navigasi harus dirancang berbeda');
  assert.match(navMatch[0], /data-nav-view="quran"/, 'Pembaca Al-Qur’an tersedia sebagai tujuan navbar');
  assert.match(htmlSource, /--shell-max:\s*480px/, 'Konten harus tetap ramping pada layar lebar');
  assert.match(htmlSource, /@media\s*\(max-width:\s*380px\)/, 'Navbar harus punya tata letak layar kecil');
  assert.match(htmlSource, /prefers-reduced-motion:\s*reduce/, 'Gerak harus menghormati preferensi aksesibilitas');

  const markPath = path.join(path.dirname(htmlPath), 'rutinitas-mark.svg');
  assert.ok(fs.existsSync(markPath), 'Ikon merek offline harus ikut dalam paket aplikasi');
  assert.match(fs.readFileSync(markPath, 'utf8'), /viewBox="0 0 48 48"/);
});

test('33. Kontras tema, permukaan nonaktif, dan jarak ikon-teks tetap jelas', () => {
  assert.ok(htmlSource.includes('--accent-text: #1652F6;'));
  assert.ok(htmlSource.includes('--accent-text: #8FAEFF;'));
  assert.ok(htmlSource.includes('--disabled-bg: #ECEAE6;'));
  assert.ok(htmlSource.includes('--disabled-bg: #242422;'));
  assert.ok(htmlSource.includes('--disabled-text: #65625D;'));
  assert.ok(htmlSource.includes('--scrim: rgba(17, 17, 17, 0.38);'));
  assert.ok(htmlSource.includes('--scrim: rgba(0, 0, 0, 0.62);'));

  assert.match(htmlSource, /\.state-badge\s*\{[^}]*gap:\s*0\.45rem;/);
  assert.match(htmlSource, /\.routine-item-title\.is-completed\s*\{[^}]*gap:\s*0\.45rem;/);
  assert.match(htmlSource, /\.btn-gate-fail\s*\{[^}]*background-color:\s*var\(--bg-surface\);/);
  assert.match(htmlSource, /\.nav-pill-btn\.is-active\s*\{[^}]*gap:\s*0\.45rem;/);
  assert.match(htmlSource, /html\.mid-low-device\.android-webview \.floating-pill-nav\s*\{[^}]*backdrop-filter:\s*none !important;/);
  assert.match(htmlSource, /\.mini-chip-btn\.has-arrow::after\s*\{[^}]*margin-inline-start:\s*0\.1rem;/);
  assert.match(htmlSource, /jumpBtn\.classList\.add\('has-arrow'\)/);
  assert.ok(htmlSource.includes('MENIT → ' + String.fromCharCode(36) + '{b.windowMins} MENIT'));

  const disabledRules = [
    htmlSource.match(/\.opt-btn:disabled\s*\{([^}]*)\}/),
    htmlSource.match(/\.opt-btn-sm:disabled\s*\{([^}]*)\}/),
    htmlSource.match(/\.cselect-trigger:disabled\s*\{([^}]*)\}/),
    htmlSource.match(/\.day-block-seg\.is-disabled\s*\{([^}]*)\}/),
    htmlSource.match(/\.timeline-row\.is-completed\s*\{([^}]*)\}/),
    htmlSource.match(/\.timeline-row\.is-skipped\s*\{([^}]*)\}/),
    htmlSource.match(/\.timeline-row\.is-muted-disabled\s*\{([^}]*)\}/)
  ];
  disabledRules.forEach((rule) => {
    assert.ok(rule, 'Aturan keadaan nonaktif/selesai harus tersedia');
    assert.doesNotMatch(rule[1], /opacity:\s*0\.[0-9]+/, 'Keadaan tidak boleh memudarkan seluruh komponen');
  });
});

test('34. Target hafalan Juz/rentang khusus selesai tepat batas tanpa menandai khatam 30 Juz', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T05:00:00+07:00' });
  h.setInput('selSabaqTargetJuz', 18);
  const selected = h.getState().sabaq;
  assert.strictEqual(selected.targetJuz, 18);
  assert.strictEqual(selected.rangeStartPage, 342);
  assert.strictEqual(selected.rangeEndPage, 361);
  assert.deepStrictEqual(
    { surah: selected.targetStartSurahNum, ayah: selected.targetStartAyah },
    { surah: 23, ayah: 1 }
  );
  assert.deepStrictEqual(
    { surah: selected.targetEndSurahNum, ayah: selected.targetEndAyah },
    { surah: 25, ayah: 20 }
  );
  assert.match(h.ctx.getSabaqTargetSummary(selected), /Al-Mu'minun 23:1/);
  assert.match(h.ctx.getSabaqTargetSummary(selected), /Al-Furqan 25:20/);

  let quarter = h.ctx.normalizeSabaqState({ ...selected, mode: 'quarter_page' });
  let steps = 0;
  while (!quarter.targetCompleted && steps < 100) {
    quarter = h.ctx.describeCurrentSabaqUnit(quarter).nextState();
    steps += 1;
  }
  assert.strictEqual(steps, 80);
  assert.strictEqual(quarter.currentPage, 361);
  assert.strictEqual(quarter.targetCompleted, true);
  assert.strictEqual(quarter.sabaqCompleted, false);

  const halfEnd = h.ctx.describeCurrentSabaqUnit({
    ...selected, mode: 'half_page', currentPage: 361, subSlot: 1
  }).nextState();
  assert.strictEqual(halfEnd.targetCompleted, true);
  assert.strictEqual(halfEnd.sabaqCompleted, false);
  const multiEnd = h.ctx.describeCurrentSabaqUnit({
    ...selected, mode: 'multi_page', currentPage: 360, pagesPerDay: 5
  }).nextState();
  assert.strictEqual(multiEnd.currentPage, 361);
  assert.strictEqual(multiEnd.targetCompleted, true);

  let ayahTarget = h.ctx.normalizeSabaqState({ ...selected, mode: 'ayah', ayahsPerDay: 150 });
  let ayahSteps = 0;
  while (!ayahTarget.targetCompleted && ayahSteps < 30) {
    ayahTarget = h.ctx.describeCurrentSabaqUnit(ayahTarget).nextState();
    ayahSteps += 1;
  }
  assert.ok(ayahSteps > 0 && ayahSteps < 30);
  assert.strictEqual(ayahTarget.surahNum, 25);
  assert.strictEqual(ayahTarget.startAyah, 21);
  assert.strictEqual(ayahTarget.targetCompleted, true);
  assert.strictEqual(ayahTarget.sabaqCompleted, false);

  const singlePage = h.ctx.normalizeSabaqState({ rangeStartPage: 7, rangeEndPage: 7, currentPage: 1 });
  assert.strictEqual(singlePage.currentPage, 7);
  assert.strictEqual(singlePage.rangeEndPage, 7);
  assert.ok(htmlSource.includes('toggleNotificationSurfaceBtn'));
  assert.ok(htmlSource.includes('toggleWidgetSurfaceBtn'));
  assert.ok(htmlSource.includes('selSabaqEndSurah'));
});

test('35. Setelan layar kunci/widget punya langkah jelas dan semua dropdown konsisten', () => {
  assert.ok(htmlSource.includes('id="openNotificationSettingsBtn"'), 'Ada tombol untuk membuka setelan notifikasi Android.');
  assert.ok(htmlSource.includes('id="addHomeWidgetBtn"'), 'Ada tombol untuk memasang widget dari aplikasi.');
  assert.ok(htmlSource.includes('id="widgetActivationHint"'), 'Status aktivasi widget tampil terpisah dari aksi pemasangan.');
  assert.ok(htmlSource.includes('id="openDeviceDateTimeSettingsBtn"'), 'Setelan waktu HP dapat dibuka dari aplikasi.');
  assert.ok(htmlSource.includes('tekan lama area kosong di layar utama'), 'Tersedia cara manual pada launcher yang tidak mendukung pin widget.');
  assert.ok(htmlSource.includes('id="quranSurahPicker" class="dark-select quran-surah-select"'), 'Dropdown surah juga memakai komponen pilihan kustom.');
  assert.ok(htmlSource.includes('scrollbar-gutter: stable;'), 'Konten scroll memiliki ruang sisi yang stabil.');
  assert.ok(htmlSource.includes('--font-sans: "Rutinitas Inter",'), 'Font UI tidak mengikuti font tulisan tangan sistem.');
  assert.ok(fs.existsSync(path.join(path.dirname(htmlPath), 'fonts', 'Inter-Regular.otf')), 'Font antarmuka tersedia offline di dalam paket.');
  assert.ok(fs.existsSync(path.join(path.dirname(htmlPath), 'fonts', 'Inter-OFL.txt')), 'Lisensi font ikut didistribusikan.');

  const markupEnd = htmlSource.indexOf('<script');
  const markupSource = markupEnd >= 0 ? htmlSource.slice(0, markupEnd) : htmlSource;
  const selects = [...markupSource.matchAll(/<select\b([^>]*)>/g)];
  assert.ok(selects.length > 0);
  for (const [, attrs] of selects) {
    assert.match(attrs, /class="[^"]*\bdark-select\b/, 'Setiap dropdown memakai trigger dan menu kustom yang sama.');
  }
});

test('36. Jam HP dan jadwal otomatis mengikuti perubahan waktu maju maupun mundur', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T05:00:00+07:00' });
  let openTimeSettingsCalls = 0;
  h.window.RutinitasAndroid = {
    openDeviceDateTimeSettings() { openTimeSettingsCalls += 1; }
  };
  h.click('openDeviceDateTimeSettingsBtn');
  assert.strictEqual(openTimeSettingsCalls, 1, 'Tombol membuka pengaturan jam Android lewat bridge.');

  let scheduleBuilds = 0;
  const originalBuildDailySchedule = h.ctx.buildDailySchedule;
  h.ctx.buildDailySchedule = (...args) => {
    scheduleBuilds += 1;
    return originalBuildDailySchedule(...args);
  };

  h.elapseClockMs(7 * 60 * 60 * 1000);
  h.tick();
  assert.strictEqual(h.el('topLiveClock').textContent, '12:00', 'Jam maju langsung mengikuti waktu perangkat.');
  assert.strictEqual(scheduleBuilds, 1, 'Jadwal dibangun ulang setelah jam maju.');

  h.elapseClockMs(-7 * 60 * 60 * 1000);
  h.tick();
  assert.strictEqual(h.el('topLiveClock').textContent, '05:00', 'Jam mundur langsung mengikuti waktu perangkat.');
  assert.strictEqual(scheduleBuilds, 2, 'Jadwal dibangun ulang setelah jam mundur.');
});

test('37. Target hafalan baru tidak meminta murajaah halaman yang belum dihafal', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T05:00:00+07:00' });
  h.setInput('selSabaqTargetJuz', 2);

  assert.strictEqual(h.getState().sabaq.rangeStartPage, 22);
  assert.strictEqual(h.getState().sabaq.rangeEndPage, 41);
  assert.deepStrictEqual(Array.from(h.getState().sabqiHistory), [], 'Target baru memulai riwayat Sabqi yang kosong.');
  assert.strictEqual(h.getState().hifzPhase, 3, 'Juz 2 baru tidak dianggap sebagai hafalan lebih dari 7 Juz.');
  assert.deepStrictEqual(Array.from(h.ctx.getLVManzilContext().pool), [], 'Manzil kosong sebelum ada halaman target yang selesai.');

  h.engine.setState({
    sabaq: { ...h.getState().sabaq, currentPage: 30 },
    sabqiHistory: [23, 24, 25, 26, 27, 28, 29].map((page) => ({ unitShort: `Halaman ${page}`, approxPage: page, mode: 'one_page' }))
  });
  assert.deepStrictEqual(Array.from(h.ctx.getLVManzilContext().pool), [22], 'Murajaah hanya memuat halaman target yang sudah lulus.');

  h.setInput('selSabaqTargetJuz', 18);
  assert.strictEqual(h.getState().hifzPhase, 3, 'Target Juz 18 tidak menganggap Juz 1–17 sudah dihafal.');
  assert.deepStrictEqual(Array.from(h.getState().sabqiHistory), []);
  assert.deepStrictEqual(Array.from(h.ctx.getLVManzilContext().pool), []);
  h.engine.setState({ priorJuzList: [1] });
  assert.deepStrictEqual(
    Array.from(h.ctx.getLVManzilContext().pool),
    Array.from({ length: 21 }, (_, i) => i + 1),
    'Juz lama tetap masuk murajaah setelah pengguna memilihnya secara eksplisit.'
  );

  const staleHistory = Array.from({ length: 7 }, (_, i) => i + 15)
    .map((page) => ({ unitShort: `Halaman ${page}`, approxPage: page, mode: 'one_page' }));
  const reloaded = createAppHarness({
    fixedNowIso: '2026-11-16T05:00:00+07:00',
    initialStorage: {
      [STORAGE_KEY]: {
        schemaVersion: 10,
        sabaq: {
          mode: 'one_page', targetJuz: 2, rangeStartPage: 22, rangeEndPage: 41, currentPage: 22,
          targetStartSurahNum: 2, targetStartAyah: 142, targetEndSurahNum: 2, targetEndAyah: 252
        },
        sabqiHistory: staleHistory,
        conservedPartialSpans: { 18: { page: 18, label: 'Baris 1–4' } },
        priorJuzList: []
      }
    }
  });
  assert.deepStrictEqual(Array.from(reloaded.getState().sabqiHistory), [], 'Riwayat salah dari versi lama dibersihkan saat dimuat.');
  assert.strictEqual(reloaded.getState().conservedPartialSpans[18], undefined, 'Sisa halaman di luar target ikut dibersihkan.');
  assert.deepStrictEqual(Array.from(reloaded.ctx.getLVManzilContext().pool), []);
});

test('38. Notifikasi layar kunci menyiapkan target Subuh ringkas saat mode tidur', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T22:15:00+07:00' });
  const updates = [];
  h.window.RutinitasAndroid = {
    updateLockScreen(...args) { updates.push(args); }
  };
  h.engine.render();

  assert.strictEqual(updates.length, 1, 'Status lock screen dikirim saat preview jadwal tidur ditampilkan.');
  assert.strictEqual(updates[0][0], 'Hafalan Subuh');
  assert.ok(updates[0][1].length > 0, 'Target Subuh tersedia sebagai satu baris ringkas.');
  assert.ok(updates[0][2] > 0, 'Countdown menuju jadwal bangun ikut dikirim.');
  assert.strictEqual(updates[0][3], true, 'Notifikasi tetap aktif selama preview menuju Subuh.');
  assert.strictEqual(updates[0][4], true, 'Countdown dapat ditampilkan oleh notifikasi Android.');
  const androidSource = fs.readFileSync(path.resolve(__dirname, '../app/src/main/java/com/rutinitas/islamic/NativeMainActivity.java'), 'utf8');
  assert.match(androidSource, /\.setContentTitle\(title\)\.setContentText\(content\)/, 'Judul dan target notifikasi tetap ringkas.');
  assert.doesNotMatch(androidSource, /Notification\.BigTextStyle/, 'Notifikasi lock screen tetap satu kartu ringkas.');
  assert.match(androidSource, /\.setVisibility\(Notification\.VISIBILITY_PRIVATE\)\.setPublicVersion\(publicVersion\)/, 'Tersedia versi ringkas untuk lock screen dengan privasi aktif.');
  assert.match(androidSource, /NOTIFICATION_CHANNEL = "active_routine_v2"/, 'Channel Android tetap dipertahankan.');
});

test('39. Lompat Al-Qur’an menerima angka saja dan pemisah yang tersedia di keyboard', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T08:00:00+07:00' });
  const ayahOnly = h.ctx.parseQuranJumpReference('19', 19);
  assert.strictEqual(ayahOnly.surah, 19, 'Angka tunggal mengikuti surah yang dipilih.');
  assert.strictEqual(ayahOnly.ayah, 19);

  ['19,1', '19.1', '19 1', '19-1', '19/1', '19:1'].forEach((input) => {
    const parsed = h.ctx.parseQuranJumpReference(input, 1);
    assert.ok(parsed, `Referensi ${input} dapat dibaca.`);
    assert.strictEqual(parsed.surah, 19);
    assert.strictEqual(parsed.ayah, 1);
  });
  assert.strictEqual(h.ctx.parseQuranJumpReference('Maryam 1', 19), null, 'Teks lain ditolak dengan aman.');
  assert.ok(htmlSource.includes('placeholder="Ayat / Surah,ayat · 19,1"'), 'Placeholder tidak meminta tanda titik dua.');
});

test('40. Target bacaan tampil per baris dan panduan layar kunci tersedia di Pengaturan', () => {
  const h = createAppHarness();
  const target = h.engine.formatReadingTargetForDisplay(
    'Juz 2 (Al-Baqarah 2:142 → Al-Baqarah 2:252) · Halaman 22 · Baris 1–4'
  );
  assert.strictEqual(target, [
    'Juz 2 · Halaman 22–41',
    'Mulai · Al-Baqarah 2:142',
    'Selesai · Al-Baqarah 2:252',
    'Hari ini · Halaman 22 · Baris 1–4'
  ].join('\n'));
  assert.strictEqual(
    h.engine.formatReadingTargetForDisplay('Target halaman 22–41 · Halaman 22 · Baris 1–4'),
    'Target · Halaman 22–41\nHari ini · Halaman 22 · Baris 1–4'
  );
  assert.ok(htmlSource.includes('CARA MENAMPILKAN DI LAYAR KUNCI'));
  assert.ok(htmlSource.includes('Tampilkan konten'));
});

test('41. Target Sabaq menyatu, ringkasan berulang dihapus, Manzil ringkas, dan opsi dropdown tidak terpotong', () => {
  const targetCardStart = htmlSource.indexOf('<div class="sabaq-target-card">');
  const targetControlsEnd = htmlSource.indexOf('<div id="sabaqPageControls"', targetCardStart);
  const targetCard = htmlSource.slice(targetCardStart, targetControlsEnd);
  assert.ok(targetCard.includes('id="selSabaqMode"'), 'Pilihan target per hari ada di satu kartu.');
  assert.ok(targetCard.includes('id="selSabaqTargetJuz"'), 'Cakupan mushaf/Juz ada di kartu target yang sama.');
  assert.doesNotMatch(htmlSource, /id="sabaqRangeSummary"|id="sabaqDetachedSummary"/, 'Teks ringkasan yang berulang sudah dihilangkan.');

  const h = createAppHarness();
  const manzilTarget = h.ctx.formatManzilReadingTarget(
    'Juz 2 · halaman 22–31',
    'Halaman 22–31',
    'Al-Baqarah 2:142 → Akhir halaman 31'
  );
  assert.strictEqual(manzilTarget, 'Juz 2 · halaman 22–31\nBacaan · Al-Baqarah 2:142 → Akhir halaman 31');

  h.engine.syncSettingsDrawerUI();
  assert.strictEqual(h.el('sabaqPageRangeFields').classList.contains('hidden'), false);
  h.engine.setState({ sabaq: { ...h.getState().sabaq, mode: 'ayah' } });
  h.engine.syncSettingsDrawerUI();
  assert.strictEqual(h.el('sabaqPageRangeFields').classList.contains('hidden'), true, 'Batas halaman tidak tampil ganda pada mode target ayat.');

  const optionCss = htmlSource.match(/\.cselect-option-label\s*\{([^}]*)\}/);
  assert.ok(optionCss, 'Gaya teks pilihan dropdown ditemukan.');
  assert.match(optionCss[1], /white-space:\s*normal/);
  assert.doesNotMatch(optionCss[1], /text-overflow:\s*ellipsis/);
  assert.ok(htmlSource.includes('const availableWidth = Math.max(0, vw - sideGap * 2)'), 'Lebar menu dropdown dibatasi sesuai lebar layar.');
});

test('42. Riwayat Sabqi lama disaring dan target bacaan ditampilkan dengan titik mulai/selesai', () => {
  const h = createAppHarness({ fixedNowIso: '2026-11-16T05:00:00+07:00' });
  const sabaq = h.ctx.normalizeSabaqState({
    mode: 'quarter_page', targetJuz: 10, rangeStartPage: 182, rangeEndPage: 200,
    currentPage: 200, subSlot: 0
  });
  const history = h.ctx.buildBackfilledSabqiHistory(sabaq, 7);
  assert.strictEqual(history[0].unitShort, 'Halaman 198 (Baris 5–8)');
  assert.strictEqual(history[history.length - 1].unitShort, 'Halaman 199 (Baris 13–15)');
  assert.strictEqual(h.ctx.formatRolling7DaySabqi(history, sabaq), [
    'Mulai · Juz 10 · Halaman 198 (Baris 5–8)',
    'Selesai · Juz 10 · Halaman 199 (Baris 13–15)',
    '7 hari terakhir'
  ].join('\n'));

  const current = h.ctx.normalizeSabaqState({ mode: 'one_page', rangeStartPage: 1, rangeEndPage: 604, currentPage: 22 });
  const stale = [198, 199].map((page) => ({ mode: 'one_page', approxPage: page, unitShort: `Halaman ${page}` }));
  assert.deepStrictEqual(Array.from(h.ctx.getValidSabqiHistory(stale, current)), [], 'Halaman yang belum dicapai tidak masuk Sabqi.');
  assert.match(h.ctx.formatRolling7DaySabqi(stale, current), /Belum ada Sabqi/);

  const reloaded = createAppHarness({
    fixedNowIso: '2026-11-16T05:00:00+07:00',
    initialStorage: {
      [STORAGE_KEY]: {
        schemaVersion: 10,
        sabaq: { mode: 'one_page', rangeStartPage: 1, rangeEndPage: 604, currentPage: 22 },
        sabqiHistory: stale
      }
    }
  });
  assert.deepStrictEqual(Array.from(reloaded.getState().sabqiHistory), [], 'Riwayat usang dibersihkan saat state dimuat ulang.');
});

test('43. Jadwal Subuh tidak didorong melewati batas malam dan peringatan bentrok tetap ringkas', () => {
  const h = createAppHarness({
    fixedNowIso: '2026-11-16T04:00:00+07:00',
    initialStorage: {
      [STORAGE_KEY]: {
        schemaVersion: 10,
        completedDate: '2026-11-16',
        customRoutines: [
          { id: 'fixed_morning', title: 'Agenda Pagi', anchor: { type: 'fixed', time: '05:00' }, windowMins: 480, durationMins: 480, isFlexible: false, conflictPolicy: 'strict', daysOfWeek: [1], enabled: true },
          { id: 'fixed_day', title: 'Agenda Siang', anchor: { type: 'fixed', time: '13:00' }, windowMins: 480, durationMins: 480, isFlexible: false, conflictPolicy: 'strict', daysOfWeek: [1], enabled: true }
        ]
      }
    }
  });
  const schedule = h.engine.getSchedule(new Date('2026-11-16T04:00:00+07:00'));
  const hifz = schedule.blocks.find((block) => block.id === 'primary_hifz');
  assert.strictEqual(hifz.start, '05:00', 'Hafalan Subuh tidak ikut digeser ke malam saat slot pagi penuh.');
  assert.strictEqual(hifz.end, '06:30');
  assert.ok(schedule.conflicts.some((message) => message.includes('tidak digeser melewati evaluasi 21:15')));

  h.click('openScheduleSheetBtn');
  const summary = h.el('unifiedConflictBox').textContent;
  assert.match(summary, /BENTROK JADWAL/);
  assert.match(summary, /Lihat baris TUNDA|Periksa waktu aktivitas/);
  assert.doesNotMatch(summary, /Agenda Pagi|Agenda Siang|Hafalan Al-Quran Subuh \(/);
  assert.ok(summary.length < 180, 'Peringatan tidak lagi menggabungkan seluruh jadwal menjadi satu paragraf panjang.');
});

// ===========================================================================
// RUNNER
// ===========================================================================

(async () => {
  let passed = 0;
  let failed = 0;
  for (const t of tests) {
    try {
      await t.fn();
      passed += 1;
      console.log(`PASS  ${t.name}`);
    } catch (err) {
      failed += 1;
      console.error(`FAIL  ${t.name}`);
      console.error(err && err.stack ? err.stack : err);
    }
  }
  console.log(`\nRingkasan Pengujian Terpadu Rutin: ${passed}/${tests.length} LULUS, ${failed} GAGAL.`);
  process.exit(failed > 0 ? 1 : 0);
})();
