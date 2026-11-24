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
        input: '#fdf6e3', line: '#93a1a1', text: '#586e75', muted: '#586e75',
        accent: '#268bd2', 'accent-ink': '#586e75', selected: '#eee8d5',
        'selected-line': '#268bd2', 'manual-text': '#586e75', 'manual-label': '#586e75',
        'state-bg': '#fdf6e3', 'state-text': '#586e75', 'on-accent': '#fdf6e3',
        danger: '#dc322f', 'danger-text': '#cb4b16', 'danger-bg': '#fdf6e3'
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
