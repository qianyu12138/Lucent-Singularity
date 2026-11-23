package com.syl.lux;

/** Runtime UI colors; launcher and startup resources do not use this palette. */
final class ThemeColors {
    static final String PREFERENCE = "themeColor";
    static final ThemeColors[] OPTIONS = {
        new ThemeColors("暖金（默认）", 0xffe3c17d),
        new ThemeColors("雾蓝", 0xff9dbfea),
        new ThemeColors("松绿", 0xff9bcab1),
        new ThemeColors("暮紫", 0xffc4ace5),
        new ThemeColors("玫瑰", 0xffe5a6b3)
    };

    final String name;
    final int accent;

    private ThemeColors(String name, int accent) {
        this.name = name;
        this.accent = accent;
    }

    int color(int original) {
        if (this == OPTIONS[0]) return original;
        int rgb = original | 0xff000000;
        int result;
        switch (rgb) {
            case 0xffe3c17d: result = accent; break;
            case 0xff141716: result = blend(0xff141619, accent, .035f); break;
            case 0xff141915: result = blend(0xff15181b, accent, .035f); break;
            case 0xff1c211d: result = blend(0xff1c2024, accent, .045f); break;
            case 0xff202421: result = blend(0xff202428, accent, .05f); break;
            case 0xff292e28: result = blend(0xff292d32, accent, .065f); break;
            case 0xff363c36: result = blend(0xff363c42, accent, .1f); break;
            case 0xff394236: result = blend(0xff394048, accent, .12f); break;
            case 0xff302c22: result = blend(0xff202428, accent, .12f); break;
            case 0xff25291f: result = blend(0xff202428, accent, .075f); break;
            case 0xff64563b: result = blend(0xff363c42, accent, .4f); break;
            case 0xff9aa397: result = blend(0xff9aa1a9, accent, .18f); break;
            case 0xfff1f3eb: result = blend(0xfff1f3f5, accent, .05f); break;
            default: return original;
        }
        return (original & 0xff000000) | (result & 0x00ffffff);
    }

    private static int blend(int base, int tint, float amount) {
        int r = Math.round(((base >> 16) & 255) * (1 - amount) + ((tint >> 16) & 255) * amount);
        int g = Math.round(((base >> 8) & 255) * (1 - amount) + ((tint >> 8) & 255) * amount);
        int b = Math.round((base & 255) * (1 - amount) + (tint & 255) * amount);
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }
}
