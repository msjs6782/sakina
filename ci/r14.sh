# Round 14: the time to and since each prayer on Home, and the calendar's raised look (depth, gold, the moon,
# months turning in 3D). Sourced by ci/screenshots.sh (uses its helpers and phase8.sh's). Defines r14().

r14() {
save_log
adb shell svc power stayon true >/dev/null 2>&1
Q0=(--ez demo_onboarded true --es demo_cities riyadh --es demo_active riyadh --es demo_now "$T_AFTERNOON" "${FREEZE[@]}")
Q=(--ez demo_reset true "${Q0[@]}")

# Home at 16:20 in Riyadh: "منذ" beside Fajr, Dhuhr and Asr, "باقي" beside Maghrib (next, outlined) and Isha.
shot 800_home_chips 7 "${Q[@]}"
scroll_to id chip_isha 4; bring_up chip_isha; cap 801_home_chips_list
F=$(ui_text chip_fajr); M=$(ui_text chip_maghrib); I=$(ui_text chip_isha)
echo "$F" | grep -q "منذ" && echo "$M" | grep -q "باقي" && echo "$I" | grep -q "باقي" \
  && check prayer_chips PASS "$F / $M / $I" || check prayer_chips FAIL "'$F' / '$M' / '$I'"
# Maghrib is at 17:27: 1 h 7 min from 16:20 (the exact minutes depend on the calculation; the hour must be there).
echo "$M" | grep -q "١ س" && check prayer_chip_value PASS "$M" || check prayer_chip_value FAIL "'$M'"
shot 802_home_chips_dark 7 "${Q[@]}" --es demo_theme DARK
scroll_to id chip_isha 4; bring_up chip_isha; cap 803_home_chips_dark_list
shot 804_home_chips_english 7 "${Q[@]}" --es demo_lang en
scroll_to id chip_isha 4; bring_up chip_isha; cap 805_home_chips_english_list
F=$(ui_text chip_fajr); M=$(ui_text chip_maghrib)
echo "$F" | grep -q "ago" && echo "$M" | grep -q "^in " && check prayer_chips_en PASS "$F / $M" || check prayer_chips_en FAIL "'$F' / '$M'"
# They can be turned off.
start "${Q[@]}" --es demo_route home; sleep 6
scroll_to id prayer_chips_switch 10; into_view prayer_chips_switch; cap 806_chips_setting
tap id prayer_chips_switch && sleep 2
settings_json | grep -q '"prayerChips":false' && check prayer_chips_setting PASS "turned off" || check prayer_chips_setting FAIL "$(settings_json | grep -o '"prayerChips":[a-z]*')"
start "${Q0[@]}"; sleep 7
scroll_to id prayed_fajr 4
ui_has id chip_fajr && check prayer_chips_hidden FAIL "chips still shown" || check prayer_chips_hidden PASS "no chips when turned off"

# The calendar: raised tiles, the gold-edged month on the night sky, the switch and the Today pill.
shot 810_calendar_raised 7 "${Q[@]}" --es demo_tab calendar
ui_has id cal_header_art && ui_has id cal_kind_hijri && ui_has id cal_today && ui_has id cal_day_2026-10-14 \
  && check cal_raised PASS "$(ui_text cal_title)" || check cal_raised FAIL "parts missing"
scroll_to id cal_upcoming 5; bring_up cal_upcoming; cap 811_calendar_upcoming
shot 812_calendar_raised_dark 7 "${Q[@]}" --es demo_tab calendar --es demo_theme DARK
shot 813_calendar_raised_english 7 "${Q[@]}" --es demo_tab calendar --es demo_lang en
shot 814_calendar_plain 7 "${Q[@]}" --es demo_tab calendar --ez demo_hero_art false
ui_has id cal_header && check cal_plain PASS "plain header without the painting" || check cal_plain FAIL "no plain header"
# Gregorian through the switch.
start "${Q[@]}" --es demo_tab calendar; sleep 6
tap id cal_kind_gregorian && sleep 2 && cap 815_calendar_gregorian
T=$(ui_text cal_title); echo "$T" | grep -q "أكتوبر" && check cal_switch PASS "$T" || check cal_switch FAIL "'$T'"
tap id cal_kind_hijri && sleep 2
# The month turning, caught halfway (a held drag; Android 11+ has motionevent).
if [ "$SDK" -ge 30 ]; then
  adb shell input motionevent DOWN 300 1300; sleep 0.2
  for x in 360 420 480 540 600; do adb shell input motionevent MOVE "$x" 1300; sleep 0.05; done
  sleep 0.6; cap 816_calendar_turning
  adb shell input motionevent UP 600 1300; sleep 2
fi
# A day's sheet with its moon: 15 Jumada al-Ula is full, 3 Jumada al-Ula a young crescent.
start "${Q[@]}" --es demo_route calendar_day:2026-10-26; sleep 7
cap 817_day_full_moon
ui_has id day_moon && check day_moon PASS "moon beside the dates" || check day_moon FAIL "no moon"
start "${Q[@]}" --es demo_route calendar_day:2026-10-14 --es demo_theme DARK; sleep 7
cap 818_day_crescent_dark
shot 819_calendar_list_raised 8 "${Q[@]}" --ez demo_cal_list true --es demo_route month
save_log
}
