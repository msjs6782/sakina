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
adhan_running() { adb shell dumpsys activity services "$PKG/com.sakina.app.alerts.AdhanService" 2>/dev/null | grep -c "ServiceRecord" ; }
alarm_dump() { adb shell dumpsys alarm 2>/dev/null | grep -B3 -A8 "$PKG" ; }

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
  adb shell settings put system user_rotation 1
  sleep 2
  shot 52_landscape_dashboard 7 "${BASE[@]}" --es demo_layout DASHBOARD
  adb shell cmd window user-rotation lock 0 >/dev/null 2>&1 || true
  adb shell settings put system user_rotation 0
  sleep 2
fi

# ---------- Phase 2: alerts (real AlarmManager, notifications, playback) ----------
adb logcat -d > "$OUT/logcat_part2.txt"; adb logcat -c
REAL=(--ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh,jeddah --es demo_active riyadh)
if [ "$SDK" -ge 33 ]; then adb shell pm revoke "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true; fi
if [ "$SDK" -ge 31 ]; then adb shell appops set "$PKG" SCHEDULE_EXACT_ALARM default >/dev/null 2>&1 || true; fi
shot 60_alerts_permissions_missing 7 "${REAL[@]}" --es demo_route alerts
if [ "$SDK" -ge 33 ]; then adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS; fi
if [ "$SDK" -ge 31 ]; then adb shell appops set "$PKG" SCHEDULE_EXACT_ALARM allow; fi
shot 61_alerts_permissions_granted 7 "${REAL[@]}" --es demo_route alerts --es demo_alerts FAJR=ADHAN,DHUHR=NOTIFY,ASR=EXCERPT,MAGHRIB=VIBRATE,ISHA=SILENT --ei demo_pre 10 --ei demo_iqama 15

# Turning on an alert by tapping: Settings > Adhan and alerts > Maghrib > Notification with sound.
start "${REAL[@]}" --es demo_tab settings
sleep 4
tap id nav_alerts && tap id prayer_alert_maghrib && cap 62_prayer_page_before
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
grep -q "exactAllowReason=permission\|exactAllowReason=policy\|EXACT\|setExact" "$OUT/alarm_dump.txt" && check alarm_exact PASS "exact" || check alarm_exact FAIL "no exact marker in dump (see alarm_dump.txt)"

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
  sleep 2
  R2=$(adhan_running)
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
Q=$(adb logcat -d -s SakinaAlerts:I | grep -c "SKIP_QUIET")
N=$(notif_posted 900)
[ "$Q" -ge 1 ] && [ "$N" -eq 0 ] && check quiet PASS "test alert skipped" || check quiet FAIL "skip=$Q posted=$N"
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
  adb reboot
  adb wait-for-device
  for _ in $(seq 1 90); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 2; done
  sleep 25
  adb shell input keyevent 82 >/dev/null 2>&1 || true
  alarm_dump > "$OUT/alarm_dump_after_reboot.txt"
  adb logcat -d -s SakinaAlerts:I > "$OUT/alerts_after_reboot.txt"
  if grep -q "ALERT_FIRE" "$OUT/alarm_dump_after_reboot.txt" && grep -q "BOOT_COMPLETED" "$OUT/alerts_after_reboot.txt"; then
    check reboot PASS "alarm rescheduled after BOOT_COMPLETED" critical
  else
    check reboot FAIL "see alarm_dump_after_reboot.txt" critical
  fi
  cat "$OUT/logcat_part3.txt" >> "$OUT/logcat_part2.txt"; rm -f "$OUT/logcat_part3.txt"
fi

adb logcat -d >> "$OUT/logcat_part2.txt"
cat "$OUT/logcat_part1.txt" "$OUT/logcat_part2.txt" > "$OUT/logcat.txt"
rm -f "$OUT"/logcat_part*.txt "$OUT/.ui.xml"
grep -E "SakinaDemo|SakinaAlerts|SakinaAdhan|AndroidRuntime: FATAL" "$OUT/logcat.txt" | head -200 > "$OUT/app_log.txt" || true
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
