/*
 * Página anfitriona del visor (PLAN §7.5). Sin eval, sin scripts en línea, sin recursos externos.
 * Lee ?kind=…&nonce=…, descarga /doc/<nonce>, lo renderiza y avisa a Kotlin con mensajes de una lista cerrada:
 *   ready | rendered | error:<código> | sheets:<n>      (longitud máxima 64)
 * Todos los datos del archivo se insertan con textContent, nunca con innerHTML.
 */
(function () {
  'use strict';

  var MAX_ROWS = 2000;
  var MAX_COLS = 100;
  var MAX_SHEETS = 50;
  var TIMEOUT_MS = 30000;
  var HEX = /^[0-9a-fA-F]{6}$/;
  var KINDS = { xlsx: 'sheet', xls: 'sheet', ods: 'sheet', csv: 'sheet', docx: 'docx', pptx: 'pptx' };

  var params = new URLSearchParams(location.search);
  var kind = params.get('kind') || '';
  var nonce = params.get('nonce') || '';
  var root = document.getElementById('root');
  var settled = false;
  var uncaught = false;
  var timer = 0;

  function post(message) {
    if (typeof message !== 'string' || message.length > 64) return;
    try {
      var bridge = window.lectorBridge;
      if (bridge && typeof bridge.postMessage === 'function') bridge.postMessage(message);
      else document.title = message; // respaldo: Kotlin lo lee en onReceivedTitle
    } catch (e) { /* sin canal disponible */ }
  }

  function fail(code) {
    if (settled) return;
    settled = true;
    clearTimeout(timer);
    post('error:' + code);
  }

  function succeed() {
    if (settled) return;
    settled = true;
    clearTimeout(timer);
    post('rendered');
  }

  function codeError(code) {
    var e = new Error(code);
    e.lectorCode = code;
    return e;
  }

  function applyTheme() {
    var map = { bg: '--bg', surface: '--surface', sunken: '--sunken', text: '--text', muted: '--muted', accent: '--accent' };
    Object.keys(map).forEach(function (key) {
      var value = params.get(key);
      if (value && HEX.test(value)) document.documentElement.style.setProperty(map[key], '#' + value);
    });
    var line = params.get('line');
    if (line && HEX.test(line)) {
      // El token `line` es el color del texto al 8–9 %.
      var r = parseInt(line.substr(0, 2), 16), g = parseInt(line.substr(2, 2), 16), b = parseInt(line.substr(4, 2), 16);
      document.documentElement.style.setProperty('--line', 'rgba(' + r + ',' + g + ',' + b + ',0.12)');
    }
    ['top', 'bottom'].forEach(function (key) {
      var v = parseInt(params.get(key === 'top' ? 'ti' : 'bi'), 10);
      if (isFinite(v) && v >= 0 && v <= 400) document.documentElement.style.setProperty('--' + key, v + 'px');
    });
    var lang = params.get('lang');
    if (lang && /^[a-z]{2}$/.test(lang)) document.documentElement.lang = lang;
  }

  function notice(text) {
    var div = document.createElement('div');
    div.className = 'notice';
    div.textContent = text;
    root.insertBefore(div, root.firstChild);
  }

  function loadDocument() {
    return fetch('/doc/' + encodeURIComponent(nonce), { credentials: 'omit', cache: 'no-store' }).then(function (response) {
      if (!response.ok) throw codeError('corrupt');
      return response.arrayBuffer();
    });
  }

  function loadScript(src) {
    return new Promise(function (resolve, reject) {
      var script = document.createElement('script');
      script.src = src;
      script.onload = resolve;
      script.onerror = function () { reject(codeError('unsupported')); };
      document.head.appendChild(script);
    });
  }

  function decodeText(buffer) {
    try {
      return new TextDecoder('utf-8', { fatal: true }).decode(buffer);
    } catch (e) {
      return new TextDecoder('iso-8859-1').decode(buffer);
    }
  }

  // ------------------------------------------------------------------ hojas de cálculo (SheetJS)
  function renderSheets(buffer) {
    return loadScript('vendor/xlsx.full.min.js').then(function () {
      var options = { dense: true, cellFormula: false, cellHTML: false, cellStyles: false, sheetRows: MAX_ROWS + 1 };
      var workbook = kind === 'csv'
        ? XLSX.read(decodeText(buffer), Object.assign({ type: 'string' }, options))
        : XLSX.read(buffer, Object.assign({ type: 'array' }, options));
      var names = (workbook.SheetNames || []).slice(0, MAX_SHEETS);
      if (names.length === 0) throw codeError('corrupt');

      var sheets = [];
      var truncated = false;

      function build(index) {
        var ws = workbook.Sheets[names[index]];
        var host = document.createElement('div');
        host.className = 'sheet';
        host.hidden = true;
        var ref = ws && ws['!ref'] ? XLSX.utils.decode_range(ws['!ref']) : null;
        if (!ref) {
          var empty = document.createElement('div');
          empty.className = 'empty';
          host.appendChild(empty);
          return host;
        }
        var rows = Math.min(ref.e.r - ref.s.r + 1, MAX_ROWS);
        var cols = Math.min(ref.e.c - ref.s.c + 1, MAX_COLS);
        if (ref.e.r - ref.s.r + 1 > MAX_ROWS || ref.e.c - ref.s.c + 1 > MAX_COLS) truncated = true;
        var data = ws['!data'] || [];
        var table = document.createElement('table');
        var head = document.createElement('tr');
        var corner = document.createElement('th');
        corner.className = 'row corner';
        head.appendChild(corner);
        for (var c = 0; c < cols; c++) {
          var th = document.createElement('th');
          th.textContent = XLSX.utils.encode_col(ref.s.c + c);
          head.appendChild(th);
        }
        table.appendChild(head);
        for (var r = 0; r < rows; r++) {
          var tr = document.createElement('tr');
          var rh = document.createElement('th');
          rh.className = 'row';
          rh.textContent = String(ref.s.r + r + 1);
          tr.appendChild(rh);
          var line = data[ref.s.r + r] || [];
          for (var cc = 0; cc < cols; cc++) {
            var cell = line[ref.s.c + cc];
            var td = document.createElement('td');
            if (cell) {
              var text = cell.w != null ? String(cell.w) : (cell.v != null ? String(cell.v) : '');
              td.textContent = text;
              if (cell.t === 'n') td.className = 'num';
              else if (cell.t === 'b') td.className = 'bool';
            }
            tr.appendChild(td);
          }
          table.appendChild(tr);
        }
        host.appendChild(table);
        return host;
      }

      function show(index) {
        if (index < 0 || index >= names.length) return;
        if (!sheets[index]) {
          sheets[index] = build(index);
          root.appendChild(sheets[index]);
        }
        sheets.forEach(function (s) { if (s) s.hidden = true; });
        sheets[index].hidden = false;
        window.scrollTo(0, 0);
      }

      show(0);
      if (truncated && params.get('notice')) notice(String(params.get('notice')).slice(0, 200));
      window.lectorApp = {
        showSheet: function (i) { show(i | 0); },
        sheetNames: function () { return JSON.stringify(names.map(function (n) { return String(n).slice(0, 60); })); }
      };
      post('sheets:' + names.length);
    });
  }

  // ------------------------------------------------------------------ Word (docx-preview + JSZip)
  function renderDocx(buffer) {
    return loadScript('vendor/jszip.min.js')
      .then(function () { return loadScript('vendor/docx-preview.min.js'); })
      .then(function () {
        return docx.renderAsync(buffer, root, null, {
          className: 'docx', inWrapper: true, breakPages: true, useBase64URL: false,
          ignoreLastRenderedPageBreak: true, renderHeaders: true, renderFooters: true,
          renderFootnotes: true, renderEndnotes: true, renderAltChunks: false, experimental: false
        });
      })
      .then(function () {
        function fit() {
          var wrapper = root.querySelector('.docx-wrapper');
          var page = root.querySelector('section.docx');
          if (!wrapper || !page) return;
          wrapper.style.zoom = '1';
          var width = page.getBoundingClientRect().width + 16;
          var available = document.documentElement.clientWidth;
          wrapper.style.zoom = width > available ? String(available / width) : '1';
        }
        fit();
        window.addEventListener('resize', fit);
      });
  }

  // ------------------------------------------------------------------ PowerPoint (vista aproximada)
  function renderPptx(buffer) {
    document.body.classList.add('pptx');
    return import('./vendor/pptx-renderer.browser.es.js').then(function (m) {
      return m.PptxViewer.open(buffer, root, {
        fitMode: 'contain', externalMedia: 'block', pdfjs: false, lazyMedia: true, lazySlides: true,
        zipLimits: m.RECOMMENDED_ZIP_LIMITS,
        listOptions: { windowed: true, initialSlides: 3, batchSize: 3 }
      });
    });
  }

  function classify(error) {
    var code = error && error.lectorCode;
    if (code) return code;
    var message = String(error && error.message || error || '');
    if (/memory|allocation/i.test(message)) return 'memory';
    return 'corrupt';
  }

  function main() {
    applyTheme();
    var family = KINDS[kind];
    if (!family || !/^[0-9a-f]{8,64}$/.test(nonce)) {
      fail('unsupported');
      return;
    }
    timer = setTimeout(function () { fail(uncaught ? 'render' : 'timeout'); }, TIMEOUT_MS);
    post('ready');
    loadDocument()
      .then(function (buffer) {
        if (family === 'sheet') return renderSheets(buffer);
        if (family === 'docx') return renderDocx(buffer);
        return renderPptx(buffer);
      })
      .then(succeed)
      .catch(function (error) { fail(classify(error)); });
  }

  window.addEventListener('error', function () { uncaught = true; });
  window.addEventListener('unhandledrejection', function (event) {
    uncaught = true;
    if (event && typeof event.preventDefault === 'function') event.preventDefault();
  });

  try {
    main();
  } catch (error) {
    fail(classify(error));
  }
})();
