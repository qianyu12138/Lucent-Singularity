(() => {
  const palettes = {
    warm: {
      name: '默认暖金', short: '暖金', mode: 'dark',
      description: '当前默认配色，作为对照。',
      source: null,
      colors: {
        bg: '#141716', stage: '#090b0a', surface: '#202421', card: '#1c211d',
        input: '#141915', line: '#363c36', text: '#f1f3eb', muted: '#9aa397',
        accent: '#e3c17d', 'accent-ink': '#e3c17d', selected: '#302c22',
        'selected-line': '#64563b', 'manual-text': '#f6d99d', 'manual-label': '#e5d4af',
        'state-bg': '#e3c17d', 'state-text': '#25251d', 'on-accent': '#25251d',
        danger: '#f06a62', 'danger-text': '#ff8c83', 'danger-bg': '#302321'
      }
    },
    nord: {
      name: 'Nord · 冷蓝灰', short: 'Nord', mode: 'dark',
      description: '冷蓝灰背景、白色读数，冰蓝只用于选中态和刻度。',
      source: 'https://www.nordtheme.com/docs/colors-and-palettes/',
      colors: {
        bg: '#2e3440', stage: '#3b4252', surface: '#3b4252', card: '#3b4252',
        input: '#2e3440', line: '#4c566a', text: '#eceff4', muted: '#d8dee9',
        accent: '#88c0d0', 'accent-ink': '#88c0d0', selected: '#3b4252',
        'selected-line': '#88c0d0', 'manual-text': '#eceff4', 'manual-label': '#d8dee9',
        'state-bg': '#2e3440', 'state-text': '#88c0d0', 'on-accent': '#2e3440',
        danger: '#bf616a', 'danger-text': '#eceff4', 'danger-bg': '#434c5e'
      }
    },
    rose: {
      name: 'Rosé Pine · 暗紫灰', short: 'Rosé Pine', mode: 'dark',
      description: '暗紫灰背景、浅色读数，柔和玫瑰色用于少量交互标记。',
      source: 'https://rosepinetheme.com/palette/',
      colors: {
        bg: '#191724', stage: '#191724', surface: '#1f1d2e', card: '#1f1d2e',
        input: '#191724', line: '#403d52', text: '#e0def4', muted: '#908caa',
        accent: '#ebbcba', 'accent-ink': '#ebbcba', selected: '#26233a',
        'selected-line': '#ebbcba', 'manual-text': '#e0def4', 'manual-label': '#908caa',
        'state-bg': '#1f1d2e', 'state-text': '#ebbcba', 'on-accent': '#191724',
        danger: '#eb6f92', 'danger-text': '#eb6f92', 'danger-bg': '#26233a'
      }
    },
    frappe: {
      name: 'Catppuccin Frappé · 柔蓝灰', short: 'Frappé', mode: 'dark',
      description: '柔蓝灰背景、偏冷的浅色读数，以淡绿区分手动控制。',
      source: 'https://catppuccin.com/palette/#frapp%C3%A9',
      colors: {
        bg: '#303446', stage: '#232634', surface: '#414559', card: '#414559',
        input: '#292c3c', line: '#626880', text: '#c6d0f5', muted: '#b5bfe2',
        accent: '#a6d189', 'accent-ink': '#a6d189', selected: '#414559',
        'selected-line': '#a6d189', 'manual-text': '#c6d0f5', 'manual-label': '#b5bfe2',
        'state-bg': '#303446', 'state-text': '#a6d189', 'on-accent': '#303446',
        danger: '#e78284', 'danger-text': '#e78284', 'danger-bg': '#303446'
      }
    },
    solarized: {
      name: 'Solarized Light · 米白', short: 'Solarized', mode: 'light',
      description: '米白纸感背景、深灰读数，蓝色只用于边框、刻度和选中标记。',
      source: 'https://ethanschoonover.com/solarized/',
      colors: {
        bg: '#fdf6e3', stage: '#eee8d5', surface: '#eee8d5', card: '#eee8d5',
        input: '#fdf6e3', line: '#93a1a1', text: '#566c73', muted: '#566c73',
        accent: '#268bd2', 'accent-ink': '#566c73', selected: '#eee8d5',
        'selected-line': '#268bd2', 'manual-text': '#566c73', 'manual-label': '#566c73',
        'state-bg': '#fdf6e3', 'state-text': '#566c73', 'on-accent': '#fdf6e3',
        danger: '#dc322f', 'danger-text': '#cb4b16', 'danger-bg': '#fdf6e3'
      }
    },
    dawn: {
      name: 'Rosé Pine Dawn · 暖白', short: 'Dawn', mode: 'light',
      description: '暖白纸感背景、深灰紫读数，以少量豆沙色标记手动控制。',
      source: 'https://rosepinetheme.com/palette/',
      colors: {
        bg: '#faf4ed', stage: '#f2e9e1', surface: '#fffaf3', card: '#fffaf3',
        input: '#faf4ed', line: '#cecacd', text: '#464261', muted: '#625e78',
        accent: '#b4637a', 'accent-ink': '#464261', selected: '#f2e9e1',
        'selected-line': '#b4637a', 'manual-text': '#464261', 'manual-label': '#625e78',
        'state-bg': '#fffaf3', 'state-text': '#464261', 'on-accent': '#fffaf3',
        danger: '#b4233b', 'danger-text': '#b4233b', 'danger-bg': '#faf4ed'
      }
    },
    sage: {
      name: 'Radix Sage · 鼠尾草浅绿', short: 'Sage', mode: 'light',
      description: '清淡灰绿背景、深色读数，绿色只用于刻度与选中标记。',
      source: 'https://www.radix-ui.com/colors/docs/palette-composition/composing-a-palette',
      colors: {
        bg: '#eef1f0', stage: '#dfe2e0', surface: '#f7f9f8', card: '#fbfdfc',
        input: '#fbfdfc', line: '#b8bcba', text: '#1a211e', muted: '#5f6563',
        accent: '#208368', 'accent-ink': '#1d3b31', selected: '#e6e9e8',
        'selected-line': '#208368', 'manual-text': '#1a211e', 'manual-label': '#5f6563',
        'state-bg': '#fbfdfc', 'state-text': '#1d3b31', 'on-accent': '#fbfdfc',
        danger: '#b4233b', 'danger-text': '#b4233b', 'danger-bg': '#fbfdfc'
      }
    }
  };

  const options = document.getElementById('palette-options');
  const selectors = [document.getElementById('palette-select'), document.getElementById('settings-theme')];
  for (const [key, palette] of Object.entries(palettes)) {
    const button = document.createElement('button');
    button.className = 'palette-option';
    button.type = 'button';
    button.dataset.palette = key;
    button.setAttribute('aria-label', palette.name);
    const swatches = document.createElement('span');
    swatches.className = 'palette-colors';
    swatches.setAttribute('aria-hidden', 'true');
    for (const value of [palette.colors.bg, palette.colors.text, palette.colors.accent]) {
      const swatch = document.createElement('i');
      swatch.style.setProperty('--swatch', value);
      swatches.appendChild(swatch);
    }
    button.append(swatches, document.createTextNode(palette.short));
    button.addEventListener('click', () => apply(key));
    options.appendChild(button);
    for (const select of selectors) {
      const option = document.createElement('option');
      option.value = key;
      option.textContent = palette.name;
      select.appendChild(option);
    }
  }

  function apply(key) {
    const palette = palettes[key];
    document.documentElement.dataset.palette = key;
    document.documentElement.style.colorScheme = palette.mode;
    for (const [name, value] of Object.entries(palette.colors)) {
      document.documentElement.style.setProperty('--' + name, value);
    }
    for (const button of options.children) button.setAttribute('aria-pressed', String(button.dataset.palette === key));
    for (const select of selectors) select.value = key;
    document.getElementById('palette-description').textContent = palette.description;
    const source = document.getElementById('palette-source');
    source.hidden = !palette.source;
    if (palette.source) source.href = palette.source;
    localStorage.setItem('lux-demo-theme', key);
  }
  for (const select of selectors) select.addEventListener('change', () => apply(select.value));
  const initial = localStorage.getItem('lux-demo-theme');
  apply(Object.hasOwn(palettes, initial) ? initial : 'warm');
})();
