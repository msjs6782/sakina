# Phase 8: home-screen widgets, icon shortcuts and Quick Settings tiles. Sourced by ci/screenshots.sh,
# which provides the helpers (tap, scroll_to, ui_text, check, cap...). Defines phase8().

wlog() { adb logcat -d -s SakinaWidgets:* SakinaOpen:* SakinaShortcuts:* SakinaTiles:* 2>/dev/null; }
wait_wlog() { for _ in $(seq 1 "$2"); do wlog | grep -qE "$1" && return 0; sleep 1; done; return 1; }
# Tap without counting a failure (for dialogs that differ between launchers).
try_tap() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT/.ui.xml" 2>/dev/null
  local xy; xy=$(python3 "$HERE/find_node.py" "$OUT/.ui.xml" "$2" "$1") || return 1
  adb shell input tap $xy; sleep 1.5
}
# Accepts the launcher's "add widget/shortcut" dialog (Launcher3 wording differs across versions).
pin_accept() {
  sleep 3
  try_tap button "add automatically" || try_tap button "add to home" || try_tap button "add widget" || try_tap button "add"
  sleep 4
  # Some launchers open the widget's own settings after placing it; keep the defaults.
  if ui_has id wcfg_save; then tap id wcfg_save; sleep 3; fi
}
# Home screen, then the next pages, until a node is on screen.
home_find() {
  adb shell input keyevent 3; sleep 3
  ui_has "$1" "$2" && return 0
  # Pages to one side, then to the other (the launcher may open on any page). Swipes run below the widgets.
  for dir in left right; do
    for _ in 1 2 3; do
      if [ "$dir" = left ]; then adb shell input swipe 980 1450 100 1450 300; else adb shell input swipe 100 1450 980 1450 300; fi
      sleep 2
      ui_has "$1" "$2" && return 0
    done
  done
  return 1
}
# Scrolls until a node's centre sits in the middle band of the screen (off-screen nodes can be in the dump).
into_view() {
  local xy y
  for _ in 1 2 3 4 5 6 7 8; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb shell cat /sdcard/ui.xml > "$OUT/.ui.xml" 2>/dev/null
    xy=$(python3 "$HERE/find_node.py" "$OUT/.ui.xml" "$1" id) || { adb shell input swipe 540 1700 540 1000 500; sleep 1; continue; }
    y=${xy#* }
    if [ "$y" -gt 1750 ]; then adb shell input swipe 540 1700 540 1100 500; sleep 1
    elif [ "$y" -lt 350 ]; then adb shell input swipe 540 1000 540 1600 500; sleep 1
    else return 0; fi
  done
  return 1
}
widgets_json() { adb shell run-as "$PKG" cat files/datastore/widgets.json 2>/dev/null; }
scroll_top() { for _ in 1 2 3 4 5 6; do adb shell input swipe 540 700 540 1800 250; done; sleep 1; }
pin_kind() {  # pin_kind <kind> <shot>
  start "${WB[@]}" --es demo_route widgets; sleep 6
  scroll_to id "pin_$1" 14; into_view "pin_$1"; tap id "pin_$1" && cap "$2"
  pin_accept
  wait_wlog "pinned $1 " 8 && check "widget_pinned_$1" PASS "placed by the launcher" || check "widget_pinned_$1" FAIL "no pin callback ($(wlog | grep -E 'pin requested' | tail -1))"
}

phase8() {
save_log
# Widgets are redrawn by alarms that wait while the screen is off: keep it on for these checks.
adb shell svc power stayon true >/dev/null 2>&1
WB=(--ez demo_onboarded true --es demo_cities riyadh,jeddah,makkah --es demo_active riyadh --es demo_now "$T_AFTERNOON" "${FREEZE[@]}")
W=(--ez demo_reset true "${WB[@]}")

# ---- The gallery: native previews (the same RemoteViews the launcher gets) at 16:20 in Riyadh, Maghrib next.
shot 240_widgets_gallery 8 "${W[@]}" --es demo_tab discover
scroll_to id tool_widgets && tap id tool_widgets && sleep 3 && cap 241_widgets_screen
scroll_to id w_remain 10
N=$(ui_text w_name); R=$(ui_text w_remain)
[ "$N" = "المغرب" ] && echo "$R" | grep -q "حُدّث" && check widget_preview_next PASS "$N · $R" || check widget_preview_next FAIL "name='$N' remain='$R'"
for k in next countdown day glass mini strip digital analog content fajr_maghrib; do
  scroll_to id "gallery_$k" 14; into_view "gallery_$k"; cap "242_gallery_$k"
  case $k in
    countdown) B=$(ui_text w_big); [ -n "$B" ] && check widget_preview_countdown PASS "$B" || check widget_preview_countdown FAIL "no big remaining" ;;
    strip) S=$(ui_text w_n0); [ "$S" = "الفجر" ] && check widget_preview_strip PASS "$S" || check widget_preview_strip FAIL "'$S'" ;;
    content) SRC=$(ui_text w_source); echo "$SRC" | grep -qE "سورة|صحيح|سنن|جامع" && check widget_preview_content PASS "$SRC" || check widget_preview_content FAIL "'$SRC'" ;;
    fajr_maghrib) RM=$(ui_text w_ramadan); echo "$RM" | grep -q "رمضان بعد" && check widget_preview_ramadan PASS "$RM" || check widget_preview_ramadan FAIL "'$RM'" ;;
  esac
done
wlog | grep -q "preview failed\|render failed" && check widget_previews_render FAIL "$(wlog | grep -E 'failed' | head -2)" || check widget_previews_render PASS "all ten rendered"

# ---- Each widget at a small, the default and a large size: text is sized to fit, never cut.
if [ "$SUITE" = "full" ]; then
  shot 243_sizes 8 "${W[@]}" --es demo_route widgets --ez demo_widget_sizes true
  for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18; do adb shell input swipe 540 1700 540 600 500; sleep 1.2; cap "243_sizes_$i"; done
  wlog | grep -q "fit overflow" && check widget_fit FAIL "$(wlog | grep 'fit overflow' | head -3 | tr '\n' ' ')" || check widget_fit PASS "no overflow at any sample size"
  shot 244_gallery_english_dark 8 "${W[@]}" --es demo_lang en --es demo_theme DARK --es demo_route widgets
  scroll_to id gallery_next 10 && bring_up gallery_next && cap 244b_gallery_en_next
  scroll_to id gallery_glass 14 && bring_up gallery_glass && cap 244c_gallery_en_glass
  scroll_to id gallery_analog 14 && bring_up gallery_analog && cap 244d_gallery_en_analog
fi

# ---- Pinning from the gallery puts real widgets on the launcher's home screen.
start "${W[@]}"; sleep 6
pin_kind next 250_pin_dialog_next
if home_find id w_name; then
  cap 251_home_next
  N=$(ui_text w_name); R=$(ui_text w_remain); ST=$(ui_text w_stamp)
  [ "$N" = "المغرب" ] && echo "$R $ST" | grep -q "حُدّث" && check widget_home_next PASS "$N · $R $ST" || check widget_home_next FAIL "name='$N' remain='$R' stamp='$ST'"
else
  check widget_home_next FAIL "not found on the home screen"; cap 251_home_next_missing
fi
pin_kind analog 252_pin_dialog_analog
pin_kind strip 253_pin_dialog_strip
if [ "$SUITE" = "full" ]; then
  pin_kind content 254_pin_dialog_content
  pin_kind fajr_maghrib 255_pin_dialog_fajr_maghrib
else
  pin_kind digital 254_pin_dialog_digital
fi
adb shell input keyevent 3; sleep 3; cap 256_home_page1
adb shell input swipe 980 1450 100 1450 300; sleep 2; cap 257_home_page_left
adb shell input keyevent 3; sleep 2; adb shell input swipe 100 1450 980 1450 300; sleep 2; cap 258_home_page_right

# The analog clock: the place's own time on Android 12+, the device clock (labelled) before it.
OFFS=$(adb shell date +%z | tr -d '\r')
if home_find id w_dial; then
  cap 259_home_analog
  if [ "$SDK" -lt 31 ] && [ "$OFFS" != "+0300" ]; then
    ui_has contains "ساعة الجهاز" && check widget_analog_label PASS "device clock labelled (device $OFFS)" || check widget_analog_label FAIL "no device-clock label (device $OFFS)"
  else
    ui_has contains "ساعة الجهاز" && check widget_analog_label FAIL "labelled as device clock (device $OFFS)" || check widget_analog_label PASS "the place's own clock (device $OFFS)"
  fi
else
  check widget_analog_label FAIL "analog widget not found"
fi
if home_find id w_n0; then
  S=$(ui_text w_n0); [ "$S" = "الفجر" ] && check widget_home_strip PASS "$S" || check widget_home_strip FAIL "'$S'"
fi

# Taps: the widget opens the prayer times; its refresh button redraws it.
save_log
if home_find id w_name; then
  tap id w_refresh && sleep 3
  wlog | grep -q "update (manual:" && check widget_refresh PASS "$(wlog | grep -o 'update (manual:[a-z_]*)' | tail -1)" || check widget_refresh FAIL "$(wlog | grep 'update (' | tail -1)"
  home_find id w_name && tap id w_name && sleep 4
  wlog | grep -q "open times" && ui_has id tab_times && check widget_opens_times PASS "opens the times tab" || check widget_opens_times FAIL "$(wlog | grep SakinaOpen | tail -1)"
  cap 260_widget_opened_times
fi
if [ "$SUITE" = "full" ]; then
  if home_find id w_source; then
    S1=$(ui_text w_source)
    tap id w_next_btn && sleep 3
    S2=$(ui_text w_source)
    [ -n "$S1" ] && [ "$S1" != "$S2" ] && check widget_content_next PASS "$S1 → $S2" || check widget_content_next FAIL "'$S1' / '$S2'"
    cap 261_home_content_next
    if ui_has id w_text_ayah; then tap id w_text_ayah; else tap id w_text_dhikr; fi
    sleep 5
    wlog | grep -qE "open (ayah|dhikr):" && check widget_content_opens PASS "$(wlog | grep -oE 'open (ayah|dhikr):[^ ]*' | tail -1)" || check widget_content_opens FAIL "$(wlog | grep SakinaOpen | tail -1)"
    cap 262_content_opened
  fi
  if home_find id w_hijri; then
    tap id w_hijri && sleep 4
    wlog | grep -q "open ramadan" && check widget_fm_opens PASS "opens Ramadan" || check widget_fm_opens FAIL "$(wlog | grep SakinaOpen | tail -1)"
  fi
fi

# ---- The app lists the placed widgets; one widget's own settings: English, Jeddah, dark, corners, border.
save_log
start "${WB[@]}" --es demo_route widgets; sleep 6
ui_has id placed_next && ui_has id placed_analog && check widget_list PASS "placed widgets listed" || check widget_list FAIL "list incomplete"
cap 263_my_widgets
tap id placed_settings_next && sleep 4 && cap 264_widget_config
# Sections from the top: place, language, style and theme, text size, colours, corners and border.
scroll_to exact "مدينة خاصة بهذه الويدجت" 4 && tap exact "مدينة خاصة بهذه الويدجت" && sleep 1
scroll_to exact "جدة" 4 && tap exact "جدة" && sleep 1
scroll_to id wcfg_lang_en 6 && tap id wcfg_lang_en
scroll_to id wcfg_theme_dark 8 && tap id wcfg_theme_dark
scroll_to id wcfg_bg_101010 8 && tap id wcfg_bg_101010
scroll_to id wcfg_corner_24 8 && tap id wcfg_corner_24
scroll_to id wcfg_border_1 4 && tap id wcfg_border_1
scroll_top; sleep 1; cap 265_widget_config_changed
tap id wcfg_save && sleep 4
J=$(widgets_json)
echo "$J" | grep -q '"lang":"EN"' && echo "$J" | grep -q '"placeMode":"FIXED"' && echo "$J" | grep -q 'city:jeddah' && echo "$J" | grep -q '"corner":24' \
  && check widget_config_saved PASS "saved on the device" || check widget_config_saved FAIL "$(echo "$J" | head -c 400)"
if home_find exact "Jeddah"; then
  cap 266_home_next_configured
  ui_has exact "Maghrib" && check widget_config_applied PASS "Jeddah · Maghrib, in English" || check widget_config_applied FAIL "no English prayer name"
else
  check widget_config_applied FAIL "Jeddah not on the home screen"
fi
# Its own city stays when the app's city changes; the strip (following the app) switches language with it.
start "${WB[@]}" --es demo_active makkah --es demo_lang en; sleep 6
sleep 3
if home_find exact "Jeddah"; then
  cap 267_home_after_app_changes
  check widget_fixed_place PASS "kept Jeddah after the app moved to Makkah"
else
  check widget_fixed_place FAIL "Jeddah gone after the app changed city"; cap 267_home_after_app_changes
fi
if home_find id w_n0; then
  S=$(ui_text w_n0); [ "$S" = "Fajr" ] && check widget_follows_app PASS "strip follows the app language" || check widget_follows_app FAIL "'$S'"
fi
start "${WB[@]}" --es demo_lang ar; sleep 5

# ---- With the real clock, the remaining time and its update time are redrawn about every minute.
save_log
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/$ACT" >/dev/null; sleep 5
if home_find id w_remain; then
  R1=$(ui_text w_remain); R2="$R1"
  for _ in $(seq 1 16); do sleep 10; R2=$(ui_text w_remain); [ "$R2" != "$R1" ] && break; done
  [ -n "$R1" ] && [ "$R1" != "$R2" ] && check widget_tick PASS "$R1 → $R2" || check widget_tick FAIL "'$R1' / '$R2' ($(wlog | grep 'update (tick)' | tail -1))"
  cap 268_home_after_tick
fi

# ---- Icon shortcuts (in the app's language) and the pinned adhkar shortcut.
save_log
start "${WB[@]}"; sleep 6
adb shell dumpsys shortcut 2>/dev/null | tr -d '\r' > "$OUT/shortcuts_dump.txt"
SC=0; for id in times qibla quran ramadan; do grep -q "id=$id," "$OUT/shortcuts_dump.txt" && SC=$((SC + 1)); done
[ "$SC" -eq 4 ] && check shortcuts_published PASS "times, qibla, quran, ramadan" || check shortcuts_published FAIL "$SC of 4 (see shortcuts_dump.txt)"
if [ "$SUITE" = "full" ]; then
  # Long-press the app icon in the app drawer and pick "القبلة".
  adb shell input keyevent 3; sleep 2
  adb shell input swipe 540 2100 540 500 400; sleep 3
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/.ui.xml"
  XY=$(python3 "$HERE/find_node.py" "$OUT/.ui.xml" "Sakina" exact)
  if [ -n "$XY" ]; then
    adb shell input swipe $XY $XY 1500; sleep 2; cap 270_icon_shortcuts
    try_tap exact "القبلة" && sleep 4
    wlog | grep -q "open qibla" && check shortcut_long_press PASS "Qibla opened from the icon" || check shortcut_long_press FAIL "$(wlog | grep SakinaOpen | tail -1)"
    cap 271_shortcut_qibla
  else
    check shortcut_long_press FAIL "app icon not found in the drawer"; cap 270_drawer
  fi
fi
start "${WB[@]}" --es demo_route widgets; sleep 6
scroll_to id sc_target_morning 20; into_view sc_target_morning; tap id sc_target_morning
tap id pin_adhkar && cap 272_pin_shortcut_dialog
pin_accept
adb shell dumpsys shortcut 2>/dev/null | tr -d '\r' | grep -q "id=adhkar_morning," && check shortcut_pinned PASS "adhkar shortcut pinned" || check shortcut_pinned FAIL "not in the shortcut list"
if home_find exact "أذكار الصباح"; then
  cap 273_home_adhkar_shortcut
  tap exact "أذكار الصباح" && sleep 4
  wlog | grep -q "open adhkar:morning" && check shortcut_adhkar_opens PASS "opens the morning adhkar" || check shortcut_adhkar_opens FAIL "$(wlog | grep SakinaOpen | tail -1)"
  cap 274_shortcut_opened
else
  check shortcut_adhkar_opens FAIL "shortcut not found on the home screen"
fi

# ---- Quick Settings tiles (Android 13+ can ask to add them; the shell can add and click them on 10+).
if [ "$SDK" -ge 33 ]; then
  save_log
  start "${WB[@]}" --es demo_route widgets; sleep 6
  scroll_to id tile_add_qibla 22; into_view tile_add_qibla; tap id tile_add_qibla && sleep 3 && cap 275_tile_request
  try_tap buttonis "add tile" || try_tap exact "Add tile" || true
  sleep 2
  wait_wlog "add tile QiblaTileService result=" 6 && check tile_request PASS "$(wlog | grep -o 'add tile .*' | tail -1)" || check tile_request FAIL "no result"
  adb shell cmd statusbar add-tile "$PKG/com.sakina.app.widgets.TimesTileService" >/dev/null 2>&1
  adb shell cmd statusbar expand-settings >/dev/null 2>&1; sleep 3; cap 276_quick_settings
  adb shell cmd statusbar collapse >/dev/null 2>&1; sleep 1
  save_log
  adb shell input keyevent 3; sleep 1
  adb shell cmd statusbar click-tile "$PKG/com.sakina.app.widgets.TimesTileService" >/dev/null 2>&1; sleep 4
  wlog | grep -q "tile clicked: times" && wlog | grep -q "open times" && check tile_times PASS "opens the prayer times" || check tile_times FAIL "$(wlog | grep -E 'Tiles|Open' | tail -2 | tr '\n' ' ')"
  cap 277_tile_opened_times
fi
save_log
}
