# Localization

Haven ships 50 languages. English is the fallback.

| Group | Languages |
|---|---|
| European | English, French, German, Spanish, Portuguese, Italian, Dutch, Polish, Russian, Ukrainian, Swedish, Norwegian (Bokmål), Danish, Finnish, Greek, Czech, Slovak, Hungarian, Romanian, Bulgarian, Croatian, Serbian (Cyrillic), Slovenian, Turkish, Icelandic, Irish, Catalan, Basque, Welsh |
| Middle East | Hebrew, Arabic, Persian, Urdu |
| Asia | Simplified Chinese, Traditional Chinese, Japanese, Korean, Hindi, Bengali, Tamil, Telugu, Thai, Vietnamese, Indonesian, Malay, Filipino |
| Africa | Swahili, Afrikaans, Amharic, Zulu |

The full list with direction and confidence tier is in [TRANSLATIONS.md](../TRANSLATIONS.md).

## How it works

- Strings live in normal Android resources (`values-xx`). On Android 13 and newer the system owns the per-app language (it appears in system settings and `res/xml/locales_config.xml` lists the tags). On older versions Haven applies the saved language itself.
- Settings > Language & region > Language lists every language by its own name, plus System default. Changing it recreates the screen and reopens Settings.
- Plurals use the correct categories per language. Dates use the locale's own pattern.
- Uppercase and letter spacing are only applied to Latin, Cyrillic and Greek text; letter spacing would break joined Arabic and Indic scripts.
- Right-to-left: the layout mirrors with start/end attributes. The row of tiles, the settings pages, focus movement and animations follow the layout direction. The Easter egg draws in fixed screen coordinates so it never mirrors.

## What has been checked

Checked on a Xiaomi TV Box S (3rd Gen), Android 14, by looking at screenshots:

- Every language was loaded and its home screen captured. Scripts that need shaping (Arabic, Devanagari, Thai, Bengali, Tamil, Telugu, Amharic, Hebrew, CJK) rendered without missing glyphs in the sampled lines.
- English, Hebrew and Arabic: the full Settings screens and D-pad navigation (in RTL the arrow toward the content is Left).
- German (long text) and the `en-XA` and `ar-XB` pseudolocales: no clipping in the sampled screens.

Not checked: every settings row in every language, and native-speaker correctness.

## Translation quality

The translations are machine-assisted drafts. None have been reviewed by a native speaker. Languages marked tier B in `TRANSLATIONS.md` (Icelandic, Irish, Basque, Welsh, Urdu, Bengali, Tamil, Telugu, Swahili, Amharic, Zulu) are the ones I trust least. Corrections are welcome as pull requests against `tools/i18n_data_*.py`.

## Testing a language without the UI

```bash
adb shell cmd locale set-app-locales dev.l1avzh.haven --locales he
adb shell cmd locale set-app-locales dev.l1avzh.haven --locales ""   # back to system default
```
