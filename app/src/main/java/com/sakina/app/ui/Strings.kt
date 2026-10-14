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
    /** Arabic counted noun for pages: صفحة واحدة، صفحتان، ٣–١٠ صفحات، ثم صفحة. */
    fun pagesCount(n: String, raw: Int) = if (isAr) {
        when (raw) {
            1 -> "صفحة واحدة"
            2 -> "صفحتان"
            in 3..10 -> "$n صفحات"
            else -> "$n صفحة"
        }
    } else if (raw == 1) "1 page" else "$n pages"
    fun ayahsCount(n: String, raw: Int) = if (isAr) {
        when (raw) {
            1 -> "آية واحدة"
            2 -> "آيتان"
            in 3..10 -> "$n آيات"
            else -> "$n آية"
        }
    } else if (raw == 1) "1 ayah" else "$n ayahs"
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
    fun surahTitle(nameAr: String, translit: String) = t("سورة $nameAr", "Surah $translit")
    val searchTitle get() = t("البحث في القرآن", "Search the Quran")
    fun searchCount(n: String, raw: Int) = ayahsCount(n, raw)
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
    fun khatmaNoProgress(pct: String) = t("لم يُسجَّل تقدم بعد ($pct٪)", "No progress recorded yet ($pct%)")
    fun khatmaSpan(from: String, to: String, days: String) = t("من $from إلى $to · $days", "$from to $to · $days")
    fun khatmaBehind(n: String, raw: Int) = t("متأخر ${pagesCount(n, raw)} عن الخطة", "${pagesCount(n, raw)} behind the plan")
    fun khatmaAhead(n: String, raw: Int) = t("متقدم ${pagesCount(n, raw)}", "${pagesCount(n, raw)} ahead")
    val khatmaOnTrack get() = t("على الخطة", "On track")
    fun khatmaRemainingToday(n: String, raw: Int) = t("بقي من ورد اليوم ${pagesCount(n, raw)}", "${pagesCount(n, raw)} left today")
    val khatmaDone get() = t("اكتملت الختمة", "Khatma completed")
    fun khatmaOverdue(n: String, raw: Int) = t("انتهت المدة وبقي ${pagesCount(n, raw)}", "The period ended with ${pagesCount(n, raw)} left")
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

    // Printed Mushaf
    private fun iso(s: String) = "⁦$s⁩"
    val mushafTitle get() = t("المصحف المطبوع", "Printed Mushaf")
    val printedView get() = t("صفحات المصحف", "Mushaf pages")
    val textView get() = t("العرض النصي", "Text view")
    val defaultView get() = t("ما يُفتح من الفهرس والبحث والعلامات", "What opens from the index, search and bookmarks")
    val defaultViewNote get() = t("صفحات المصحف هي صفحات طبعة مجمع الملك فهد الأصلية؛ والعرض النصي من تنزيل بخط أميري قرآن.", "Mushaf pages are the original King Fahd Complex pages; the text view is Tanzil's text in the Amiri Quran font.")
    fun pageOfTotal(n: String, total: String) = t("صفحة $n من $total", "Page $n of $total")
    val pageNotHere get() = t("هذه الصفحة غير منزّلة على الجهاز", "This page is not on the device yet")
    fun pageSize(size: String) = t("حجمها $size", "Size $size")
    val downloadThisPage get() = t("تنزيل هذه الصفحة", "Download this page")
    fun downloadingPage(pct: String) = t("جارٍ تنزيل الصفحة… $pct٪", "Downloading the page… $pct%")
    val autoDownload get() = t("تنزيل الصفحة تلقائيًا عند فتحها", "Download a page automatically when opened")
    val autoDownloadNote get() = t("يستعمل الإنترنت فقط عند فتح صفحة غير منزّلة.", "Uses the internet only when you open a page that is not on the device.")
    val readInText get() = t("اقرأها في العرض النصي", "Read it in the text view")
    val downloadWhole get() = t("تنزيل المصحف كاملًا…", "Download the whole Mushaf…")
    val offline get() = t("لا يوجد اتصال بالإنترنت.", "No internet connection.")
    fun fetchFailure(f: com.sakina.app.core.mushaf.FetchFailure) = when (f) {
        com.sakina.app.core.mushaf.FetchFailure.NETWORK -> t("تعذّر الوصول إلى المصدر. تحقّق من الاتصال وحاول مرة أخرى.", "Could not reach the source. Check the connection and try again.")
        com.sakina.app.core.mushaf.FetchFailure.NO_RANGE, com.sakina.app.core.mushaf.FetchFailure.BAD_REPLY -> t("ردّ المصدر بشكل غير متوقع؛ لم يُحفظ شيء.", "The source replied unexpectedly; nothing was saved.")
        com.sakina.app.core.mushaf.FetchFailure.SIZE, com.sakina.app.core.mushaf.FetchFailure.CRC, com.sakina.app.core.mushaf.FetchFailure.SHA256 -> t("الملف المنزّل لا يطابق بصمة الصفحة، فلم يُحفظ.", "The downloaded file does not match the page's fingerprint, so it was not saved.")
        com.sakina.app.core.mushaf.FetchFailure.STORAGE -> t("تعذّرت الكتابة على الجهاز؛ تحقّق من المساحة المتاحة.", "Could not write to the device; check the free space.")
        com.sakina.app.core.mushaf.FetchFailure.CANCELLED -> t("أُلغي التنزيل.", "Download cancelled.")
    }
    val nightFilter get() = t("المرشح الليلي", "Night filter")
    val nightOff get() = t("الألوان الأصلية", "Original colors")
    val nightDark get() = t("داكن", "Dark")
    val nightWarm get() = t("دافئ", "Warm")
    val nightNote get() = t("يغيّر طريقة العرض فقط، ولا يمسّ ملفات الصفحات.", "Changes the display only; the page files stay untouched.")
    val mushafIndex get() = t("الفهرس", "Index")
    val tabHizb get() = t("الأحزاب", "Hizbs")
    val tabPages get() = t("الصفحات", "Pages")
    fun focusHere(name: String, aya: String) = t("$name · الآية $aya في هذه الصفحة", "$name · ayah $aya is on this page")
    val showInText get() = t("عرضها نصًا", "Show as text")
    val bookmarkPage get() = t("علامة على الصفحة", "Bookmark the page")
    val unbookmarkPage get() = t("إزالة علامة الصفحة", "Remove the page bookmark")
    val pageBookmarked get() = t("أُضيفت علامة على أول آية في الصفحة", "Bookmarked the first ayah of the page")
    val zoomHint get() = t("قرّب بإصبعين أو بالنقر المزدوج، وانقر لإظهار الأدوات أو إخفائها.", "Pinch or double-tap to zoom; tap to show or hide the controls.")
    val downloadedMark get() = t("منزّلة", "On device")

    val dlTitle get() = t("صفحات المصحف على الجهاز", "Mushaf pages on this device")
    fun dlCount(n: String, total: String) = t("$n من $total صفحة على الجهاز", "$n of $total pages on the device")
    fun dlStored(size: String) = t("المساحة المستخدمة: $size", "Space used: $size")
    fun dlTotal(size: String) = t("حجم المصدر كاملًا ${iso(size)} (٦٠٤ ملفات PDF)", "Whole source ${iso(size)} (604 PDF files)")
    fun dlRemaining(pages: String, rawPages: Int, size: String, transfer: String) =
        t("المتبقي ${pagesCount(pages, rawPages)}: $size على الجهاز، ونحو $transfer تنزيلًا", "Remaining ${pagesCount(pages, rawPages)}: $size on the device, about $transfer to download")
    fun dlFree(size: String) = t("المساحة المتاحة على الجهاز: $size", "Free space on the device: $size")
    val dlStart get() = t("تنزيل الصفحات المتبقية", "Download the remaining pages")
    val dlResume get() = t("استكمال التنزيل", "Resume the download")
    val dlStop get() = t("إيقاف", "Stop")
    fun dlProgress(done: String, total: String) = t("نُزّلت $done من $total صفحة", "$done of $total pages downloaded")
    fun dlBytes(a: String, b: String) = t("$a من $b", "$a of $b")
    val dlStaysNote get() = t("يتوقف التنزيل عند مغادرة هذه الشاشة أو انتقال التطبيق إلى الخلفية، وتبقى الصفحات المكتملة المتحقَّقة، ويمكن استكماله لاحقًا.", "The download stops when you leave this screen or the app goes to the background. Finished, verified pages stay, and you can resume later.")
    fun dlMetered(size: String) = t("أنت متصل عبر شبكة محسوبة التكلفة (مثل بيانات الجوال). حجم التنزيل نحو $size. هل تتابع؟", "You are on a metered network (such as mobile data). The download is about $size. Continue?")
    val dlContinue get() = t("متابعة", "Continue")
    fun dlStopped(r: com.sakina.app.data.StopReason, need: String) = when (r) {
        com.sakina.app.data.StopReason.USER -> t("أوقفت التنزيل.", "You stopped the download.")
        com.sakina.app.data.StopReason.LEFT_SCREEN -> t("توقف التنزيل لأنك غادرت الشاشة.", "The download stopped because you left the screen.")
        com.sakina.app.data.StopReason.BACKGROUND -> t("توقف التنزيل لأن التطبيق انتقل إلى الخلفية.", "The download stopped because the app went to the background.")
        com.sakina.app.data.StopReason.NETWORK -> t("توقف التنزيل بسبب مشكلة في الاتصال.", "The download stopped because of a connection problem.")
        com.sakina.app.data.StopReason.STORAGE -> t("توقف التنزيل: تعذّرت الكتابة على الجهاز.", "The download stopped: could not write to the device.")
        com.sakina.app.data.StopReason.NO_SPACE -> t("المساحة المتاحة لا تكفي؛ يلزم نحو $need.", "Not enough free space; about $need is needed.")
        com.sakina.app.data.StopReason.VERIFY -> t("توقف التنزيل: صفحة لم تطابق بصمتها فلم تُحفظ.", "The download stopped: a page did not match its fingerprint and was not saved.")
    }
    fun dlFetchedNow(n: String, raw: Int) = t("نُزّل في هذه المرة ${pagesCount(n, raw)}.", "${pagesCount(n, raw)} downloaded this time.")
    val dlComplete get() = t("المصحف كاملًا على الجهاز.", "The whole Mushaf is on the device.")
    val dlManage get() = t("إدارة الصفحات", "Manage pages")
    fun dlJuzLine(have: String, total: String) = t("$have من $total", "$have of $total")
    val dlDeleteAll get() = t("حذف كل الصفحات المنزّلة", "Delete all downloaded pages")
    fun dlDeleteAllQ(n: String, raw: Int, size: String) = t("حذف ${pagesCount(n, raw)} ($size)؟ تبقى الصفحتان ١ و٢ المضمّنتان في التطبيق.", "Delete ${pagesCount(n, raw)} ($size)? Pages 1 and 2, which come with the app, stay.")
    fun dlDeleteJuzQ(j: String) = t("حذف صفحات الجزء $j المنزّلة؟", "Delete the downloaded pages of juz $j?")
    val dlVerify get() = t("التحقق من سلامة الصفحات", "Check the stored pages")
    fun dlVerifying(i: String, n: String) = t("جارٍ التحقق… $i من $n", "Checking… $i of $n")
    fun dlVerified(n: String, raw: Int) = t("فُحصت ${pagesCount(n, raw)}، وكلها مطابقة لبصماتها.", "Checked ${pagesCount(n, raw)}; all match their fingerprints.")
    fun dlVerifyRemoved(n: String, raw: Int) = t("حُذفت ${pagesCount(n, raw)} لا تطابق بصمتها؛ يمكن تنزيلها من جديد.", "Removed ${pagesCount(n, raw)} that did not match; they can be downloaded again.")
    val dlBusy get() = t("أوقف التنزيل أولًا.", "Stop the download first.")
    val dlSource get() = t(
        "المصدر: النسخة الرقمية لمصحف المدينة النبوية برواية حفص، طبعة ١٤٤١هـ، من مجمع الملك فهد لطباعة المصحف الشريف: ٦٠٤ ملفات PDF في الملف ${iso("1441-AI-hafs.zip")}. تُجلب كل صفحة على حدة من نسخة مطابقة لهذا الملف على ${iso("archive.org")}، ولا تُعرض إلا بعد مطابقة حجمها وبصمتي ${iso("CRC-32")} و${iso("SHA-256")}.",
        "Source: the digital Madinah Mushaf, Hafs, 1441H edition, by the King Fahd Glorious Quran Printing Complex: 604 PDF files in 1441-AI-hafs.zip. Each page is fetched on its own from an identical copy of that file on archive.org, and is shown only after its size, CRC-32 and SHA-256 match.",
    )
    // Listening
    val listenTitle get() = t("الاستماع للقرآن", "Listen to the Quran")
    val listenEntry get() = t("استمع إلى تلاوة مرتلة بصوت العفاسي أو المنشاوي أو الحصري، عبر الإنترنت أو من تنزيلاتك.", "Murattal recitation by Alafasy, al-Minshawi or al-Husary, streamed or from your downloads.")
    val listenThisSurah get() = t("استمع لهذه السورة", "Listen to this surah")
    val reciterLabel get() = t("القارئ", "Reciter")
    fun reciterName(r: com.sakina.app.core.recitation.Reciter) = if (isAr) r.nameAr else r.nameEn
    val recordingSet get() = t("حفص عن عاصم · مرتل · من ${iso("MP3Quran")}", "Hafs an Asim · murattal · from MP3Quran")
    fun libraryUse(used: String, limit: String) = t("مكتبة التنزيلات: $used من $limit", "Downloads library: $used of $limit")
    fun recordingInfo(duration: String, size: String) = t("$duration · $size", "$duration · $size")
    val play get() = t("تشغيل", "Play")
    val pause get() = t("إيقاف مؤقت", "Pause")
    val stopPlayback get() = t("إيقاف", "Stop")
    val back10 get() = t("رجوع ١٠ ثوانٍ", "Back 10 seconds")
    val forward10 get() = t("تقديم ١٠ ثوانٍ", "Forward 10 seconds")
    val speedLabel get() = t("السرعة", "Speed")
    val downloadRecording get() = t("تنزيل", "Download")
    val cancelDownload get() = t("إلغاء التنزيل", "Cancel download")
    val deleteRecording get() = t("حذف التنزيل", "Delete download")
    val downloadedLabel get() = t("منزّلة", "Downloaded")
    val streaming get() = t("عبر الإنترنت", "Streaming")
    val fromDevice get() = t("من الجهاز", "From the device")
    val buffering get() = t("جارٍ التحميل…", "Loading…")
    fun downloadingPct(p: String) = t("$p٪", "$p%")
    fun recordingFailure(f: com.sakina.app.data.DownloadFailure) = when (f) {
        com.sakina.app.data.DownloadFailure.OFFLINE -> t("لا يوجد اتصال بالإنترنت.", "No internet connection.")
        com.sakina.app.data.DownloadFailure.NETWORK -> t("تعذّر التنزيل؛ حاول مرة أخرى.", "The download failed; try again.")
        com.sakina.app.data.DownloadFailure.TOO_BIG -> t("التسجيل أكبر من حد ٢٥٦ ميغابايت للتسجيل الواحد.", "The recording is over the 256 MB limit for one recording.")
        com.sakina.app.data.DownloadFailure.LIBRARY_FULL -> t("مكتبة التنزيلات ممتلئة (الحد ١ غيغابايت). احذف تسجيلات لتفريغ مساحة.", "The downloads library is full (1 GB limit). Delete recordings to make room.")
        com.sakina.app.data.DownloadFailure.UNKNOWN_SIZE -> t("لم يذكر المصدر حجم الملف، فلم يُنزَّل.", "The source did not give the file size, so it was not downloaded.")
        com.sakina.app.data.DownloadFailure.NOT_AUDIO -> t("الملف المستلم ليس تسجيلًا صوتيًا، فلم يُحفظ.", "The file received is not audio, so it was not kept.")
        com.sakina.app.data.DownloadFailure.STORAGE -> t("تعذّرت الكتابة على الجهاز.", "Could not write to the device.")
        com.sakina.app.data.DownloadFailure.NO_SPACE -> t("لا توجد مساحة كافية على الجهاز.", "Not enough free space on the device.")
    }
    val nothingPlaying get() = t("اختر سورة لتشغيلها.", "Choose a surah to play.")
    val quietBlocksPlay get() = t("الهدوء المؤقت مفعّل، فلا تُشغَّل التلاوة حتى ينتهي أو تنهيه.", "Quiet mode is on; recitation will not play until it ends or you end it.")
    val endQuiet get() = t("إنهاء الهدوء", "End quiet mode")

    val repeatTitle get() = t("تكرار آية أو آيات", "Repeat an ayah or a range")
    val repeatFrom get() = t("من الآية", "From ayah")
    val repeatTo get() = t("إلى الآية", "To ayah")
    val repeatTimes get() = t("عدد المرات", "Times")
    fun repeatTimesLabel(n: Int) = when (n) { 1 -> t("مرة", "Once"); 3 -> t("٣ مرات", "3 times"); 5 -> t("٥ مرات", "5 times"); else -> t("باستمرار", "Continuously") }
    val repeatStart get() = t("ابدأ التكرار", "Start repeating")
    val repeatStop get() = t("إيقاف التكرار", "Stop repeating")
    fun repeatProgress(from: String, to: String, played: String, total: String?) = if (total == null) t("يُكرَّر من الآية $from إلى $to · المرة $played", "Repeating ayahs $from–$to · pass $played")
        else t("يُكرَّر من الآية $from إلى $to · المرة $played من $total", "Repeating ayahs $from–$to · pass $played of $total")
    val repeatDone get() = t("انتهى التكرار وتوقف التشغيل عند آخر آية في النطاق.", "Repeat finished; playback paused at the end of the range.")
    val timingNeeded get() = t("التكرار يحتاج خريطة توقيت الآيات لهذه السورة من MP3Quran (ملف صغير).", "Repeat needs this surah's ayah timing map from MP3Quran (a small file).")
    val timingDownload get() = t("تنزيل خريطة التوقيت", "Download the timing map")
    val timingLoading get() = t("جارٍ تنزيل خريطة التوقيت والتحقق منها…", "Downloading and checking the timing map…")
    val timingWaitAudio get() = t("ستُطابَق الخريطة مع مدة التسجيل بعد بدء التشغيل.", "The map is matched with the recording's length once playback starts.")
    fun currentAyah(n: String) = t("الآية الحالية: $n", "Current ayah: $n")
    fun timingInvalid(p: com.sakina.app.core.recitation.TimingProblem) = t("التكرار غير متاح لهذه السورة: ", "Repeat is not available for this surah: ") + when (p) {
        com.sakina.app.core.recitation.TimingProblem.NOT_A_LIST, com.sakina.app.core.recitation.TimingProblem.BAD_ENTRY -> t("خريطة التوقيت غير مقروءة.", "the timing map is unreadable.")
        com.sakina.app.core.recitation.TimingProblem.PREAMBLE, com.sakina.app.core.recitation.TimingProblem.NUMBERING -> t("ترقيم الآيات في خريطة التوقيت لا يطابق السورة.", "the ayah numbering in the timing map does not match the surah.")
        com.sakina.app.core.recitation.TimingProblem.ORDER, com.sakina.app.core.recitation.TimingProblem.END_BEFORE_START, com.sakina.app.core.recitation.TimingProblem.OVERLAP -> t("حدود الآيات في الخريطة متداخلة أو غير مرتبة.", "the ayah boundaries in the map overlap or are out of order.")
        com.sakina.app.core.recitation.TimingProblem.PAST_AUDIO_END, com.sakina.app.core.recitation.TimingProblem.SHORT_OF_AUDIO -> t("الخريطة لا تطابق مدة هذا التسجيل.", "the map does not match this recording's length.")
        com.sakina.app.core.recitation.TimingProblem.WRONG_SOURCE -> t("مصدر الخريطة لم يعد يخص هذا التسجيل.", "the map's source no longer belongs to this recording.")
    } + t(" لن يُخمَّن مكان الآيات.", " Ayah positions are not guessed.")
    val timingFailed get() = t("تعذّر تنزيل خريطة التوقيت.", "Could not download the timing map.")

    val diagTitle get() = t("مساعدة تشخيص التشغيل", "Playback diagnostics")
    val diagSwitch get() = t("تسجيل أحداث التشغيل على الجهاز", "Record playback events on the device")
    val diagNote get() = t("متوقفة افتراضيًا. التقرير يبقى على جهازك، ويمكنك معاينته ثم مشاركته إن أردت.", "Off by default. The report stays on your device; you can preview it and share it if you choose.")
    val diagPreview get() = t("معاينة التقرير", "Preview the report")
    val diagShare get() = t("مشاركة التقرير…", "Share the report…")
    val diagClear get() = t("مسح التقرير", "Clear the report")
    val listenSources get() = t(
        "التلاوات من موقع ${iso("MP3Quran")} (${iso("mp3quran.net")}): مشاري العفاسي ومحمد صديق المنشاوي ومحمود خليل الحصري، رواية حفص عن عاصم، مرتل. خرائط توقيت الآيات من واجهة MP3Quran نفسها، ولا تُستعمل إلا إذا طابقت السورة والتسجيل.",
        "Recitations from MP3Quran (mp3quran.net): Mishary Alafasy, Mohamed Siddiq al-Minshawi and Mahmoud Khalil al-Husary, Hafs an Asim, murattal. Ayah timing maps come from the same MP3Quran service and are used only when they match the surah and the recording.",
    )

    val mushafSourcesBody get() = t(
        "صفحات المصحف المطبوع هي ملفات PDF الأصلية للنسخة الرقمية من مصحف المدينة النبوية (حفص، ١٤٤١هـ) التي يتيحها مجمع الملك فهد لطباعة المصحف الشريف، تُعرض كما هي دون إعادة رسم. موقع المجمع لا يسمح بالوصول الآلي، فالتطبيق يجلب الصفحات من نسختين متطابقتين للملف ${iso("1441-AI-hafs.zip")} على ${iso("archive.org")}, وبصمته ${iso("MD5 f21863db…")}. شروط الاستخدام المنقولة مع النسخة: الاستخدام المجاني في المجالات الشخصية والأعمال الفردية، دون طباعة المصاحف أو استيرادها للبيع. ترقيم الصفحات مطابق لبيانات تنزيل، وجرى التحقق من بدايات صفحات منها بمقارنتها بصريًا.",
        "The printed pages are the original PDF files of the digital Madinah Mushaf (Hafs, 1441H) made available by the King Fahd Glorious Quran Printing Complex, shown as they are without redrawing. The Complex's site does not allow automated access, so the app fetches pages from two identical copies of 1441-AI-hafs.zip on archive.org (MD5 f21863db…). Terms of use carried with the copy: free use for personal and individual purposes, without printing or importing mushafs for sale. Page numbers match Tanzil's data; the first ayahs of sample pages were checked by eye.",
    )
    // ---------- Adhkar, tasbih and qibla (phase 6) ----------
    val discoverTitle get() = t("اكتشف", "Discover")
    val toolsAdhkar get() = t("الأذكار", "Adhkar")
    val toolsAdhkarBody get() = t("٥٤ ذكرًا ودعاءً موثقًا، وأوراد الصباح والمساء والنوم والاستيقاظ وما بعد الصلاة", "54 authenticated adhkar and supplications, with the morning, evening, sleep, waking and after-prayer sets")
    val toolsTasbih get() = t("المسبحة", "Tasbih")
    val toolsTasbihBody get() = t("عدّاد حر بهدف اختياري واهتزاز اختياري وتراجع", "A free counter with an optional target, vibration and undo")
    val toolsQibla get() = t("القبلة", "Qibla")
    val toolsQiblaBody get() = t("الاتجاه والمسافة، وبوصلة، وخريطة تعمل دون إنترنت", "Direction and distance, a compass, and a map that works offline")
    val plannedLater get() = (if (isAr) listOf(
        "التقويم ورمضان والليل والجمعة" to "المرحلة ٧",
        "الويدجت والاختصارات" to "المرحلة ٨",
        "السجل والتصدير ومساجدي والنسخ الاحتياطي" to "المرحلة ٩",
    ) else listOf(
        "Calendar, Ramadan, night and Friday" to "Phase 7",
        "Widgets and shortcuts" to "Phase 8",
        "Log, export, my mosques and backup" to "Phase 9",
    )).map { digits(it.first) to digits(it.second) }

    val adhkarTitle get() = t("الأذكار", "Adhkar")
    val adhkarSearchHint get() = t("ابحث في النصوص والعناوين والتصنيفات", "Search texts, titles and categories")
    val adhkarPractices get() = t("الأوراد", "Daily sets")
    val adhkarCategories get() = t("التصنيفات", "Categories")
    val adhkarFavorites get() = t("المفضلة", "Favourites")
    val adhkarNoFavorites get() = t("لم تضف ذكرًا إلى المفضلة بعد. افتح أي ذكر واضغط النجمة.", "No favourites yet. Open any dhikr and tap the star.")
    val adhkarNoResults get() = t("لا نتائج. جرّب كلمة أخرى من النص أو العنوان.", "No results. Try another word from the text or title.")
    val adhkarLibraryNote get() = t(
        "مكتبة محلية تعمل دون إنترنت. كل نص منقول بلفظ الرواية المذكورة، ومعه مصدره ودرجته وروابط للتحقق منه.",
        "A local library that works offline. Each text is quoted in the wording of the cited narration, with its source, grade and links to check it.",
    )
    fun adhkarCount(n: String, raw: Int) = if (isAr) {
        when (raw) {
            1 -> "ذكر واحد"
            2 -> "ذكران"
            in 3..10 -> "$n أذكار"
            else -> "$n ذكرًا"
        }
    } else if (raw == 1) "1 dhikr" else "$n adhkar"
    fun times(n: String, raw: Int) = if (isAr) {
        when (raw) {
            0 -> "بلا عدد محدد"
            1 -> "مرة واحدة"
            2 -> "مرتان"
            in 3..10 -> "$n مرات"
            else -> "$n مرة"
        }
    } else when (raw) {
        0 -> "No set number"
        1 -> "Once"
        else -> "$n times"
    }
    fun progressOf(done: String, total: String) = t("أنجزت $done من $total", "$done of $total done")
    fun roundOf(r: String, total: String) = t("الجولة $r من $total", "Round $r of $total")
    fun wholeSetTimes(n: String) = t("تُقرأ المجموعة كاملة $n مرات", "Say the whole set $n times")
    val wirdTapHint get() = t("اضغط على البطاقة للعدّ؛ يُحفظ التقدم لهذا اليوم وهذا الورد وحده.", "Tap a card to count; progress is kept for this day and this set only.")
    val wirdDone get() = t("تمّ", "Done")
    val wirdReset get() = t("تصفير هذا الورد", "Reset this set")
    val wirdResetConfirm get() = t("تصفير عدّادات هذا الورد لهذا اليوم؟", "Reset this set's counters for today?")
    val wirdAllDone get() = t("أتممت هذا الورد. تقبّل الله منك.", "You have completed this set. May Allah accept it.")
    val untapOne get() = t("إنقاص واحد", "Count one less")
    val resetItem get() = t("تصفير هذا الذكر", "Reset this dhikr")
    val wirdSession get() = t("بعد صلاة", "After")
    val morningWording get() = t("صيغة الصباح", "Morning wording")
    val eveningWording get() = t("صيغة المساء", "Evening wording")
    val reminderLink get() = t("التذكير", "Reminder")
    fun reminderOn(time: String) = t("مفعّل يوميًا عند $time", "On, at $time")
    val reminderOffNote get() = t("غير مفعّل. يمكنك تفعيل تذكير بوقت تختاره.", "Off. You can turn on a reminder at a time you choose.")
    val reminderSetup get() = t("إعداد التذكير", "Set up reminder")
    val contextTitle get() = t("السياق والفضل", "Context and virtue")
    val sourceTitle get() = t("المصدر والدرجة", "Source and grade")
    fun gradeLine(grade: String, by: String) = t("الدرجة: $grade — $by", "Grade: $grade — $by")
    val sahihayn get() = t("حديث في الصحيحين أو أحدهما", "In al-Bukhari or Muslim")
    val gradersTitle get() = t("أحكام المحققين", "Graders")
    val linksTitle get() = t("روابط المراجع", "References")
    val noteTitle get() = t("ملاحظة", "Note")
    val noBrowser get() = t("لا يوجد تطبيق يفتح الرابط.", "No app can open the link.")
    val favoriteAdd get() = t("إضافة إلى المفضلة", "Add to favourites")
    val favoriteRemove get() = t("إزالة من المفضلة", "Remove from favourites")
    val shareDhikr get() = t("مشاركة", "Share")
    val itemCounterTitle get() = t("عدّاد هذا الذكر", "Counter for this dhikr")
    val itemCounterNote get() = t("مستقل عن عدّادات الأوراد، ويُحفظ لهذا اليوم.", "Separate from the daily sets, kept for today.")
    val countLabel get() = t("العدد المروي", "Narrated count")
    fun quranPassage(sura: String, from: String, to: String, whole: Boolean, single: Boolean) = when {
        whole -> t("سورة $sura", "Surah $sura")
        single -> t("سورة $sura، الآية $from", "Surah $sura, ayah $from")
        else -> t("سورة $sura، الآيات $from–$to", "Surah $sura, ayahs $from–$to")
    }

    val tasbihTitle get() = t("المسبحة", "Tasbih")
    val tasbihTap get() = t("اضغط للعدّ", "Tap to count")
    val tasbihTarget get() = t("الهدف", "Target")
    val tasbihNoTarget get() = t("بلا هدف", "No target")
    val tasbihCustom get() = t("هدف آخر", "Other")
    val tasbihCustomTitle get() = t("هدف مخصص", "Custom target")
    val tasbihCustomHint get() = t("عدد بين ١ و١٠٠٠٠", "A number from 1 to 10000")
    val tasbihVibrate get() = t("اهتزاز خفيف مع كل ضغطة، وأطول عند بلوغ الهدف", "A light buzz on each tap, a longer one at the target")
    val tasbihReset get() = t("تصفير", "Reset")
    val tasbihUndo get() = t("تراجع", "Undo")
    val tasbihUndoNote get() = t("يتراجع عن آخر تعديل فعلي: ضغطة أو تصفير أو تغيير الهدف أو العبارة.", "Undoes the last real change: a tap, a reset, or a new target or phrase.")
    fun tasbihRounds(n: String) = t("الدورات المكتملة: $n", "Completed rounds: $n")
    fun tasbihTotal(n: String) = t("المجموع $n", "total $n")
    val tasbihLabel get() = t("ما تسبّح به (اختياري)", "What you are saying (optional)")
    val tasbihLabelHint get() = t("مثل: سبحان الله", "e.g. Subhan Allah")
    val tasbihReached get() = t("بلغت الهدف", "Target reached")
    val openAdhkarSet get() = t("فتح الأذكار", "Open the adhkar")

    val qiblaTitle get() = t("القبلة", "Qibla")
    val qiblaNoPlace get() = t("لم تختر موقعًا بعد. اختر موقعًا لحساب اتجاه القبلة.", "No place chosen yet. Choose a place to compute the qibla.")
    val qiblaChoosePlace get() = t("اختيار موقع", "Choose a place")
    fun qiblaFrom(place: String) = t("من: $place", "From: $place")
    val qiblaChangePlace get() = t("تغيير", "Change")
    val qiblaBearing get() = t("اتجاه القبلة", "Qibla direction")
    fun qiblaBearingValue(deg: String) = t("$deg من الشمال الحقيقي", "$deg from true north")
    val qiblaDistance get() = t("المسافة إلى الكعبة", "Distance to the Kaaba")
    fun distanceKm(km: String) = t("نحو $km كم", "about $km km")
    val qiblaNear get() = t("أنت عند الكعبة أو قريب جدًا منها، فلا معنى للاتجاه هنا.", "You are at or very near the Kaaba; a direction has no meaning here.")
    val compassTitle get() = t("البوصلة", "Compass")
    fun phoneHeading(deg: String) = t("اتجاه أعلى الهاتف: $deg", "Top of phone: $deg")
    val compassAligned get() = t("الهاتف متجه نحو القبلة", "The phone faces the qibla")
    fun turnRight(deg: String) = t("استدر يمينًا $deg", "Turn right $deg")
    fun turnLeft(deg: String) = t("استدر يسارًا $deg", "Turn left $deg")
    val compassWaiting get() = t("بانتظار قراءة المستشعر…", "Waiting for the sensor…")
    val compassHowTo get() = t("ضع الهاتف أفقيًا والشاشة للأعلى، وأدره حتى تصل علامة الكعبة إلى أعلى القرص.", "Hold the phone flat, screen up, and turn until the Kaaba mark reaches the top of the dial.")
    val vibrateAlign get() = t("اهتزاز عند المحاذاة الموثوقة", "Vibrate on reliable alignment")
    fun compassSource(s: com.sakina.app.core.qibla.CompassSource) = when (s) {
        com.sakina.app.core.qibla.CompassSource.ROTATION_VECTOR -> t("المستشعرات: متجه الدوران المدمج (تسارع وجيروسكوب ومغناطيسية)", "Sensors: fused rotation vector (accelerometer, gyroscope, magnetometer)")
        com.sakina.app.core.qibla.CompassSource.GEOMAGNETIC_ROTATION_VECTOR -> t("المستشعرات: متجه الدوران المغناطيسي (دون جيروسكوب)", "Sensors: geomagnetic rotation vector (no gyroscope)")
        com.sakina.app.core.qibla.CompassSource.ACCEL_MAGNETOMETER -> t("المستشعرات: مقياس التسارع والمغناطيسية، يدمجهما التطبيق", "Sensors: accelerometer and magnetometer, combined by the app")
        com.sakina.app.core.qibla.CompassSource.NONE -> t("لا توجد بوصلة في هذا الجهاز", "This device has no compass")
    }
    fun compassIssue(i: com.sakina.app.core.qibla.CompassIssue) = when (i) {
        com.sakina.app.core.qibla.CompassIssue.NO_SENSOR -> t("لا يوجد مستشعر مغناطيسي يعمل في هذا الجهاز. استعمل الدرجة أعلاه مع معلم معروف، أو الخريطة.", "No working magnetic sensor on this device. Use the degree above with a known landmark, or the map.")
        com.sakina.app.core.qibla.CompassIssue.LOW_ACCURACY -> t("دقة البوصلة ضعيفة: حرّك الهاتف في الهواء على شكل الرقم 8 عدة مرات لمعايرتها.", "Low compass accuracy: move the phone in a figure-8 a few times to calibrate it.")
        com.sakina.app.core.qibla.CompassIssue.TILT -> t("الهاتف مائل: ضعه أفقيًا والشاشة للأعلى.", "The phone is tilted: hold it flat, screen up.")
        com.sakina.app.core.qibla.CompassIssue.INTERFERENCE -> t("تداخل مغناطيسي محتمل: قوة المجال المقيسة بعيدة عن المتوقع هنا. ابتعد عن المعادن والمغناطيس والأجهزة والسيارة.", "Possible magnetic interference: the measured field is far from what is expected here. Move away from metal, magnets, devices and cars.")
        com.sakina.app.core.qibla.CompassIssue.STALE -> t("توقفت قراءات المستشعر؛ ما يظهر قراءة قديمة.", "The sensor stopped reporting; what you see is an old reading.")
        com.sakina.app.core.qibla.CompassIssue.POLE_CAUTION -> t("أنت قرب قطب مغناطيسي: البوصلة أقل موثوقية هنا.", "You are near a magnetic pole: compasses are less reliable here.")
        com.sakina.app.core.qibla.CompassIssue.POLE_BLACKOUT -> t("أنت في منطقة انقطاع قرب القطب المغناطيسي: لا يُعتمد على البوصلة هنا.", "You are in a blackout zone near the magnetic pole: do not rely on a compass here.")
        com.sakina.app.core.qibla.CompassIssue.MODEL_EXPIRED -> t("انتهت مدة نموذج \u2066WMM2025\u2069 (صالح حتى بداية ٢٠٣٠)، فتصحيح الشمال الحقيقي تقريبي.", "The \u2066WMM2025\u2069 model has expired (valid until early 2030); the true-north correction is approximate.")
    }
    fun declination(deg: String, east: Boolean) = if (east) t("الانحراف المغناطيسي هنا: $deg شرقًا", "Magnetic declination here: $deg east")
    else t("الانحراف المغناطيسي هنا: $deg غربًا", "Magnetic declination here: $deg west")
    val trueNorthNote get() = t("تقيس البوصلة الشمال المغناطيسي؛ يحوّله التطبيق إلى الشمال الحقيقي بإضافة الانحراف المحسوب على الجهاز من نموذج \u2066WMM2025\u2069.", "The compass measures magnetic north; the app converts it to true north by adding the declination computed on the device from \u2066WMM2025\u2069.")
    val qiblaAccuracyTitle get() = t("حدود الدقة", "Accuracy and limits")
    val qiblaCalcNote get() = t("الاتجاه محسوب على الجهاز كدائرة عظمى على كرة الأرض، من إحداثيات الموقع المختار والكعبة.", "The direction is computed on the device as a great circle on the globe, from the chosen place's coordinates and the Kaaba's.")
    fun qiblaSensitivity(km: String, deg: String) = t("إذا كان مكانك الفعلي يبعد $km كم عن الموقع المختار فقد يختلف الاتجاه حتى $deg.", "If you are $km km from the chosen place, the direction may differ by up to $deg.")
    val qiblaCompassNote get() = t("دقة بوصلة الهاتف عادة بين ٥° و١٥°، وتتأثر بالمعادن القريبة. تحقّق بمعلم معروف عند الشك.", "A phone compass is usually accurate to 5–15° and is affected by nearby metal. Check against a known landmark when in doubt.")
    val mapTitle get() = t("الخريطة (دون إنترنت)", "Map (offline)")
    val mapCentered get() = t("مركّزة عليك", "Centred on you")
    val mapWorld get() = t("العالم", "World")
    val mapYou get() = t("موقعك", "You")
    val mapMecca get() = t("مكة", "Mecca")
    val mapPath get() = t("أقصر طريق (دائرة عظمى)", "Shortest path (great circle)")
    val mapCenteredNote get() = t(
        "إسقاط سمتي متساوي الأبعاد مركزه موقعك: الاتجاهات والمسافات منك صحيحة، فيظهر طريق القبلة خطًا مستقيمًا بزاوية القبلة نفسها والشمال إلى الأعلى. الأشكال البعيدة عن المركز مشوهة، والجانب المقابل من الأرض ممطوط عند الحافة.",
        "Azimuthal equidistant projection centred on you: directions and distances from you are true, so the qibla path is a straight line at the qibla angle, north up. Shapes far from the centre are distorted, and the far side of the Earth is stretched around the rim.",
    )
    val mapWorldNote get() = t(
        "إسقاط متساوي المستطيلات: خطوط الطول والعرض شبكة منتظمة، ولا تُحفظ فيه الاتجاهات ولا المسافات، فيظهر أقصر طريق إلى مكة منحنيًا. لا تقس زاوية القبلة على هذه الخريطة.",
        "Equirectangular projection: longitude and latitude form a regular grid; it keeps neither directions nor distances, so the shortest path to Mecca looks curved. Do not measure the qibla angle on this map.",
    )
    val mapDataNote get() = t(
        "بيانات Natural Earth (ملكية عامة) بمقياسي \u20661:50m\u2069 و\u20661:110m\u2069، مخزّنة بدقة نحو ٠٫٦ كم؛ السواحل مبسّطة والحدود تقريبية ولا تمثل موقفًا سياسيًا. الخريطة توضيحية، والرقم أعلاه هو المرجع.",
        "Natural Earth data (public domain) at \u20661:50m\u2069 and \u20661:110m\u2069, stored to about 0.6 km; coastlines are simplified and borders approximate, not a political statement. The map is illustrative; the number above is the reference.",
    )
    val mapZoomHint get() = t("قرّب أو حرّك بإصبعين، واضغط مرتين للعودة.", "Pinch or drag to zoom and pan; double-tap to reset.")
    val srcAdhkar get() = t("الأذكار: نصوص الأحاديث من بيانات hadith-api المفتوحة بترقيم sunnah.com، مع أحكام المحققين؛ ونصوص الآيات من نسخة Tanzil المضمنة.", "Adhkar: hadith texts from the open hadith-api data with sunnah.com numbering, with the graders' rulings; ayah texts from the bundled Tanzil copy.")
    val srcQibla get() = t("القبلة: نموذج \u2066WMM2025\u2069 من NOAA وبيانات Natural Earth للخريطة (ملكية عامة).", "Qibla: NOAA's \u2066WMM2025\u2069 model and Natural Earth map data (public domain).")
    fun compassPoint(i: Int) = (if (isAr) listOf(
        "شمال", "شمال شمال شرق", "شمال شرق", "شرق شمال شرق", "شرق", "شرق جنوب شرق", "جنوب شرق", "جنوب جنوب شرق",
        "جنوب", "جنوب جنوب غرب", "جنوب غرب", "غرب جنوب غرب", "غرب", "غرب شمال غرب", "شمال غرب", "شمال شمال غرب",
    ) else listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"))[i]

}

val LocalStrings = staticCompositionLocalOf { Strings(Lang.AR) }
