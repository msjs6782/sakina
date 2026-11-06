# Round 17: the widgets in 3D. The 3D diorama and the marble and relief backgrounds, the day's name in gold relief,
# the glass panel in relief (on the wallpaper and floating over a painting), and the new text settings: the size of
# each part, bold text and clarity. Sourced by ci/screenshots.sh (uses its helpers and phase8.sh's). Defines r17().

widgets_json17() { adb shell run-as "$PKG" cat files/datastore/widgets.json 2>/dev/null | tr -d '\r'; }

# gallery17 <shot prefix> <kinds...>: each kind's gallery card in view, one shot each (kinds in the gallery's order:
# next countdown day glass mini strip digital analog content fajr_maghrib dayname khatma dhikr calendar). Kinds whose
# preview was not on screen are collected in G17_MISSING.
G17_MISSING=""
gallery17() {
  local pre="$1"; shift
  for k in "$@"; do
    scroll_to id "gallery_$k" 40; into_view "gallery_$k"; sleep 1; cap "${pre}_$k"
    ui_has id "preview_$k" || G17_MISSING="$G17_MISSING ${pre}_$k"
  done
}

r17() {
save_log
adb shell svc power stayon true >/dev/null 2>&1
WB=(--ez demo_onboarded true --es demo_cities riyadh,jeddah,makkah --es demo_active riyadh --es demo_now "$T_AFTERNOON" "${FREEZE[@]}")
W=(--ez demo_reset true "${WB[@]}")

# ---- The gallery on the 3D scene (Asr at 16:20 in Riyadh): the glass floats over it, the day in gold relief.
adb logcat -c
start "${W[@]}" --es demo_route widgets --es demo_gallery_bg SCENE; sleep 8
G17_MISSING=""
gallery17 900_scene next day glass dayname dhikr
[ -z "$G17_MISSING" ] && check widget_scene_gallery PASS "previews on the 3D scene" || check widget_scene_gallery FAIL "missing:$G17_MISSING"
wlog | grep -qE "preview failed|render failed" && check widget_previews_r17 FAIL "$(wlog | grep -E 'failed' | head -2 | tr '\n' ' ')" || check widget_previews_r17 PASS "all rendered"
# At night and at Fajr the scene follows the time.
start "${W[@]}" --es demo_now "$T_NIGHT" --es demo_route widgets --es demo_gallery_bg SCENE; sleep 8
gallery17 905_scene_night glass dayname
start "${W[@]}" --es demo_now "$T_FAJR" --es demo_route widgets --es demo_gallery_bg SCENE; sleep 8
gallery17 906_scene_fajr dayname

# ---- The textures: marble white and navy, emerald and gold, carved ivory.
i=0
for bg in MARBLE MARBLE_NAVY EMERALD IVORY; do
  start "${W[@]}" --es demo_route widgets --es demo_gallery_bg "$bg"; sleep 7
  gallery17 "91${i}_$(echo "$bg" | tr 'A-Z' 'a-z')" day glass dayname
  i=$((i + 1))
done
wlog | grep -qE "preview failed|render failed" && check widget_textures_r17 FAIL "$(wlog | grep -E 'failed' | head -2 | tr '\n' ' ')" || check widget_textures_r17 PASS "all rendered"

# ---- The glass on the wallpaper, in relief and plain, and the day's name raised and flat.
start "${W[@]}" --es demo_route widgets; sleep 7
gallery17 920_wallpaper day glass dayname
start "${W[@]}" --es demo_route widgets --es demo_gallery_opts "glass3d=0,word=RAISED"; sleep 7
gallery17 921_plain glass dayname
start "${W[@]}" --es demo_route widgets --es demo_gallery_bg SKY --es demo_gallery_opts "word=FLAT"; sleep 7
gallery17 922_flat dayname

# ---- Text: each part larger, bold, clear; and at the small and large sizes.
start "${W[@]}" --es demo_route widgets --es demo_gallery_opts "bold=1,table=140,small=140,clarity=1"; sleep 7
gallery17 930_bold_large next day glass dayname
start "${W[@]}" --es demo_route widgets --es demo_gallery_opts "bold=1,font=KUFI,hero=80,table=130"; sleep 7
gallery17 931_kufi_bold day dayname
[ -z "$G17_MISSING" ] && check widget_gallery_r17 PASS "every preview shown" || check widget_gallery_r17 FAIL "missing:$G17_MISSING"
start "${W[@]}" --es demo_route widgets --es demo_gallery_bg SCENE --ez demo_widget_sizes true; sleep 9
for k in glass dayname; do
  scroll_to id "gallery_$k" 40; into_view "gallery_$k"; cap "932_sizes_$k"
  adb shell input swipe 540 1600 540 900 500; sleep 1; cap "933_sizes_${k}_more"
done
wlog | grep -qE "preview failed|render failed" && check widget_sizes_r17 FAIL "$(wlog | grep -E 'failed' | head -3 | tr '\n' ' ')" || check widget_sizes_r17 PASS "all sizes drawn ($(wlog | grep -c 'fit overflow') overflow notes)"

# ---- On the home screen: the glass over the scene and the day in gold.
pin_kind glass 940_pin_dialog_glass
GLASS_ID=$(wlog | grep -o "pinned glass id=[0-9]*" | tail -1 | grep -o "[0-9]*$")
pin_kind dayname 941_pin_dialog_dayname
start "${WB[@]}" --es demo_widget_opts "glass:bg=SCENE/dayname:bg=SCENE"; sleep 6
if home_find id w_dayname; then cap 942_home_scene; check widget_home_dayname PASS "on the home screen"; else cap 942_home_scene; check widget_home_dayname FAIL "not found"; fi
home_find id w_cells >/dev/null && cap 943_home_glass
[ "$(widgets_json17 | grep -o '"background":"SCENE"' | wc -l)" -ge 2 ] && check widget_scene_saved PASS "scene set on both" || check widget_scene_saved FAIL "$(widgets_json17 | head -c 300)"
start "${WB[@]}" --es demo_widget_opts "glass:bg=COLOR,glass3d=1/dayname:bg=MARBLE_NAVY,word=GOLD"; sleep 6
home_find id w_dayname >/dev/null; cap 944_home_wallpaper_navy

# ---- The glass's settings: sizes, bold, clarity, background and the relief.
if [ -n "$GLASS_ID" ]; then
  adb shell am start -n "$PKG/com.sakina.app.widgets.WidgetConfigActivity" --ei appWidgetId "$GLASS_ID" >/dev/null; sleep 5
  scroll_to id wcfg_text 10; into_view wcfg_text; cap 950_config_text
  ui_has id wcfg_hero && ui_has id wcfg_table && ui_has id wcfg_small && check widget_part_sizes PASS "three part sliders" || check widget_part_sizes FAIL "part sliders missing"
  # A tap right after a swipe only stops the scrolling: wait for it to settle.
  scroll_to id wcfg_bold 10; sleep 2; into_view wcfg_bold; sleep 1; tap id wcfg_bold; sleep 1
  scroll_to id wcfg_clarity_1 10; sleep 2; tap id wcfg_clarity_1; sleep 1
  cap 951_config_bold_clear
  scroll_to id wcfg_background_scene 10; sleep 2; tap id wcfg_background_scene; sleep 2
  into_view wcfg_bg_card; cap 952_config_glass_scene
  scroll_to id wcfg_glass3d 10 && check widget_glass3d_setting PASS "relief setting" || check widget_glass3d_setting FAIL "no relief setting"
  into_view wcfg_look; cap 953_config_look
  tap id wcfg_save; sleep 3
  J=$(widgets_json17)
  echo "$J" | grep -q '"bold":true' && echo "$J" | grep -q '"clarity":1' && check widget_text_saved PASS "bold and clear saved" || check widget_text_saved FAIL "$(echo "$J" | head -c 300)"
  home_find id w_cells >/dev/null && cap 954_home_glass_bold
else
  check widget_glass_config FAIL "no glass widget id"
fi
save_log
}
