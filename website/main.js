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

  // Latest release (version + date). Purely cosmetic: the page works without it.
  fetch('https://api.github.com/repos/lsunol/my-iptv-player/releases/latest', { headers: { Accept: 'application/vnd.github+json' } })
    .then((r) => (r.ok ? r.json() : Promise.reject(r.status)))
    .then((rel) => {
      const date = new Date(rel.published_at).toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });
      document.querySelectorAll('[data-release]').forEach((el) => {
        el.textContent = `Versión ${rel.tag_name} · ${date} · Android 7.0 o superior`;
      });
    })
    .catch(() => {});
})();
