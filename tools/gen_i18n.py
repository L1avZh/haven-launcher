#!/usr/bin/env python3
"""Generates res/values*/strings.xml, locales_config.xml, Langs.kt and TRANSLATIONS.md from i18n_data_*.py.
Run from the project root:  python3 tools/gen_i18n.py"""
import os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
import i18n_data_a as A, i18n_data_b as B, i18n_data_c1 as C1, i18n_data_c2 as C2, i18n_data_d1 as D1, i18n_data_d2 as D2, i18n_data_d3 as D3, i18n_data_d4 as D4

KEYS_C = "remote_control input_devices button_test button_test_hint tester_note test_key test_code test_device test_usable yes no custom_mappings add_mapping choose_action action_open_app action_search action_settings action_start press_button mapping_saved no_mappings remove reset_mappings reset_done numbers_open_favs back_to_start".split()
KEYS = """search settings favorites library tag_favorite hold_ok open add_fav remove_fav move_earlier move_later hide app_info
wallpaper accent_glow reduce_motion clock hidden_apps refresh set_home system_settings on off language system_default no_hidden show
search_apps type_to_search no_results no_home_picker use_adb cant_open info_unavailable settings_unavailable no_favorites about
wp_midnight wp_ember wp_forest wp_slate wp_violet wp_black not_installed adguard_open_page adguard_note version loading""".split()

# (BCP-47 tag used by Android LocaleConfig, resource qualifier, native name, review tier)
LANGS = [
 ('en','', 'English','A'), ('he','iw','עברית','A'), ('fr','fr','Français','A'), ('de','de','Deutsch','A'),
 ('es','es','Español','A'), ('pt','pt','Português','A'), ('it','it','Italiano','A'), ('nl','nl','Nederlands','A'),
 ('pl','pl','Polski','A'), ('ru','ru','Русский','A'), ('uk','uk','Українська','A'), ('sv','sv','Svenska','A'),
 ('nb','nb','Norsk bokmål','A'), ('da','da','Dansk','A'), ('fi','fi','Suomi','A'), ('el','el','Ελληνικά','A'),
 ('cs','cs','Čeština','A'), ('sk','sk','Slovenčina','A'), ('hu','hu','Magyar','A'), ('ro','ro','Română','A'),
 ('bg','bg','Български','A'), ('hr','hr','Hrvatski','A'), ('sr','sr','Српски','A'), ('sl','sl','Slovenščina','A'),
 ('tr','tr','Türkçe','A'), ('is','is','Íslenska','B'), ('ga','ga','Gaeilge','B'), ('ca','ca','Català','A'),
 ('eu','eu','Euskara','B'), ('cy','cy','Cymraeg','B'), ('ar','ar','العربية','A'), ('fa','fa','فارسی','A'),
 ('ur','ur','اردو','B'), ('zh-CN','zh-rCN','简体中文','A'), ('zh-TW','zh-rTW','繁體中文','A'), ('ja','ja','日本語','A'),
 ('ko','ko','한국어','A'), ('hi','hi','हिन्दी','A'), ('bn','bn','বাংলা','B'), ('ta','ta','தமிழ்','B'),
 ('te','te','తెలుగు','B'), ('th','th','ไทย','A'), ('vi','vi','Tiếng Việt','A'), ('in','in','Bahasa Indonesia','A'),
 ('ms','ms','Bahasa Melayu','A'), ('fil','fil','Filipino','A'), ('sw','sw','Kiswahili','B'), ('af','af','Afrikaans','A'),
 ('am','am','አማርኛ','B'), ('zu','zu','isiZulu','B'),
]
RTL = {'he','ar','fa','ur'}
dataC = {**C1.C, **C2.C}
KEYS_D = 'cat_general cat_appearance cat_apps cat_language cat_display cat_privacy cat_about d_general d_appearance d_apps d_remote d_language d_display d_adguard d_privacy d_about return_start_home return_start_home_d set_home_d reset_all reset_all_d restore_title restore_body restore cancel settings_reset accent_color accent_color_d accent_auto accent_blue accent_green accent_coral accent_gold accent_violet dimming dimming_d level_light level_medium level_strong card_size card_size_d size_small size_medium size_large accent_glow_d time_format time_12 time_24 manage_apps show_system_apps show_system_apps_d sort_apps sort_az sort_new app_shortcuts app_shortcuts_d numbers_open_favs_d return_shortcut return_shortcut_d floating_home floating_home_d text_direction dir_ltr dir_rtl languages_available animation animation_d anim_full anim_subtle performance performance_d ag_status ag_installed ag_launch ag_compat ag_compat_d ag_verify priv_stored priv_counts priv_network priv_permissions priv_diag none priv_note ver_build developer licenses licenses_d egg_hint egg_reveal egg_sequence hidden_tag'.split()
dataD = {**D1.Dd, **D2.Dd, **D3.Dd, **D4.Dd}
data = {**A.D, **B.D}; plurals = {**A.P, **B.P}

def esc(s):
    s = s.replace('&','&amp;').replace('<','&lt;').replace("'", "\\'").replace('"','\\"')
    return '\\' + s if s[:1] in '@?' else s

def xml(lang):
    lines = data[lang].split('\n')
    assert len(lines) == len(KEYS), f'{lang}: {len(lines)} strings, expected {len(KEYS)}'
    for k, v in zip(KEYS, lines):
        assert v.strip() == v and v, f'{lang}:{k} empty/whitespace'
        if k == 'cant_open': assert '%1$s' in v, f'{lang}: cant_open lacks %1$s'
    linesC = dataC[lang].split('\n')
    assert len(linesC) == len(KEYS_C), f'{lang}: {len(linesC)} remote strings, expected {len(KEYS_C)}'
    assert '%1$s' in linesC[KEYS_C.index('mapping_saved')] and '%2$s' in linesC[KEYS_C.index('mapping_saved')], f'{lang}: mapping_saved placeholders'
    linesD = dataD[lang].split('\n')
    assert len(linesD) == len(KEYS_D), f'{lang}: {len(linesD)} settings strings, expected {len(KEYS_D)}'
    for k in ('ag_installed','ver_build','priv_counts'):
        assert '%1$s' in linesD[KEYS_D.index(k)], f'{lang}: {k} lacks %1$s'
    assert '%3$s' in linesD[KEYS_D.index('priv_counts')] and '%2$s' in linesD[KEYS_D.index('ver_build')] and '%1$s' in linesD[KEYS_D.index('ag_compat_d')], f'{lang}: placeholders'
    out = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    if lang == 'en': out.append('    <string name="app_name" translatable="false">Haven</string>')
    for k, v in zip(KEYS, lines): out.append(f'    <string name="{k}">{esc(v)}</string>')
    for k, v in zip(KEYS_C, linesC): out.append(f'    <string name="{k}">{esc(v)}</string>')
    for k, v in zip(KEYS_D, linesD): out.append(f'    <string name="{k}">{esc(v)}</string>')
    out.append('    <plurals name="apps_count">')
    for q, v in plurals[lang].items():
        assert '%d' in v or q in ('one','two','zero'), f'{lang} plural {q}'
        out.append(f'        <item quantity="{q}">{esc(v)}</item>')
    out += ['    </plurals>', '</resources>', '']
    return '\n'.join(out)

res = 'app/src/main/res'
for tag, qual, name, tier in LANGS:
    d = f'{res}/values' if tag == 'en' else f'{res}/values-{qual}'
    os.makedirs(d, exist_ok=True)
    open(f'{d}/strings.xml','w',encoding='utf-8').write(xml(tag))
os.makedirs(f'{res}/xml', exist_ok=True)
tags = ['en'] + [('id' if t == 'in' else t) for t,_,_,_ in LANGS if t != 'en']
open(f'{res}/xml/locales_config.xml','w').write('<?xml version="1.0" encoding="utf-8"?>\n<locale-config xmlns:android="http://schemas.android.com/apk/res/android">\n' +
    ''.join(f'    <locale android:name="{t}" />\n' for t in tags) + '</locale-config>\n')

kt = ['package dev.l1avzh.haven', '', '// GENERATED by tools/gen_i18n.py. Do not edit.', 'class LangEntry(val tag: String, val name: String, val rtl: Boolean)', '',
      'val LANGUAGES = listOf(']
for tag, _, name, _ in LANGS:
    t = 'id' if tag == 'in' else tag
    kt.append(f'    LangEntry("{t}", "{name}", {"true" if tag in RTL else "false"}),')
kt += [')', '']
open('app/src/main/java/dev/l1avzh/haven/Langs.kt','w',encoding='utf-8').write('\n'.join(kt))

rep = ['# Translation status', '', f'{len(KEYS)+len(KEYS_C)+len(KEYS_D)} strings + 1 plural per language. Source of truth: `tools/i18n_data_*.py` (run `python3 tools/gen_i18n.py`).',
       '', '**No translation has been reviewed by a native speaker.** They are machine-assisted drafts and should be treated as such.',
       'Tier A: common vocabulary, drafted with reasonable confidence. Tier B: lower confidence (smaller corpus / complex morphology); review before public release.', '',
       '| Tag | Language | Direction | Tier | Strings |', '|---|---|---|---|---|']
for tag, _, name, tier in LANGS:
    rep.append(f'| {"id" if tag=="in" else tag} | {name} | {"RTL" if tag in RTL else "LTR"} | {tier} | {len(KEYS)+len(KEYS_C)+len(KEYS_D)}/{len(KEYS)+len(KEYS_C)+len(KEYS_D)} |')
open('TRANSLATIONS.md','w',encoding='utf-8').write('\n'.join(rep) + '\n')
print('languages:', len(LANGS), 'strings each:', len(KEYS))
