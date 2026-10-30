# Round 15: the printed Mushaf filling the screen (centred, the lines spread to its height), and the widgets'
# raised look, gold progress line and the Hijri calendar widget. Sourced by ci/screenshots.sh (uses its helpers
# and phase8.sh's). Defines r15().

r15() {
save_log
adb shell svc power stayon true >/dev/null 2>&1
M5=(--ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh --es demo_now "$T_AFTERNOON" "${FREEZE[@]}")

# ---- The Mushaf. Page 295 is a right-hand page: its frame sits towards the left of the paper, a hizb mark beside it.
adb logcat -c
start "${M5[@]}" --es demo_quran auto=1 --es demo_route mushaf:295
if wait_log "filled page 295" 120; then
  sleep 3; cap 850_mushaf_filled_295
  check mushaf_fill PASS "$(mlog | grep -o 'filled page 295: .*' | head -1)"
else
  cap 850_mushaf_filled_295
  check mushaf_fill FAIL "$(mlog | grep -E 'page 295|cannot' | tail -3 | tr '\n' ' ')"
fi
ui_has id mushaf_filled && check mushaf_filled_view PASS "page drawn to fill the screen" || check mushaf_filled_view FAIL "no filled page on screen"
MK=$(ui_text mushaf_hizb_mark); [ "$MK" = "ربع الحزب ٣٠" ] && check mushaf_hizb_mark PASS "$MK" || check mushaf_hizb_mark FAIL "'$MK'"
adb shell input tap 540 1300; sleep 2; cap 851_mushaf_filled_full
# The next page (296) is a left-hand page: its frame is on the other side of the paper, centred here too.
adb shell input swipe 150 1300 950 1300 250
if wait_log "filled page 296" 120; then
  sleep 3; check mushaf_fill_left PASS "$(mlog | grep -o 'filled page 296: .*' | head -1)"
else
  check mushaf_fill_left FAIL "$(mlog | grep -E 'page 296' | tail -2 | tr '\n' ' ')"
fi
cap 852_mushaf_filled_296
# Zoomed: the visible part drawn again from the PDF at full sharpness, the lines still spread.
adb logcat -c
shot 853_mushaf_filled_zoomed 12 "${M5[@]}" --es demo_route mushaf:295 --ef demo_zoom 2.5
mlog | grep -q "rendered 295.pdf .*fill region" && check mushaf_fill_zoom PASS "$(mlog | grep -o 'rendered 295.pdf [0-9x]* fill region in [0-9]* ms' | head -1)" || check mushaf_fill_zoom FAIL "no zoomed drawing"
shot 854_mushaf_filled_dark 10 "${M5[@]}" --es demo_quran night=DARK --es demo_route mushaf:295
shot 855_mushaf_filled_warm 10 "${M5[@]}" --es demo_quran night=WARM --es demo_route mushaf:296
# The option off: the page as printed, for comparison.
shot 856_mushaf_as_printed 10 "${M5[@]}" --es demo_quran fill=0 --es demo_route mushaf:295
ui_has id mushaf_filled && check mushaf_fill_off FAIL "still filled" || check mushaf_fill_off PASS "as printed when turned off"
# The opening pages keep their own ornate frame.
adb logcat -c
shot 857_mushaf_page1 9 "${M5[@]}" --es demo_route mushaf:1
mlog | grep -q "frame page 1: none" && check mushaf_opening_printed PASS "page 1 as printed" || check mushaf_opening_printed FAIL "$(mlog | grep 'frame page 1' | head -1)"
start "${M5[@]}" --es demo_route mushaf:295; sleep 8
tap id mushaf_menu && sleep 1 && cap 858_mushaf_menu
ui_has id mushaf_fill && check mushaf_fill_option PASS "option in the menu" || check mushaf_fill_option FAIL "no option"
adb shell input keyevent 4; sleep 1

# ---- Widgets: the raised look and the progress line in the gallery (16:20 in Riyadh, Maghrib next).
WB=(--ez demo_onboarded true --es demo_cities riyadh,jeddah,makkah --es demo_active riyadh --es demo_now "$T_AFTERNOON" "${FREEZE[@]}")
W=(--ez demo_reset true "${WB[@]}")
start "${W[@]}" --es demo_route widgets; sleep 7
i=0
for k in next countdown day glass mini strip digital analog dayname calendar; do
  scroll_to id "gallery_$k" 40; into_view "gallery_$k"; cap "86${i}_gallery_$k"; i=$((i + 1))
done
ui_has contains "جمادى الأولى ١٤٤٨" && check widget_calendar_preview PASS "$(ui_text w_cal)" || check widget_calendar_preview FAIL "no month in the calendar widget"
wlog | grep -q "preview failed\|render failed" && check widget_previews_r15 FAIL "$(wlog | grep -E 'failed' | head -2)" || check widget_previews_r15 PASS "all rendered"
# On the painted sky and in the dark.
start "${W[@]}" --es demo_route widgets; sleep 7
scroll_to id gallery_bg_sky 10; tap id gallery_bg_sky; sleep 2
for k in next day calendar; do scroll_to id "gallery_$k" 40; into_view "gallery_$k"; cap "870_gallery_sky_$k"; done
start "${W[@]}" --es demo_route widgets --es demo_theme DARK; sleep 7
for k in next day calendar; do scroll_to id "gallery_$k" 40; into_view "gallery_$k"; cap "871_gallery_dark_$k"; done
start "${W[@]}" --es demo_route widgets --es demo_lang en; sleep 7
scroll_to id gallery_calendar 40; into_view gallery_calendar; cap 872_gallery_english_calendar

# ---- On the home screen: the calendar and the next prayer with its line.
pin_kind calendar 873_pin_dialog_calendar
if home_find id w_cal; then cap 874_home_calendar; check widget_home_calendar PASS "on the home screen"; else cap 874_home_calendar; check widget_home_calendar FAIL "not found on the home screen"; fi
pin_kind day 875_pin_dialog_day
if home_find id w_cells; then cap 876_home_day; check widget_home_day PASS "$(ui_text w_n4) $(ui_text w_t4)"; else check widget_home_day FAIL "not found"; fi
# Its settings: the look section.
start "${WB[@]}" --es demo_route widgets; sleep 6
scroll_to id placed_settings_calendar 10 && tap id placed_settings_calendar && sleep 3
scroll_to id wcfg_look 10; into_view wcfg_look; cap 877_config_look
ui_has id wcfg_depth && check widget_look_setting PASS "raised look setting" || check widget_look_setting FAIL "no look setting"
adb shell input keyevent 4; sleep 1
save_log
}
