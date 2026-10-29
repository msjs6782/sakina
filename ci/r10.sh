# Round 10: the Mushaf on a phone set to Arabic, the calendar and More tabs, quick access on Home, the day-name
# widget and the new widget settings. Sourced by ci/screenshots.sh (uses its helpers and phase8.sh's). Defines r10().

r10() {
save_log
adb shell svc power stayon true >/dev/null 2>&1
R=(--ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh,jeddah --es demo_active riyadh --es demo_now "$T_AFTERNOON" "${FREEZE[@]}")

# Four tabs (the times tables open from Home); the Mushaf page without its white paper.
shot 398_tabs 7 "${R[@]}"
ui_has id tab_times && check no_times_tab FAIL "times tab still there" || check no_times_tab PASS "four tabs"
tap_scroll id home_open_times && sleep 2 && cap 399_times_from_home
ui_has exact "الأسبوع" && check times_from_home PASS "tables open from Home" || check times_from_home FAIL "times screen not opened"
adb logcat -c
shot 397_mushaf_trimmed 9 "${R[@]}" --es demo_route mushaf:1
mlog | grep -q "crop page 1:" && check mushaf_trim PASS "$(mlog | grep -o 'crop page 1: [0-9, x]*' | head -1)" || check mushaf_trim FAIL "no crop logged"

# Home: quick access to the most used tools.
shot 400_home_quick 7 "${R[@]}"
scroll_to id home_quick 4 && cap 401_home_quick_card && check home_quick PASS "quick access on Home" || check home_quick FAIL "no quick access card"
tap id quick_tasbih && sleep 2
ui_has id tasbih_label && check quick_opens PASS "tasbih opened from Home" || check quick_opens FAIL "tasbih not opened"

# The calendar has its own tab, with what is coming up under the month.
start "${R[@]}"; sleep 6
tap id tab_calendar && sleep 3 && cap 402_calendar_tab
ui_has id cal_title && check calendar_tab PASS "$(ui_text cal_title)" || check calendar_tab FAIL "no month on the calendar tab"
scroll_to id cal_upcoming 5 && cap 403_calendar_upcoming && check calendar_upcoming PASS "coming up listed" || check calendar_upcoming FAIL "no upcoming list"
# Dark and English.
shot 404_calendar_tab_dark 7 "${R[@]}" --es demo_tab calendar --es demo_theme DARK
shot 405_calendar_tab_english 7 "${R[@]}" --es demo_tab calendar --es demo_lang en

# More: tools in groups, then the settings.
shot 406_more 7 "${R[@]}" --es demo_tab more
ui_has id tool_adhkar && ui_has id tool_ramadan && check more_tools PASS "tools in groups" || check more_tools FAIL "tools missing"
scroll_to id settings_groups 6 && cap 407_more_settings
scroll_to id nav_appearance 6 && check more_settings PASS "settings in More" || check more_settings FAIL "no settings in More"
shot 408_more_dark 7 "${R[@]}" --es demo_tab more --es demo_theme DARK
shot 409_more_english 7 "${R[@]}" --es demo_tab more --es demo_lang en

# The day-name widget: previews in the gallery, then on the home screen with its day stretched by kashida.
WB=(--ez demo_onboarded true --es demo_cities riyadh,jeddah --es demo_active riyadh --es demo_now "$T_AFTERNOON" "${FREEZE[@]}")
start "${R[@]}" --es demo_route widgets; sleep 7
scroll_to id gallery_dayname 40; into_view gallery_dayname; cap 410_gallery_dayname
D=$(ui_text w_dayname)
echo "$D" | grep -q "ـ" && check dayname_preview PASS "$D" || check dayname_preview FAIL "'$D'"
pin_kind dayname 411_pin_dayname
if home_find id w_dayname; then
  cap 412_home_dayname
  D=$(ui_text w_dayname); echo "$D" | grep -q "الأربع" && echo "$D" | grep -q "ـ" && check dayname_home PASS "$D" || check dayname_home FAIL "'$D'"
else
  check dayname_home FAIL "not on the home screen"
fi
# Its settings: Kufi, highlight by colour only, sunrise shown, no update time.
start "${WB[@]}" --es demo_route widgets; sleep 6
scroll_to id placed_settings_dayname 40; into_view placed_settings_dayname; tap id placed_settings_dayname && sleep 4
cap 413_dayname_config
into_view wcfg_elements; cap 414_config_elements
into_view wcfg_show_sunrise; tap id wcfg_show_sunrise
into_view wcfg_show_stamp; tap id wcfg_show_stamp
into_view wcfg_highlight_text; tap id wcfg_highlight_text
into_view wcfg_font_kufi; tap id wcfg_font_kufi && sleep 1
cap 415_config_changed
tap id wcfg_save && sleep 4
J=$(widgets_json)
echo "$J" | grep -q '"font":"KUFI"' && echo "$J" | grep -q '"highlight":"TEXT"' && echo "$J" | grep -q '"showSunrise":true' && echo "$J" | grep -q '"showStamp":false' \
  && check widget_options_saved PASS "font, highlight, sunrise and update time saved" || check widget_options_saved FAIL "$(echo "$J" | grep -o '"kind":"dayname"[^}]*' | head -c 400)"
if home_find id w_dayname; then
  cap 416_home_dayname_kufi
  ui_has id w_cell1 && check widget_sunrise_shown PASS "sunrise column back" || check widget_sunrise_shown FAIL "no sunrise column"
fi
# No widget fails to load (the tint path on Android 12+).
adb shell input keyevent 3; sleep 2
ui_has contains "Problem loading" && check widgets_load FAIL "a widget failed to load" || check widgets_load PASS "widgets load"
wlog | grep -q "preview failed\|render failed" && check widget_previews_r10 FAIL "$(wlog | grep failed | head -2)" || check widget_previews_r10 PASS "previews rendered"
save_log

# The printed Mushaf on a phone set to Arabic (Saudi Arabia): page names keep Latin digits, nothing crashes.
if [ "$SDK" -ge 33 ]; then
  adb shell am force-stop "$PKG"
  adb shell run-as "$PKG" rm -rf files/mushaf
  adb shell cmd locale set-app-locales "$PKG" --locales ar-SA >/dev/null 2>&1
  adb logcat -c
  shot 420_quran_arabic_locale 8 "${R[@]}" --es demo_tab quran
  scroll_to id surah_2 8; into_view surah_2; tap id surah_2 && sleep 5 && cap 421_mushaf_arabic_locale
  S1=$(stored_size 001)
  if ui_has id mushaf_viewer && [ "$S1" = "393966" ] && ! adb logcat -d | grep -q "FATAL EXCEPTION"; then
    check mushaf_arabic_locale PASS "opened surah 2; page 001.pdf in place ($S1 bytes)" critical
  else
    check mushaf_arabic_locale FAIL "viewer=$(ui_has id mushaf_viewer && echo yes || echo no) size='$S1' $(adb logcat -d | grep -A3 'FATAL EXCEPTION' | head -4 | tr '\n' ' ')" critical
  fi
  adb shell cmd locale set-app-locales "$PKG" --locales en-US >/dev/null 2>&1
fi
save_log
}
