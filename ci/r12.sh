# Round 12: recitation followed in the text reader and the Mushaf, and memorisation. Sourced by ci/screenshots.sh
# (uses its helpers: tap, ui_has, ui_text, check, rlog...). Defines r12().

qjson() { adb shell run-as "$PKG" cat files/datastore/quran.json 2>/dev/null | tr -d '\r'; }
wait_ui() { for _ in $(seq 1 "$3"); do ui_has "$1" "$2" && return 0; sleep 1; done; return 1; }

r12() {
save_log
adb shell svc power stayon true >/dev/null 2>&1
Q=(--ez demo_reset true --ez demo_onboarded true --es demo_cities riyadh --es demo_active riyadh --es demo_now "$T_AFTERNOON" "${FREEZE[@]}")

# The text reader: listen from here; the recited ayah is highlighted and the small player shows the ayah.
start "${Q[@]}" --es demo_route quran_reader:1:1; sleep 7
cap 600_reader_before
tap id reader_listen && sleep 2
if wait_ui id follow_bar 30; then
  check follow_bar PASS "small player under the reader"
  wait_ui contains "الآية" 40 && T=$(ui_text follow_title) && check follow_ayah PASS "$T" || check follow_ayah FAIL "no ayah in the player: $(ui_text follow_title)"
  wait_ui id reader_playing 20 && check reader_highlight PASS "recited ayah highlighted" || check reader_highlight FAIL "no highlighted ayah"
  sleep 3; cap 601_reader_following
  tap id follow_toggle && sleep 2 && cap 602_reader_paused
else
  check follow_bar FAIL "no player after tapping listen ($(rlog | tail -2 | tr '\n' ' '))"
fi

# The Mushaf: listen from the page; the small player stays over the page.
start "${Q[@]}" --es demo_route mushaf:1; sleep 8
tap id mushaf_listen_here && sleep 3
if wait_ui id follow_bar 30; then
  wait_ui contains "الآية" 40
  sleep 2; cap 603_mushaf_following
  check mushaf_follow PASS "$(ui_text follow_title)"
  tap id follow_stop && sleep 2
else
  check mushaf_follow FAIL "no player in the Mushaf"
fi

# Memorisation: a new passage (Al-Fatiha), words hidden, an ayah revealed by a tap, the audio, then "I know it".
shot 610_hifz_home 7 "${Q[@]}" --es demo_route hifz
ui_has id hifz_new && check hifz_home PASS "memorisation home" || check hifz_home FAIL "no memorisation home"
tap id hifz_start && sleep 4 && cap 611_hifz_first_word
ui_has id hifz_masked_2 && check hifz_mask PASS "words hidden after the first" || check hifz_mask FAIL "ayah 2 not masked"
tap id hifz_ayah_2 && sleep 1
ui_has id hifz_shown_2 && check hifz_reveal PASS "tap shows the ayah" || check hifz_reveal FAIL "ayah 2 still hidden"
tap exact "مخفي" && sleep 1 && cap 612_hifz_hidden
scroll_to id hifz_play 6; tap id hifz_play && sleep 3
if wait_ui id follow_bar 30; then sleep 3; cap 613_hifz_audio; check hifz_audio PASS "passage playing"; tap id follow_stop; sleep 1; else check hifz_audio FAIL "no audio"; fi
tap id hifz_done && sleep 3
qjson | grep -q '"hifz":\[{"sura":1,"from":1,"to":7' && check hifz_saved PASS "passage saved, first review tomorrow" || check hifz_saved FAIL "$(qjson | grep -o '"hifz":[^]]*' | head -c 200)"
shot 614_hifz_list 7 --ez demo_onboarded true --es demo_now "$T_AFTERNOON" "${FREEZE[@]}" --es demo_route hifz
ui_has id "hifz_item_1:1-7" && check hifz_listed PASS "listed with its next review" || check hifz_listed FAIL "not listed"
save_log
}
