// 黄果解析站 · 共享脚本
//
// 架构改为浏览器直连源站，Worker 只做封面解密兜底。
//
// 为什么改成直连：源站所有接口和 CDN 都返回 Access-Control-Allow-Origin 星号，
// 浏览器完全可以自己解析。Cloudflare Workers 免费档的 10 万次每天是按账号算的，
// 多开 Worker 不会增加额度，只会把同一份额度分着用。
// 直连后 Worker 只承担封面的 AES 解密，请求量能降一个数量级。
const SITE = 'https://huangguoai.io';
// Worker 只做兜底。留一个就够，额度是账号级的。
const WORKER = 'https://huangguo-parser.13681235735.workers.dev';
const PAGE_SIZE = 50; // 一页 50 条

// 封面解密参数，与 Worker 里一致
const IMG_KEY = 'f5d965df75336270';
const IMG_IV = '97b60394abc2fbe1';

const UA_HEADERS = { 'Accept': 'application/json, text/plain, */*' };

function esc(s) {
  return String(s || '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// ===== 封面：源站是 AES-CBC 加密流，浏览器解密后转 dataURL =====
const coverCache = new Map();

function looksLikeImage(u) {
  if (!u) return false;
  if (/cover-placeholder|logo-huangguo|\.svg|mc\.yandex|google-analytics|pixel|tracker|\/icons\//i.test(u)) return false;
  return /\.(jpg|jpeg|png|webp)(\?|$)/i.test(u);
}

function imageMime(b) {
  if (!b || b.length < 4) return '';
  if (b[0] === 0xff && b[1] === 0xd8) return 'image/jpeg';
  if (b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e) return 'image/png';
  if (b[0] === 0x52 && b[1] === 0x49 && b[8] === 0x57) return 'image/webp';
  if (b[0] === 0x47 && b[1] === 0x49 && b[2] === 0x46) return 'image/gif';
  return '';
}

function toDataUrl(body, type) {
  let bin = '';
  for (let i = 0; i < body.length; i += 8192) bin += String.fromCharCode.apply(null, body.subarray(i, i + 8192));
  return 'data:' + type + ';base64,' + btoa(bin);
}

async function decryptCover(url) {
  if (!looksLikeImage(url)) return '';
  if (coverCache.has(url)) return coverCache.get(url);
  try {
    const r = await fetch(url);
    if (!r.ok) return '';
    const buf = new Uint8Array(await r.arrayBuffer());
    if (!buf.length) return '';
    // 有些封面/正文图本来就是明文，直接转 dataURL，避免长度不是 16 倍数时被误判失败
    const direct = imageMime(buf);
    if (direct) {
      const dataUrl = toDataUrl(buf, direct);
      coverCache.set(url, dataUrl);
      return dataUrl;
    }
    if (buf.length % 16) return '';
    const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(IMG_KEY), 'AES-CBC', false, ['decrypt']);
    const plain = new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-CBC', iv: new TextEncoder().encode(IMG_IV) }, key, buf));
    const pad = plain[plain.length - 1];
    const body = (pad > 0 && pad <= 16 && plain.slice(-pad).every((x) => x === pad)) ? plain.slice(0, -pad) : plain;
    const type = imageMime(body);
    if (!type) return '';
    const dataUrl = toDataUrl(body, type);
    coverCache.set(url, dataUrl);
    return dataUrl;
  } catch (e) { return ''; }
}

// ===== 反爬签名 =====
// Worker 用同一密钥与算法校验，窗口 5 分钟。密钥在前端可见，
// 目的是挡掉不执行 JS 的脚本爬虫，不是对抗有心的逆向者。
const HG_SIG_SECRET = 'hg-dx150-2026-11-w7Qm4pLz';

async function hmacHex(msg) {
  const key = await crypto.subtle.importKey(
    'raw', new TextEncoder().encode(HG_SIG_SECRET),
    { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
  );
  const sig = new Uint8Array(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(msg)));
  return Array.from(sig).map((b) => b.toString(16).padStart(2, '0')).join('');
}

// 对 path+query 生成签名，必须与实际请求的 URL 完全一致
async function signHeaders(pathWithQuery) {
  const win = Math.floor(Date.now() / 1000 / 300);
  const sig = await hmacHex(win + '\n' + pathWithQuery);
  return { 'X-HG-Ts': String(win * 300), 'X-HG-Sig': sig };
}

function mediaUrl(p) {
  if (!p) return '';
  if (/^https?:\/\//i.test(p)) return p;
  if (p.charAt(0) === '/') return SITE + p;
  return p;
}

// 生成 img 标签。源站封面是密文，直接放 src 会裂图，
// 所以把地址放进 data-src，等解密完再替换 src。
function imgTag(url, alt, cls) {
  const real = mediaUrl(url);
  const ph = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="240" height="320"><rect width="240" height="320" fill="#1a1a1a"/><text x="120" y="165" fill="#555" font-size="15" text-anchor="middle">黄果</text></svg>'
  );
  const klass = cls ? ' class="' + cls + '"' : '';
  const attr = real ? ' data-src="' + esc(real) + '"' : '';
  return '<img src="' + ph + '"' + attr + klass + ' loading="lazy" alt="' + esc(alt || '') + '">';
}

// ===== 路径翻译 =====
// 页面沿用旧 Worker 的接口风格，这里翻译成源站的真实路径。
// 好处是所有 html 一行都不用改，风险面最小。
function translatePath(path) {
  let p = path.split('?')[0];
  const qs = path.indexOf('?') >= 0 ? path.slice(path.indexOf('?')) : '';
  let m;
  // /category/{tid}  ->  /videos/category/{tid}
  if ((m = p.match(/^\/category\/([^\/]+)$/))) return '/videos/category/' + m[1] + qs;
  // /search/{kw}  ->  /search?q={kw}&...
  if ((m = p.match(/^\/search\/(.+)$/))) {
    const sep = qs ? '&' : '?';
    return '/search?q=' + m[1] + (qs ? sep + qs.slice(1) : '');
  }
  // /detail/{id}  ->  /videos/{id}
  if ((m = p.match(/^\/detail\/([^\/]+)$/))) return '/videos/' + m[1];
  // /topics?slug=xxx  ->  /topics/xxx  专辑内视频走详情接口
  if (p === '/topics') {
    const sm = qs.match(/[?&]slug=([^&]+)/);
    if (sm) return '/topics/' + sm[1] + qs;
    return '/topics' + qs;
  }
  // /post/{id}  ->  /posts/{id}  吃瓜文章详情
  if ((m = p.match(/^\/post\/([^\/]+)$/))) return '/posts/' + m[1];
  // /chigua?...  ->  /posts?...  站点把吃瓜放在 posts 下
  if (p === '/chigua') return '/posts' + qs;
  // 其余路径源站同构，原样透传
  return p + qs;
}

// Worker 的返回格式与源站不同，这里把源站原始响应包成页面期望的形状。
// 页面读的是 {list:[...], total, page}，源站给的是 {data:{items, pagination}}。
function shapeResponse(path, raw) {
  const p = path.split('?')[0];
  if (p === '/' || p === '/categories') return raw;
  const d = raw && raw.data;
  if (!d) return raw;

  // 详情：保留 episodes（选集的关键）
  if (/^\/detail\//.test(p)) {
    const item = fromItem(d);
    item.episodes = episodesOf(d);
    if (item.episodes.length) item.vod_episode_count = item.episodes.length;
    item.source = 'api';
    return { list: [item], total: 1, source: 'api' };
  }

  // 吃瓜文章详情
  if (/^\/post\//.test(p)) return { list: [fromPost(d)], total: 1, source: 'api' };

  // 专辑：/topics 是专辑列表；/topics/{slug} 返回 {topic, videos}
  if (p === '/topics') {
    if (d.videos) {
      const pg = d.videos.pagination || {};
      return {
        list: (d.videos.items || []).map(fromItem),
        page: pg.page || 1, pagecount: pg.pages || 1, total: pg.total || 0, source: 'api'
      };
    }
    const items = d.items || [];
    return {
      list: items.map((t) => ({
        vod_id: 'topic_' + t.slug,
        vod_name: cleanTitle(t.title),
        vod_pic: mediaUrl(t.cover || ''),
        vod_remarks: (t.video_count || t.count || 0) + ' 部',
        vod_desc: t.intro || '',
        vod_tags: [],
        vod_slug: t.slug
      })),
      page: 1, pagecount: 1, total: items.length, source: 'api'
    };
  }

  // 吃瓜列表（源站 /posts）：文章不是视频，标记 post_ 前缀，交给吃瓜详情页
  if (p === '/chigua') {
    const items = d.items || [];
    const pg = d.pagination || {};
    return {
      list: items.map(fromPost),
      page: pg.page || 1, pagecount: pg.pages || 1, total: pg.total || items.length, source: 'api'
    };
  }

  // 分类 / 搜索 / 榜单：items -> list，并补上分页信息
  if (d.items) {
    const pg = d.pagination || {};
    const map = /^\/ranks\//.test(p) ? rankItem : fromItem;
    return {
      list: d.items.map(map),
      page: pg.page || 1, pagecount: pg.pages || 1, total: pg.total || d.items.length, source: 'api'
    };
  }
  if (d.id) return { list: [fromItem(d)], total: 1, source: 'api' };
  return raw;
}

// ===== 请求封装：直连源站优先，Worker 兜底 =====
async function api(path, options) {
  const target = translatePath(path);
  const url = SITE + '/api' + target;
  try {
    const r = await fetch(url, Object.assign({ headers: Object.assign({}, UA_HEADERS) }, options || {}));
    if (!r.ok) throw Error('HTTP ' + r.status);
    return shapeResponse(path, await r.json());
  } catch (e) {
    // 直连失败才回落 Worker，路径用原始的 Worker 风格。
    // Worker 有反爬签名校验，兜底请求也必须带 X-HG-Ts / X-HG-Sig，否则一律 403。
    const sig = await signHeaders('/api' + path);
    const r2 = await fetch(WORKER + '/api' + path, {
      headers: Object.assign({}, UA_HEADERS, sig, (options && options.headers) || {})
    });
    if (!r2.ok) throw e;
    return await r2.json();
  }
}

// ===== 数据归一化 =====
function cleanTitle(s) {
  let t = String(s || '').replace(/<[^>]+>/g, ' ').replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&');
  t = t.replace(/全集在线观看/g, '');
  t = t.replace(/[-–—|]\s*黄果短剧(吃瓜社区|社区)?\s*$/, '');
  t = t.replace(/黄果吃瓜侠\s*[·•]\s*\d{4}-\d{2}-\d{2}\s*[·•]\s*\S*/g, '');
  t = t.replace(/吃瓜社区/g, '');
  return t.replace(/\s+/g, ' ').trim();
}

function remarksOf(it) {
  const parts = [];
  if (it.is_finished) {
    const n = Number(it.total_episodes || it.episode_count || 0);
    parts.push(n ? '全' + n + '集' : '已完结');
  } else if (it.episode_count) parts.push('更新至' + it.episode_count + '集');
  if (it.score) parts.push(it.score + '分');
  return parts.join(' · ');
}

function fromItem(it) {
  const ec = Number(it.episode_count || 0);
  const te = Number(it.total_episodes || 0);
  return {
    vod_id: String(it.video_id || it.id || ''),
    vod_name: cleanTitle(it.title),
    vod_pic: mediaUrl(it.cover || ''),
    vod_remarks: remarksOf(it),
    vod_score: it.score || '',
    vod_desc: it.description || '',
    vod_tags: it.tags || [],
    episode_count: ec,
    total_episodes: te,
    vod_episode_count: Math.max(ec, te),
    is_finished: !!it.is_finished,
    vod_finished: !!it.is_finished,
    vod_year: (it.created_at || '').slice(0, 4),
    vod_author: (it.author && it.author.name) || '',
    vod_hot: (it.counts && it.counts.hot) || it.hot || 0,
    vod_created: it.created_at || ''
  };
}

// 详情接口自带 episodes（ep_num / duration）。这里必须完整保留：
// 旧版把详情也交给 fromItem，episodes 被丢掉，播放页就只剩默认第 1 集。
function episodesOf(it) {
  const id = String(it.video_id || it.id || '');
  const raw = Array.isArray(it.episodes) ? it.episodes : [];
  const out = [];
  const seen = {};
  raw.forEach((e) => {
    const n = String(e.ep_num != null ? e.ep_num : (e.ep != null ? e.ep : e.num)).replace(/[^0-9]/g, '');
    if (!n || seen[n]) return;
    seen[n] = 1;
    out.push({
      ep: n,
      title: '第' + n + '集',
      duration: Number(e.duration || 0),
      url: SITE + '/video/' + id + '/' + (n === '1' ? '' : 'ep-' + n + '/')
    });
  });
  // episodes 缺失时，用集数字段兜底生成，至少保证集数正确
  if (!out.length) {
    const n = Number(it.episode_count || it.total_episodes || 0);
    for (let i = 1; i <= n; i++) {
      out.push({ ep: String(i), title: '第' + i + '集', duration: 0, url: SITE + '/video/' + id + '/' + (i === 1 ? '' : 'ep-' + i + '/') });
    }
  }
  return out.sort((a, b) => Number(a.ep) - Number(b.ep));
}

function fromPost(p) {
  return {
    vod_id: 'post_' + p.id,
    vod_name: cleanTitle(p.title || p.desc || ('吃瓜 ' + p.id)),
    vod_pic: mediaUrl(p.cover || ''),
    vod_remarks: (p.publish_time || p.created_at || '').slice(0, 10),
    vod_desc: p.desc || '',
    vod_tags: p.tags || [],
    vod_content: p.content || '',
    vod_author: (p.author && p.author.name) || '',
    vod_created: p.publish_time || p.created_at || '',
    vod_post_id: p.id
  };
}

function rankItem(it) {
  const o = fromItem(it);
  const metric = [it.metric_label, it.metric_value].filter((x) => x !== undefined && x !== null && x !== '').join(' ');
  o.vod_remarks = ['#' + (it.rank || '-'), metric, o.vod_remarks].filter(Boolean).join(' · ');
  return o;
}

function cardHtml(x) {
  const score = x.vod_score ? `<i class="score">${esc(String(x.vod_score))}</i>` : '';
  const pic = x.vod_pic ? imgTag(x.vod_pic, x.vod_name) : '<span>黄果</span>';
  return `<article class="card" data-id="${esc(x.vod_id)}">`
    + `<div class="cover">${pic}${score}</div>`
    + `<div class="card-body"><h3>${esc(x.vod_name)}</h3><p>${esc(x.vod_remarks || '黄果短剧')}</p></div></article>`;
}

function gridHtml(list) {
  return (list || []).map(cardHtml).join('');
}

// 解封面。支持两种形态：
// 1. 卡片带 data-cover，图在 .cover img 里
// 2. 任意 img 直接挂 src，播放页海报和推荐位属于这种
// 两种都必须解，否则浏览器拿到密文就是一串裂图。
async function hydrateCovers(root) {
  const jobs = [];

  root.querySelectorAll('.card[data-cover]:not([data-cover-done])').forEach((card) => {
    card.setAttribute('data-cover-done', '1');
    const url = card.dataset.cover;
    const img = card.querySelector('.cover img');
    if (url && img) jobs.push({ url, img });
  });

  root.querySelectorAll('img[data-src]:not([data-src-done])').forEach((img) => {
    img.setAttribute('data-src-done', '1');
    const url = img.getAttribute('data-src');
    if (url) jobs.push({ url, img });
  });

  let idx = 0;
  async function runner() {
    while (idx < jobs.length) {
      const job = jobs[idx++];
      const dataUrl = await decryptCover(job.url);
      if (dataUrl) job.img.src = dataUrl;
    }
  }
  await Promise.all(Array.from({ length: 4 }, runner));
}

// 各页面直接用 insertAdjacentHTML 插卡片，不经过 fill，
// 所以用 MutationObserver 监听整篇文档，新卡片一出现就解封面。
function bootCoverObserver() {
  if (typeof MutationObserver === 'undefined' || !document.body) return;
  const scan = (node) => {
    if (!node || node.nodeType !== 1) return;
    hydrateCovers(node);
    // 新增节点本身就是带 data-src 的 img 时，querySelectorAll 查不到自己，
    // 单独补一次，否则单张海报会一直停在占位图。
    if (node.matches && node.matches('img[data-src]:not([data-src-done])')) {
      const url = node.getAttribute('data-src');
      node.setAttribute('data-src-done', '1');
      if (url) decryptCover(url).then((d) => { if (d) node.src = d; });
    }
    if (node.classList && node.classList.contains('card') && node.dataset.cover && !node.dataset.coverDone) {
      node.setAttribute('data-cover-done', '1');
      const img = node.querySelector('.cover img');
      if (img) decryptCover(node.dataset.cover).then((d) => { if (d) img.src = d; });
    }
  };
  const mo = new MutationObserver((records) => {
    for (const rec of records) {
      for (const node of rec.addedNodes) scan(node);
    }
  });
  mo.observe(document.body, { childList: true, subtree: true });
  hydrateCovers(document.body);
}

function bindCards(el) {
  el.querySelectorAll('.card').forEach((c) => {
    c.onclick = () => {
      const id = c.dataset.id;
      if (/^topic_/.test(id)) {
        location.href = 'topics.html?slug=' + encodeURIComponent(id.replace('topic_', ''));
      } else if (/^post_/.test(id)) {
        // 吃瓜是图文文章，不能当视频详情去请求，否则 /api/videos/{postId} 返回 410
        location.href = 'chigua.html?post=' + encodeURIComponent(id.replace('post_', ''));
      } else {
        location.href = 'play.html?id=' + encodeURIComponent(id) + '&ep=1';
      }
    };
  });
}

function fill(el, list, empty) {
  el.innerHTML = list && list.length ? gridHtml(list) : `<div class="empty">${esc(empty || '暂无内容')}</div>`;
  bindCards(el);
  hydrateCovers(el);
}

function chips(el, items, activeKey, onPick) {
  el.innerHTML = (items || []).map((i) => `<button class="${i.v === activeKey ? 'active' : ''}" data-v="${esc(i.v)}">${esc(i.n)}</button>`).join('');
  el.querySelectorAll('button').forEach((b) => {
    b.onclick = () => {
      el.querySelectorAll('button').forEach((x) => x.classList.remove('active'));
      b.classList.add('active');
      onPick(b.dataset.v);
    };
  });
}

function setStatus(el, t) { if (el) el.textContent = t || ''; }

const CATS_FALLBACK = [
  { type_id: 'ai-duanju', type_name: 'AI成人短剧' },
  { type_id: 'ai-manju', type_name: 'AI成人漫剧' }
];
const SORTS_FALLBACK = [
  { n: '最新更新', v: 'latest' },
  { n: '当前热播', v: 'hot' }
];
const RANK_TABS = [
  { n: '热播榜', v: 'hot' },
  { n: '推荐榜', v: 'recommend' },
  { n: '潜力榜', v: 'potential' }
];
const CHIGUA_TABS = [
  { n: '全部', v: 'all' },
  { n: '热门吃瓜', v: 'remen' },
  { n: 'AI原创', v: 'yuanchuang' }
];

async function loadMeta() {
  return { cats: CATS_FALLBACK, sorts: SORTS_FALLBACK };
}

// ===== 页面级接口：直连源站并归一化 =====
async function getCategory(tid, sort, page, size) {
  const q = '/videos/category/' + encodeURIComponent(tid) + '?page=' + page + '&size=' + size + '&sort=' + encodeURIComponent(sort || 'latest');
  const d = await api(q);
  const items = (d && d.data && d.data.items) || [];
  const pg = (d && d.data && d.data.pagination) || {};
  return { list: items.map(fromItem), page: pg.page || page, total: pg.total || 0, hasMore: items.length >= size };
}

async function getDetail(id) {
  const d = await api('/detail/' + encodeURIComponent(id));
  const it = (d && d.list && d.list[0]) || {};
  return { item: it, episodes: it.episodes || episodesOf(it), raw: it };
}

async function getTopics() {
  const d = await api('/topics');
  const items = (d && d.data && d.data.items) || [];
  return items.map((t, i) => ({
    vod_id: 'topic_' + (t.slug || i),
    vod_name: cleanTitle(t.title),
    vod_pic: mediaUrl(t.cover || ''),
    vod_remarks: (t.video_count || t.count || 0) + ' 部'
  }));
}

async function getChigua(cate, page, size) {
  const d = await api('/chigua?cate=' + encodeURIComponent(cate || 'all') + '&page=' + page + '&size=' + size);
  const items = (d && d.data && d.data.items) || [];
  return {
    list: items.map((p) => ({
      vod_id: String(p.id),
      vod_name: cleanTitle(p.title || p.content || ''),
      vod_pic: mediaUrl(p.cover || ''),
      vod_remarks: p.created_at || ''
    })),
    total: (d && d.data && d.data.pagination && d.data.pagination.total) || 0
  };
}

async function getSearch(kw, page, size) {
  const d = await api('/search?q=' + encodeURIComponent(kw) + '&page=' + page + '&size=' + size);
  const items = (d && d.data && d.data.items) || [];
  return {
    list: items.map(fromItem),
    total: (d && d.data && d.data.pagination && d.data.pagination.total) || 0
  };
}

// ===== 浏览器端取播放地址：直连源站的加密通道 =====
// 站点把业务接口放在 dx150-edge-v1 通道里，握手是 ECDH P-256，
// 派生用 HKDF-SHA256，载荷是 AES-GCM。全部用 WebCrypto 实现。
const DX_PROTOCOL = 'dx150-edge-v1';
const DX_ENDPOINTS = {
  user: '/api/user/request',
  playback: '/api/playback/request',
  public: '/api/public/request',
  dynamic: '/api/dynamic/request'
};

function dxB64u(bytes) {
  let s = '';
  for (let i = 0; i < bytes.length; i += 8192) s += String.fromCharCode.apply(null, bytes.subarray(i, i + 8192));
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function dxUb64u(v) {
  const s = atob(v.replace(/-/g, '+').replace(/_/g, '/'));
  const b = new Uint8Array(s.length);
  for (let i = 0; i < s.length; i++) b[i] = s.charCodeAt(i);
  return b;
}
function dxUb64(v) {
  const s = atob(v);
  const b = new Uint8Array(s.length);
  for (let i = 0; i < s.length; i++) b[i] = s.charCodeAt(i);
  return b;
}
function dxB64(b) {
  let s = '';
  for (let i = 0; i < b.length; i += 8192) s += String.fromCharCode.apply(null, b.subarray(i, i + 8192));
  return btoa(s);
}
function dxNonce(seq) {
  const b = new Uint8Array(12);
  new DataView(b.buffer).setUint32(8, seq, false);
  return b;
}

const dxSessions = {};

async function dxOpen(scope) {
  const enc = new TextEncoder();
  const ecdh = { name: 'ECDH', namedCurve: 'P-256' };
  const pair = await crypto.subtle.generateKey(ecdh, false, ['deriveBits']);
  const pub = await crypto.subtle.exportKey('raw', pair.publicKey);
  const hr = await fetch(SITE + DX_ENDPOINTS[scope], {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ v: 1, type: 'hello', client_pub: dxB64u(new Uint8Array(pub)) })
  });
  if (!hr.ok) throw Error('dx hello ' + hr.status);
  const hello = await hr.json();
  if (!hello || hello.v !== 1 || hello.type !== 'hello') throw Error('dx bad hello');
  const sp = await crypto.subtle.importKey('raw', dxUb64u(hello.server_pub), ecdh, false, []);
  const shared = await crypto.subtle.deriveBits({ name: 'ECDH', public: sp }, pair.privateKey, 256);
  const hkdf = await crypto.subtle.importKey('raw', shared, 'HKDF', false, ['deriveKey']);
  const salt = dxUb64u(hello.salt);
  const info = [DX_PROTOCOL, scope, hello.site, hello.kid, hello.sid].join('\n');
  const mk = (dir, op) => crypto.subtle.deriveKey(
    { name: 'HKDF', hash: 'SHA-256', salt, info: enc.encode(info + '\n' + dir) },
    hkdf, { name: 'AES-GCM', length: 256 }, false, [op]
  );
  const keys = await Promise.all([mk('req', 'encrypt'), mk('res', 'decrypt')]);
  const lt = Math.floor(Date.now() / 1000);
  const s = {
    info, kid: hello.kid, sid: hello.sid,
    off: hello.server_time - lt,
    exp: lt + hello.expires_at - hello.server_time,
    seq: 0, rk: keys[0], sk: keys[1]
  };
  dxSessions[scope] = s;
  return s;
}

async function dxCall(scope, packet) {
  const enc = new TextEncoder();
  let s = dxSessions[scope];
  if (!s || s.exp <= Math.floor(Date.now() / 1000) + 5) s = await dxOpen(scope);
  const seq = ++s.seq;
  const ts = Math.floor(Date.now() / 1000) + s.off;
  const iv = dxNonce(seq);
  const params = (dir) => ({
    name: 'AES-GCM', iv,
    additionalData: enc.encode([s.info, dir, seq, ts].join('\n'))
  });
  const cipher = await crypto.subtle.encrypt(params('req'), s.rk, enc.encode(JSON.stringify({ request: packet })));
  const r = await fetch(SITE + DX_ENDPOINTS[scope], {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ v: 1, type: 'call', kid: s.kid, sid: s.sid, seq, ts, ciphertext: dxB64u(new Uint8Array(cipher)) })
  });
  if (!r.ok) { delete dxSessions[scope]; throw Error('dx call ' + r.status); }
  const j = await r.json();
  const plain = await crypto.subtle.decrypt(params('res'), s.sk, dxUb64u(j.ciphertext));
  const inner = JSON.parse(new TextDecoder().decode(plain));
  return { status: inner.status, body: new TextDecoder().decode(dxUb64(inner.body)) };
}

function dxPacket(method, path, obj) {
  const bytes = new TextEncoder().encode(obj === undefined ? '' : JSON.stringify(obj));
  return { method, path, body: dxB64(bytes), content_type: obj === undefined ? '' : 'application/json', visitor_id: '' };
}

// 取播放地址。
// 实测：源站加密端点对 Origin 为 github.io 的请求返回 403，
// 而浏览器跨域 POST 必然带 Origin，所以播放这一步必须走 Worker。
// 好在只此一步，列表、封面、m3u8、分片全部由浏览器直连。
async function getPlay(id, ep) {
  const path = '/play?url=' + encodeURIComponent(SITE + '/video/' + id + '/') + '&ep=' + ep;
  const sig = await signHeaders('/api' + path);
  const r = await fetch(WORKER + '/api' + path, { headers: Object.assign({ 'Accept': 'application/json, text/plain, */*' }, sig) });
  if (!r.ok) throw Error('play HTTP ' + r.status);
  const d = await r.json();
  if (!d || !d.url) throw Error('无播放地址');
  return { url: d.url, proxy: d.proxy || '', hevc: d.hevc || '', source: d.source || 'worker', declaredDuration: d.declaredDuration || 0 };
}

// 页面就绪即开始监听新卡片并解封面
if (typeof document !== "undefined") {
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", bootCoverObserver);
  else bootCoverObserver();
}
