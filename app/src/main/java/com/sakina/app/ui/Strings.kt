package com.sakina.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.sakina.app.core.calendar.Occasion
import com.sakina.app.core.format.Lang
import com.sakina.app.core.prayer.AsrMethod
import com.sakina.app.core.prayer.CalcMethod
import com.sakina.app.core.prayer.DayIssue
import com.sakina.app.core.prayer.HighLatitudeRule
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.UmmAlQuraIsha
import com.sakina.app.data.HomeCard
import com.sakina.app.data.HomeLayout
import com.sakina.app.data.ThemeMode
import com.sakina.app.data.VisualStyle

/** All UI text, Arabic and English side by side so the two never drift apart. */
class Strings(val lang: Lang, val arabicDigits: Boolean = lang == Lang.AR) {
    private fun t(ar: String, en: String) = digits(if (lang == Lang.AR) ar else en)

    /** Applies the user's digit choice to fixed text (both directions, so it is idempotent). */
    fun digits(text: String): String {
        val sb = StringBuilder(text.length)
        var isolated = 0
        for (c in text) {
            if (c == '\u2066') isolated++
            if (c == '\u2069' && isolated > 0) isolated--
            sb.append(
                when {
                    isolated > 0 -> c
                    arabicDigits && c in '0'..'9' -> '\u0660' + (c - '0')
                    !arabicDigits && c in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                    !arabicDigits && c == '\u066b' -> '.'                    else -> c
                }
            )
        }
        return sb.toString()
    }
    val isAr get() = lang == Lang.AR

    val appName get() = t("سكينة", "Sakina")

    // Tabs
    val tabHome get() = t("الرئيسية", "Home")
    val tabTimes get() = t("المواقيت", "Times")
    val tabQuran get() = t("القرآن", "Quran")
    val tabDiscover get() = t("اكتشف", "Discover")
    val tabSettings get() = t("الإعدادات", "Settings")

    fun prayer(k: PrayerKey) = when (k) {
        PrayerKey.FAJR -> t("الفجر", "Fajr")
        PrayerKey.SUNRISE -> t("الشروق", "Sunrise")
        PrayerKey.DHUHR -> t("الظهر", "Dhuhr")
        PrayerKey.ASR -> t("العصر", "Asr")
        PrayerKey.MAGHRIB -> t("المغرب", "Maghrib")
        PrayerKey.ISHA -> t("العشاء", "Isha")
    }

    // Common
    val back get() = t("رجوع", "Back")
    val close get() = t("إغلاق", "Close")
    val cancel get() = t("إلغاء", "Cancel")
    val save get() = t("حفظ", "Save")
    val confirm get() = t("تأكيد", "Confirm")
    val delete get() = t("حذف", "Delete")
    val edit get() = t("تعديل", "Edit")
    val reset get() = t("إعادة الضبط", "Reset")
    val skip get() = t("تخطٍّ", "Skip")
    val skipAll get() = t("تخطّي الإعداد", "Skip setup")
    val next get() = t("التالي", "Next")
    val done get() = t("تم", "Done")
    val today get() = t("اليوم", "Today")
    val tomorrow get() = t("غدًا", "Tomorrow")
    val yesterday get() = t("أمس", "Yesterday")
    val search get() = t("بحث", "Search")
    val minutesShort get() = t("د", "min")
    val unavailable get() = t("تعذّر الحساب", "Unavailable")
    val comingLater get() = t("في مرحلة قادمة", "In a later phase")

    // Home
    val nextPrayer get() = t("الصلاة القادمة", "Next prayer")
    val remaining get() = t("متبقٍّ", "Remaining")
    fun sinceAdhan(name: String) = t("مضى على موعد أذان $name", "Since $name time")
    val sinceNote get() = t("المدة من الموعد المحسوب، لا من تشغيل صوت فعلي.", "Measured from the calculated time, not from any sound actually played.")
    val todayTimes get() = t("مواقيت اليوم", "Today's times")
    val nextSunrise get() = t("الشروق القادم", "Next sunrise")
    val nextSunset get() = t("الغروب القادم", "Next sunset")
    val sunCardTitle get() = t("الشمس", "Sun")
    val astronomicalNote get() = t("وقتا الشروق والغروب الفلكيان، دون الإزاحات.", "Astronomical sunrise and sunset, without offsets.")
    val chooseLocationTitle get() = t("اختر موقعك لعرض المواقيت", "Choose a place to see prayer times")
    val chooseLocationBody get() = t("المواقيت والقبلة تحتاج موقعًا تختاره أنت. القرآن والأذكار تعمل دون موقع.", "Prayer times and Qibla need a place you choose. Quran and adhkar work without one.")
    val pickCity get() = t("اختيار مدينة", "Pick a city")
    val useGps get() = t("موقعي الحالي", "Current location")
    val manualEntry get() = t("إدخال يدوي", "Enter manually")
    val addPlace get() = t("إضافة موقع", "Add place")
    val travelling get() = t("وضع السفر", "Travel mode")
    val returnHome get() = t("العودة للمنزل", "Return home")
    fun homeIs(name: String) = t("المنزل: $name", "Home: $name")
    val noTimesToday get() = t("لا توجد صلاة قادمة يمكن حسابها في الأيام القريبة لهذا الموقع.", "No upcoming prayer can be calculated for this place in the next days.")
    val dateCardTitle get() = t("التاريخ والمناسبات", "Date and occasions")
    fun nextOccasion(name: String, days: String) = t("$name بعد $days", "$name in $days")
    fun daysCount(n: String, raw: Long) = if (isAr) {
        when (raw) {
            1L -> "يوم واحد"
            2L -> "يومين"
            in 3L..10L -> "$n أيام"
            else -> "$n يومًا"
        }
    } else if (raw == 1L) "1 day" else "$n days"
    val occasionNote get() = t("محسوبة بتقويم أم القرى، وقد تختلف بداية الشهر محليًا بحسب الرؤية.", "Calculated with the Umm al-Qura calendar; the start of a month may differ locally with moon sighting.")
    val todayIs get() = t("اليوم", "Today")
    fun occasion(o: Occasion) = when (o) {
        Occasion.HIJRI_NEW_YEAR -> t("رأس السنة الهجرية", "Islamic New Year")
        Occasion.ASHURA -> t("يوم عاشوراء", "Day of Ashura")
        Occasion.RAMADAN_START -> t("بداية رمضان", "Start of Ramadan")
        Occasion.EID_AL_FITR -> t("عيد الفطر", "Eid al-Fitr")
        Occasion.DHUL_HIJJAH_TEN -> t("العشر الأوائل من ذي الحجة", "First ten days of Dhu al-Hijjah")
        Occasion.ARAFAH -> t("يوم عرفة", "Day of Arafah")
        Occasion.EID_AL_ADHA -> t("عيد الأضحى", "Eid al-Adha")
        Occasion.TASHREEQ -> t("أيام التشريق", "Days of Tashreeq")
    }
    val ramadanDay get() = t("رمضان", "Ramadan")

    // Times screen
    val day get() = t("اليوم", "Day")
    val week get() = t("الأسبوع", "Week")
    val month30 get() = t("٣٠ يومًا", "30 days")
    val prevDay get() = t("اليوم السابق", "Previous day")
    val nextDay get() = t("اليوم التالي", "Next day")
    val highLatBadge get() = t("بقاعدة العرض العالي", "High-latitude rule")
    val highLatExplain get() = t("لم تبلغ الشمس زاوية الطريقة، فحُدّد الوقت بقاعدة خطوط العرض العالية المختارة.", "The sun did not reach the method's angle, so the chosen high-latitude rule set this time.")
    val calcDetails get() = t("تفاصيل الحساب", "Calculation details")
    fun ishaInterval(min: String, ramadan: Boolean) = if (ramadan) t("العشاء بعد المغرب بـ$min دقيقة (رمضان)", "Isha $min minutes after Maghrib (Ramadan)") else t("العشاء بعد المغرب بـ$min دقيقة", "Isha $min minutes after Maghrib")
    val offsetsApplied get() = t("إزاحات يدوية مطبّقة", "Manual offsets applied")
    val tableNoSuffix get() = t("الأوقات بلا ص/م في الجدول.", "Times are shown without AM/PM in the table.")
    val tableScrollHint get() = t("اسحب الجدول أفقيًا لرؤية بقية الأوقات.", "Swipe the table sideways to see the remaining times.")
    fun useCityZone(city: String, zone: String) = t("استخدام منطقة $city: $zone", "Use $city's zone: $zone")

    fun issue(i: DayIssue) = when (i) {
        DayIssue.NO_SUNSET -> t("لا تغرب الشمس في هذا اليوم عند هذا الموقع، فتعذّر حساب المغرب والعشاء والفجر.", "The sun does not set here on this day, so Maghrib, Isha and Fajr cannot be calculated.")
        DayIssue.NO_SUNRISE -> t("لا تشرق الشمس في هذا اليوم عند هذا الموقع، فتعذّر حساب بعض المواقيت.", "The sun does not rise here on this day, so some times cannot be calculated.")
        DayIssue.NIGHT_UNKNOWN -> t("تعذّر تحديد طول الليل لهذا اليوم، فقد يتعذّر الفجر أو العشاء.", "The night's length is unknown for this day; Fajr or Isha may be unavailable.")
        DayIssue.OUT_OF_ORDER -> t("خرجت المواقيت عن ترتيبها قرب الدائرة القطبية، فلم تُعرض.", "Times came out of order near the polar circle and are not shown.")
        DayIssue.HIJRI_OUT_OF_RANGE -> t("التاريخ خارج نطاق تقويم أم القرى المنشور، فاستُخدم فاصل ٩٠ دقيقة للعشاء.", "The date is outside the published Umm al-Qura range; a 90-minute Isha interval was used.")
    }
    val checkLocalCalendar get() = t("راجع التقويم المعتمد في بلدك أو مسجدك المحلي.", "Check the calendar used by your local mosque or authority.")

    // Locations
    val locations get() = t("المواقع", "Places")
    val savedPlaces get() = t("المواقع المحفوظة", "Saved places")
    val active get() = t("الحالي", "Active")
    val searchCities get() = t("ابحث بالاسم العربي أو الإنجليزي", "Search in Arabic or English")
    val noResults get() = t("لا نتائج. جرّب اسمًا آخر أو أدخل الموقع يدويًا.", "No results. Try another name or enter the place manually.")
    val manualTitle get() = t("موقع يدوي", "Manual place")
    val placeNameAr get() = t("الاسم بالعربية", "Name in Arabic")
    val placeNameEn get() = t("الاسم بالإنجليزية (اختياري)", "Name in English (optional)")
    val latitude get() = t("خط العرض (−٩٠ إلى ٩٠)", "Latitude (−90 to 90)")
    val longitude get() = t("خط الطول (−١٨٠ إلى ١٨٠)", "Longitude (−180 to 180)")
    val timeZone get() = t("المنطقة الزمنية", "Time zone")
    val chooseZone get() = t("اختر المنطقة الزمنية", "Choose time zone")
    val searchZones get() = t("ابحث، مثل Riyadh أو Europe", "Search, e.g. Riyadh or Europe")
    val invalidLat get() = t("أدخل خط عرض بين −٩٠ و٩٠", "Enter a latitude between −90 and 90")
    val invalidLon get() = t("أدخل خط طول بين −١٨٠ و١٨٠", "Enter a longitude between −180 and 180")
    val nameRequired get() = t("أدخل اسمًا للموقع", "Enter a name for the place")
    val zoneRequired get() = t("اختر المنطقة الزمنية", "Choose a time zone")
    val gpsTitle get() = t("تأكيد الموقع الحالي", "Confirm current location")
    val gpsLocating get() = t("جارٍ تحديد موقعك…", "Finding your location…")
    val gpsDisabled get() = t("خدمة الموقع متوقفة في الجهاز. فعّلها ثم أعد المحاولة، أو اختر مدينة.", "Location is turned off on the device. Turn it on and try again, or pick a city.")
    val gpsTimeout get() = t("لم يصل موقع خلال المهلة. جرّب في مكان مفتوح أو اختر مدينة.", "No position arrived in time. Try outdoors, or pick a city.")
    val gpsDenied get() = t("لم يُمنح إذن الموقع. يمكنك اختيار مدينة أو الإدخال اليدوي.", "Location permission was not granted. You can pick a city or enter a place manually.")
    val gpsZoneNote get() = t("تأكد من المنطقة الزمنية. لا يستنتج التطبيق الدولة ولا طريقة الحساب من الإحداثيات.", "Confirm the time zone. The app does not infer the country or the calculation method from coordinates.")
    fun gpsAccuracy(m: String) = t("الدقة التقريبية: $m م", "Approx. accuracy: $m m")
    fun nearCity(name: String) = t("قرب $name", "Near $name")
    val myLocation get() = t("موقعي", "My location")
    val retry get() = t("إعادة المحاولة", "Try again")
    val deviceZone get() = t("منطقة الجهاز", "Device zone")
    val startTravel get() = t("بدء السفر", "Start travelling")
    val travelExplain get() = t("يحفظ موقعك الحالي كمنزل، ويستخدم وجهة مؤقتة حتى تعود صراحةً.", "Keeps your current place as home and uses a temporary destination until you return.")
    val travelDestination get() = t("الوجهة", "Destination")
    val keepDestination get() = t("حفظ الوجهة ضمن المواقع", "Save destination to places")
    fun confirmPlace(name: String) = t("استخدام «$name»؟", "Use “$name”?")
    val useAsDestination get() = t("اعتماد كوجهة سفر", "Use as travel destination")
    val usePlace get() = t("اعتماد الموقع", "Use this place")
    val deletePlaceQ get() = t("حذف هذا الموقع؟", "Delete this place?")
    val coordinates get() = t("الإحداثيات", "Coordinates")

    // Settings
    val settingsCalc get() = t("حساب المواقيت", "Prayer calculation")
    val settingsAppearance get() = t("المظهر واللغة", "Appearance and language")
    val settingsHome get() = t("الصفحة الرئيسية", "Home screen")
    val settingsCalendar get() = t("التقويم الهجري", "Hijri calendar")
    val settingsAbout get() = t("عن التطبيق والمصادر", "About and sources")
    val method get() = t("طريقة الحساب", "Calculation method")
    fun methodName(m: CalcMethod) = when (m) {
        CalcMethod.UMM_AL_QURA -> t("أم القرى", "Umm al-Qura")
        CalcMethod.MUSLIM_WORLD_LEAGUE -> t("رابطة العالم الإسلامي", "Muslim World League")
        CalcMethod.EGYPTIAN -> t("الهيئة المصرية العامة للمساحة", "Egyptian General Authority")
        CalcMethod.KARACHI -> t("جامعة العلوم الإسلامية، كراتشي", "University of Islamic Sciences, Karachi")
        CalcMethod.NORTH_AMERICA -> t("أمريكا الشمالية (ISNA)", "North America (ISNA)")
    }
    fun methodDetail(m: CalcMethod) = when (m) {
        CalcMethod.UMM_AL_QURA -> t("الفجر ١٨٫٥°، العشاء بعد المغرب بفاصل زمني", "Fajr 18.5°, Isha a fixed interval after Maghrib")
        CalcMethod.MUSLIM_WORLD_LEAGUE -> t("الفجر ١٨°، العشاء ١٧°، الظهر بعد الزوال بدقيقة", "Fajr 18°, Isha 17°, Dhuhr 1 min after zenith")
        CalcMethod.EGYPTIAN -> t("الفجر ١٩٫٥°، العشاء ١٧٫٥°، الظهر بعد الزوال بدقيقة", "Fajr 19.5°, Isha 17.5°, Dhuhr 1 min after zenith")
        CalcMethod.KARACHI -> t("الفجر ١٨°، العشاء ١٨°، الظهر بعد الزوال بدقيقة", "Fajr 18°, Isha 18°, Dhuhr 1 min after zenith")
        CalcMethod.NORTH_AMERICA -> t("الفجر ١٥°، العشاء ١٥°، الظهر بعد الزوال بدقيقة", "Fajr 15°, Isha 15°, Dhuhr 1 min after zenith")
    }
    val asrTitle get() = t("وقت العصر", "Asr time")
    fun asrName(a: AsrMethod) = when (a) {
        AsrMethod.STANDARD -> t("الجمهور (ظل المثل)", "Standard (one shadow)")
        AsrMethod.HANAFI -> t("الحنفي (ظل المثلين)", "Hanafi (two shadows)")
    }
    val highLatTitle get() = t("خطوط العرض العالية", "High latitudes")
    val highLatNote get() = t("تحدد الفجر والعشاء حين لا تبلغ الشمس زاوية الطريقة أو يطول الشفق.", "Limits Fajr and Isha when the sun does not reach the method's angle or twilight lasts long.")
    fun highLatName(r: HighLatitudeRule) = when (r) {
        HighLatitudeRule.MIDDLE_OF_THE_NIGHT -> t("منتصف الليل", "Middle of the night")
        HighLatitudeRule.SEVENTH_OF_THE_NIGHT -> t("سُبع الليل", "Seventh of the night")
        HighLatitudeRule.TWILIGHT_ANGLE -> t("زاوية الشفق", "Twilight angle")
    }
    val uqIshaTitle get() = t("عشاء أم القرى", "Umm al-Qura Isha")
    fun uqIshaName(p: UmmAlQuraIsha) = when (p) {
        UmmAlQuraIsha.AUTO -> t("تلقائي: ١٢٠ د في رمضان و٩٠ خارجه", "Automatic: 120 min in Ramadan, 90 otherwise")
        UmmAlQuraIsha.FIXED_90 -> t("ثابت ٩٠ دقيقة", "Always 90 minutes")
        UmmAlQuraIsha.FIXED_120 -> t("ثابت ١٢٠ دقيقة", "Always 120 minutes")
    }
    val uqIshaNote get() = t("رمضان هنا بحسب اليوم المدني في تقويم أم القرى غير المعدّل؛ لا تؤثر فيه إزاحة عرض التاريخ الهجري.", "Ramadan here follows the civil day in the unadjusted Umm al-Qura calendar; the Hijri display offset does not affect it.")
    val offsetsTitle get() = t("إزاحة كل وقت (دقائق)", "Offset per time (minutes)")
    val offsetsNote get() = t("من −٦٠ إلى +٦٠ دقيقة، تُضاف إلى الوقت المحسوب.", "From −60 to +60 minutes, added to the calculated time.")
    val resetOffsets get() = t("تصفير الإزاحات", "Clear offsets")
    val preview get() = t("معاينة اليوم", "Today's preview")
    val noLocationPreview get() = t("اختر موقعًا لرؤية المعاينة.", "Choose a place to see a preview.")

    val styleTitle get() = t("النمط", "Style")
    fun styleName(s: VisualStyle) = when (s) {
        VisualStyle.OASIS -> t("واحة", "Oasis")
        VisualStyle.SAHAR -> t("سَحَر", "Dawn")
        VisualStyle.RAML -> t("رمل", "Dune")
    }
    fun styleDetail(s: VisualStyle) = when (s) {
        VisualStyle.OASIS -> t("أخضر هادئ وسماء تتبدّل مع الوقت", "Calm green, sky that follows the time of day")
        VisualStyle.SAHAR -> t("نيلي ووردي بنجوم خافتة", "Indigo and rose with faint stars")
        VisualStyle.RAML -> t("ألوان رملية بسيطة دون تدرّجات", "Simple sand tones, no gradients")
    }
    val themeTitle get() = t("المظهر", "Theme")
    fun themeName(m: ThemeMode) = when (m) {
        ThemeMode.SYSTEM -> t("الجهاز", "Device")
        ThemeMode.LIGHT -> t("فاتح", "Light")
        ThemeMode.DARK -> t("داكن", "Dark")
        ThemeMode.SUN -> t("الشمس", "Sun")
    }
    val sunThemeNote get() = t("«الشمس»: فاتح من الشروق حتى المغرب في الموقع المختار، وداكن بعده.", "“Sun”: light from sunrise to Maghrib at the chosen place, dark otherwise.")
    val dynamicColor get() = t("ألوان النظام الديناميكية", "System dynamic colors")
    val dynamicColorNote get() = t("تستخدم ألوان خلفية جهازك (\u2066Android 12\u2069 فأعلى).", "Uses your wallpaper colors (Android 12 and later).")
    val dynamicUnsupported get() = t("غير متاحة في هذا الإصدار من Android.", "Not available on this Android version.")
    val language get() = t("اللغة", "Language")
    val timeFormat get() = t("تنسيق الوقت", "Time format")
    val h12 get() = t("١٢ ساعة", "12-hour")
    val h24 get() = t("٢٤ ساعة", "24-hour")
    val digits get() = t("الأرقام", "Digits")
    val digitsArabic get() = "٠١٢٣"
    val digitsLatin get() = "0123"
    val mushafNight get() = t("الوضع الليلي للمصحف", "Mushaf night mode")
    val mushafNightNote get() = t("مستقل عن مظهر التطبيق، ويُستخدم مع المصحف المطبوع.", "Independent of the app theme; used by the printed Mushaf.")

    val layoutTitle get() = t("تخطيط الرئيسية", "Home layout")
    fun layoutName(l: HomeLayout) = when (l) {
        HomeLayout.BALANCED -> t("متوازن", "Balanced")
        HomeLayout.FOCUS -> t("تركيز", "Focus")
        HomeLayout.DASHBOARD -> t("لوحة اليوم", "Day board")
    }
    fun layoutDetail(l: HomeLayout) = when (l) {
        HomeLayout.BALANCED -> t("الصلاة القادمة ومواقيت اليوم والبطاقات", "Next prayer, today's times and cards")
        HomeLayout.FOCUS -> t("عدّ تنازلي كبير وتفاصيل قليلة", "A large countdown and little else")
        HomeLayout.DASHBOARD -> t("شبكة المواقيت والبطاقات في نظرة واحدة", "A grid of times and cards at a glance")
    }
    val cardsTitle get() = t("البطاقات", "Cards")
    val cardsNote get() = t("أظهر البطاقات أو أخفها، ورتّبها بالأسهم.", "Show or hide cards and reorder them with the arrows.")
    fun cardName(c: HomeCard) = when (c) {
        HomeCard.DATE -> t("التاريخ والمناسبات المحسوبة", "Date and calculated occasions")
        HomeCard.SUN -> t("الشروق والغروب القادمان", "Next sunrise and sunset")
    }
    val moveUp get() = t("تحريك للأعلى", "Move up")
    val moveDown get() = t("تحريك للأسفل", "Move down")
    val laterCards get() = t("بطاقات آية اليوم وحديث اليوم ومساحة الجمعة تُضاف مع محتواها الموثّق في المراحل القادمة.", "Verse, hadith and Friday cards arrive with their verified content in later phases.")

    val hijriOffsetTitle get() = t("إزاحة عرض التاريخ الهجري", "Hijri display offset")
    val hijriOffsetNote get() = t("تغيّر التاريخ المعروض والمناسبات فقط، ولا تغيّر حساب الصلاة ولا قاعدة عشاء رمضان.", "Changes only the shown date and occasions, never prayer calculation or the Ramadan Isha rule.")
    fun daysSigned(n: String) = t("$n يوم", "$n day(s)")

    val aboutVersion get() = t("الإصدار", "Version")
    val aboutPrivacy get() = t("الخصوصية", "Privacy")
    val aboutPrivacyBody get() = t("كل الحسابات على جهازك. لا حساب ولا خادم ولا تتبّع. الموقع يُطلب فقط حين تضغط «موقعي الحالي»، ولا يُرسل لأي جهة.", "Everything is calculated on your device. No account, no server, no tracking. Location is read only when you tap “Current location” and is sent nowhere.")
    val aboutSources get() = t("المصادر والتراخيص", "Sources and licenses")
    val srcAlgorithm get() = t("معادلات موضع الشمس من كتاب \u2066Astronomical Algorithms\u2069 لـ\u2066Jean Meeus\u2069. واختُبرت النتائج مقابل مكتبة \u2066Adhan (MIT)\u2069 وجداول أم القرى الرسمية لمكة.", "Solar equations: Jean Meeus, “Astronomical Algorithms”. Results are tested against the Adhan library (MIT License) and official Umm al-Qura tables for Makkah.")
    val srcCalendar get() = t("التقويم: أم القرى كما في مكتبة \u2066Java (HijrahChronology)\u2069، ومطابق لتقويم أندرويد.", "Calendar: Umm al-Qura as published in Java's time library (HijrahChronology).")
    val srcCities get() = t("إحداثيات المدن: \u2066GeoNames (CC BY 4.0)\u2069. الأسماء العربية والمناطق الزمنية: مراجعة سكينة.", "City coordinates: GeoNames (CC BY 4.0). Arabic names and time zones: curated by Sakina.")
    val srcFonts get() = t("الخطوط: \u2066IBM Plex Sans Arabic\u2069 و\u2066Amiri\u2069 و\u2066Noto Kufi Arabic\u2069 للأرقام، برخصة \u2066SIL OFL 1.1\u2069.", "Fonts: IBM Plex Sans Arabic, Amiri and Noto Kufi Arabic for digits (SIL Open Font License 1.1).")
    val showLicense get() = t("عرض نص الرخصة", "Show license text")

    // Onboarding
    val welcomeTitle get() = t("أهلًا بك في سكينة", "Welcome to Sakina")
    val welcomeBody get() = t("مواقيت دقيقة تُحسب على جهازك، دون حساب ولا إنترنت. الإعداد قصير، ويمكنك تخطيه.", "Accurate times calculated on your device, with no account and no internet. Setup is short and optional.")
    val stepLanguage get() = t("اللغة والنمط", "Language and style")
    val stepLocation get() = t("الموقع", "Place")
    val stepMethod get() = t("طريقة الحساب", "Calculation method")
    val methodChoiceNote get() = t("اختر الطريقة المعتمدة في بلدك أو مسجدك. يمكنك تغييرها لاحقًا.", "Choose the method used in your country or mosque. You can change it later.")
    val locationStepNote get() = t("يمكنك التخطي الآن؛ القرآن والأذكار لا تحتاج موقعًا.", "You can skip for now; Quran and adhkar don't need a place.")
    val readyTitle get() = t("كل شيء جاهز", "All set")
    val readyBody get() = t("التنبيهات متوقفة افتراضيًا، وستتمكن من تفعيلها حين تُضاف في المرحلة القادمة.", "Alerts are off by default; you can enable them when they arrive in the next phase.")
    val start get() = t("ابدأ", "Start")
    fun stepOf(i: String, n: String) = t("الخطوة $i من $n", "Step $i of $n")

    // Placeholders (later phases)
    val quranSoon get() = t("القرآن الكريم", "The Holy Quran")
    val quranSoonBody get() = t("يُبنى في المرحلتين ٣ و٤: النص العثماني كاملًا دون إنترنت، الترجمة والتفسير، البحث والعلامات والختمة، ثم صفحات مصحف المدينة المطبوع.", "Built in phases 3 and 4: the full Uthmani text offline, translation and tafsir, search, bookmarks and khatma, then the printed Madinah Mushaf pages.")
    val discoverSoon get() = t("اكتشف", "Discover")
    val discoverSoonBody get() = t("ستجمع هنا الأدوات القادمة. ما يعمل الآن: المواقيت، المواقع والسفر، والمظهر.", "Upcoming tools will gather here. Working now: prayer times, places and travel, and appearance.")
    val plannedTitle get() = t("قادم", "Coming")
    val planned get() = (if (isAr) listOf(
        "الأذان والتنبيهات والتذكيرات" to "المرحلة ٢",
        "القرآن النصي والترجمة والتفسير" to "المرحلة ٣",
        "المصحف المطبوع" to "المرحلة ٤",
        "الاستماع للتلاوة" to "المرحلة ٥",
        "الأذكار والتسبيح والقبلة" to "المرحلة ٦",
        "التقويم ورمضان والليل والجمعة" to "المرحلة ٧",
        "الويدجت والاختصارات" to "المرحلة ٨",
        "السجل والتصدير ومساجدي والنسخ الاحتياطي" to "المرحلة ٩",
    ) else listOf(
        "Adhan, alerts and reminders" to "Phase 2",
        "Quran text, translation and tafsir" to "Phase 3",
        "Printed Mushaf" to "Phase 4",
        "Recitation audio" to "Phase 5",
        "Adhkar, tasbih and Qibla" to "Phase 6",
        "Calendar, Ramadan, night and Friday" to "Phase 7",
        "Widgets and shortcuts" to "Phase 8",
        "Log, export, my mosques and backup" to "Phase 9",
    )).map { digits(it.first) to digits(it.second) }
}

val LocalStrings = staticCompositionLocalOf { Strings(Lang.AR) }
