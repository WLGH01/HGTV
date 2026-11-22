// Android TV 遥控器导航增强（只由 TV flavor 注入）。
// 网页里的卡片是 <article class="card" onclick>，默认不可聚焦；
// 这里补 tabindex、方向键空间导航、Enter 点击，并给焦点加高亮。
(function () {
  if (window.__hgtvTvNav) return;
  window.__hgtvTvNav = true;

  var style = document.createElement('style');
  style.textContent =
    ':focus{outline:3px solid #f5c518 !important;outline-offset:3px;border-radius:8px}' +
    '.card:focus{transform:scale(1.02);transition:transform .12s ease}' +
    'html{scroll-behavior:smooth}';
  document.head.appendChild(style);

  var SELECTOR = '.card, button, a, input, select, textarea, [tabindex]';

  function visible(el) {
    var r = el.getBoundingClientRect();
    if (r.width < 4 || r.height < 4) return false;
    var s = window.getComputedStyle(el);
    return s.visibility !== 'hidden' && s.display !== 'none' && Number(s.opacity) !== 0;
  }

  function focusables() {
    var all = document.querySelectorAll(SELECTOR);
    var out = [];
    for (var i = 0; i < all.length; i++) {
      if (visible(all[i])) out.push(all[i]);
    }
    return out;
  }

  function ensureTabindex() {
    var cards = document.querySelectorAll('.card');
    for (var i = 0; i < cards.length; i++) {
      if (!cards[i].hasAttribute('tabindex')) cards[i].setAttribute('tabindex', '0');
    }
  }

  function move(dir) {
    var list = focusables();
    if (!list.length) return;
    var cur = document.activeElement;
    if (!cur || list.indexOf(cur) < 0) {
      list[0].focus();
      return;
    }
    var a = cur.getBoundingClientRect();
    var ax = a.left + a.width / 2;
    var ay = a.top + a.height / 2;
    var best = null;
    var bestScore = Infinity;
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      if (el === cur) continue;
      var b = el.getBoundingClientRect();
      var dx = (b.left + b.width / 2) - ax;
      var dy = (b.top + b.height / 2) - ay;
      var primary;
      var secondary;
      if (dir === 'left') {
        if (dx > -4) continue;
        primary = -dx; secondary = Math.abs(dy);
      } else if (dir === 'right') {
        if (dx < 4) continue;
        primary = dx; secondary = Math.abs(dy);
      } else if (dir === 'up') {
        if (dy > -4) continue;
        primary = -dy; secondary = Math.abs(dx);
      } else {
        if (dy < 4) continue;
        primary = dy; secondary = Math.abs(dx);
      }
      var score = primary + secondary * 2.5;
      if (score < bestScore) {
        bestScore = score;
        best = el;
      }
    }
    if (best) {
      best.focus();
      best.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'smooth' });
    }
  }

  document.addEventListener('keydown', function (e) {
    var key = e.key;
    var active = document.activeElement;
    var tag = (active && active.tagName) || '';
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;

    if (key === 'ArrowLeft' || key === 'ArrowRight' || key === 'ArrowUp' || key === 'ArrowDown') {
      // 视频聚焦时保留左右键的“上一集/下一集”语义
      if (active && active.id === 'video') return;
      e.preventDefault();
      e.stopPropagation();
      move(key.replace('Arrow', '').toLowerCase());
      return;
    }

    if (key === 'Enter' || key === ' ') {
      if (active && active.classList && active.classList.contains('card')) {
        e.preventDefault();
        e.stopPropagation();
        active.click();
      }
    }
  }, true);

  try {
    new MutationObserver(ensureTabindex).observe(document.body, { childList: true, subtree: true });
  } catch (err) {}

  ensureTabindex();
  setTimeout(function () {
    if (document.activeElement === document.body || document.activeElement === document.documentElement) {
      var first = document.querySelector('.card');
      if (first) {
        first.setAttribute('tabindex', '0');
        first.focus();
      }
    }
  }, 500);
})();
