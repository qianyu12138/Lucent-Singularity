package com.qianyu.lux;

/** Runtime UI colors; launcher and startup resources do not use this palette. */
final class ThemeColors {
    static final String PREFERENCE = "themeColor";
    static final ThemeColors[] OPTIONS = {
        new ThemeColors("暖金（默认）", false,
            0xffe3c17d, 0xffe3c17d, 0xffe3c17d,
            0xff141716, 0xff202421, 0xff1c211d, 0xff292e28,
            0xff363c36, 0xff302c22, 0xff141915, 0xfff1f3eb, 0xff9aa397),
        new ThemeColors("Nord · 冷蓝灰", false,
            0xff88c0d0, 0xff88c0d0, 0xffeceff4,
            0xff2e3440, 0xff3b4252, 0xff3b4252, 0xff3b4252,
            0xff4c566a, 0xff3b4252, 0xff2e3440, 0xffeceff4, 0xffd8dee9),
        new ThemeColors("Rosé Pine · 暗紫灰", false,
            0xffebbcba, 0xffebbcba, 0xffe0def4,
            0xff191724, 0xff1f1d2e, 0xff1f1d2e, 0xff26233a,
            0xff403d52, 0xff26233a, 0xff191724, 0xffe0def4, 0xff908caa),
        new ThemeColors("Catppuccin Frappé · 柔蓝灰", false,
            0xffa6d189, 0xffa6d189, 0xffc6d0f5,
            0xff303446, 0xff414559, 0xff414559, 0xff414559,
            0xff626880, 0xff414559, 0xff292c3c, 0xffc6d0f5, 0xffb5bfe2),
        new ThemeColors("Solarized Light · 米白", true,
            0xff268bd2, 0xff566c73, 0xff566c73,
            0xfffdf6e3, 0xffeee8d5, 0xffeee8d5, 0xffeee8d5,
            0xff93a1a1, 0xffeee8d5, 0xfffdf6e3, 0xff566c73, 0xff566c73),
        new ThemeColors("Rosé Pine Dawn · 暖白", true,
            0xffb4637a, 0xff464261, 0xff464261,
            0xfffaf4ed, 0xfffffaf3, 0xfffffaf3, 0xfff2e9e1,
            0xffcecacd, 0xfff2e9e1, 0xfffaf4ed, 0xff464261, 0xff625e78),
        new ThemeColors("Radix Sage · 鼠尾草浅绿", true,
            0xff208368, 0xff1d3b31, 0xff1a211e,
            0xffeef1f0, 0xfff7f9f8, 0xfffbfdfc, 0xffe6e9e8,
            0xffb8bcba, 0xffe6e9e8, 0xfffbfdfc, 0xff1a211e, 0xff5f6563)
    };

    final String name;
    final boolean light;
    final int accent, accentInk, manualText;
    final int background, surface, card, button, border, selected, input, foreground, muted;
    final int warning, warningBackground;

    private ThemeColors(String name, boolean light, int accent, int accentInk, int manualText, int background, int surface,
            int card, int button, int border, int selected, int input, int foreground, int muted) {
        this.name = name;
        this.light = light;
        this.accent = accent;
        this.accentInk = accentInk;
        this.manualText = manualText;
        this.background = background;
        this.surface = surface;
        this.card = card;
        this.button = button;
        this.border = border;
        this.selected = selected;
        this.input = input;
        this.foreground = foreground;
        this.muted = muted;
        warning = light ? 0xffb4233b : 0xfff06a62;
        warningBackground = light ? 0xffffede9 : 0xff302321;
    }

    int color(int original) {
        if (this == OPTIONS[0]) return original;
        int rgb = original | 0xff000000;
        int result;
        switch (rgb) {
            case 0xffe3c17d: result = accentInk; break;
            case 0xff141716: result = background; break;
            case 0xff141915: result = input; break;
            case 0xff1c211d: result = card; break;
            case 0xff202421: result = surface; break;
            case 0xff292e28: result = button; break;
            case 0xff363c36:
            case 0xff394236: result = border; break;
            case 0xff302c22:
            case 0xff25291f: result = selected; break;
            case 0xff64563b: result = accent; break;
            case 0xff9aa397: result = muted; break;
            case 0xfff1f3eb: result = foreground; break;
            default: return original;
        }
        return (original & 0xff000000) | (result & 0x00ffffff);
    }

}
