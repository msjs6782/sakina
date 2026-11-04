# Round 16: the printed Mushaf made interactive (a long press opens an ayah; the ayah held, recited or pointed to is
# washed in gold; the edition's own page breaks), the prayer log's statistics and make-up prayers, and fasts marked on
# the calendar. Sourced by ci/screenshots.sh (uses its helpers and phase8.sh's). Defines r16().

tools_json16() { adb shell run-as "$PKG" cat files/datastore/tools.json 2>/dev/null | tr -d '\r'; }

r16() {
save_log
adb shell svc power stayon true >/dev/null 2>&1
N0=(--ez demo_onboarded true --es demo_cities riyadh --es demo_active riyadh --es demo_now "$T_AFTERNOON" "${FREEZE[@]}")
N=(--ez demo_reset true "${N0[@]}")

# ---- The Mushaf: the hint, then a long press on an ayah of page 295 (18:16–20).
adb logcat -c
start "${N[@]}" --es demo_quran auto=1 --es demo_route mushaf:295
wait_log "filled page 295" 120; sleep 3
ui_has id mushaf_hold_hint && check mushaf_hold_hint PASS "hint shown once" || check mushaf_hold_hint FAIL "no hint"
cap 880_mushaf_hint
adb shell input swipe 540 760 540 760 1000; sleep 3
cap 881_mushaf_ayah_sheet
if ui_has id ayah_bookmark && ui_has contains "الكهف"; then check mushaf_hold_ayah PASS "ayah sheet from the page"; else check mushaf_hold_ayah FAIL "no ayah sheet"; fi
ui_has id ayah_listen && check mushaf_ayah_listen PASS "listen from the ayah" || check mushaf_ayah_listen FAIL "no listen action"
ui_has id mushaf_ayah_highlight && check mushaf_hold_highlight PASS "the held ayah is washed" || check mushaf_hold_highlight FAIL "no highlight"
adb shell input keyevent 4; sleep 2
ui_has id mushaf_hold_hint && check mushaf_hint_gone FAIL "hint still shown" || check mushaf_hint_gone PASS "hint gone after the first hold"
# Opened at an ayah: it is washed in gold on the page (controls hidden), at night and zoomed too.
start "${N0[@]}" --es demo_route mushaf:295:18:18; sleep 9
adb shell input tap 540 1300; sleep 2; try_tap button "got it"; sleep 1
cap 882_mushaf_focus_ayah
ui_has id mushaf_ayah_highlight && check mushaf_focus_highlight PASS "18:18 washed" || check mushaf_focus_highlight FAIL "no highlight"
shot 883_mushaf_focus_dark 10 "${N0[@]}" --es demo_quran night=DARK --es demo_route mushaf:295:18:19
shot 884_mushaf_focus_zoomed 12 "${N0[@]}" --es demo_route mushaf:295:18:17 --ef demo_zoom 2
shot 885_mushaf_focus_printed 10 "${N0[@]}" --es demo_quran fill=0 --es demo_route mushaf:295:18:18
# This edition's page breaks: page 586 opens with the end of Abasa (Tanzil's index has At-Takwir there).
adb logcat -c
start "${N0[@]}" --es demo_quran auto=1 --es demo_route mushaf:586
wait_log "filled page 586" 120; sleep 3; cap 886_mushaf_page586
ui_has contains "عبس" && ui_has contains "التكوير" && check mushaf_edition_pages PASS "page 586: Abasa and At-Takwir" || check mushaf_edition_pages FAIL "page 586 surahs wrong"

# ---- The prayer log: thirty days, each prayer's share, the best run, and make-up prayers.
start "${N[@]}"; sleep 7
scroll_to id prayed_fajr 4; tap id prayed_fajr; tap id prayed_dhuhr; sleep 1
start "${N0[@]}" --es demo_route prayer_log; sleep 6
scroll_to id prayer_stats_bars 6; into_view prayer_stats; cap 890_prayer_stats
S=$(ui_text prayer_share_fajr); [ -n "$S" ] && check prayer_stats PASS "Fajr $S" || check prayer_stats FAIL "no shares"
scroll_to id qada_add_day 8
tap id qada_add_day; tap id qada_plus_fajr; tap id qada_plus_fajr; tap id qada_done_fajr; sleep 1
into_view qada_card; cap 891_qada
L=$(ui_text qada_left_fajr); [ "$L" = "٢" ] && check qada_count PASS "Fajr left $L" || check qada_count FAIL "'$L'"
tools_json16 | grep -q '"qada":{"owed":{' && check qada_saved PASS "$(tools_json16 | grep -o '"qada":{[^}]*}[^}]*}')" || check qada_saved FAIL "not saved"
shot 892_prayer_log_dark 7 "${N0[@]}" --es demo_route prayer_log --es demo_theme DARK
scroll_to id qada_card 8; into_view qada_card; cap 893_qada_dark

# ---- Fasts on the calendar (today is 14 October 2026).
start "${N[@]}" --es demo_route calendar_day:2026-10-13; sleep 6
scroll_to id day_my_fast 8; into_view day_my_fast
tap id day_fast_voluntary; tap id day_fast_owed_plus; tap id day_fast_owed_plus; tap id day_fast_owed_plus; sleep 1
cap 894_day_my_fast
tools_json16 | grep -q '"2026-10-13":1' && check fast_saved PASS "13 October fasted" || check fast_saved FAIL "$(tools_json16 | grep -o '"fasts":{[^}]*}')"
start "${N0[@]}" --es demo_route calendar_day:2026-10-12; sleep 6
scroll_to id day_my_fast 8; into_view day_my_fast; tap id day_fast_qada; sleep 1
O=$(ui_text day_fast_owed); echo "$O" | grep -q "٢" && check fast_owed PASS "$O" || check fast_owed FAIL "'$O'"
cap 895_day_fast_qada
start "${N0[@]}" --es demo_route calendar_day:2026-10-20; sleep 6
scroll_to id day_my_fast 8; into_view day_my_fast
ui_has contains "يُسجَّل الصيام في يومه أو بعده" && check fast_future PASS "not ahead of time" || check fast_future FAIL "future day markable"
shot 896_calendar_fasts 7 "${N0[@]}" --es demo_tab calendar
ui_has id cal_fasted_2026-10-13 && check cal_fasted PASS "marked on the month" || check cal_fasted FAIL "no mark on the month"
scroll_to id cal_fast_summary 5; T=$(ui_text cal_fast_summary); echo "$T" | grep -q "٢" && check cal_fast_summary PASS "$T" || check cal_fast_summary FAIL "'$T'"
cap 897_calendar_fast_summary
save_log
}
