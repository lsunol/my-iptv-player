// Open IPTV — landing page. No tracking, no cookies.
(() => {
  // Nav border once the page scrolls.
  const nav = document.querySelector('.nav');
  const onScroll = () => nav.classList.toggle('is-scrolled', window.scrollY > 8);
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  // Sticky phone: show the screenshot of the step crossing the middle of the viewport.
  const steps = [...document.querySelectorAll('.step')];
  const shots = [...document.querySelectorAll('.phone--stack > img')];
  const dots = [...document.querySelectorAll('.dots li')];
  const activate = (i) => {
    steps.forEach((s, n) => s.classList.toggle('is-active', n === i));
    shots.forEach((s, n) => s.classList.toggle('is-active', n === i));
    dots.forEach((d, n) => d.classList.toggle('is-active', n === i));
  };
  if ('IntersectionObserver' in window && steps.length) {
    const io = new IntersectionObserver((entries) => {
      entries.forEach((e) => { if (e.isIntersecting) activate(Number(e.target.dataset.step)); });
    }, { rootMargin: '-45% 0px -45% 0px' });
    steps.forEach((s) => io.observe(s));
  } else {
    steps.forEach((s) => s.classList.add('is-active'));
  }

  document.querySelectorAll('[data-year]').forEach((el) => { el.textContent = new Date().getFullYear(); });

  // ---------- i18n ----------
  // English lives in the HTML (default, no-JS and crawler text). Other languages are
  // locales/<code>.json, applied over it; switching back restores the original English.
  const SUPPORTED = ['en', 'es', 'ca'];
  const orig = new Map(); // element -> { text, attrs }
  const dicts = {};
  let current = 'en';
  let release = null;

  const remember = (el) => {
    if (!orig.has(el)) orig.set(el, { text: el.hasAttribute('data-i18n') ? el.innerHTML : null, attrs: {} });
    return orig.get(el);
  };

  const apply = (dict) => {
    document.querySelectorAll('[data-i18n]').forEach((el) => {
      const o = remember(el), v = dict && dict[el.dataset.i18n];
      el.innerHTML = typeof v === 'string' ? v : o.text;
    });
    document.querySelectorAll('[data-i18n-attr]').forEach((el) => {
      const o = remember(el);
      el.dataset.i18nAttr.split(',').forEach((pair) => {
        const [attr, key] = pair.split(':');
        if (!(attr in o.attrs)) o.attrs[attr] = el.getAttribute(attr);
        const v = dict && dict[key];
        el.setAttribute(attr, typeof v === 'string' ? v : o.attrs[attr]);
      });
    });
  };

  const renderRelease = () => {
    if (!release) return;
    const loc = (dicts[current] && dicts[current].date_locale) || 'en-GB';
    const date = new Date(release.published_at).toLocaleDateString(loc, { day: 'numeric', month: 'short', year: 'numeric' });
    document.querySelectorAll('[data-release]').forEach((el) => {
      if (el.dataset.tpl) el.textContent = el.dataset.tpl.replace('{tag}', release.tag_name).replace('{date}', date);
    });
  };

  const setLang = async (lang, persist) => {
    if (!SUPPORTED.includes(lang)) lang = 'en';
    try {
      if (lang !== 'en' && !dicts[lang]) dicts[lang] = await (await fetch('locales/' + lang + '.json')).json();
    } catch (e) { lang = 'en'; }
    current = lang;
    apply(lang === 'en' ? null : dicts[lang]);
    document.documentElement.lang = lang;
    document.querySelectorAll('.lang a').forEach((a) => {
      if (a.dataset.lang === lang) a.setAttribute('aria-current', 'true'); else a.removeAttribute('aria-current');
    });
    renderRelease();
    document.documentElement.removeAttribute('data-i18n-state');
    if (persist) {
      try { localStorage.setItem('lang', lang); } catch (e) {}
      const u = new URL(location.href); u.searchParams.set('lang', lang);
      history.replaceState(null, '', u);
    }
  };

  document.querySelectorAll('.lang a').forEach((a) => {
    a.addEventListener('click', (e) => { e.preventDefault(); setLang(a.dataset.lang, true); });
  });
  setLang(window.__lang || 'en', false);

  // Latest release (version + date). Purely cosmetic: the page works without it.
  fetch('https://api.github.com/repos/lsunol/my-iptv-player/releases/latest', { headers: { Accept: 'application/vnd.github+json' } })
    .then((r) => (r.ok ? r.json() : Promise.reject(r.status)))
    .then((rel) => { release = rel; renderRelease(); })
    .catch(() => {});
})();
