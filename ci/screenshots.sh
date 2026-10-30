#!/usr/bin/env bash
# Installs the debug APK on the emulator, walks real flows with taps (onboarding, choosing a city,
# tabs), opens prepared states through debug-only intent extras, and saves a screenshot of each.
# Fails if the app crashes or an expected element cannot be found.
# Usage: ci/screenshots.sh <apk> <out-dir> <full|smoke>
set -u
APK="$1"
OUT="$2"
SUITE="${3:-full}"
PKG=com.sakina.app.debug
ACT=com.sakina.app.MainActivity
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"
FAILS=0

adb wait-for-device
adb shell input keyevent 82 >/dev/null 2>&1 || true
adb install -r "$APK"
adb shell settings put global hide_error_dialogs 1
adb shell settings put system font_scale 1.0
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
adb logcat -c
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
echo "emulator SDK $SDK, suite $SUITE"

cap() { adb exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }

start() {
  adb shell am force-stop "$PKG"
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
  adb shell am start -W -n "$PKG/$ACT" "$@" >/dev/null
}

shot() {
  local name="$1"; local wait="$2"; shift 2
  start "$@"
  sleep "$wait"
  cap "$name"
}

# Like tap, but scrolls the screen up between attempts to reach elements below the fold.
tap_scroll() {
  local mode="$1"; local needle="$2"; local xy=""
  for _ in 1 2 3 4 5 6 7; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb shell cat /sdcard/ui.xml > "$OUT/.ui.xml" 2>/dev/null
    xy=$(python3 "$HERE/find_node.py" "$OUT/.ui.xml" "$needle" "$mode") && break
    adb shell input swipe 540 1700 540 900 400
    sleep 1.2
  done
  if [ -z "$xy" ]; then
    echo "::warning::Could not find '$needle' ($mode) after scrolling"
    FAILS=$((FAILS + 1))
    return 1
  fi
  adb shell input tap $xy
  sleep 1.2
}

# Scrolls up until a node is on screen, without tapping it.
scroll_to() {
  local mode="$1"; local needle="$2"; local tries="${3:-6}"
  for _ in $(seq 1 "$tries"); do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb shell cat /sdcard/ui.xml > "$OUT/.ui.xml" 2>/dev/null
    python3 "$HERE/find_node.py" "$OUT/.ui.xml" "$needle" "$mode" >/dev/null && return 0
    adb shell input swipe 540 1700 540 1000 600
    sleep 1.2
  done
  echo "::warning::Could not scroll to '$needle' ($mode)"
  FAILS=$((FAILS + 1))
  return 1
}

# After scroll_to: moves a node found low on the screen up to the middle, clear of the gesture bar.
bring_up() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT/.ui.xml" 2>/dev/null
  local xy; xy=$(python3 "$HERE/find_node.py" "$OUT/.ui.xml" "$1" id) || return 0
  local y=${xy#* }
  if [ "$y" -gt 1300 ]; then adb shell input swipe 540 1700 540 $((1700 - (y - 900))) 800; sleep 1.5; fi
}

# True if a node is on screen now (no tap).
ui_has() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT/.ui.xml" 2>/dev/null
  python3 "$HERE/find_node.py" "$OUT/.ui.xml" "$2" "$1" >/dev/null
}
mlog() { adb logcat -d -s SakinaMushaf:* 2>/dev/null; }
# wait_log <regex> <seconds>: waits for a Mushaf log line.
wait_log() {
  for _ in $(seq 1 "$2"); do mlog | grep -qE "$1" && return 0; sleep 1; done
  return 1
}
stored_size() { adb shell run-as "$PKG" stat -c %s "files/mushaf/pages/$1.pdf" 2>/dev/null | tr -d '\r'; }
stored_count() { adb shell run-as "$PKG" ls files/mushaf/pages 2>/dev/null | tr -d '\r' | grep -c '\.pdf$'; }
part_count() { adb shell run-as "$PKG" ls files/mushaf/pages 2>/dev/null | tr -d '\r' | grep -c '\.part$'; }

rlog() { adb logcat -d -s SakinaRecitation:* 2>/dev/null; }
# wait_rlog <regex> <seconds>: waits for a recitation log line.
wait_rlog() {
  for _ in $(seq 1 "$2"); do rlog | grep -qE "$1" && return 0; sleep 1; done
  return 1
}
session_dump() { adb shell dumpsys media_session 2>/dev/null | tr -d '\r' | grep -A 14 "package=$PKG" | head -40; }

# Finds a node by text (exact), text (contains) or test tag (id) in a UI dump and taps its centre.
tap() {
  local mode="$1"; local needle="$2"; local xy=""
  for _ in 1 2 3 4; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb shell cat /sdcard/ui.xml > "$OUT/.ui.xml" 2>/dev/null
    xy=$(python3 "$HERE/find_node.py" "$OUT/.ui.xml" "$needle" "$mode") && break
    sleep 1.5
  done
  if [ -z "$xy" ]; then
    echo "::warning::Could not find '$needle' ($mode) on screen"
    cp "$OUT/.ui.xml" "$OUT/missing_$(echo "$needle" | tr -c 'A-Za-z0-9_' '_').xml" 2>/dev/null
    FAILS=$((FAILS + 1))
    return 1
  fi
  adb shell input tap $xy
  sleep 1.2
}

CHECKS="$OUT/checks.txt"
: > "$CHECKS"
CRITICAL=0
check() {  # check <name> <PASS|FAIL> <details> [critical]
  echo "CHECK $1: $2 $3" | tee -a "$CHECKS"
  if [ "$2" = "FAIL" ] && [ "${4:-}" = "critical" ]; then CRITICAL=$((CRITICAL + 1)); fi
}
shade_open() { adb shell cmd statusbar expand-notifications >/dev/null 2>&1 || adb shell service call statusbar 1 >/dev/null 2>&1; sleep 2; }
save_log() { adb logcat -d >> "$OUT/logcat_part2.txt"; adb logcat -c; }
shade_close() { adb shell cmd statusbar collapse >/dev/null 2>&1 || adb shell service call statusbar 2 >/dev/null 2>&1; sleep 1; }
notif_posted() { adb shell dumpsys notification --noredact 2>/dev/null | grep -c "|$PKG|$1|" ; }
# Active notifications only (the dump also lists recently removed ones on some versions).
notif_active() { adb shell dumpsys notification --noredact 2>/dev/null | grep -cE "NotificationRecord\(.*pkg=$PKG .*id=$1 " ; }
adhan_running() { adb shell dumpsys activity services "$PKG/com.sakina.app.alerts.AdhanService" 2>/dev/null | grep -c "ServiceRecord" ; }
alarm_dump() { adb shell dumpsys alarm 2>/dev/null | grep -B3 -A8 "$PKG" ; }

T_AFTERNOON="2026-10-14T13:20:00Z"   # 16:20 in Riyadh: between Asr and Maghrib
T_NIGHT="2026-10-14T18:30:00Z"       # 21:30 in Riyadh: after Isha
T_FAJR="2026-10-15T01:50:00Z"        # 04:50 in Riyadh: after Fajr
T_RAMADAN="2026-03-01T14:30:00Z"     # 17:30 in Riyadh, 12 Ramadan 1447
FREEZE=(--ez demo_freeze true)
BASE=(--es demo_now "$T_AFTERNOON" "${FREEZE[@]}" --ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh,jeddah,makkah --es demo_active riyadh)

# Helpers used by several phases (also defined again where they were first needed).
ui_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT/.ui.xml" 2>/dev/null
  python3 "$HERE/find_node.py" "$OUT/.ui.xml" "$1" idtext
}
settings_json() { adb shell run-as "$PKG" cat files/datastore/settings.json 2>/dev/null; }
hide_ime() { if adb shell dumpsys input_method 2>/dev/null | grep -q "mInputShown=true"; then adb shell input keyevent 4; sleep 1; fi; }
# shellcheck source=ci/phase8.sh
source "$HERE/phase8.sh"
# shellcheck source=ci/visual.sh
source "$HERE/visual.sh"
# shellcheck source=ci/r10.sh
source "$HERE/r10.sh"
# shellcheck source=ci/r11.sh
source "$HERE/r11.sh"
# shellcheck source=ci/r12.sh
source "$HERE/r12.sh"
# shellcheck source=ci/r13.sh
source "$HERE/r13.sh"
# shellcheck source=ci/r14.sh
source "$HERE/r14.sh"
# shellcheck source=ci/r15.sh
source "$HERE/r15.sh"

finish_run() {
  adb logcat -d >> "$OUT/logcat_part2.txt"
  cat "$OUT/logcat_part1.txt" "$OUT/logcat_part2.txt" > "$OUT/logcat.txt" 2>/dev/null
  rm -f "$OUT"/logcat_part*.txt "$OUT/.ui.xml"
  grep -E "SakinaDemo|SakinaAlerts|SakinaAdhan|SakinaWidgets|SakinaOpen|SakinaShortcuts|SakinaTiles|AndroidRuntime: FATAL" "$OUT/logcat.txt" | head -400 > "$OUT/app_log.txt" || true
  grep "HijriCheck" "$OUT/logcat.txt" | tee "$OUT/hijri_check.txt"
  if ! grep -q "HijriCheck compared=.* mismatches=0" "$OUT/logcat.txt"; then
    echo "::warning::Umm al-Qura check against Android ICU did not report zero mismatches (SDK $SDK)"
  fi
  if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
    echo "::error::The app crashed on the emulator (SDK $SDK)"
    grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80
    exit 1
  fi
  if [ "$FAILS" -gt 0 ]; then
    echo "::error::$FAILS expected element(s) were not found on screen (SDK $SDK)"
    exit 1
  fi
  if [ "$CRITICAL" -gt 0 ]; then
    echo "::error::$CRITICAL critical alert check(s) failed (SDK $SDK)"
    grep "FAIL" "$CHECKS"
    exit 1
  fi
  echo "UI check passed (SDK $SDK)"
  exit 0
}

# A commit marked [ci:widgets] runs only the phase 8 checks (quicker while developing; never released).
if [ "${ONLY:-}" = "widgets" ]; then
  phase8
  adb logcat -d > "$OUT/logcat_part1.txt"; adb logcat -c
  finish_run
fi
# The "extras" suite (a second Android 14 job, run in parallel): widgets, paintings, rounds 10 and 11.
if [ "$SUITE" = "extras" ]; then
  if [ "${ONLY:-all}" = "all" ]; then phase8; visual; r10; r11; r12; r13; r14; r15; fi
  adb logcat -d > "$OUT/logcat_part1.txt"; adb logcat -c
  finish_run
fi
# [ci:r10] runs only this round's checks (tabs, quick access, day-name widget, Mushaf on an Arabic phone).
if [ "${ONLY:-}" = "r10" ]; then
  r10
  adb logcat -d > "$OUT/logcat_part1.txt"; adb logcat -c
  finish_run
fi
# [ci:r11] runs only round 11 (painted headers, textures, khatma and dhikr widgets, lock-screen note).
if [ "${ONLY:-}" = "r11" ]; then
  r11
  adb logcat -d > "$OUT/logcat_part1.txt"; adb logcat -c
  finish_run
fi
# [ci:r12] runs only round 12 (recitation followed in the reader and the Mushaf, memorisation).
if [ "${ONLY:-}" = "r12" ]; then
  r12
  adb logcat -d > "$OUT/logcat_part1.txt"; adb logcat -c
  finish_run
fi
# [ci:r13] runs only round 13 (prayer log, silent at prayer, tasbih week).
if [ "${ONLY:-}" = "r13" ]; then
  r13
  adb logcat -d > "$OUT/logcat_part1.txt"; adb logcat -c
  finish_run
fi
# [ci:r14] runs only round 14 (time to and since each prayer, the calendar's raised look).
if [ "${ONLY:-}" = "r14" ]; then
  r14
  adb logcat -d > "$OUT/logcat_part1.txt"; adb logcat -c
  finish_run
fi
# [ci:r15] runs only round 15 (the Mushaf filling the screen, the widgets' look and the calendar widget).
if [ "${ONLY:-}" = "r15" ]; then
  r15
  adb logcat -d > "$OUT/logcat_part1.txt"; adb logcat -c
  finish_run
fi
# [ci:visual] runs only the painted-sky checks.
if [ "${ONLY:-}" = "visual" ]; then
  visual
  adb logcat -d > "$OUT/logcat_part1.txt"; adb logcat -c
  finish_run
fi

# ---------- Real flow: onboarding, picking a city by typing, switching tabs, restart ----------
shot 01_onboarding_welcome 7 --es demo_now "$T_AFTERNOON" "${FREEZE[@]}" --ez demo_reset true
tap exact "التالي" && cap 02_onboarding_location
tap exact "التالي" && cap 03_onboarding_method
tap exact "التالي" && cap 04_onboarding_ready
tap exact "ابدأ" && sleep 1 && cap 05_home_no_location
tap exact "اختيار مدينة" && sleep 1.5 && cap 06_city_picker
tap id city_search && adb shell input text "Jeddah" && sleep 2 && cap 07_city_search
tap exact "جدة" && sleep 2 && cap 08_home_jeddah_after_tap
# The tables open from today's times on Home (no longer a tab).
tap_scroll id home_open_times && sleep 1.5 && tap exact "اليوم" && sleep 1 && cap 09_times_day
tap exact "الأسبوع" && sleep 1.5 && cap 10_times_week
adb shell input keyevent 4; sleep 1.5
tap id tab_more && sleep 1.5 && cap 11a_more
scroll_to exact "المظهر واللغة" 6 && cap 11_settings
tap exact "المظهر واللغة" && sleep 1.5 && tap exact "English" && sleep 2 && cap 11b_switched_to_english
adb shell input keyevent 4; sleep 1.5; cap 11c_back_to_settings_en
# Close the app completely and reopen it normally: the chosen city and language must come back.
adb shell am force-stop "$PKG"; sleep 1
adb shell am start -W -n "$PKG/$ACT" >/dev/null; sleep 7
cap 12_restart_persisted
adb logcat -d > "$OUT/logcat_part1.txt"; adb logcat -c

# ---------- Prepared states ----------
shot 20_home_balanced 7 "${BASE[@]}"
shot 23_home_night_dark 7 --es demo_now "$T_NIGHT" "${FREEZE[@]}" --ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh,jeddah --es demo_active riyadh --es demo_theme DARK
shot 28_english_24h 7 "${BASE[@]}" --es demo_lang en --ez demo_clock24 true
shot 30_times_month 7 "${BASE[@]}" --es demo_tab times --es demo_view month
shot 32_ramadan_day 7 --es demo_now "$T_RAMADAN" "${FREEZE[@]}" --ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh --es demo_tab times
shot 33_calc 7 "${BASE[@]}" --es demo_route calc --es demo_offsets fajr=2,isha=-3
shot 40_tromso_polar 7 --es demo_now "2026-06-21T10:00:00Z" "${FREEZE[@]}" --ez demo_reset true --ez demo_onboarded true --es demo_manual "Tromso,69.6492,18.9553,Europe/Oslo" --es demo_method MUSLIM_WORLD_LEAGUE
shot 53_hijri_check 10 "${BASE[@]}" --ez demo_hijri_check true

if [ "$SUITE" = "full" ]; then
  shot 21_home_focus 7 "${BASE[@]}" --es demo_layout FOCUS
  shot 22_home_dashboard 7 "${BASE[@]}" --es demo_layout DASHBOARD
  shot 24_sahar_light 7 "${BASE[@]}" --es demo_style SAHAR --es demo_theme LIGHT
  shot 25_sahar_dark_focus 7 --es demo_now "$T_NIGHT" "${FREEZE[@]}" --ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh --es demo_style SAHAR --es demo_theme DARK --es demo_layout FOCUS
  shot 26_raml_light 7 "${BASE[@]}" --es demo_style RAML --es demo_theme LIGHT
  shot 27_raml_dark_dashboard 7 "${BASE[@]}" --es demo_style RAML --es demo_theme DARK --es demo_layout DASHBOARD
  shot 29_fajr_period 7 --es demo_now "$T_FAJR" "${FREEZE[@]}" --ez demo_reset true --ez demo_onboarded true --es demo_cities makkah,riyadh --es demo_active makkah
  shot 31_times_week_en 7 "${BASE[@]}" --es demo_lang en --es demo_tab times --es demo_view week
  shot 34_appearance 7 "${BASE[@]}" --es demo_route appearance
  shot 35_locations 7 "${BASE[@]}" --es demo_route locations
  shot 36_home_setup 7 "${BASE[@]}" --es demo_route home
  shot 37_about 7 "${BASE[@]}" --es demo_route about
  shot 38_calendar_offset 7 "${BASE[@]}" --es demo_route calendar --ei demo_hijri 1
  shot 39_travel 7 "${BASE[@]}" --es demo_travel jeddah
  shot 41_tromso_times 7 --es demo_now "2026-06-21T10:00:00Z" "${FREEZE[@]}" --ez demo_reset true --ez demo_onboarded true --es demo_manual "Tromso,69.6492,18.9553,Europe/Oslo" --es demo_method MUSLIM_WORLD_LEAGUE --es demo_tab times
  shot 42_oslo_high_latitude 7 --es demo_now "2026-06-21T10:00:00Z" "${FREEZE[@]}" --ez demo_reset true --ez demo_onboarded true --es demo_cities oslo --es demo_method MUSLIM_WORLD_LEAGUE --es demo_tab times
  shot 43_manual_form 7 "${BASE[@]}" --es demo_route manual
  # Manual entry with invalid input shows errors; then a valid save.
  tap id save_place && sleep 1 && cap 43b_manual_errors
  shot 44_travel_city_picker 7 "${BASE[@]}" --es demo_route locations,city_travel
  tap id city_search && adb shell input text "Dubai" && sleep 1.5
  tap exact "دبي" && sleep 1.5 && cap 44b_travel_confirm
  tap exact "اعتماد كوجهة سفر" && sleep 1.5 && adb shell input keyevent 4 && sleep 1.5 && cap 44c_travel_home

  # GPS with a simulated fix (Riyadh).
  adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION
  adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION
  for _ in 1 2 3; do adb emu geo fix 46.6753 24.7136 >/dev/null 2>&1; sleep 1; done
  start "${BASE[@]}" --es demo_route gps
  for _ in 1 2 3 4 5; do adb emu geo fix 46.6753 24.7136 >/dev/null 2>&1; sleep 2; done
  cap 45_gps_confirm
  tap contains "Asia/Riyadh" && sleep 1 && cap 45b_gps_zone_suggested
  tap id save_place && sleep 2 && cap 45c_gps_saved_home

  # Larger system font.
  adb shell settings put system font_scale 1.3
  sleep 1
  shot 46_font130_home 7 "${BASE[@]}"
  shot 47_font130_week 7 "${BASE[@]}" --es demo_tab times --es demo_view week
  shot 48_font130_dashboard 7 "${BASE[@]}" --es demo_layout DASHBOARD
  adb shell settings put system font_scale 1.0

  # A short 16:9 phone (360x640 dp).
  adb shell wm size 720x1280
  adb shell wm density 320
  sleep 2
  shot 49_small_home 7 "${BASE[@]}"
  shot 50_small_focus 7 "${BASE[@]}" --es demo_layout FOCUS
  adb shell wm size reset
  adb shell wm density reset
  sleep 2

  # Landscape.
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 1
  adb shell cmd window user-rotation lock 1 >/dev/null 2>&1 || true
  sleep 3
  shot 51_landscape_home 7 "${BASE[@]}"
  adb shell settings put system user_rotation 1
  sleep 2
  shot 52_landscape_dashboard 7 "${BASE[@]}" --es demo_layout DASHBOARD
  adb shell cmd window user-rotation lock 0 >/dev/null 2>&1 || true
  adb shell settings put system user_rotation 0
  sleep 2
fi

# ---------- Phase 3: Quran ----------
# The text view here; the printed pages (the default view) are tested in phase 4.
Q=(--ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh --es demo_now "$T_AFTERNOON" "${FREEZE[@]}" --es demo_quran view=TEXT)
adb logcat -d > "$OUT/logcat_part2.txt"; adb logcat -c
shot 80_quran_home 9 "${Q[@]}" --es demo_tab quran
adb logcat -d -s SakinaQuran:I | grep "Loaded" | tail -1 | tee "$OUT/quran_load.txt"
grep -q "114 surahs, 6236 ayahs" "$OUT/quran_load.txt" && check quran_load PASS "$(cat "$OUT/quran_load.txt" | grep -o 'in [0-9]* ms')" critical || check quran_load FAIL "not loaded" critical
# Real taps: open Al-Fatiha from the index, tap an ayah, bookmark it.
tap id surah_1 && sleep 3 && cap 81_reader_fatiha
adb shell input tap 540 1000; sleep 2; cap 82_ayah_sheet
tap id ayah_bookmark && sleep 1 && cap 82b_ayah_bookmarked
adb shell input keyevent 4; sleep 1; adb shell input keyevent 4; sleep 2
cap 82c_quran_home_continue
# The bookmark and the reading position are written to the app's own storage.
QJ=$(adb shell run-as "$PKG" cat files/datastore/quran.json 2>/dev/null | tr -d '\r')
echo "$QJ" > "$OUT/quran_state.json"
echo "$QJ" | grep -q '"bookmarks":\[{"sura":1,' && check bookmark_saved PASS "$(echo "$QJ" | grep -o '"bookmarks":\[[^]]*\]')" || check bookmark_saved FAIL "see quran_state.json"
echo "$QJ" | grep -q '"lastRead":{"sura":1,' && check last_read_saved PASS "$(echo "$QJ" | grep -o '"lastRead":{[^}]*}')" || check last_read_saved FAIL "see quran_state.json"
tap exact "العلامات" && sleep 2 && cap 82d_bookmarks_tab
shot 83_reader_baqara_255 9 "${Q[@]}" --es demo_route quran_reader:2:255 --ez demo_highlight true
shot 84_reader_ayahs_translation 9 "${Q[@]}" --es demo_quran view=TEXT,mode=AYAHS,translation=1,tafsir=1 --es demo_route quran_reader:2:255 --ez demo_highlight true
# Searching by typing: English words and a reference.
start "${Q[@]}" --es demo_route quran_search
sleep 6
adb shell input text "throne"; sleep 3; cap 86_search_english
adb shell input keyevent 123; for _ in $(seq 1 8); do adb shell input keyevent 67; done
adb shell input text "2:255"; sleep 3; cap 87_search_reference
tap id search_reference && sleep 3 && cap 88_reference_opened
shot 85_search_arabic 9 "${Q[@]}" --es demo_route "quran_search:الرحمن الرحيم"
shot 89_khatma_new 8 "${Q[@]}" --es demo_route khatma
tap id khatma_start && sleep 2 && cap 90_khatma_started
shot 91_khatma_behind 8 "${Q[@]}" --es demo_khatma 30:40:5 --es demo_route khatma
save_log

if [ "$SUITE" = "full" ]; then
  start "${Q[@]}" --es demo_tab quran --es demo_bookmarks 2:255,18:10,36:1 --es demo_lastread 18:1:293 --es demo_khatma 30:120:5
  sleep 7
  cap 92_quran_home_state
  tap exact "العلامات" && sleep 2 && cap 93_quran_bookmarks
  tap exact "الأجزاء" && sleep 2 && cap 94_quran_juz
  shot 95_quran_sources 8 "${Q[@]}" --es demo_route quran_sources
  shot 96_reader_dark_large 9 "${Q[@]}" --es demo_theme DARK --es demo_quran view=TEXT,font=34 --es demo_route quran_reader:36:1
  shot 97_reader_english 9 "${Q[@]}" --es demo_lang en --es demo_quran view=TEXT,mode=AYAHS,translation=1 --es demo_route quran_reader:1:1
  start "${Q[@]}" --es demo_route quran_reader:112:1
  sleep 7
  tap id reader_settings && sleep 2 && cap 98_reader_settings
  adb shell input keyevent 4; sleep 1
  adb shell input tap 540 1100; sleep 2
  tap id ayah_share_image && sleep 4 && cap 99_share_image_chooser
  adb shell input keyevent 4; sleep 1
  ls_share=$(adb shell run-as "$PKG" ls cache/share 2>/dev/null | tr -d '\r')
  [ -n "$ls_share" ] && check share_png PASS "$ls_share" || check share_png FAIL "no image written"
  adb exec-out run-as "$PKG" cat "cache/share/$ls_share" > "$OUT/99b_shared_ayah.png" 2>/dev/null || true
  adb shell input keyevent 4; sleep 1
  # The home screen with the verse of the day (the balanced layout shows the cards; focus hides them by design).
  start "${Q[@]}"
  sleep 8
  scroll_to id verse_card && adb shell input swipe 540 1500 540 1100 600 && sleep 1.5 && cap 79_home_verse
  tap id verse_text && sleep 3 && cap 79b_verse_opened
  save_log
fi

# ---------- Phase 4: printed Mushaf (original KFGQPC 1441H page PDFs) ----------
save_log
M=(--ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh --es demo_now "$T_AFTERNOON" "${FREEZE[@]}")
adb shell am force-stop "$PKG"
adb shell run-as "$PKG" rm -rf files/mushaf
adb logcat -c
# The printed pages are the default view: tapping Al-Fatiha opens the bundled page 1, with no network needed.
shot 100_quran_home_printed 8 "${M[@]}" --es demo_tab quran
tap id surah_1 && sleep 4 && cap 101_mushaf_page1
S1=$(stored_size 001); S2=$(stored_size 002)
if [ "$S1" = "393966" ] && [ "$S2" = "469559" ] && mlog | grep -q "rendered 001.pdf"; then
  check mushaf_bundled PASS "pages 1-2 in place ($S1, $S2 bytes); $(mlog | grep -o 'rendered 001.pdf [0-9x]* in [0-9]* ms' | head -1)" critical
else
  check mushaf_bundled FAIL "sizes '$S1' '$S2'; $(mlog | grep -E 'cannot render|bundled' | head -3)" critical
fi
# Swiping right turns to the next page (the Mushaf reads right to left).
adb shell input swipe 150 1300 950 1300 250; sleep 3; cap 102_mushaf_page2
ui_has contains "صفحة ٢ من ٦٠٤" && check mushaf_swipe PASS "page 2 after a right swipe" || check mushaf_swipe FAIL "page 2 not shown"
adb shell input swipe 150 1300 950 1300 250; sleep 3; cap 103_mushaf_page3_missing
ui_has contains "صفحة ٣ من ٦٠٤" && ui_has id mushaf_missing_3 && check mushaf_missing PASS "page 3 offers a download" || check mushaf_missing FAIL "no download card on page 3"
# A real download of page 3: one byte range of the zip on archive.org, inflated and checked.
tap id mushaf_download_page_3
if wait_log "page 3 verified" 45; then
  sleep 3; cap 104_mushaf_page3_downloaded
  check mushaf_download PASS "$(mlog | grep -o 'page 3 verified.*' | head -1); stored $(stored_size 003) bytes" critical
else
  cap 104_mushaf_page3_downloaded
  check mushaf_download FAIL "$(mlog | grep -E 'page 3' | tail -2)" critical
fi
mlog | grep -q "rendered 003.pdf" && check mushaf_render_downloaded PASS "$(mlog | grep -o 'rendered 003.pdf [0-9x]* in [0-9]* ms' | head -1)" critical || check mushaf_render_downloaded FAIL "page 3 not rendered" critical
# A tap hides the reading controls and the system bars; another brings them back.
adb shell input tap 540 1300; sleep 2; cap 105_mushaf_controls_hidden
ui_has id mushaf_page_label && check mushaf_tap_hide FAIL "controls still shown" || check mushaf_tap_hide PASS "controls hidden"
adb shell input tap 540 1300; sleep 2
# Zoomed: the visible part is drawn again from the PDF at full sharpness.
adb logcat -c
shot 106_mushaf_zoomed 9 "${M[@]}" --es demo_route mushaf:3 --ef demo_zoom 2.5
mlog | grep -q "rendered 003.pdf .* region" && check mushaf_zoom_render PASS "$(mlog | grep -o 'rendered 003.pdf [0-9x]* region in [0-9]* ms' | head -1)" || check mushaf_zoom_render FAIL "no region render"
shot 107_mushaf_dark 8 "${M[@]}" --es demo_quran night=DARK --es demo_route mushaf:3
# Index: jump to page 50 by typing, then the automatic-download option fetches it on opening.
start "${M[@]}" --es demo_route mushaf:3
sleep 6
tap id mushaf_index && sleep 2 && cap 108_mushaf_index
tap id mushaf_page_input && adb shell input text 50 && sleep 1 && tap id mushaf_page_go && sleep 3
ui_has contains "صفحة ٥٠ من ٦٠٤" && check mushaf_goto PASS "page 50 opened from the index" || check mushaf_goto FAIL "page 50 not shown"
tap id mushaf_auto_switch_50
if wait_log "page 50 verified" 45; then sleep 3; check mushaf_auto PASS "page 50 fetched on opening with the option on"; else check mushaf_auto FAIL "$(mlog | grep 'page 50' | tail -1)"; fi
cap 109_mushaf_page50_auto
# From a search result the printed page opens with the ayah pointed out; the text view is one tap away.
shot 110_mushaf_focus 8 "${M[@]}" --es demo_route mushaf:3:2:10
tap id mushaf_to_text && sleep 3 && cap 111_text_from_printed
tap id reader_to_printed && sleep 3
ui_has id mushaf_viewer && check view_switch PASS "printed -> text -> printed" || check view_switch FAIL "did not return to the printed view"

# The whole-Mushaf download: progress, then it stops when the app goes to the background, keeping verified pages.
adb logcat -c
shot 112_downloads 8 "${M[@]}" --es demo_route mushaf_downloads
BEFORE=$(stored_count)
tap id dl_start
sleep 2
ui_has id dl_metered_ok && tap id dl_metered_ok
if wait_log "full download started" 20; then
  sleep 25
  cap 113_downloads_running
  N1=$(mlog | grep -c "verified:")
  adb shell input keyevent 3
  if wait_log "full download stopped: BACKGROUND" 40; then
    sleep 1
    AFTER=$(stored_count); PARTS=$(part_count)
    [ "$PARTS" -eq 0 ] && [ "$AFTER" -gt "$BEFORE" ] && check full_bg_stop PASS "stopped in the background; pages $BEFORE -> $AFTER, $N1 verified while running, no partial files" critical \
      || check full_bg_stop FAIL "pages $BEFORE -> $AFTER, partial files $PARTS" critical
  else
    check full_bg_stop FAIL "no background stop: $(mlog | grep 'full download' | tail -2)" critical
  fi
  adb shell am start -n "$PKG/$ACT" >/dev/null; sleep 4; cap 114_downloads_stopped_background
  # Resume, then leave the screen: it stops again.
  adb logcat -c
  tap id dl_start; sleep 2; ui_has id dl_metered_ok && tap id dl_metered_ok
  sleep 10
  adb shell input keyevent 4
  wait_log "full download stopped: LEFT_SCREEN" 40 && check full_leave_stop PASS "stopped on leaving the screen" || check full_leave_stop FAIL "$(mlog | grep 'full download' | tail -2)"
else
  check full_bg_stop FAIL "did not start: $(mlog | tail -3)" critical
fi
# A changed page is found and removed by the check; a juz can be deleted.
adb logcat -c
shot 115_downloads_before_verify 8 "${M[@]}" --es demo_route mushaf_downloads --ei demo_mushaf_corrupt 3
tap id dl_verify
wait_log "verify: checked" 60 && sleep 2
cap 116_downloads_verified
mlog | grep -q "verify: checked .* removed 1 \[3\]" && check verify_corrupt PASS "$(mlog | grep -o 'verify: .*' | tail -1)" critical || check verify_corrupt FAIL "$(mlog | grep -o 'verify: .*' | tail -1)" critical
scroll_to id dl_delete_juz_1 && tap id dl_delete_juz_1 && sleep 1 && tap id dl_delete_ok && sleep 2
mlog | grep -q "deleted [1-9][0-9]* pages" && check delete_juz PASS "$(mlog | grep -o 'deleted .*' | tail -1)" || check delete_juz FAIL "no delete logged"
cap 117_downloads_after_delete
if [ "$SUITE" = "full" ]; then
  shot 118_mushaf_warm 8 "${M[@]}" --es demo_quran night=WARM --es demo_route mushaf:50
  shot 119_mushaf_english 8 "${M[@]}" --es demo_lang en --es demo_route mushaf:4
  # Offline: the page says so instead of failing silently.
  adb shell svc wifi disable; adb shell svc data disable; sleep 3
  start "${M[@]}" --es demo_route mushaf:5
  sleep 6
  tap id mushaf_download_page_5 && sleep 2 && cap 120_mushaf_offline
  ui_has contains "لا يوجد اتصال" && check offline_message PASS "offline shown" || check offline_message FAIL "no offline message"
  adb shell svc wifi enable; adb shell svc data enable; sleep 5
  shot 121_mushaf_page1_dark 8 "${M[@]}" --es demo_quran night=DARK --es demo_route mushaf:1
fi
save_log

# ---------- Phase 5: listening (MP3Quran recitations, Media3 session) ----------
save_log
if [ "$SDK" -ge 33 ]; then adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS; fi
if [ "$SDK" -ge 31 ]; then adb shell appops set "$PKG" SCHEDULE_EXACT_ALARM allow; fi
L0=(--ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh --es demo_now "$T_AFTERNOON" "${FREEZE[@]}" --ez demo_diagnostics true --el demo_idle_ms 25000 --el demo_dl_slow_ms 40)
L=("${L0[@]}" --ez demo_recitations_clear true)
save_log
start "${L[@]}" --es demo_tab quran
sleep 8
tap id quran_listen && sleep 3 && cap 130_listen
# Streaming Al-Baqara by Alafasy, started by a tap.
tap id listen_play_2
if wait_rlog "playing alafasy:2" 40; then
  check stream_play PASS "$(rlog | grep -o 'load alafasy:2 from .*' | head -1)" critical
else
  check stream_play FAIL "$(rlog | tail -4 | tr '\n' ' ')" critical
fi
sleep 3; cap 131_listen_playing
session_dump > "$OUT/media_session.txt"
grep -qE "state=(3|PLAYING\(3\))" "$OUT/media_session.txt" && check media_session PASS "MediaSession is playing" critical || check media_session FAIL "see media_session.txt" critical
N=$(notif_posted 4001)
[ "$N" -ge 1 ] && check media_notification PASS "notification posted" critical || check media_notification FAIL "no media notification" critical
shade_open; sleep 1; cap 132_listen_notification; shade_close
# Media keys reach the session the same way as the lock screen, the notification and headset buttons.
save_log
adb shell input keyevent 127
wait_rlog "paused at" 10 && check media_key_pause PASS "paused by the media pause key" critical || check media_key_pause FAIL "no pause logged" critical
save_log
adb shell input keyevent 126
wait_rlog "playing alafasy:2" 10 && check media_key_play PASS "played by the media play key" || check media_key_play FAIL "no play logged"
sleep 1
tap id speed_1.25 && sleep 2
session_dump > "$OUT/media_session_speed.txt"
grep -q "speed=1.25" "$OUT/media_session_speed.txt" && check speed PASS "speed 1.25 in the session" || check speed FAIL "see media_session_speed.txt"
tap id listen_forward && sleep 2 && cap 133_listen_speed_seek
# The adhan takes audio focus: the recitation pauses and does not resume by itself afterwards.
save_log
adb shell am start -n "$PKG/$ACT" --es demo_test ADHAN:3 >/dev/null
if wait_rlog "audio focus lost" 25; then
  sleep 6
  adb shell am start -n "$PKG/$ACT" --ez demo_adhan_stop true >/dev/null
  wait_rlog "audio focus regained" 15
  sleep 4
  RESUMED=$(rlog | grep -c "playing alafasy")
  REGAINED=$(rlog | grep -c "audio focus regained")
  [ "$RESUMED" -eq 0 ] && [ "$REGAINED" -ge 1 ] && check focus_no_resume PASS "paused for the adhan; focus came back and playback stayed paused" critical \
    || check focus_no_resume FAIL "resumed=$RESUMED regained=$REGAINED" critical
else
  check focus_no_resume FAIL "focus loss not seen: $(rlog | tail -3 | tr '\n' ' ')" critical
fi
cap 134_listen_after_adhan
# Quiet mode stops the recitation and blocks playing until it ends.
tap id listen_toggle; sleep 3
save_log
adb shell am start -n "$PKG/$ACT" --ei demo_quiet 30 >/dev/null
wait_rlog "session ended: quiet mode" 15 && check quiet_stops PASS "quiet mode ended the session" critical || check quiet_stops FAIL "$(rlog | tail -2 | tr '\n' ' ')" critical
sleep 2
tap id listen_play_2; sleep 3
rlog | grep -q "play refused: Sakina quiet mode is on" && check quiet_blocks PASS "play refused during quiet" || check quiet_blocks FAIL "play not refused"
cap 135_listen_quiet
adb shell am start -n "$PKG/$ACT" --ei demo_quiet 0 >/dev/null; sleep 2

# Download Al-Ikhlas (with its timing map), play it from the device, and repeat ayahs 1-2 three times.
save_log
start "${L0[@]}" --es demo_route listen:112
sleep 7
tap id listen_download_112
if wait_rlog "downloaded alafasy:112" 60; then
  check download_recording PASS "$(rlog | grep -o 'downloaded alafasy:112.*' | head -1)" critical
else
  check download_recording FAIL "$(rlog | tail -3 | tr '\n' ' ')" critical
fi
wait_rlog "timing alafasy:112" 30
rlog | grep -q "timing alafasy:112: valid, 4 ayahs" && check timing_valid PASS "Al-Ikhlas timing map checked: 4 ayahs" critical || check timing_valid FAIL "$(rlog | grep timing | tail -1)" critical
tap id listen_play_112
wait_rlog "load alafasy:112 from downloaded file" 15 && check play_downloaded PASS "played from the device" critical || check play_downloaded FAIL "$(rlog | grep 'load alafasy:112' | tail -1)" critical
sleep 4
scroll_to id repeat_start
tap id repeat_to_plus; sleep 1
cap 136_listen_repeat_setup
save_log
tap id repeat_start
if wait_rlog "repeat finished after 3 passes" 120; then
  P=$(rlog | grep -c "repeat pass [12] done; again")
  [ "$P" -eq 2 ] && check repeat_range PASS "ayahs 1-2 played 3 times, then paused" critical || check repeat_range FAIL "passes logged: $P" critical
else
  check repeat_range FAIL "$(rlog | grep repeat | tail -3 | tr '\n' ' ')" critical
fi
cap 137_listen_repeat_done
# A timing map that does not match its surah keeps repeat off (Husary, Al-Fatiha: 6 ayahs instead of 7).
save_log
adb shell input swipe 540 700 540 1900 300; sleep 1
tap id reciter_husary && sleep 1
scroll_to id listen_play_1 && tap id listen_play_1
wait_rlog "playing husary:1" 40
sleep 2
scroll_to id timing_download && tap id timing_download
wait_rlog "timing husary:1" 30
sleep 1
if rlog | grep -q "timing husary:1: invalid NUMBERING" && ui_has id timing_invalid; then
  check timing_invalid PASS "Husary Al-Fatiha map rejected; repeat disabled" critical
else
  check timing_invalid FAIL "$(rlog | grep timing | tail -1)" critical
fi
cap 138_listen_timing_invalid
# Cancel a large download: no partial file stays.
save_log
adb shell input swipe 540 700 540 1900 300; sleep 1
tap id reciter_alafasy && sleep 1
scroll_to id listen_download_2 && tap id listen_download_2 && sleep 4
tap id listen_cancel_2
wait_rlog "download cancelled alafasy:2" 20
PARTS=$(adb shell run-as "$PKG" ls files/recitations/alafasy 2>/dev/null | tr -d '\r' | grep -c part)
rlog | grep -q "download cancelled alafasy:2" && [ "$PARTS" -eq 0 ] && check download_cancel PASS "cancelled, no partial file" || check download_cancel FAIL "parts=$PARTS"
# Playback goes on after leaving the screen.
scroll_to id listen_play_2 && tap id listen_play_2
wait_rlog "playing alafasy:2" 40
save_log
adb shell input keyevent 4; sleep 4
session_dump > "$OUT/media_session_left.txt"
grep -qE "state=(3|PLAYING\(3\))" "$OUT/media_session_left.txt" && check leave_screen PASS "still playing after leaving the screen" || check leave_screen FAIL "not playing after leaving"
cap 139_home_playing
# A pause longer than the limit (25 s here, ten minutes in the app) ends the session and removes the notification.
tap id tab_quran && sleep 2
tap id quran_listen && sleep 3
tap id listen_toggle
if wait_rlog "session ended: paused for 25 s" 45; then
  adb shell dumpsys notification --noredact > "$OUT/notifications_after_idle.txt" 2>/dev/null
  for _ in 1 2 3 4 5 6; do sleep 1; N=$(notif_active 4001); [ "$N" -eq 0 ] && break; done
  [ "$N" -eq 0 ] && check idle_end PASS "paused session ended; notification removed" critical || check idle_end FAIL "notification still shown" critical
else
  check idle_end FAIL "$(rlog | tail -3 | tr '\n' ' ')" critical
fi
cap 140_listen_after_idle
# Delete the download; the diagnostics report can be previewed on the device before any sharing.
start "${L0[@]}" --es demo_route listen:112
sleep 7
tap id listen_delete_112 && sleep 1 && tap id listen_delete_ok && sleep 2
rlog | grep -q "deleted alafasy:112" && check delete_recording PASS "deleted" || check delete_recording FAIL "no delete logged"
scroll_to id diag_preview && tap id diag_preview && sleep 2 && cap 141_listen_diagnostics
ui_has id diag_report && check diagnostics PASS "report shown on the device" || check diagnostics FAIL "no report"
adb shell input keyevent 4; sleep 1
if [ "$SUITE" = "full" ]; then
  shot 142_listen_english 8 "${L0[@]}" --es demo_lang en --es demo_route listen
  shot 143_listen_dark 8 "${L0[@]}" --es demo_theme DARK --es demo_reciter minshawi --es demo_route listen:36
  # Playback continues with the screen off.
  tap id listen_play_36; wait_rlog "playing minshawi:36" 40
  save_log
  adb shell input keyevent 26; sleep 10
  session_dump > "$OUT/media_session_screen_off.txt"
  grep -qE "state=(3|PLAYING\(3\))" "$OUT/media_session_screen_off.txt" && ! rlog | grep -q "paused at" && check screen_off_playing PASS "still playing with the screen off" || check screen_off_playing FAIL "see media_session_screen_off.txt"
  adb shell input keyevent 26; sleep 2; adb shell input keyevent 82; sleep 2
  cap 144_after_screen_off
  tap id listen_stop; sleep 2
fi
save_log

# ---------- Phase 6: adhkar, tasbih and qibla ----------
save_log
qlog() { adb logcat -d -s SakinaQibla:* 2>/dev/null; }
wait_qlog() { for _ in $(seq 1 "$2"); do qlog | grep -qE "$1" && return 0; sleep 1; done; return 1; }
ui_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT/.ui.xml" 2>/dev/null
  python3 "$HERE/find_node.py" "$OUT/.ui.xml" "$1" idtext
}
tools_json() { adb shell run-as "$PKG" cat files/datastore/tools.json 2>/dev/null; }
# Arabic text without vowel marks, for comparisons that should not depend on how marks are ordered.
plain() { python3 -c "import sys,re; print(re.sub('[\u0610-\u061a\u064b-\u065f\u0670\u06d6-\u06ed]','',sys.stdin.read()))"; }
sensor() { adb emu sensor set "$1" "$2" >/dev/null 2>&1; }
# A level phone (screen up) whose top edge points to magnetic heading $1 in Riyadh's field (H 33.9 µT, Z 27.0 µT down).
point_to() {
  local v; v=$(python3 -c "import math; h=math.radians($1); print('%.3f:%.3f:%.3f' % (-33.9*math.sin(h), 33.9*math.cos(h), -27.0))")
  sensor acceleration 0:0:9.81; sensor magnetic-field "$v"
}
A=(--ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh --es demo_active riyadh --es demo_now "$T_AFTERNOON" "${FREEZE[@]}")
TODAY=2026-10-14

# Discover tab and the adhkar home, reached by taps.
shot 150_discover 7 "${A[@]}" --es demo_tab discover
tap id tool_adhkar && sleep 2 && cap 151_adhkar_home
scroll_to id cat_travel && cap 152_adhkar_categories
tap id cat_sleep && sleep 2 && cap 153_category_sleep
tap id dhikr_ayat_kursi_sleep && sleep 3 && cap 154_item_ayat_kursi
ui_text quran_part_2_255 | plain | grep -q "لقيوم" && check adhkar_quran_text PASS "ayat al-kursi from the bundled Tanzil text" || check adhkar_quran_text FAIL "no ayah text"
tap id adhkar_fav && sleep 1
scroll_to id dhikr_source && cap 155_item_source
T=$(ui_text dhikr_source); echo "$T" | grep -q "صحيح البخاري" && check adhkar_source PASS "$T" || check adhkar_source FAIL "source: $T"
tools_json | grep -q '"favorites":\["ayat_kursi_sleep"\]' && check adhkar_favorite PASS "favourite stored" || check adhkar_favorite FAIL "$(tools_json | head -c 300)"

# Search: Arabic (opened with the query, since adb cannot type Arabic) and English (typed).
shot 156_search_arabic 8 "${A[@]}" --es demo_route "adhkar_search:الكرسي"
ui_has id adhkar_result_ayat_kursi_sleep && check adhkar_search_ar PASS "found by Arabic text" || check adhkar_search_ar FAIL "no result"
start "${A[@]}" --es demo_route adhkar; sleep 6
tap id adhkar_search && adb shell input text "travel" && sleep 2 && cap 157_search_english
ui_has id adhkar_result_travel && check adhkar_search_en PASS "found by English title" || check adhkar_search_en FAIL "no result"

# A dhikr with parts and its own counter (kept apart from the daily sets).
shot 158_item_tasbih33 8 "${A[@]}" --es demo_route adhkar_item:tasbih_33
for _ in 1 2 3; do tap id item_counter; done
cap 159_item_counter
tools_json | grep -q "\"$TODAY|item|-\":{\"tasbih_33\":3}" && check item_counter PASS "3 taps in the item counter" || check item_counter FAIL "$(tools_json | head -c 400)"

# Morning set: tap to count, completion, and no mixing with the evening set.
shot 160_wird_morning 8 "${A[@]}" --es demo_route wird:morning
scroll_to id wird_count_bismillah_la_yadurr 16
bring_up wird_count_bismillah_la_yadurr
for _ in 1 2 3 4; do tap id wird_item_bismillah_la_yadurr; done
sleep 1; cap 161_wird_morning_counted
ui_has id wird_done_bismillah_la_yadurr && check wird_complete PASS "done after 3 taps (4th ignored)" || check wird_complete FAIL "not done"
J=$(tools_json)
echo "$J" | grep -q "\"$TODAY|morning|main\":{\"bismillah_la_yadurr\":3}" && check wird_saved PASS "morning progress saved per day and set" || check wird_saved FAIL "$(echo "$J" | head -c 400)"
echo "$J" | grep -q "|evening|" && check wird_no_mixing FAIL "evening counter touched" || check wird_no_mixing PASS "evening set untouched"
shot 162_wird_evening 8 "${A[@]}" --es demo_wird morning:main:bismillah_la_yadurr=3,sayyid_istighfar=1 --es demo_route wird:evening
T=$(ui_text wird_progress); echo "$T" | grep -q "٠ من" && check wird_evening_separate PASS "$T" || check wird_evening_separate FAIL "$T"
scroll_to id wird_text_bika_asbahna_0 12
ui_text wird_text_bika_asbahna_0 | plain | grep -q "النشور" && check wird_evening_wording PASS "evening wording shown" || check wird_evening_wording FAIL "$(ui_text wird_text_bika_asbahna_0)"
# After-prayer set opens on the latest prayer (Asr at 16:20 in Riyadh); each prayer has its own session.
shot 163_wird_after_prayer 8 "${A[@]}" --es demo_route wird:after_prayer
scroll_to id wird_item_astaghfirullah_salam && tap id wird_item_astaghfirullah_salam && sleep 1
tools_json | grep -q "\"$TODAY|after_prayer|asr\":{\"astaghfirullah_salam\":1}" && check wird_session PASS "counted in the Asr session" || check wird_session FAIL "$(tools_json | head -c 400)"
start "${A[@]}" --es demo_route wird:after_prayer; sleep 6
tap id session_fajr && sleep 1 && cap 164_wird_after_fajr
# Reminder link: the set shows its civil reminder and opens the reminders screen, which links back.
start "${A[@]}" --es demo_reminders ADHKAR_MORNING:390 --es demo_wird morning:main:bismillah_la_yadurr=3 --es demo_route wird:morning; sleep 7
cap 165_wird_reminder_on
T=$(ui_text wird_reminder_state); echo "$T" | grep -q "٦:٣٠" && check wird_reminder PASS "$T" || check wird_reminder FAIL "$T"
tap id wird_reminder && sleep 2 && cap 166_reminders_with_link
scroll_to id reminder_open_morning && tap id reminder_open_morning && sleep 2
scroll_to id wird_item_sayyid_istighfar 12 && check reminder_to_wird PASS "reminders link back to the set" || check reminder_to_wird FAIL "not opened"
# Progress survives a restart (no reset).
adb shell am force-stop "$PKG"; sleep 1
adb shell am start -W -n "$PKG/$ACT" --es demo_now "$T_AFTERNOON" "${FREEZE[@]}" --es demo_route wird:morning >/dev/null; sleep 7
cap 167_wird_after_restart
scroll_to id wird_done_bismillah_la_yadurr 16 && check wird_persisted PASS "still done after restart" || check wird_persisted FAIL "lost"

# Tasbih: target, taps, reset and undo of the last real change, custom target.
shot 170_tasbih 7 "${A[@]}" --es demo_route tasbih
tap id tasbih_target_33
for _ in 1 2 3 4 5; do tap id tasbih_tap; done
cap 171_tasbih_counted
C=$(ui_text tasbih_count); [ "$C" = "٥" ] && check tasbih_count PASS "5" || check tasbih_count FAIL "count $C"
tap id tasbih_reset && sleep 1
C=$(ui_text tasbih_count); [ "$C" = "٠" ] || check tasbih_reset FAIL "after reset $C"
tap id tasbih_undo && sleep 1
C=$(ui_text tasbih_count); [ "$C" = "٥" ] && check tasbih_undo PASS "reset undone" || check tasbih_undo FAIL "after undo $C"
shot 172_tasbih_target 7 "${A[@]}" --es demo_tasbih 32:33:سبحان_الله --es demo_route tasbih
tap id tasbih_tap && sleep 1 && cap 173_tasbih_reached
T=$(ui_text tasbih_rounds); echo "$T" | grep -q "بلغت الهدف" && check tasbih_target PASS "$T" || check tasbih_target FAIL "$T"
scroll_to id tasbih_target_custom && tap id tasbih_target_custom && sleep 1 && tap id tasbih_target_input && adb shell input text "500" && tap id tasbih_target_save && sleep 1
cap 174_tasbih_custom
tools_json | grep -q '"target":500' && check tasbih_custom PASS "custom target saved" || check tasbih_custom FAIL "$(tools_json | head -c 300)"

# Qibla: computed bearing and distance, the real compass from emulator sensors, warnings, and the offline map.
save_log
point_to 241.0
shot 180_qibla 9 "${A[@]}" --es demo_route qibla
B=$(ui_text qibla_bearing); D=$(ui_text qibla_distance)
echo "$B" | grep -q "٢٤٤" && check qibla_bearing PASS "$B · $D" || check qibla_bearing FAIL "bearing $B distance $D"
wait_qlog "sensors start source=ROTATION_VECTOR" 5 && check qibla_sensor PASS "$(qlog | grep 'sensors start' | tail -1)" || check qibla_sensor FAIL "$(qlog | tail -3)"
adb shell dumpsys sensorservice > "$OUT/sensorservice_on.txt" 2>/dev/null
# Riyadh declination is about 3.1° east, so magnetic 241° is true 244.1°: aligned with the qibla.
for _ in 1 2 3 4 5 6; do point_to 241.0; sleep 1; done
cap 181_qibla_aligned
S=$(ui_text compass_state); H=$(ui_text compass_heading)
echo "$S" | grep -q "متجه نحو القبلة" && check compass_aligned PASS "$S ($H)" || check compass_aligned FAIL "$S ($H)"
# The system's fused rotation vector follows a turn through the gyroscope, which the emulator keeps still;
# so for each new orientation the screen is reopened and the fusion starts again from the new readings.
point_to 211.0; sleep 1
start "${A[@]}" --es demo_route qibla; sleep 8
cap 182_qibla_turn_right
S=$(ui_text compass_state); echo "$S" | grep -qE "يمينًا (٢٩|٣٠|٣١)°" && check compass_turn PASS "$S" || check compass_turn FAIL "$S ($(ui_text compass_heading))"
sensor acceleration 0:6.94:6.94; sleep 1
start "${A[@]}" --es demo_route qibla; sleep 8
cap 183_qibla_tilt
ui_has id compass_issue_tilt && check compass_tilt PASS "tilt warning at 45°" || check compass_tilt FAIL "no tilt warning"
point_to 211.0; sensor magnetic-field 0:80:-40; sleep 4
cap 184_qibla_interference
ui_has id compass_issue_interference && check compass_interference PASS "field 89 µT vs 43 µT expected" || check compass_interference FAIL "no interference warning"
# Sensors stop when the screen is left (back) and when the app goes to the background.
save_log
adb shell input keyevent 4; sleep 2
qlog | grep -q "sensors stop" && check qibla_stop_back PASS "stopped on leaving" || check qibla_stop_back FAIL "$(qlog | tail -3)"
adb shell dumpsys sensorservice > "$OUT/sensorservice_off.txt" 2>/dev/null
start "${A[@]}" --es demo_route qibla; sleep 6
save_log
adb shell input keyevent 3; sleep 2
qlog | grep -q "sensors stop" && check qibla_stop_home PASS "stopped in the background" || check qibla_stop_home FAIL "$(qlog | tail -3)"
# Turning into alignment with the option on: the app's accelerometer + magnetometer path follows live changes.
point_to 211.0
start "${A[@]}" --es demo_compass accelmag --es demo_route qibla; sleep 6
scroll_to contains "اهتزاز عند المحاذاة" && tap contains "اهتزاز عند المحاذاة" && sleep 1
save_log
for _ in 1 2 3 4 5 6; do point_to 241.0; sleep 1; done
wait_qlog "aligned offset=" 5 && check compass_align_event PASS "$(qlog | grep aligned | tail -1)" || check compass_align_event FAIL "no alignment event"
tools_json | grep -q '"vibrateOnAlign":true' && check compass_vibrate_option PASS "option saved" || check compass_vibrate_option FAIL "$(tools_json | head -c 300)"
# The app's own accelerometer + magnetometer path gives the same answer.
point_to 241.0
shot 185_qibla_accelmag 9 "${A[@]}" --es demo_compass accelmag --es demo_route qibla
for _ in 1 2 3; do point_to 241.0; sleep 1; done
S=$(ui_text compass_state); echo "$S" | grep -q "متجه نحو القبلة" && check compass_accelmag PASS "$S" || check compass_accelmag FAIL "$S ($(ui_text compass_heading))"
# Without a compass: the bearing and the map still work.
shot 186_qibla_no_sensor 8 "${A[@]}" --es demo_compass none --es demo_route qibla
ui_has id compass_issue_no_sensor && check compass_none PASS "no-sensor warning" || check compass_none FAIL "missing"
scroll_to id map_note && sleep 3 && cap 187_qibla_map_centered
qlog | grep -q "map drawn mode=CENTERED" && check qibla_map PASS "centred map drawn" || check qibla_map FAIL "no map drawn"
tap id map_mode_world && sleep 3 && scroll_to id map_note && cap 188_qibla_map_world
shot 189_qibla_newyork 9 --ez demo_reset true --ez demo_onboarded true --es demo_manual New_York,40.7128,-74.0060,America/New_York --es demo_now "$T_AFTERNOON" "${FREEZE[@]}" --es demo_compass none --es demo_route qibla
B=$(ui_text qibla_bearing); echo "$B" | grep -q "٥٨" && check qibla_newyork PASS "$B" || check qibla_newyork FAIL "$B"
scroll_to id map_note && sleep 3 && cap 190_qibla_map_newyork
if [ "$SUITE" = "full" ]; then
  shot 191_adhkar_english 8 "${A[@]}" --es demo_lang en --es demo_route adhkar
  shot 192_wird_dark 8 "${A[@]}" --es demo_theme DARK --es demo_route wird:sleep
  shot 193_item_english 8 "${A[@]}" --es demo_lang en --es demo_route adhkar_item:asbahna_mulk
  shot 194_qibla_dark_world 9 "${A[@]}" --es demo_theme DARK --es demo_qibla_map world --es demo_compass none --es demo_route qibla
  scroll_to id map_note && sleep 3 && cap 195_qibla_dark_map
  shot 196_qibla_english 9 "${A[@]}" --es demo_lang en --es demo_compass none --es demo_route qibla
  shot 197_tasbih_dark 7 "${A[@]}" --es demo_theme DARK --es demo_tasbih 12:99:الحمد_لله --es demo_route tasbih
fi
save_log

# ---------- Phase 7: calendar, Ramadan, night and Friday ----------
save_log
settings_json() { adb shell run-as "$PKG" cat files/datastore/settings.json 2>/dev/null; }
# Hides the on-screen keyboard if it is showing (Back would otherwise leave the screen).
hide_ime() { if adb shell dumpsys input_method 2>/dev/null | grep -q "mInputShown=true"; then adb shell input keyevent 4; sleep 1; fi; }
# Minutes after midnight of a time as the app shows it ("٥:٣٧ م", "4:35 PM" or "16:35").
mins() { python3 -c "
import sys,re
t=sys.argv[1].translate(str.maketrans('٠١٢٣٤٥٦٧٨٩','0123456789'))
h,m=map(int,re.search(r'(\d+):(\d+)',t).groups())
pm=('م' in t) or ('PM' in t); am=('ص' in t) or ('AM' in t)
if pm and h<12: h+=12
if am and h==12: h=0
print(h*60+m)" "$1"; }
C=(--ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh --es demo_active riyadh --es demo_now "$T_AFTERNOON" "${FREEZE[@]}")

# Month grid (Hijri first), navigation by arrows, swipe, the month picker and Today; the calendar has its own tab.
shot 200_home_before_calendar 7 "${C[@]}"
tap id tab_calendar && sleep 3 && cap 201_calendar_hijri
ui_has id calendar_tab && check cal_tab PASS "calendar opens as a tab" || check cal_tab FAIL "no calendar tab"
T0=$(ui_text cal_title); echo "$T0" | grep -q "١٤٤٨" && ui_has id cal_day_2026-10-14 && check cal_grid PASS "$T0" || check cal_grid FAIL "title $T0"
tap id cal_next && sleep 2
T1=$(ui_text cal_title); [ "$T1" != "$T0" ] && check cal_next PASS "$T1" || check cal_next FAIL "$T1"
# Right-to-left: the previous month is to the right, so a swipe from right to left goes back.
adb shell input swipe 900 1300 300 1300 300; sleep 2
T2=$(ui_text cal_title); [ "$T2" = "$T0" ] && check cal_swipe PASS "back to $T2 by swiping" || check cal_swipe FAIL "after swipe: $T2"
tap id cal_kind_gregorian && sleep 2 && cap 202_calendar_gregorian
T=$(ui_text cal_title); echo "$T" | grep -q "أكتوبر ٢٠٢٦" && check cal_gregorian PASS "$T" || check cal_gregorian FAIL "$T"
tap id cal_title && sleep 1 && tap id picker_year_minus && tap id picker_month_3 && sleep 2
T=$(ui_text cal_title); echo "$T" | grep -q "مارس ٢٠٢٥" && check cal_picker PASS "$T" || check cal_picker FAIL "$T"
tap id cal_today && sleep 2
T=$(ui_text cal_title); echo "$T" | grep -q "أكتوبر ٢٠٢٦" && check cal_today PASS "$T" || check cal_today FAIL "$T"
settings_json | grep -q '"kind":"GREGORIAN"' && check cal_kind_saved PASS "calendar choice saved" || check cal_kind_saved FAIL "$(settings_json | grep -o '"calendar":{[^}]*' | head -c 200)"
# A day of the next month shown dimmed opens its details too; Monday shows its sunnah fast with references.
tap id cal_day_2026-11-02 && sleep 2 && cap 203_day_adjacent_month
ui_has id day_fasting_monday && check cal_adjacent_day PASS "2 Nov (next month) opens; Monday fast" || check cal_adjacent_day FAIL "no details"
adb shell input keyevent 4; sleep 1
# Add an occasion from a day, then delete it and undo the delete.
start "${C[@]}" --es demo_route calendar_day:2026-10-22; sleep 7
cap 204_day_sheet
scroll_to id day_add_occasion && tap id day_add_occasion && sleep 2
tap id occ_title && adb shell input text "Family%svisit" && hide_ime
scroll_to id occ_repeat_yearly_hijri && tap id occ_repeat_yearly_hijri
scroll_to contains "تنبيه في يومها" && tap contains "تنبيه في يومها"
cap 205_occasion_edit
scroll_to id occ_save && bring_up occ_save && tap id occ_save && sleep 2 && cap 206_occasion_saved
J=$(settings_json)
echo "$J" | grep -q '"title":"Family visit"' && echo "$J" | grep -q '"repeat":"YEARLY_HIJRI"' && echo "$J" | grep -q '"notify":true' \
  && check occ_saved PASS "saved with yearly Hijri repeat and alert" || check occ_saved FAIL "$(echo "$J" | grep -o '"occasions":\[[^]]*' | head -c 300)"
# Back on the calendar, which opened on this day: its details now list the new occasion.
scroll_to exact "Family visit" && check occ_back PASS "day details list the new occasion" || check occ_back FAIL "not listed"
start "${C[@]}" --es demo_occasions 2026-10-22:Family_visit --es demo_route calendar_day:2026-10-22; sleep 7
scroll_to id occasion_delete_demo0 && tap id occasion_delete_demo0 && sleep 1 && cap 207_occasion_deleted
settings_json | grep -q '"occasions":\[\]' && check occ_delete PASS "deleted" || check occ_delete FAIL "still stored"
tap id day_undo && sleep 2
settings_json | grep -q '"title":"Family visit"' && check occ_undo PASS "delete undone" || check occ_undo FAIL "$(settings_json | grep -o '"occasions":\[[^]]*' | head -c 200)"
# List mode is a separate choice: a larger font keeps the grid.
shot 208_calendar_list 8 "${C[@]}" --ez demo_cal_list true --es demo_occasions 2026-10-22:Family_visit --es demo_route month
ui_has id cal_row_2026-10-14 && check cal_list PASS "list rows" || check cal_list FAIL "no list"
adb shell settings put system font_scale 1.3; sleep 1
shot 209_calendar_font130 8 "${C[@]}" --es demo_route month
ui_has id cal_day_2026-10-14 && check cal_font_grid PASS "grid kept at 130% font" || check cal_font_grid FAIL "grid not shown"
adb shell settings put system font_scale 1.0; sleep 1
# The display offset moves the Hijri date shown, not the prayer times.
start "${C[@]}" --es demo_route calendar_day:2026-10-14; sleep 7
H0=$(ui_text day_hijri); F0=$(ui_text day_gregorian)
start "${C[@]}" --ei demo_hijri 1 --es demo_route calendar_day:2026-10-14; sleep 7
H1=$(ui_text day_hijri); cap 210_day_offset_plus1
[ -n "$H0" ] && [ "$H0" != "$H1" ] && check cal_offset PASS "$H0 → $H1 ($F0)" || check cal_offset FAIL "$H0 / $H1"
# Tools: conversion and the interval between dates.
shot 211_calendar_tools 7 "${C[@]}" --es demo_route calendar_tools
# The fields start with today's date: clear each one before typing.
typein() { tap id "$1" && adb shell input keyevent 123 && for _ in 1 2 3 4 5; do adb shell input keyevent 67; done && adb shell input text "$2"; }
tap id conv_hijri; typein conv_day 1; typein conv_month 9; typein conv_year 1448
hide_ime; sleep 1
T=$(ui_text conv_result); cap 212_converter
echo "$T" | grep -q "رمضان ١٤٤٨" && echo "$T" | grep -q "٢٠٢٧" && check cal_convert PASS "$T" || check cal_convert FAIL "$T"
scroll_to id int_days && T=$(ui_text int_days)
echo "$T" | grep -q "٣٠ يومًا" && check cal_interval PASS "$T" || check cal_interval FAIL "$T"
cap 213_interval
# Fasting reminders: off by default; when on, the next Monday/Thursday evening-before reminder is scheduled.
save_log
start "${C[@]}" --es demo_fasting monthu:1260 --ez demo_log_alarm true --es demo_route month; sleep 8
adb logcat -d -s SakinaAlerts:I | grep "Scheduled" | tail -1 > "$OUT/fasting_scheduled.txt"
grep -q "FASTING:" "$OUT/fasting_scheduled.txt" && check fasting_scheduled PASS "$(cut -c1-160 "$OUT/fasting_scheduled.txt")" || check fasting_scheduled FAIL "$(cat "$OUT/fasting_scheduled.txt")"
# A personal occasion with an alert two minutes from now (the emulator's real clock, Riyadh time): a real notification.
save_log
D=$(TZ=Asia/Riyadh date -d '+2 min' +%F); M=$(TZ=Asia/Riyadh date -d '+2 min' +'%H %M' | awk '{print $1*60+$2}')
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/$ACT" --ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh --es demo_active riyadh --es demo_occasions "$D:Occasion_test:$M" >/dev/null
adb shell input keyevent 3
POSTED=0
for _ in $(seq 1 200); do
  if adb shell dumpsys notification --noredact 2>/dev/null | grep -q "android.title=String (Occasion test)"; then POSTED=1; break; fi
  sleep 1
done
[ "$POSTED" -eq 1 ] && check occ_notification PASS "posted at its time ($D, minute $M)" || check occ_notification FAIL "not posted; $(adb logcat -d -s SakinaAlerts:I | tail -2 | tr '\n' ' ')"
if [ "$POSTED" -eq 1 ]; then
  shade_open; cap 214_occasion_notification
  tap contains "Occasion test" && sleep 3
  cap 215_occasion_opens_calendar
  ui_has id day_hijri && check occ_notif_opens PASS "opens the calendar on its day" || check occ_notif_opens FAIL "calendar not opened"
  shade_close
fi
save_log

# Ramadan: on 12 Ramadan 1447 at 17:30 in Riyadh the fast is waiting for Maghrib.
shot 220_ramadan 8 --es demo_now "$T_RAMADAN" "${FREEZE[@]}" --ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh --es demo_route ramadan
S=$(ui_text ramadan_status); L=$(ui_text ramadan_length)
echo "$S" | grep -q "حتى الإفطار" && check ramadan_status PASS "$S · $L" || check ramadan_status FAIL "$S"
scroll_to id imsak_12 && cap 221_imsakiya && check ramadan_imsakiya PASS "imsakiya rows" || check ramadan_imsakiya FAIL "no imsakiya"
scroll_to id night_27 12 && cap 222_last_ten
ui_has id night_27 && check ramadan_last_ten PASS "last ten nights listed" || check ramadan_last_ten FAIL "missing"
start --es demo_now "$T_RAMADAN" "${FREEZE[@]}" --ez demo_onboarded true --es demo_route ramadan; sleep 7
tap id ramadan_khatma && sleep 2 && ui_has id khatma_start && check ramadan_khatma PASS "khatma linked" || check ramadan_khatma FAIL "not opened"
shot 223_ramadan_ahead 7 "${C[@]}" --es demo_route ramadan
S=$(ui_text ramadan_status); echo "$S" | grep -q "يبدأ بعد" && check ramadan_ahead PASS "$S" || check ramadan_ahead FAIL "$S"
# Night: at 21:30 in Riyadh the night is under way; midnight is halfway from Maghrib to Fajr.
shot 224_night 8 --es demo_now "$T_NIGHT" "${FREEZE[@]}" --ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh --es demo_route night
MG=$(mins "$(ui_text night_maghrib)"); MD=$(mins "$(ui_text night_midnight)"); FJ=$(mins "$(ui_text night_fajr)"); LT=$(mins "$(ui_text night_last_third)")
python3 -c "
m,md,f,lt=$MG,$MD,$FJ,$LT
f+=1440
lt=lt+1440 if lt<m else lt
md=md+1440 if md<m else md
ok=abs((m+f)/2-md)<=1 and abs(m+(f-m)*2/3-lt)<=1
print('ok' if ok else 'bad')" | grep -q ok && check night_times PASS "maghrib $MG, midnight $MD, last third $LT, fajr $FJ (minutes)" || check night_times FAIL "maghrib $MG midnight $MD third $LT fajr $FJ"
ui_has id night_until_third && check night_current PASS "countdown to the last third" || check night_current FAIL "no countdown"
scroll_to id duha_range && cap 225_duha
D1=$(ui_text duha_range)
start --es demo_now "$T_NIGHT" "${FREEZE[@]}" --ez demo_onboarded true --es demo_route night; sleep 7
scroll_to id duha_range; tap exact "+" && sleep 1
D2=$(ui_text duha_range); [ -n "$D1" ] && [ "$D1" != "$D2" ] && check duha_margin PASS "$D1 → $D2" || check duha_margin FAIL "$D1 / $D2"
# Friday: Surat al-Kahf opens in the reader; the blessing opens its dhikr; the home card shows on Friday only.
shot 226_friday 7 "${C[@]}" --es demo_quran view=TEXT --es demo_route friday
tap id friday_kahf && sleep 4 && cap 227_friday_kahf
ui_has contains "الكهف" && check friday_kahf PASS "al-Kahf opened" || check friday_kahf FAIL "not opened"
start "${C[@]}" --es demo_route friday; sleep 6
tap id friday_salawat && sleep 3 && ui_has id dhikr_source && check friday_salawat PASS "blessing opened" || check friday_salawat FAIL "not opened"
shot 228_home_friday 8 --es demo_now "2026-10-16T06:30:00Z" "${FREEZE[@]}" --ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh
scroll_to id home_friday && check friday_home PASS "card on Friday morning" || check friday_home FAIL "no card"
bring_up home_friday; cap 229_home_friday_card
start "${C[@]}"; sleep 7
H=0; for _ in 1 2 3; do ui_has id home_friday && H=1; adb shell input swipe 540 1700 540 900 400; sleep 1; done
[ "$H" -eq 0 ] && check friday_home_hidden PASS "hidden on Wednesday" || check friday_home_hidden FAIL "card on Wednesday"
if [ "$SUITE" = "full" ]; then
  shot 230_calendar_english 8 "${C[@]}" --es demo_lang en --es demo_route month
  shot 231_calendar_dark 8 "${C[@]}" --es demo_theme DARK --es demo_occasions 2026-10-22:Family_visit --es demo_route month
  shot 232_ramadan_english 8 --es demo_now "$T_RAMADAN" "${FREEZE[@]}" --ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh --es demo_lang en --es demo_route ramadan
  shot 233_night_dark 8 --es demo_now "$T_NIGHT" "${FREEZE[@]}" --ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh --es demo_theme DARK --es demo_route night
  shot 234_friday_dark 7 "${C[@]}" --es demo_theme DARK --es demo_route friday
  shot 235_calendar_settings 7 "${C[@]}" --es demo_fasting both:1260 --es demo_route calendar
fi
save_log

# ---------- Phase 8 and the painted skies: in the extras job on Android 14, here on Android 8 ----------
if [ "$SUITE" != "full" ]; then phase8; visual; fi

# ---------- Phase 2: alerts (real AlarmManager, notifications, playback) ----------
save_log
REAL=(--ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh,jeddah --es demo_active riyadh)
if [ "$SDK" -ge 33 ]; then adb shell pm revoke "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true; fi
if [ "$SDK" -ge 31 ]; then adb shell appops set "$PKG" SCHEDULE_EXACT_ALARM default >/dev/null 2>&1 || true; fi
shot 60_alerts_permissions_missing 7 "${REAL[@]}" --es demo_route alerts
if [ "$SDK" -ge 31 ]; then
  start "${REAL[@]}" --es demo_alerts DHUHR=NOTIFY,ASR=NOTIFY,MAGHRIB=NOTIFY,ISHA=NOTIFY,FAJR=NOTIFY
  sleep 6
  alarm_dump > "$OUT/alarm_dump_inexact.txt"
  W=$(grep -A3 "ALERT_FIRE" "$OUT/alarm_dump_inexact.txt" | grep -c "window=+10m0s0ms")
  T=$(grep -c "ALERT_FIRE" "$OUT/alarm_dump_inexact.txt")
  [ "$W" -ge 1 ] && [ "$T" -ge 2 ] && check inexact_fallback PASS "10-minute window plus idle backup" || check inexact_fallback FAIL "window10=$W alarms=$T"
fi
if [ "$SDK" -ge 33 ]; then adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS; fi
if [ "$SDK" -ge 31 ]; then adb shell appops set "$PKG" SCHEDULE_EXACT_ALARM allow; fi
shot 61_alerts_permissions_granted 7 "${REAL[@]}" --es demo_route alerts --es demo_alerts FAJR=ADHAN,DHUHR=NOTIFY,ASR=EXCERPT,MAGHRIB=VIBRATE,ISHA=SILENT --ei demo_pre 10 --ei demo_iqama 15

# Turning on an alert by tapping: Settings > Adhan and alerts > Maghrib > Notification with sound.
start "${REAL[@]}" --es demo_tab settings
sleep 4
scroll_to id nav_alerts 8; into_view nav_alerts; tap id nav_alerts && tap_scroll id prayer_alert_maghrib && cap 62_prayer_page_before
tap exact "إشعار بصوت" && sleep 1 && cap 63_prayer_page_selected
adb shell input keyevent 4; sleep 2; cap 64_alerts_after_tap

# The chain alarm is set in AlarmManager at the time the app logged.
start "${REAL[@]}" --es demo_alerts FAJR=NOTIFY,DHUHR=NOTIFY,ASR=NOTIFY,MAGHRIB=NOTIFY,ISHA=NOTIFY --ez demo_log_alarm true
sleep 6
LOGGED=$(adb logcat -d -s SakinaAlerts:I | grep -o "Scheduled .* epochMs=[0-9]*" | tail -1)
EPOCH=$(echo "$LOGGED" | grep -o "epochMs=[0-9]*" | cut -d= -f2)
alarm_dump > "$OUT/alarm_dump.txt"
if [ -n "$EPOCH" ] && grep -q "$EPOCH" "$OUT/alarm_dump.txt"; then check alarm_set PASS "$LOGGED" critical
elif [ -n "$EPOCH" ] && grep -q "ALERT_FIRE" "$OUT/alarm_dump.txt"; then check alarm_set PASS "fire alarm present; logged $LOGGED" critical
else check alarm_set FAIL "logged='$LOGGED'" critical; fi
if grep -A4 "ALERT_FIRE" "$OUT/alarm_dump.txt" | grep -q "window=0"; then check alarm_exact PASS "window=0 $(grep -o "exactAllowReason=[a-z_]*" "$OUT/alarm_dump.txt" | head -1)"
else check alarm_exact FAIL "not exact (see alarm_dump.txt)"; fi

# A test notification through the real alarm path.
save_log
start "${REAL[@]}" --es demo_test NOTIFY:5
sleep 9
N=$(notif_posted 900)
[ "$N" -ge 1 ] && check test_notification PASS "posted" critical || check test_notification FAIL "not posted" critical
adb logcat -d -s SakinaAlerts:I | grep "Test alert" | tail -2 | tee -a "$CHECKS"
shade_open; cap 70_test_notification_shade; shade_close

# Adhan playback through the foreground service, then the Stop action.
save_log
start "${REAL[@]}" --es demo_test ADHAN:4
sleep 9
R=$(adhan_running)
adb shell dumpsys audio > "$OUT/audio_during_adhan.txt" 2>/dev/null
grep -q "$PKG" "$OUT/audio_during_adhan.txt" && FOCUS=yes || FOCUS=no
[ "$R" -ge 1 ] && check adhan_service PASS "running, focus=$FOCUS" critical || check adhan_service FAIL "not running" critical
adb logcat -d -s SakinaAdhan:I | tail -3 | tee -a "$CHECKS"
shade_open; cap 71_adhan_playing_shade
if tap exact "إيقاف"; then
  for _ in 1 2 3 4 5 6 7 8; do sleep 1; R2=$(adhan_running); [ "$R2" -eq 0 ] && break; done
  [ "$R2" -eq 0 ] && check adhan_stop PASS "stopped from notification" critical || check adhan_stop FAIL "still running" critical
else
  check adhan_stop FAIL "stop action not found" critical
fi
cap 72_after_stop; shade_close
adb logcat -d -s SakinaAdhan:I | grep "finished" | tail -1 | tee -a "$CHECKS"

# The 20-second excerpt ends on its own.
save_log
start "${REAL[@]}" --es demo_test EXCERPT:3
sleep 8
R=$(adhan_running)
sleep 20
R2=$(adhan_running)
DONE=$(adb logcat -d -s SakinaAdhan:I | grep -c "finished EXCERPT_DONE")
[ "$R" -ge 1 ] && [ "$R2" -eq 0 ] && [ "$DONE" -ge 1 ] && check excerpt PASS "ran then ended after 20 s" critical || check excerpt FAIL "running=$R after=$R2 done=$DONE" critical

# Quiet mode skips alerts.
save_log
start "${REAL[@]}" --ei demo_quiet 30 --es demo_test NOTIFY:4
sleep 9
Q=$(adb logcat -d -s SakinaAlerts:I | grep -c "delivered .*SKIP_QUIET")
F=$(adb logcat -d -s SakinaAlerts:I | grep -c "delivered .*: FIRE")
[ "$Q" -ge 1 ] && [ "$F" -eq 0 ] && check quiet PASS "test alert skipped during quiet" || check quiet FAIL "skipped=$Q fired=$F"
cap 73_quiet_home
start "${REAL[@]}" --ei demo_quiet 0
sleep 2

if [ "$SUITE" = "full" ]; then
  shot 65_reminders 7 "${REAL[@]}" --es demo_route alerts,reminders --es demo_reminders DUHA,ADHKAR_MORNING,FRIDAY
  shot 66_recordings 7 "${REAL[@]}" --es demo_route alerts,recordings
  shot 67_prayer_fajr_adhan 7 "${REAL[@]}" --es demo_route alerts,prayer_fajr --es demo_alerts FAJR=ADHAN
  shot 68_alerts_english 7 "${REAL[@]}" --es demo_lang en --es demo_route alerts --es demo_alerts ASR=ADHAN --ei demo_pre 5
  shot 69_alerts_dark 7 "${REAL[@]}" --es demo_theme DARK --es demo_route alerts --es demo_alerts ASR=ADHAN

  # Next-prayer card in the notification shade.
  start "${REAL[@]}" --ez demo_card true
  sleep 6
  shade_open; cap 74_card_shade; shade_close
  [ "$(notif_posted 1)" -ge 1 ] && check card PASS "posted" || check card FAIL "not posted"

  # Exact alarms while the device is idle (Doze).
  save_log
  start "${REAL[@]}" --es demo_test NOTIFY:25
  sleep 3
  adb shell input keyevent 26
  adb shell dumpsys deviceidle force-idle >/dev/null 2>&1
  sleep 30
  adb shell dumpsys deviceidle unforce >/dev/null 2>&1
  adb shell input keyevent 224
  D=$(adb logcat -d -s SakinaAlerts:I | grep "Test alert NOTIFY delivered" | tail -1)
  [ -n "$D" ] && check doze PASS "$D" || check doze FAIL "no delivery while idle"

  # A time-zone change rebuilds the chain.
  save_log
  OLDTZ=$(adb shell getprop persist.sys.timezone | tr -d '\r')
  adb shell cmd alarm set-timezone America/New_York >/dev/null 2>&1 || adb shell service call alarm 3 s16 America/New_York >/dev/null 2>&1
  sleep 6
  adb logcat -d -s SakinaAlerts:I | grep -q "TIMEZONE_CHANGED" && check timezone PASS "rescheduled" || check timezone FAIL "no reschedule logged"
  adb shell cmd alarm set-timezone "${OLDTZ:-GMT}" >/dev/null 2>&1 || true

  # Reboot: the alarm chain comes back without opening the app.
  start "${REAL[@]}" --es demo_alerts FAJR=NOTIFY,DHUHR=NOTIFY,ASR=NOTIFY,MAGHRIB=NOTIFY,ISHA=NOTIFY
  sleep 5
  adb shell input keyevent 3
  adb logcat -d > "$OUT/logcat_part3.txt"
  # A normal shutdown (like holding the power button), so Android saves its state, including exact-alarm access.
  adb shell svc power reboot >/dev/null 2>&1 || adb reboot
  sleep 10
  adb wait-for-device
  for _ in $(seq 1 90); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 2; done
  sleep 25
  adb shell input keyevent 82 >/dev/null 2>&1 || true
  # The alarm service can answer empty for a while after boot: ask again until it lists the app's alarms.
  for _ in $(seq 1 15); do
    alarm_dump > "$OUT/alarm_dump_after_reboot.txt"
    grep -q "ALERT_FIRE" "$OUT/alarm_dump_after_reboot.txt" && break
    sleep 4
  done
  adb logcat -d -s SakinaAlerts:I > "$OUT/alerts_after_reboot.txt"
  if grep -q "ALERT_FIRE" "$OUT/alarm_dump_after_reboot.txt" && grep -q "BOOT_COMPLETED" "$OUT/alerts_after_reboot.txt"; then
    check reboot PASS "alarm rescheduled after BOOT_COMPLETED" critical
  else
    check reboot FAIL "see alarm_dump_after_reboot.txt" critical
  fi
  if grep "Scheduled" "$OUT/alerts_after_reboot.txt" | tail -1 | grep -q "exact=true"; then
    check reboot_exact PASS "still exact after reboot" critical
  else
    check reboot_exact FAIL "$(grep Scheduled "$OUT/alerts_after_reboot.txt" | tail -1)" critical
  fi
  # No recitation comes back after a reboot.
  rlog | grep -q "service created" && check recitation_no_resume FAIL "recitation service started after boot" critical || check recitation_no_resume PASS "nothing resumed after boot" critical
  cat "$OUT/logcat_part3.txt" >> "$OUT/logcat_part2.txt"; rm -f "$OUT/logcat_part3.txt"
fi

# ---------- Round 10: tabs, quick access, day-name widget, widget settings, Mushaf on an Arabic phone (ci/r10.sh) ----------
if [ "$SUITE" != "full" ]; then r10; r11; r12; r13; r14; r15; fi

finish_run
