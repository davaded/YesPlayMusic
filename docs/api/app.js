const contentEl = document.getElementById('content');
const tocEl = document.getElementById('tocList');
const pageEl = document.querySelector('.page');
let headingObserver = null;
const tocLinks = new Map();

async function loadMarkdown() {
  try {
    const response = await fetch('api.md');
    if (!response.ok) {
      throw new Error(`Failed to load api.md: ${response.status}`);
    }
    const markdown = await response.text();
    marked.setOptions({
      mangle: false,
      headerIds: true,
      headerPrefix: ''
    });

    const html = marked.parse(markdown);
    contentEl.innerHTML = html;
    buildToc();
    observeHeadings();
  } catch (error) {
    contentEl.textContent = `加载失败：${error.message}`;
  }
}

function buildToc() {
  tocEl.innerHTML = '';
  tocLinks.clear();
  const headings = contentEl.querySelectorAll('h1, h2, h3');
  if (!headings.length) {
    const empty = document.createElement('div');
    empty.className = 'toc-item';
    empty.textContent = '暂无目录';
    tocEl.appendChild(empty);
    return;
  }

  headings.forEach((heading) => {
    if (!heading.id) {
      heading.id = heading.textContent
        .toLowerCase()
        .trim()
        .replace(/\s+/g, '-')
        .replace(/[^\w\u4e00-\u9fa5-]/g, '');
    }
    const link = document.createElement('a');
    link.className = 'toc-item';
    link.dataset.level = heading.tagName.substring(1);
    link.href = `#${heading.id}`;
    link.textContent = heading.textContent;
    tocEl.appendChild(link);
    tocLinks.set(heading.id, link);
  });
}

function observeHeadings() {
  if (!pageEl) return;
  if (headingObserver) headingObserver.disconnect();
  const headings = contentEl.querySelectorAll('h1, h2, h3');
  if (!headings.length) return;

  headingObserver = new IntersectionObserver(
    (entries) => {
      const visible = entries
        .filter((entry) => entry.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio);
      if (!visible.length) return;
      setActive(visible[0].target.id);
    },
    {
      root: pageEl,
      rootMargin: '-20% 0px -70% 0px',
      threshold: [0, 0.1, 0.5, 1],
    }
  );

  headings.forEach((heading) => headingObserver.observe(heading));
  setActive(headings[0].id);
}

function setActive(id) {
  tocLinks.forEach((link, key) => {
    link.classList.toggle('is-active', key === id);
  });
}

loadMarkdown();
