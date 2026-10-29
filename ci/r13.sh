# Round 13: the prayer log, silent at prayer time, and the tasbih's week. Sourced by ci/screenshots.sh.

tjson() { adb shell run-as "$PKG" cat files/datastore/tools.json 2>/dev/null | tr -d '\r'; }
ringer() { adb shell settings get global mode_ringer 2>/dev/null | tr -d '\r'; }

r13() {
save_log
adb shell svc power stayon true >/dev/null 2>&1
P=(--ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh --es demo_active riyadh --es demo_now "$T_AFTERNOON" "${FREEZE[@]}")

# Home: a mark beside each prayer whose time has come (16:20 in Riyadh: Fajr, Dhuhr and Asr).
shot 700_home_marks 7 "${P[@]}"
scroll_to id prayed_fajr 4 && check prayed_marks PASS "marks beside the passed prayers" || check prayed_marks FAIL "no mark"
ui_has id prayed_maghrib && check prayed_future FAIL "a mark on a prayer still to come" || check prayed_future PASS "no mark before its time"
tap id prayed_fajr && sleep 1 && tap id prayed_dhuhr && sleep 2 && cap 701_home_marked
tjson | grep -q '"prayed":{"2026-10-14":3}' && check prayed_saved PASS "Fajr and Dhuhr logged" || check prayed_saved FAIL "$(tjson | grep -o '"prayed":{[^}]*}')"
scroll_to id home_open_prayer_log 4 && tap id home_open_prayer_log && sleep 3 && cap 702_prayer_log
T=$(ui_text prayer_log_today); echo "$T" | grep -q "٢" && check prayer_log_today PASS "$T" || check prayer_log_today FAIL "'$T'"
tap id log_2026-10-13_isha && sleep 1
tjson | grep -q '"2026-10-13":16' && check prayer_log_fix PASS "a past day corrected from the log" || check prayer_log_fix FAIL "$(tjson | grep -o '"prayed":{[^}]*}')"
shot 703_prayer_log_dark 7 "${P[@]}" --es demo_route prayer_log --es demo_theme DARK

# Silent at prayer time: the settings, then the phone really set to vibrate and given its sound back.
start "${P[@]}" --es demo_route alerts; sleep 6
scroll_to id silence_switch 12; into_view silence_switch; tap id silence_switch && sleep 1
into_view silence_card; cap 710_silence_settings
settings_json | grep -q '"silence":{"enabled":true' && check silence_saved PASS "turned on" || check silence_saved FAIL "$(settings_json | grep -o '"silence":{[^}]*}')"
adb shell cmd audio set-ringer-mode NORMAL >/dev/null 2>&1; adb shell settings put global mode_ringer 2 >/dev/null 2>&1
R0=$(ringer)
adb shell am start -n "$PKG/$ACT" --es demo_silence on >/dev/null; sleep 3
R1=$(ringer)
shade_open; cap 711_silence_notification; shade_close
adb shell am start -n "$PKG/$ACT" --es demo_silence off >/dev/null; sleep 3
R2=$(ringer)
[ "$R1" = "1" ] && [ "$R2" = "2" ] && check silence_works PASS "ringer $R0 → vibrate → $R2" || check silence_works FAIL "ringer $R0 → $R1 → $R2"

# The tasbih keeps a count per day.
start "${P[@]}" --es demo_route tasbih; sleep 5
for _ in 1 2 3; do tap id tasbih_tap; done
sleep 1; scroll_to id tasbih_week 4; cap 720_tasbih_week
T=$(ui_text tasbih_today); echo "$T" | grep -q "٣" && check tasbih_day PASS "$T" || check tasbih_day FAIL "'$T'"

# Backup: the screen and the system's save dialog (the file itself is checked by the unit tests).
shot 730_backup 6 "${P[@]}" --es demo_route backup
tap id backup_save && sleep 4 && cap 731_backup_save_dialog
ui_has contains "sakina-backup" && check backup_dialog PASS "save dialog with the file name" || check backup_dialog FAIL "no save dialog"
adb shell input keyevent 4; sleep 2
save_log
}
