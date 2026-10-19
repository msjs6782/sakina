# Painted skies (Higgsfield art): the prayer card through the day, styles and themes, and widgets on the painted
# sky. Sourced by ci/screenshots.sh (uses its helpers and phase8.sh's pin helpers). Defines visual().

visual() {
save_log
adb shell svc power stayon true >/dev/null 2>&1
VB=(--ez demo_onboarded true --es demo_cities riyadh,jeddah --es demo_active riyadh "${FREEZE[@]}")
# One moment in each period at Riyadh on 14-15 Oct 2026 (Fajr 4:32, sunrise 5:51, Dhuhr 11:39, Asr 2:58, Maghrib 5:27, Isha 6:57).
for pair in fajr:2026-10-15T01:50:00Z morning:2026-10-14T05:00:00Z noon:2026-10-14T09:30:00Z asr:2026-10-14T13:20:00Z maghrib:2026-10-14T14:45:00Z night:2026-10-14T18:30:00Z; do
  p=${pair%%:*}; t=${pair#*:}
  shot "300_hero_$p" 7 --ez demo_reset true "${VB[@]}" --es demo_now "$t"
  ui_has id "hero_sky_$p" && check "hero_sky_$p" PASS "painting for $p" || check "hero_sky_$p" FAIL "no $p painting on the card"
done
# Dark theme, English, the other layouts and styles keep the painting; turned off, the style's colours return.
shot 306_hero_maghrib_dark 7 --ez demo_reset true "${VB[@]}" --es demo_now 2026-10-14T14:45:00Z --es demo_theme DARK
shot 307_hero_noon_english 7 --ez demo_reset true "${VB[@]}" --es demo_now 2026-10-14T09:30:00Z --es demo_lang en
shot 308_hero_focus_night 7 --ez demo_reset true "${VB[@]}" --es demo_now 2026-10-14T18:30:00Z --es demo_layout FOCUS
shot 309_hero_dashboard_asr 7 --ez demo_reset true "${VB[@]}" --es demo_now 2026-10-14T13:20:00Z --es demo_layout DASHBOARD
shot 310_hero_raml_morning 7 --ez demo_reset true "${VB[@]}" --es demo_now 2026-10-14T05:00:00Z --es demo_style RAML
ui_has id hero_sky_morning && check hero_sky_raml PASS "painting over the sand style" || check hero_sky_raml FAIL "no painting with Raml"
shot 311_hero_art_off 7 --ez demo_reset true "${VB[@]}" --es demo_now 2026-10-14T13:20:00Z --ez demo_hero_art false
ui_has id hero_sky_asr && check hero_art_off FAIL "painting still shown" || check hero_art_off PASS "style colours when turned off"
shot 312_onboarding_fajr 7 --ez demo_reset true --es demo_now 2026-10-14T13:20:00Z "${FREEZE[@]}"
# The switch in Appearance turns it off and on.
start --ez demo_reset true "${VB[@]}" --es demo_now 2026-10-14T13:20:00Z --es demo_route appearance; sleep 6
scroll_to exact "لوحة السماء في بطاقة الصلاة" 6 && cap 313_appearance_switch && tap exact "لوحة السماء في بطاقة الصلاة" && sleep 1
settings_json | grep -q '"heroArt":false' && check hero_art_switch PASS "turned off from Appearance" || check hero_art_switch FAIL "$(settings_json | grep -o '"heroArt":[a-z]*')"

# Widgets: the gallery on the painted sky, then a real widget switched to it.
shot 320_gallery_sky 8 --ez demo_reset true "${VB[@]}" --es demo_now 2026-10-14T14:45:00Z --es demo_route widgets --ez demo_gallery_sky true
for k in next countdown day mini strip digital analog content fajr_maghrib; do
  scroll_to id "gallery_$k" 14; into_view "gallery_$k"; cap "321_gallery_sky_$k"
done
wlog | grep -q "preview failed\|render failed" && check widget_sky_previews FAIL "$(wlog | grep failed | head -2)" || check widget_sky_previews PASS "previews rendered on the sky"
WB=(--ez demo_onboarded true --es demo_cities riyadh,jeddah --es demo_active riyadh --es demo_now 2026-10-14T14:45:00Z "${FREEZE[@]}")
start --ez demo_reset true "${WB[@]}"; sleep 5
pin_kind next 330_pin_next
pin_kind strip 331_pin_strip
for k in next strip; do
  start "${WB[@]}" --es demo_route widgets; sleep 6
  scroll_to id "placed_settings_$k" 16; into_view "placed_settings_$k"; tap id "placed_settings_$k" && sleep 4
  into_view wcfg_background_sky; tap id wcfg_background_sky && sleep 1 && cap "332_config_sky_$k"
  scroll_top; tap id wcfg_save && sleep 4
done
widgets_json | grep -o '"background":"SKY"' | wc -l | grep -q 2 && check widget_sky_saved PASS "two widgets on the painted sky" || check widget_sky_saved FAIL "$(widgets_json | grep -o '"background":"[A-Z]*"' | tr '\n' ' ')"
if home_find id w_sky; then
  cap 333_home_sky_widgets
  check widget_sky_home PASS "painted sky on the home screen"
else
  check widget_sky_home FAIL "no painted sky on the home screen"; cap 333_home_sky_missing
fi
adb shell input swipe 980 1450 100 1450 300; sleep 2; cap 334_home_page_left
adb shell input keyevent 3; sleep 2; adb shell input swipe 100 1450 980 1450 300; sleep 2; cap 335_home_page_right
save_log
}
