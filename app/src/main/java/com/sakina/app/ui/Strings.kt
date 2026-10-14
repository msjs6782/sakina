package com.sakina.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.sakina.app.core.alerts.AlertMode
import com.sakina.app.core.alerts.NotifyStyle
import com.sakina.app.core.alerts.ReminderKind
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
    val todayPreview get() = t("معاينة اليوم", "Today's preview")
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
        HomeCard.VERSE -> t("آية اليوم", "Verse of the day")
    }
    val moveUp get() = t("تحريك للأعلى", "Move up")
    val moveDown get() = t("تحريك للأسفل", "Move down")
    val laterCards get() = t("بطاقتا حديث اليوم ومساحة الجمعة تُضافان مع محتواهما الموثّق في المراحل القادمة.", "Hadith and Friday cards arrive with their verified content in later phases.")

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
    val srcQuran get() = t("نص المصحف (عثماني وإملائي) وبيانات السور والصفحات وترجمة عيتاني وتفسير الجلالين: مشروع تنزيل (\u2066tanzil.net\u2069)، منسوخة دون تغيير.", "Quran text (Uthmani and plain), surah and page data, the Itani translation and Tafsir al-Jalalayn: the Tanzil Project (tanzil.net), copied unchanged.")
    val srcFonts get() = t("الخطوط: \u2066IBM Plex Sans Arabic\u2069 و\u2066Amiri\u2069 و\u2066Amiri Quran\u2069 و\u2066Noto Kufi Arabic\u2069 للأرقام، برخصة \u2066SIL OFL 1.1\u2069.", "Fonts: IBM Plex Sans Arabic, Amiri, Amiri Quran and Noto Kufi Arabic for digits (SIL Open Font License 1.1).")
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
    val readyBody get() = t("كل التنبيهات متوقفة افتراضيًا. فعّل ما تريد من الإعدادات ← الأذان والتنبيهات.", "All alerts are off by default. Turn on what you want in Settings → Adhan and alerts.")
    val start get() = t("ابدأ", "Start")
    fun stepOf(i: String, n: String) = t("الخطوة $i من $n", "Step $i of $n")

    // Placeholders (later phases)
    val quranSoon get() = t("القرآن الكريم", "The Holy Quran")
    val quranSoonBody get() = t("يُبنى في المرحلتين ٣ و٤: النص العثماني كاملًا دون إنترنت، الترجمة والتفسير، البحث والعلامات والختمة، ثم صفحات مصحف المدينة المطبوع.", "Built in phases 3 and 4: the full Uthmani text offline, translation and tafsir, search, bookmarks and khatma, then the printed Madinah Mushaf pages.")
    val discoverSoon get() = t("اكتشف", "Discover")
    val discoverSoonBody get() = t("ستجمع هنا الأدوات القادمة. ما يعمل الآن: المواقيت، المواقع والسفر، المظهر، والأذان والتنبيهات والتذكيرات.", "Upcoming tools will gather here. Working now: prayer times, places and travel, appearance, and adhan, alerts and reminders.")
    val plannedTitle get() = t("قادم", "Coming")
    val planned get() = (if (isAr) listOf(
        "المصحف المطبوع" to "المرحلة ٤",
        "الاستماع للتلاوة" to "المرحلة ٥",
        "الأذكار والتسبيح والقبلة" to "المرحلة ٦",
        "التقويم ورمضان والليل والجمعة" to "المرحلة ٧",
        "الويدجت والاختصارات" to "المرحلة ٨",
        "السجل والتصدير ومساجدي والنسخ الاحتياطي" to "المرحلة ٩",
    ) else listOf(
        "Printed Mushaf" to "Phase 4",
        "Recitation audio" to "Phase 5",
        "Adhkar, tasbih and Qibla" to "Phase 6",
        "Calendar, Ramadan, night and Friday" to "Phase 7",
        "Widgets and shortcuts" to "Phase 8",
        "Log, export, my mosques and backup" to "Phase 9",
    )).map { digits(it.first) to digits(it.second) }

    // ---------- Alerts (phase 2) ----------
    val settingsAlerts get() = t("الأذان والتنبيهات", "Adhan and alerts")
    val settingsReminders get() = t("التذكيرات الشخصية", "Personal reminders")
    val alertsAllOff get() = t("كل التنبيهات متوقفة", "All alerts are off")
    fun alertsOnCount(n: String) = t("مفعّل: $n", "On: $n")

    fun modeName(m: AlertMode) = when (m) {
        AlertMode.OFF -> t("إيقاف", "Off")
        AlertMode.NOTIFY -> t("إشعار بصوت", "Notification with sound")
        AlertMode.SILENT -> t("إشعار صامت", "Silent notification")
        AlertMode.VIBRATE -> t("اهتزاز فقط", "Vibration only")
        AlertMode.ADHAN -> t("تشغيل التسجيل المختار", "Play the chosen recording")
        AlertMode.EXCERPT -> t("مقتطف ٢٠ ثانية", "20-second excerpt")
    }
    fun modeShort(m: AlertMode) = when (m) {
        AlertMode.OFF -> t("متوقف", "Off")
        AlertMode.NOTIFY -> t("إشعار", "Notification")
        AlertMode.SILENT -> t("صامت", "Silent")
        AlertMode.VIBRATE -> t("اهتزاز", "Vibrate")
        AlertMode.ADHAN -> t("أذان", "Adhan")
        AlertMode.EXCERPT -> t("مقتطف", "Excerpt")
    }
    fun modeDetail(m: AlertMode) = when (m) {
        AlertMode.OFF -> t("لا شيء عند دخول الوقت.", "Nothing at the prayer time.")
        AlertMode.NOTIFY -> t("إشعار بصوت الإشعارات في جهازك.", "A notification with your device's notification sound.")
        AlertMode.SILENT -> t("إشعار يظهر دون صوت ولا اهتزاز.", "A notification without sound or vibration.")
        AlertMode.VIBRATE -> t("إشعار مع اهتزاز دون صوت.", "A notification that vibrates, without sound.")
        AlertMode.ADHAN -> t("يشغّل التسجيل كاملًا مع زر إيقاف.", "Plays the whole recording, with a stop button.")
        AlertMode.EXCERPT -> t("يشغّل أول ٢٠ ثانية من التسجيل ثم يخفت.", "Plays the first 20 seconds, then fades out.")
    }
    fun styleNameN(st: NotifyStyle) = when (st) {
        NotifyStyle.NORMAL -> t("بصوت", "With sound")
        NotifyStyle.SILENT -> t("صامت", "Silent")
        NotifyStyle.VIBRATE -> t("اهتزاز", "Vibrate")
    }

    // Status and permissions
    val alertStatus get() = t("حالة التنبيهات", "Alert status")
    fun nextAlert(what: String, time: String) = t("التنبيه القادم: $what — $time", "Next alert: $what — $time")
    val noScheduled get() = t("لا يوجد تنبيه مجدول.", "No alert is scheduled.")
    val scheduledInexact get() = t("مجدول بتوقيت تقريبي لأن إذن المنبهات الدقيقة غير ممنوح: قد يتأخر حتى ١٠ دقائق، وأكثر في وضع توفير الطاقة، والأذان المتأخر يظهر إشعارًا صامتًا. امنح الإذن من الأعلى.", "Scheduled with approximate timing because exact-alarm access is not granted: it may be up to 10 minutes late, more in power saving, and a late adhan shows as a silent notification. Grant access above.")
    val scheduledAlarmClock get() = t("مجدول كمنبّه نظام.", "Scheduled as a system alarm.")
    val permNotif get() = t("إذن الإشعارات", "Notification permission")
    val permNotifBody get() = t("مطلوب لعرض أي تنبيه من سكينة.", "Needed to show any Sakina alert.")
    val permNotifOff get() = t("الإشعارات متوقفة لسكينة في إعدادات أندرويد.", "Notifications are turned off for Sakina in Android settings.")
    val permExact get() = t("المنبهات الدقيقة", "Exact alarms")
    val permExactBody get() = t("يجعل الأذان يصل في دقيقته. بدونه قد يؤخّر أندرويد التنبيه.", "Lets the adhan arrive on the minute. Without it Android may delay alerts.")
    val permBattery get() = t("استثناء البطارية", "Battery exemption")
    val permBatteryBody get() = t("اختياري: يقلّل تأخير التنبيهات على بعض الأجهزة التي تقيّد التطبيقات بشدة.", "Optional: reduces delays on devices that restrict apps aggressively.")
    val allow get() = t("سماح", "Allow")
    val allowed get() = t("ممنوح", "Granted")
    val openSettings get() = t("فتح الإعدادات", "Open settings")
    val channelsTitle get() = t("قنوات الإشعارات", "Notification channels")
    val channelsBody get() = t("يمكنك تغيير صوت كل نوع أو إيقافه من إعدادات أندرويد.", "You can change the sound of each type, or turn it off, in Android settings.")
    fun channelsBlocked(n: String) = t("قنوات متوقفة في أندرويد: $n", "Channels turned off in Android: $n")
    val reliabilityTitle get() = t("الموثوقية وقيود النظام", "Reliability and system limits")
    val reliabilityBody get() = t(
        "تُجدول سكينة التنبيه القادم فقط، وتعيد الجدولة عند تغيير الإعدادات أو الوقت أو المنطقة الزمنية وبعد إعادة تشغيل الجهاز. وضع توفير الطاقة (Doze) وقيود الشركات المصنّعة قد تؤخّر التنبيهات. إذا أوقفت التطبيق إجباريًا من إعدادات أندرويد تتوقف كل تنبيهاته حتى تفتحه مجددًا. التنبيه المتأخر أكثر من دقيقتين يظهر إشعارًا صامتًا بدل تشغيل الأذان، ولا يتكرر التنبيه نفسه.",
        "Sakina schedules only the next alert and reschedules after settings, clock or time-zone changes and after a reboot. Doze and manufacturer restrictions can delay alerts. If you force-stop the app in Android settings, its alerts stop until you open it again. An alert more than two minutes late shows a silent notification instead of playing the adhan, and the same alert never repeats.",
    )
    val useAlarmClock get() = t("استخدام منبّه النظام للتنبيهات", "Use system alarms for alerts")
    val useAlarmClockBody get() = t("أعلى موثوقية في وضع توفير الطاقة، لكن يظهر رمز المنبّه في شريط الحالة.", "Most reliable in Doze, but shows the alarm icon in the status bar.")

    // Prayer pages
    val perPrayerTitle get() = t("لكل صلاة", "Per prayer")
    val atAdhanTime get() = t("عند دخول الوقت", "At the prayer time")
    val prayerSound get() = t("صوت هذه الصلاة", "Sound for this prayer")
    val useGeneral get() = t("مثل الإعداد العام", "Same as general")
    val useFajrSound get() = t("مثل إعداد الفجر", "Same as Fajr setting")
    val customForPrayer get() = t("تخصيص لهذه الصلاة", "Custom for this prayer")
    val preThisPrayer get() = t("تنبيه قبل الأذان", "Alert before the adhan")
    val iqamaThisPrayer get() = t("تذكير الإقامة بعد الأذان", "Iqama reminder after the adhan")
    fun inheritValue(v: String) = t("العام ($v)", "General ($v)")
    val minutesOff get() = t("متوقف", "Off")
    fun minutesValue(n: String) = t("$n د", "$n min")
    val iqamaNote get() = t("الإقامة هنا وقت تحدده أنت، وليست بيانات مباشرة من المسجد.", "Iqama here is a time you set, not live data from a mosque.")
    val unavailableWarning get() = t("لا يُرسل تنبيه في الأيام التي يتعذّر فيها حساب هذا الوقت.", "No alert is sent on days when this time cannot be calculated.")

    // General settings
    val soundsTitle get() = t("الأصوات", "Sounds")
    val generalSound get() = t("الصوت العام", "General sound")
    val fajrSound get() = t("صوت الفجر", "Fajr sound")
    val volume get() = t("مستوى الصوت", "Volume")
    fun volumeValue(n: String) = t("$n٪", "$n%")
    val preview get() = t("معاينة", "Preview")
    val stopPreview get() = t("إيقاف المعاينة", "Stop preview")
    val chooseSound get() = t("اختيار الصوت", "Choose sound")
    val pickFile get() = t("ملف من الجهاز…", "File from device…")
    val fileCopied get() = t("نُسخ الملف إلى التطبيق.", "The file was copied into the app.")
    val fileTooLarge get() = t("الملف أكبر من ٢٠ م.ب.", "The file is larger than 20 MB.")
    val fileNotAudio get() = t("تعذّر قراءة الملف كصوت.", "The file could not be read as audio.")
    val fileFailed get() = t("تعذّر نسخ الملف.", "The file could not be copied.")
    val recordingsTitle get() = t("تسجيلات الأذان المرفقة", "Bundled adhan recordings")
    val candidateBadge get() = t("مرشّح · يحتاج مراجعة المحتوى", "Candidate · needs content review")
    val recordingsNote get() = t("هذان تسجيلان مرشّحان من ويكيميديا كومنز، بمصدر وترخيص موثّقين، ولم تُعتمد مراجعة محتواهما بعد. كلاهما أذان معتاد دون «الصلاة خير من النوم»، فاختر ملفًا من جهازك لصوت الفجر إن أردت. يمكنك استخدام ملف من جهازك بدلًا منهما في أي صلاة.", "These two candidate recordings come from Wikimedia Commons with a documented source and license; their content has not been approved yet. Both are the regular adhan without the Fajr phrase “as-salatu khayrun min an-nawm”, so choose a file from your device for the Fajr sound if you want it. You can use a device file for any prayer instead.")
    val sourceLabel get() = t("المصدر", "Source")
    val licenseLabel get() = t("الترخيص", "License")
    val authorLabel get() = t("المؤلف", "Author")
    val durationLabel get() = t("المدة", "Duration")
    val checksumLabel get() = t("بصمة \u2066SHA-256\u2069", "SHA-256")
    val openSourcePage get() = t("فتح صفحة المصدر في المتصفح", "Open the source page in a browser")
    val routeTitle get() = t("مسار صوت الأذان", "Adhan audio route")
    val routeNotification get() = t("يتبع وضع الرنين", "Follow the ringer")
    val routeNotificationBody get() = t("لا صوت إذا كان الجوال صامتًا أو على الاهتزاز؛ يظهر إشعار بدلًا منه.", "No sound when the phone is silent or on vibrate; a notification is shown instead.")
    val routeAlarm get() = t("مسار المنبّه", "Alarm stream")
    val routeAlarmBody get() = t("يعمل حتى في الوضع الصامت، بمستوى صوت المنبّه.", "Plays even in silent mode, at alarm volume.")
    val generalPre get() = t("التنبيه قبل الأذان (عام)", "Alert before the adhan (general)")
    val generalIqama get() = t("تذكير الإقامة (عام)", "Iqama reminder (general)")
    val deliveryStyle get() = t("طريقة الإشعار", "Delivery")
    val cardToggle get() = t("بطاقة الصلاة القادمة في الإشعارات", "Next-prayer card in notifications")
    val cardToggleBody get() = t("تعرض الموعد والتاريخ والمتبقي ووقت آخر تحديث. المتبقي لقطة تتحدث كل بضع دقائق وقد يؤخّرها أندرويد.", "Shows the time, date, remaining time and last update. The remaining time is a snapshot refreshed every few minutes and Android may delay it.")
    val suhoorSetting get() = t("تذكير السحور", "Suhoor reminder")
    val suhoorNote get() = t("تذكير قبل الفجر بالمدة التي تختارها، وليس وقت إمساك ملزمًا.", "A reminder before Fajr by the time you choose; not a binding imsak time.")
    val ramadanOnly get() = t("في رمضان فقط", "In Ramadan only")
    val suhoorBefore get() = t("قبل الفجر", "Before Fajr")
    val testsTitle get() = t("تجربة التنبيهات", "Test alerts")
    val testsBody get() = t("يُرسل التنبيه بعد ١٠ ثوانٍ عبر مسار الجدولة نفسه، ويعرض متى وصل فعلًا.", "Sends an alert in 10 seconds through the same scheduling path, and shows when it actually arrived.")
    val testNotify get() = t("إشعار تجريبي", "Test notification")
    val testAdhan get() = t("أذان تجريبي", "Test adhan")
    val testExcerpt get() = t("مقتطف تجريبي", "Test excerpt")
    val testScheduled get() = t("سيصل خلال ١٠ ثوانٍ تقريبًا.", "It will arrive in about 10 seconds.")
    val recentTitle get() = t("آخر الأحداث", "Recent events")
    val recentEmpty get() = t("لا أحداث بعد.", "No events yet.")
    fun outcomeName(o: String) = when (o) {
        "fire" -> t("أُرسل", "Sent")
        "fire_late_silent" -> t("أُرسل صامتًا (متأخر)", "Sent silently (late)")
        "skip_late" -> t("تُجوّز (متأخر جدًا)", "Skipped (too late)")
        "skip_duplicate" -> t("تُجوّز (مكرر)", "Skipped (duplicate)")
        "skip_quiet" -> t("تُجوّز (هدوء)", "Skipped (quiet)")
        "playing" -> t("بدأ التشغيل", "Playback started")
        "playing_fallback_sound" -> t("بدأ بالصوت البديل", "Started with fallback sound")
        "completed" -> t("اكتمل", "Completed")
        "excerpt_done" -> t("انتهى المقتطف", "Excerpt finished")
        "stopped_by_user" -> t("أوقفته", "Stopped by you")
        "focus_lost" -> t("توقف (صوت آخر)", "Stopped (other audio)")
        "in_call" -> t("لم يُشغّل (مكالمة)", "Not played (call)")
        "ringer_silent", "ringer_vibrate" -> t("لم يُشغّل (وضع الرنين)", "Not played (ringer mode)")
        "notifications_blocked" -> t("الإشعارات ممنوعة", "Notifications blocked")
        "fgs_failed" -> t("رفض أندرويد التشغيل", "Android refused playback")
        else -> o
    }
    fun eventLabel(id: String): String {
        val f = id.split(':')
        val kind = f.getOrNull(0) ?: return id
        val subject = f.getOrNull(1) ?: ""
        val prayerName = runCatching { prayer(PrayerKey.valueOf(subject)) }.getOrNull()
        val reminderName = runCatching { reminderName(ReminderKind.valueOf(subject)) }.getOrNull()
        return when (kind) {
            "ADHAN" -> t("وقت ", "") + (prayerName ?: subject)
            "PRE" -> t("قبل ", "Before ") + (prayerName ?: subject)
            "IQAMA" -> t("إقامة ", "Iqama ") + (prayerName ?: subject)
            "SUHOOR" -> suhoorSetting
            "REMINDER" -> reminderName ?: subject
            "TEST" -> testsTitle
            "QUIET" -> quietTitle
            else -> id
        }
    }

    // Reminders
    fun reminderName(k: ReminderKind) = when (k) {
        ReminderKind.DUHA -> t("صلاة الضحى", "Duha prayer")
        ReminderKind.WITR -> t("صلاة الوتر", "Witr prayer")
        ReminderKind.QIYAM -> t("قيام الليل", "Night prayer (Qiyam)")
        ReminderKind.ADHKAR_MORNING -> t("أذكار الصباح", "Morning adhkar")
        ReminderKind.ADHKAR_EVENING -> t("أذكار المساء", "Evening adhkar")
        ReminderKind.ADHKAR_SLEEP -> t("أذكار النوم", "Adhkar before sleep")
        ReminderKind.ADHKAR_WAKING -> t("أذكار الاستيقاظ", "Adhkar on waking")
        ReminderKind.FRIDAY -> t("الجمعة", "Friday")
    }
    val remindersNote get() = t("تذكيرات بوقت مدني تختاره أنت في المنطقة الزمنية للموقع الحالي، وأيام تحددها.", "Reminders at a civil time you choose, in the current place's time zone, on the days you pick.")
    val reminderTime get() = t("الوقت", "Time")
    val reminderDays get() = t("الأيام", "Days")
    val everyDay get() = t("كل يوم", "Every day")
    val pickTime get() = t("اختيار الوقت", "Pick time")

    // Quiet mode
    val quietTitle get() = t("هدوء مؤقت", "Quiet for a while")
    val quietBody get() = t("يوقف أذان سكينة وتنبيهاتها وتذكيراتها خلال المدة، دون تأجيلها. القراءة والمواقيت تبقى متاحة، ولا يتغيّر شيء في المكالمات أو التطبيقات الأخرى.", "Stops Sakina's adhan, alerts and reminders for the period, without postponing them. Reading and prayer times stay available; calls and other apps are not affected.")
    fun quietFor(d: String) = d
    fun quietActive(time: String) = t("هدوء حتى $time", "Quiet until $time")
    val quietEndNow get() = t("إنهاء الآن", "End now")
    val androidSound get() = t("إعدادات الصوت في أندرويد", "Android sound settings")
    val quietDurations get() = listOf(30L to t("٣٠ دقيقة", "30 min"), 60L to t("ساعة", "1 hour"), 120L to t("ساعتان", "2 hours"), 240L to t("٤ ساعات", "4 hours"), 480L to t("٨ ساعات", "8 hours"))

    // Notification texts
    val chGroupPrayer get() = t("الصلوات", "Prayers")
    val chGroupReminder get() = t("التذكيرات", "Reminders")
    val chPrayerSound get() = t("وقت الصلاة: إشعار بصوت", "Prayer time: with sound")
    val chPrayerSoundDesc get() = t("إشعار بصوت الإشعارات عند دخول الوقت.", "A notification with sound at the prayer time.")
    val chPrayerSilent get() = t("وقت الصلاة: صامت", "Prayer time: silent")
    val chPrayerSilentDesc get() = t("إشعار دون صوت عند دخول الوقت، وسجل الأذان بعد انتهائه.", "A silent notification at the prayer time, and a record after the adhan.")
    val chPrayerVibrate get() = t("وقت الصلاة: اهتزاز", "Prayer time: vibrate")
    val chPrayerVibrateDesc get() = t("اهتزاز دون صوت عند دخول الوقت.", "Vibration without sound at the prayer time.")
    val chAdhan get() = t("تشغيل الأذان", "Adhan playback")
    val chAdhanDesc get() = t("الإشعار الظاهر أثناء تشغيل الأذان مع زر الإيقاف.", "Shown while the adhan plays, with a stop button.")
    val chReminderSound get() = t("تذكيرات: بصوت", "Reminders: with sound")
    val chReminderSilent get() = t("تذكيرات: صامتة", "Reminders: silent")
    val chReminderVibrate get() = t("تذكيرات: اهتزاز", "Reminders: vibrate")
    val chReminderDesc get() = t("التنبيه قبل الأذان والإقامة والسحور والتذكيرات الشخصية.", "Pre-adhan, iqama, suhoor and personal reminders.")
    val chCard get() = t("بطاقة الصلاة القادمة", "Next-prayer card")
    val chCardDesc get() = t("بطاقة دائمة اختيارية تعرض الصلاة القادمة.", "An optional persistent card with the next prayer.")
    fun lateNote(min: String) = t("وصل متأخرًا $min د", "arrived $min min late")
    fun prayerTimeTitle(name: String) = t("حان وقت صلاة $name", "Time for $name")
    fun preTitle(name: String, min: String) = t("بعد $min د: أذان $name", "In $min min: $name")
    fun iqamaTitle(name: String) = t("تذكير الإقامة: $name", "Iqama reminder: $name")
    fun iqamaText(min: String) = t("بعد الأذان بـ$min د · وقت حددته أنت", "$min min after the adhan · a time you set")
    val suhoorTitle get() = t("تذكير السحور", "Suhoor reminder")
    fun suhoorText(fajr: String, min: String) = t("الفجر $fajr (بعد $min د)", "Fajr at $fajr (in $min min)")
    fun reminderTitle(k: ReminderKind) = when (k) {
        ReminderKind.FRIDAY -> t("تذكير الجمعة", "Friday reminder")
        ReminderKind.DUHA, ReminderKind.WITR, ReminderKind.QIYAM -> t("تذكير: ", "Reminder: ") + reminderName(k)
        else -> reminderName(k)
    }
    fun reminderText(time: String) = t("تذكيرك الشخصي عند $time", "Your personal reminder at $time")
    val testTitle get() = t("تنبيه تجريبي من سكينة", "Sakina test alert")
    fun testText(scheduled: String, delivered: String) = t("مجدول $scheduled · وصل $delivered", "Scheduled $scheduled · arrived $delivered")
    val stop get() = t("إيقاف", "Stop")
    val cardNoTimes get() = t("لا صلاة قادمة يمكن حسابها", "No upcoming prayer can be calculated")
    fun cardUpdated(time: String) = t("آخر تحديث $time", "Updated $time")
    fun cardRemaining(d: String) = t("متبقٍّ نحو $d", "about $d left")
    fun quietUntil(time: String) = t("هدوء حتى $time", "Quiet until $time")
    fun adhanPlayingTitle(name: String) = t("أذان $name", "Adhan · $name")
    val adhanTestTitle get() = t("أذان تجريبي", "Test adhan")
    val excerptNote get() = t("مقتطف ٢٠ ثانية", "20-second excerpt")
    val tapStop get() = t("اضغط «إيقاف» لإسكاته", "Tap Stop to silence it")
    val playFailedNote get() = t("تعذّر تشغيل الصوت", "the sound could not play")
    val noteInCall get() = t("لم يُشغّل الأذان أثناء مكالمة", "not played during a call")
    val noteRinger get() = t("لم يُشغّل الأذان لأن الجوال صامت", "not played because the phone is silent")
    val noteFocusLost get() = t("توقف الأذان لصوت آخر", "the adhan stopped for other audio")
    val noteMuted get() = t("مستوى الصوت صفر", "volume is zero")

    // ---------- Quran (phase 3) ----------
    val quranTitle get() = t("القرآن الكريم", "The Holy Quran")
    val quranLoading get() = t("جارٍ تحميل نص المصحف…", "Loading the Quran text…")
    val quranSearchHint get() = t("ابحث بكلمة، أو اكتب مرجعًا مثل البقرة ٢٥٥", "Search a word, or a reference like 2:255")
    val continueReading get() = t("تابع القراءة", "Continue reading")
    fun surahAyah(name: String, aya: String) = t("$name · الآية $aya", "$name · ayah $aya")
    fun pageN(n: String) = t("صفحة $n", "Page $n")
    fun juzN(n: String) = t("الجزء $n", "Juz $n")
    fun hizbN(n: String) = t("الحزب $n", "Hizb $n")
    fun quarterName(q: Int, fmtNum: (Int) -> String): String {
        val hizb = (q - 1) / 4 + 1
        return when ((q - 1) % 4) {
            0 -> hizbN(fmtNum(hizb))
            1 -> t("ربع الحزب ", "¼ Hizb ") + fmtNum(hizb)
            2 -> t("نصف الحزب ", "½ Hizb ") + fmtNum(hizb)
            else -> t("ثلاثة أرباع الحزب ", "¾ Hizb ") + fmtNum(hizb)
        }
    }
    val tabSurahs get() = t("السور", "Surahs")
    val tabJuz get() = t("الأجزاء", "Juz")
    val tabBookmarks get() = t("العلامات", "Bookmarks")
    val meccan get() = t("مكية", "Meccan")
    val medinan get() = t("مدنية", "Medinan")
    fun ayahCount(n: String) = t("$n آية", "$n ayahs")
    val goToPage get() = t("انتقال إلى صفحة", "Go to page")
    val pageNumberHint get() = t("رقم الصفحة (١–٦٠٤)", "Page number (1–604)")
    val go get() = t("انتقال", "Go")
    val noBookmarks get() = t("لا علامات بعد. اضغط على أي آية ثم «علامة».", "No bookmarks yet. Tap any ayah, then “Bookmark”.")
    val bookmark get() = t("علامة", "Bookmark")
    val removeBookmark get() = t("إزالة العلامة", "Remove bookmark")
    val bookmarkRemoved get() = t("أُزيلت العلامة", "Bookmark removed")
    val undo get() = t("تراجع", "Undo")
    val copy get() = t("نسخ", "Copy")
    val copied get() = t("نُسخ النص", "Text copied")
    val shareText get() = t("مشاركة نص", "Share text")
    val shareImage get() = t("مشاركة صورة", "Share image")
    val translationTitle get() = t("ترجمة المعاني (الإنجليزية)", "English translation of meanings")
    val tafsirTitle get() = t("تفسير الجلالين", "Tafsir al-Jalalayn")
    val translationSource get() = t("ترجمة طلال عيتاني (ClearQuran)، عن مشروع تنزيل", "Talal Itani (ClearQuran), via Tanzil")
    val tafsirSource get() = t("جلال الدين المحلي وجلال الدين السيوطي، عن مشروع تنزيل", "Jalal ad-Din al-Mahalli and Jalal ad-Din as-Suyuti, via Tanzil")
    val textSource get() = t("نص المصحف العثماني من مشروع تنزيل (tanzil.net)", "Uthmani Quran text from the Tanzil Project (tanzil.net)")
    val readerSettings get() = t("إعدادات القراءة", "Reading settings")
    val textSize get() = t("حجم النص", "Text size")
    val viewMode get() = t("طريقة العرض", "View")
    val viewPages get() = t("صفحات متصلة", "Flowing pages")
    val viewAyahs get() = t("آية آية", "Ayah by ayah")
    val showTranslation get() = t("إظهار ترجمة المعاني", "Show the translation")
    val showTafsir get() = t("إظهار تفسير الجلالين", "Show Tafsir al-Jalalayn")
    val ayahModeNote get() = t("الترجمة والتفسير يظهران تحت كل آية في عرض «آية آية».", "The translation and tafsir appear under each ayah in the “Ayah by ayah” view.")
    val sajdaRecommended get() = t("سجدة تلاوة", "Prostration of recitation")
    val searchTitle get() = t("البحث في القرآن", "Search the Quran")
    fun searchCount(n: String) = t("$n آية", "$n ayahs")
    fun searchShown(shown: String, total: String) = t("يُعرض أول $shown من $total", "Showing the first $shown of $total")
    val searchNone get() = t("لا نتائج.", "No results.")
    val searchNote get() = t("البحث العربي في النص الإملائي من تنزيل، دون تشكيل ولا اعتبار للهمزات؛ والبحث بالإنجليزية في ترجمة المعاني.", "Arabic search uses Tanzil's plain text, ignoring diacritics and hamza forms; English search uses the translation.")
    val openReference get() = t("انتقال إلى", "Go to")
    fun inTranslation(n: String) = t("في الترجمة · $n", "in translation · $n")
    val sourcesTitle get() = t("المصادر والتراخيص", "Sources and licenses")
    val quranSourcesBody get() = t(
        "النص العثماني والنص الإملائي وبيانات السور والأجزاء والصفحات من مشروع تنزيل، منسوخة كما هي دون تغيير، بترخيص المشاع الإبداعي (نسب المصنف 3.0)، ويُرجى متابعة التحديثات في tanzil.net. ترقيم الصفحات وفق ترتيب مصحف المدينة في بيانات تنزيل. الترجمة والتفسير من صفحة الترجمات في تنزيل، وشروطها للاستخدام غير التجاري؛ وسكينة تطبيق شخصي غير تجاري. ذكر طلال عيتاني أنه أتاح ترجمته برخصة المشاع الإبداعي.",
        "The Uthmani and plain texts and the surah, juz and page data come from the Tanzil Project, copied verbatim, under Creative Commons Attribution 3.0; check tanzil.net for updates. Page numbers follow the Madinah mushaf layout in Tanzil's data. The translation and tafsir come from Tanzil's translations page, whose terms are non-commercial use; Sakina is a personal, non-commercial app. Talal Itani states that he released his translation under a Creative Commons license.",
    )
    val openTanzil get() = t("فتح tanzil.net", "Open tanzil.net")
    val copyrightBlock get() = t("نص حقوق النسخ كما ورد في ملف تنزيل", "Copyright block as published in the Tanzil file")

    // Khatma
    val khatmaTitle get() = t("خطة الختمة", "Khatma plan")
    val khatmaNone get() = t("لا خطة حالية. اختر المدة لتوزيع ٦٠٤ صفحات على الأيام.", "No plan yet. Choose a duration to spread the 604 pages over the days.")
    val khatmaStart get() = t("بدء الخطة", "Start plan")
    val khatmaDays get() = t("المدة بالأيام", "Duration in days")
    fun khatmaPerDay(n: String) = t("نحو $n صفحة يوميًا", "about $n pages a day")
    val khatmaFrom get() = t("تبدأ اليوم", "Starts today")
    fun khatmaToday(from: String, to: String) = t("ورد اليوم: من صفحة $from إلى $to", "Today: pages $from to $to")
    val khatmaTodayEmpty get() = t("لا صفحات مقررة اليوم.", "No pages are due today.")
    fun khatmaProgress(p: String, pct: String) = t("وصلت إلى صفحة $p ($pct٪)", "Reached page $p ($pct%)")
    fun khatmaBehind(n: String) = t("متأخر $n صفحة عن الخطة", "$n pages behind the plan")
    fun khatmaAhead(n: String) = t("متقدم $n صفحة", "$n pages ahead")
    val khatmaOnTrack get() = t("على الخطة", "On track")
    fun khatmaRemainingToday(n: String) = t("بقي من ورد اليوم $n صفحة", "$n pages left today")
    val khatmaDone get() = t("اكتملت الختمة", "Khatma completed")
    fun khatmaOverdue(n: String) = t("انتهت المدة وبقي $n صفحة", "The period ended with $n pages left")
    fun khatmaNotStarted(date: String) = t("تبدأ في $date", "Starts on $date")
    val khatmaFinishToday get() = t("أنهيت ورد اليوم", "I finished today’s portion")
    val khatmaRecord get() = t("سجّل التقدم حتى صفحة…", "Record progress up to page…")
    fun khatmaRecordHere(p: String) = t("سجّل الختمة حتى صفحة $p", "Record khatma up to page $p")
    val khatmaReadToday get() = t("اقرأ ورد اليوم", "Read today’s portion")
    val khatmaSchedule get() = t("الجدول", "Schedule")
    val khatmaReset get() = t("إعادة ضبط الخطة", "Reset the plan")
    val khatmaResetQ get() = t("حذف الخطة الحالية وسجل تقدمها؟ لا يمكن التراجع.", "Delete the current plan and its progress? This cannot be undone.")
    val khatmaManual get() = t("التقدم يُسجَّل يدويًا فقط؛ فتح الصفحات لا يسجّل شيئًا.", "Progress is recorded by hand only; opening pages records nothing.")
    fun khatmaRange(from: String, to: String) = t("صفحة $from–$to", "Pages $from–$to")
    fun khatmaEnds(date: String) = t("تنتهي في $date", "Ends on $date")
    val khatmaRecorded get() = t("سُجّل التقدم", "Progress recorded")

    // Verse of the day
    val verseOfDay get() = t("آية اليوم", "Verse of the day")
    val verseNote get() = t("من قائمة آيات مختارة في سكينة؛ النص من مشروع تنزيل.", "From a list of verses chosen for Sakina; text from the Tanzil Project.")
}

val LocalStrings = staticCompositionLocalOf { Strings(Lang.AR) }
