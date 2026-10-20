# Round 11: painted headers and icon, widget textures, the khatma and stretched-dhikr widgets, and the next prayer
# on the lock screen. Sourced by ci/screenshots.sh (uses its helpers and phase8.sh's). Defines r11().

r11() {
save_log
adb shell svc power stayon true >/dev/null 2>&1
R=(--ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh,jeddah --es demo_active riyadh --es demo_now "$T_AFTERNOON" "${FREEZE[@]}")

# Painted headers: Quran, adhkar, calendar, and the welcome page.
shot 500_quran_art 7 "${R[@]}" --es demo_tab quran
ui_has id quran_art && check quran_art PASS "painted Quran header" || check quran_art FAIL "no Quran header"
shot 501_adhkar_art 7 "${R[@]}" --es demo_route adhkar
ui_has id adhkar_art && check adhkar_art PASS "painted adhkar header" || check adhkar_art FAIL "no adhkar header"
shot 502_calendar_art 7 "${R[@]}" --es demo_tab calendar
ui_has id cal_header_art && check calendar_art PASS "month on the night sky" || check calendar_art FAIL "no painted month header"
shot 503_calendar_art_dark 7 "${R[@]}" --es demo_tab calendar --es demo_theme DARK
shot 504_welcome_art 7 --ez demo_reset true --es demo_now "$T_AFTERNOON" "${FREEZE[@]}"
ui_has id welcome_art && check welcome_art PASS "welcome painting" || check welcome_art FAIL "no welcome painting"
shot 505_quran_art_off 7 "${R[@]}" --es demo_tab quran --ez demo_hero_art false
ui_has id quran_art && check art_off FAIL "header still painted" || check art_off PASS "plain header when the paintings are off"
# The launcher icon (the app drawer).
adb shell input keyevent 3; sleep 2; adb shell input swipe 540 1800 540 600 300; sleep 2; cap 506_app_drawer; adb shell input keyevent 3

# Gallery: the new widgets and a texture background.
WB=(--ez demo_onboarded true --es demo_cities riyadh,jeddah --es demo_active riyadh --es demo_now "$T_AFTERNOON" --es demo_khatma 30:120:5 "${FREEZE[@]}")
start "${R[@]}" --es demo_khatma 30:120:5 --es demo_route widgets; sleep 7
scroll_to id gallery_khatma 40; into_view gallery_khatma; cap 510_gallery_khatma
K=$(ui_text w_big); [ -n "$K" ] && check khatma_preview PASS "$K" || check khatma_preview FAIL "'$K'"
scroll_to id gallery_dhikr 40; into_view gallery_dhikr; cap 511_gallery_dhikr
D=$(ui_text w_dayname); [ -n "$D" ] && check dhikr_preview PASS "$D" || check dhikr_preview FAIL "empty"
scroll_top
for bg in geometric paper night; do
  scroll_top; scroll_to id "gallery_bg_$bg" 40; into_view "gallery_bg_$bg"; tap id "gallery_bg_$bg" && sleep 2
  scroll_to id gallery_day 10; into_view gallery_day; cap "512_gallery_$bg"
done
wlog | grep -q "preview failed\|render failed" && check r11_previews FAIL "$(wlog | grep failed | head -2)" || check r11_previews PASS "previews rendered"

# On the home screen.
pin_kind khatma 515_pin_khatma
pin_kind dhikr 516_pin_dhikr
if home_find id w_progress; then cap 517_home_khatma; check khatma_home PASS "khatma on the home screen"; else check khatma_home FAIL "not on the home screen"; fi
# The dhikr widget with a texture background.
start "${WB[@]}" --es demo_route widgets; sleep 6
scroll_to id placed_settings_dhikr 40; into_view placed_settings_dhikr; tap id placed_settings_dhikr && sleep 4
into_view wcfg_background_night; tap id wcfg_background_night && sleep 1 && cap 518_dhikr_config_night
scroll_top; tap id wcfg_save && sleep 4
widgets_json | grep -q '"background":"NIGHT"' && check texture_saved PASS "night lattice saved" || check texture_saved FAIL "$(widgets_json | grep -o '"background":"[A-Z]*"' | tr '\n' ' ')"
if home_find id w_next_btn; then cap 519_home_dhikr_night; fi

# The next prayer on the lock screen.
if [ "$SDK" -ge 33 ]; then adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS; fi
start "${R[@]}" --es demo_route widgets; sleep 6
scroll_to id lock_note_switch 40; into_view lock_note_switch; tap id lock_note_switch && sleep 4
settings_json | grep -q '"lockNote":true' && check lock_note_saved PASS "turned on" || check lock_note_saved FAIL "not saved"
N=$(adb shell dumpsys notification --noredact 2>/dev/null | grep -c "id=4200")
[ "$N" -ge 1 ] && check lock_note_posted PASS "next prayer notification posted" || check lock_note_posted FAIL "not posted"
shade_open; cap 520_lock_note_shade; shade_close
adb shell input keyevent 26; sleep 2; adb shell input keyevent 26; sleep 2; cap 521_lock_screen
adb shell input keyevent 82; adb shell wm dismiss-keyguard >/dev/null 2>&1; sleep 1
save_log
}
