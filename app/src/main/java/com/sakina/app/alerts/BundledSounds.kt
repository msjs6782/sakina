package com.sakina.app.alerts

import com.sakina.app.R

/**
 * The two candidate adhan recordings shipped with the app, copied byte for byte from Wikimedia Commons
 * (SHA-1 checked against the Commons API when fetched). Both still need a content review before they could be
 * presented as approved recordings; see docs/ADHAN_RECORDINGS.md.
 */
internal object BundledSounds {
    val list = listOf(
        BundledSound(
            id = "candidate_1",
            res = R.raw.adhan_candidate_1,
            titleAr = "المرشّح الأول: تسجيل من المسجد النبوي",
            titleEn = "Candidate 1: recording from the Prophet's Mosque",
            descriptionAr = "وصفه الرافع بأنه أذان من المسجد النبوي. المؤذن وتاريخ التسجيل غير مذكورين في المصدر.",
            descriptionEn = "Described by the uploader as a call to prayer from the Prophet's Mosque. The muezzin and recording date are not given by the source.",
            author = "ejaz215 (Freesound)",
            sourceUrl = "https://commons.wikimedia.org/wiki/File:33937_ejaz215_call-to-prayer-from-the-prophet-s-mo.ogg",
            license = "CC BY 3.0 — https://creativecommons.org/licenses/by/3.0/",
            licenseUrl = "https://creativecommons.org/licenses/by/3.0/",
            sha256 = "4d0fabe0e0ef52186646a256220f74a972ba698ac269165fed5d52de3202d656",
            durationSec = 187,
        ),
        BundledSound(
            id = "candidate_2",
            res = R.raw.adhan_candidate_2,
            titleAr = "المرشّح الثاني: «Beautiful adhan»",
            titleEn = "Candidate 2: “Beautiful adhan”",
            descriptionAr = "رفعه صاحبه بوصف «أذان» فقط. المؤذن والمكان وتاريخ التسجيل غير مذكورة في المصدر.",
            descriptionEn = "Uploaded with the description “Adhan.” only. The muezzin, place and recording date are not given by the source.",
            author = "Adam-synagda",
            sourceUrl = "https://commons.wikimedia.org/wiki/File:Beautiful_adhan.ogg",
            license = "CC0 1.0 — https://creativecommons.org/publicdomain/zero/1.0/",
            licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
            sha256 = "35fe06b08fe80505c550c33fed8a783fa9901ddc81ac884958b4be048f5b2a79",
            durationSec = 154,
        ),
    )
}
