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

T_AFTERNOON="2026-10-14T13:20:00Z"   # 16:20 in Riyadh: between Asr and Maghrib
T_NIGHT="2026-10-14T18:30:00Z"       # 21:30 in Riyadh: after Isha
T_FAJR="2026-10-15T01:50:00Z"        # 04:50 in Riyadh: after Fajr
T_RAMADAN="2026-03-01T14:30:00Z"     # 17:30 in Riyadh, 12 Ramadan 1447
FREEZE=(--ez demo_freeze true)
BASE=(--es demo_now "$T_AFTERNOON" "${FREEZE[@]}" --ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh,jeddah,makkah --es demo_active riyadh)

# ---------- Real flow: onboarding, picking a city by typing, switching tabs, restart ----------
shot 01_onboarding_welcome 7 --es demo_now "$T_AFTERNOON" "${FREEZE[@]}" --ez demo_reset true
tap exact "التالي" && cap 02_onboarding_location
tap exact "التالي" && cap 03_onboarding_method
tap exact "التالي" && cap 04_onboarding_ready
tap exact "ابدأ" && sleep 1 && cap 05_home_no_location
tap exact "اختيار مدينة" && sleep 1.5 && cap 06_city_picker
tap id city_search && adb shell input text "Jeddah" && sleep 2 && cap 07_city_search
tap exact "جدة" && sleep 2 && cap 08_home_jeddah_after_tap
tap id tab_times && sleep 1.5 && cap 09_times_day
tap exact "الأسبوع" && sleep 1.5 && cap 10_times_week
tap id tab_settings && sleep 1.5 && cap 11_settings
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
  shot 52_landscape_times 7 "${BASE[@]}" --es demo_tab times --es demo_view week
  adb shell cmd window user-rotation lock 0 >/dev/null 2>&1 || true
  adb shell settings put system user_rotation 0
  sleep 2
fi

adb logcat -d > "$OUT/logcat_part2.txt"
cat "$OUT/logcat_part1.txt" "$OUT/logcat_part2.txt" > "$OUT/logcat.txt"
rm -f "$OUT"/logcat_part*.txt "$OUT/.ui.xml"
grep -E "SakinaDemo|AndroidRuntime" "$OUT/logcat.txt" | head -80 > "$OUT/app_log.txt" || true
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
echo "UI check passed (SDK $SDK)"
