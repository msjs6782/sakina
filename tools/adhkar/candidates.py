"""The adhkar chosen for Sakina, before verification.

Each entry names the wording to find (`find`, plain Arabic) inside the hadith cited as `ref`
(sunnah.com reference). tools/adhkar/build.py checks the wording against the hadith text, takes the
vowelled text from the source, and writes app/src/main/assets/adhkar/adhkar.json. An entry whose
wording or reference does not verify is not shipped.

`find_evening`: the evening wording of a morning/evening dhikr (same hadith).
`find_from`/`find_to`: the dhikr is the span from the first phrase to the end of the second.
`prefer`: the narration (hadith-api number, e.g. "2723.02") whose wording is shown.
`remove`: narrators' or explanatory words inside the span that are not part of the dhikr (plain Arabic).
`patch`: (wrong, right) fixes for encoding damage in the source text.
`drop`: why a verified candidate is not shipped (the library keeps 54).
`count`: the narrated number of repetitions; 0 when the narration gives no number.
"""

CATEGORIES = [
    ("morning_evening", "أذكار الصباح والمساء", "Morning and evening"),
    ("sleep", "أذكار النوم", "Before sleep"),
    ("waking", "أذكار الاستيقاظ", "On waking"),
    ("after_prayer", "الأذكار بعد السلام من الصلاة", "After the prayer"),
    ("in_prayer", "أذكار الصلاة", "In the prayer"),
    ("adhan_wudu_masjid", "الأذان والوضوء والمسجد", "Adhan, wudu and the mosque"),
    ("home", "المنزل والخلاء واللباس", "Home, restroom and clothing"),
    ("food", "الطعام والشراب", "Food and drink"),
    ("travel", "السفر", "Travel"),
    ("distress", "الكرب والهم", "Distress and worry"),
    ("dhikr", "الذكر والاستغفار والدعاء", "Remembrance, forgiveness and supplication"),
]

# Five practices with context: an ordered set of adhkar for a time of day.
PRACTICES = [
    ("morning", "أذكار الصباح", "Morning adhkar", "ADHKAR_MORNING",
     "بعد صلاة الفجر إلى طلوع الشمس، ويمتد إلى الضحى.", "From the Fajr prayer until sunrise, extending into the forenoon."),
    ("evening", "أذكار المساء", "Evening adhkar", "ADHKAR_EVENING",
     "بعد صلاة العصر إلى غروب الشمس، ويمتد أول الليل.", "From the Asr prayer until sunset, extending into the early night."),
    ("sleep", "أذكار النوم", "Adhkar before sleep", "ADHKAR_SLEEP",
     "عند الاضطجاع على الفراش للنوم.", "When lying down in bed to sleep."),
    ("waking", "أذكار الاستيقاظ", "Adhkar on waking", "ADHKAR_WAKING",
     "عند الاستيقاظ من النوم، ومن تعارّ من الليل.", "On waking from sleep, or waking in the night."),
    ("after_prayer", "أذكار ما بعد الصلاة", "Adhkar after the prayer", None,
     "بعد السلام من كل صلاة مفروضة.", "After the closing salam of each obligatory prayer."),
]

C = []


def add(id, cat, titleAr, titleEn, ref, find, count=1, ctxAr="", ctxEn="", practices=(), find_evening=None, find_from=None, find_to=None, note=None,
        drop=None, prefer=None, remove=(), patch=(), count_note=None):
    C.append(dict(id=id, cat=cat, titleAr=titleAr, titleEn=titleEn, ref=ref, find=find, count=count, ctxAr=ctxAr, ctxEn=ctxEn,
                  practices=list(practices), find_evening=find_evening, find_from=find_from, find_to=find_to, note=note,
                  drop=drop, prefer=prefer, remove=list(remove), patch=list(patch), count_note=count_note))


# ---- Morning and evening ----
add("sayyid_istighfar", "morning_evening", "سيد الاستغفار", "The master supplication for forgiveness", "bukhari:6306",
    find_from="اللهم انت ربي لا اله الا انت خلقتني", find_to="فانه لا يغفر الذنوب الا انت", find=None,
    ctxAr="من قالها من النهار موقنًا بها فمات من يومه قبل أن يمسي فهو من أهل الجنة، ومن قالها من الليل موقنًا بها فمات قبل أن يصبح فهو من أهل الجنة.",
    ctxEn="Whoever says it in the day with conviction and dies that day before evening is among the people of Paradise; likewise at night.",
    practices=("morning", "evening"))
add("asbahna_mulk", "morning_evening", "أصبحنا وأصبح الملك لله", "We have entered the morning and the dominion is Allah's", "muslim:2723b",
    find_from="امسينا وامسي الملك لله", find_to="عذاب في القبر", find=None, prefer="2723.02", remove=("قال اراه قال فيهن",),
    ctxAr="كان النبي ﷺ يقوله إذا أمسى، وإذا أصبح قال: «أصبحنا وأصبح الملك لله».",
    ctxEn="The Prophet ﷺ said it in the evening, and in the morning he said “We have entered the morning…”.",
    practices=("morning", "evening"))
add("bika_asbahna", "morning_evening", "اللهم بك أصبحنا وبك أمسينا", "O Allah, by You we enter the morning", "tirmidhi:3391",
    find="اللهم بك اصبحنا وبك امسينا وبك نحيا وبك نموت واليك المصير", count=1,
    ctxAr="كان النبي ﷺ يعلّم أصحابه أن يقولوه إذا أصبحوا، وإذا أمسوا: «اللهم بك أمسينا وبك أصبحنا وبك نحيا وبك نموت وإليك النشور».",
    ctxEn="The Prophet ﷺ taught his companions to say it in the morning, with the evening wording at night.",
    practices=("morning", "evening"), find_evening="اللهم بك امسينا وبك اصبحنا وبك نحيا وبك نموت واليك النشور")
add("alim_ghayb", "morning_evening", "اللهم عالم الغيب والشهادة", "O Allah, Knower of the unseen and the seen", "tirmidhi:3392",
    find="اللهم عالم الغيب والشهاده فاطر السموات والارض رب كل شيء ومليكه اشهد ان لا اله الا انت اعوذ بك من شر نفسي ومن شر الشيطان وشركه",
    ctxAr="علّمه النبي ﷺ أبا بكر رضي الله عنه وقال: «قله إذا أصبحت وإذا أمسيت وإذا أخذت مضجعك».",
    ctxEn="Taught to Abu Bakr: “Say it in the morning, in the evening and when you lie down to sleep.”",
    practices=("morning", "evening", "sleep"))
add("bismillah_la_yadurr", "morning_evening", "بسم الله الذي لا يضر مع اسمه شيء", "In the name of Allah, with whose name nothing harms", "tirmidhi:3388",
    find="بسم الله الذي لا يضر مع اسمه شيء في الارض ولا في السماء وهو السميع العليم", count=3,
    ctxAr="من قالها ثلاث مرات حين يصبح وحين يمسي لم يضره شيء.",
    ctxEn="Said three times in the morning and evening; nothing will harm the one who says it.",
    practices=("morning", "evening"))
add("afiya", "morning_evening", "اللهم إني أسألك العافية في الدنيا والآخرة", "O Allah, I ask You for well-being", "abudawud:5074",
    find_from="اللهم اني اسالك العافيه في الدنيا والاخره", find_to="وأعوذ بعظمتك ان اغتال من تحتي", find=None, remove=("عورتي وقال عثمان",),
    note="بلفظ رواية عثمان بن أبي شيبة عند أبي داود: «اللهم استر عوراتي».",
    ctxAr="لم يكن رسول الله ﷺ يدع هؤلاء الدعوات حين يمسي وحين يصبح.",
    ctxEn="The Messenger of Allah ﷺ never left these supplications in the evening and morning.",
    practices=("morning", "evening"))
add("muawwidhat_3", "morning_evening", "المعوذات ثلاث مرات", "The three protective surahs, three times", "tirmidhi:3575",
    find="قل هو الله احد والمعوذتين حين تمسي وتصبح ثلاث مرات تكفيك من كل شيء", count=3,
    ctxAr="قراءة سورة الإخلاص والفلق والناس ثلاث مرات صباحًا ومساءً تكفي من كل شيء.",
    ctxEn="Reciting al-Ikhlas, al-Falaq and an-Nas three times morning and evening suffices against everything.",
    practices=("morning", "evening"))
add("subhanallah_wabihamdih_100", "morning_evening", "سبحان الله وبحمده مائة مرة", "Glory be to Allah and praise be to Him, one hundred times", "muslim:2692",
    find="سبحان الله وبحمده", count=100,
    ctxAr="من قال حين يصبح وحين يمسي «سبحان الله وبحمده» مائة مرة لم يأت أحد يوم القيامة بأفضل مما جاء به إلا أحد قال مثل ما قال أو زاد عليه.",
    ctxEn="Whoever says it a hundred times morning and evening, no one brings anything better on the Day of Resurrection except one who said as much or more.",
    practices=("morning", "evening"))
add("tahlil_100", "morning_evening", "لا إله إلا الله وحده لا شريك له مائة مرة", "There is no god but Allah alone, one hundred times", "bukhari:6403",
    find="لا اله الا الله وحده لا شريك له له الملك وله الحمد وهو علي كل شيء قدير", count=100,
    ctxAr="من قالها في يوم مائة مرة كانت له عدل عشر رقاب، وكتبت له مائة حسنة، ومحيت عنه مائة سيئة، وكانت له حرزًا من الشيطان يومه ذلك حتى يمسي.",
    ctxEn="Said a hundred times in a day: like freeing ten slaves, a hundred good deeds, a hundred sins erased, and protection from Satan until evening.",
    practices=("morning",))
add("kalimat_tammat_evening", "morning_evening", "أعوذ بكلمات الله التامات من شر ما خلق", "I seek refuge in the perfect words of Allah", "muslim:2709a",
    find="اعوذ بكلمات الله التامات من شر ما خلق", count=1, drop="same words as stopping_place; the evening use is a single remark",
    ctxAr="قال رسول الله ﷺ لمن لدغته عقرب: «أما لو قلت حين أمسيت: أعوذ بكلمات الله التامات من شر ما خلق، لم تضرك».",
    ctxEn="Said in the evening: “Had you said it when evening came, it would not have harmed you.”",
    practices=("evening",))

# ---- Sleep ----
add("bismika_amut", "sleep", "باسمك اللهم أموت وأحيا", "In Your name, O Allah, I die and I live", "bukhari:6324",
    find="باسمك اللهم اموت واحيا",
    ctxAr="كان النبي ﷺ إذا أراد أن ينام قال ذلك، وإذا استيقظ من منامه قال: «الحمد لله الذي أحيانا بعد ما أماتنا وإليه النشور».",
    ctxEn="The Prophet ﷺ said it when he wanted to sleep, and on waking: “Praise be to Allah who gave us life after He caused us to die, and to Him is the return.”",
    practices=("sleep",))
add("aslamtu_nafsi", "sleep", "اللهم أسلمت وجهي إليك", "O Allah, I submit my face to You", "bukhari:6311",
    find_from="اللهم اسلمت وجهي اليك", find_to="وبنبيك الذي ارسلت", find=None,
    ctxAr="إذا أتيت مضجعك فتوضأ وضوءك للصلاة، ثم اضطجع على شقك الأيمن وقل ذلك، واجعلهن آخر ما تقول؛ فإن مت مت على الفطرة.",
    ctxEn="Make wudu, lie on your right side, say it and let it be your last words; if you die, you die upon the fitrah.",
    practices=("sleep",))
add("ayat_kursi_sleep", "sleep", "آية الكرسي عند النوم", "Ayat al-Kursi before sleep", "bukhari:2311",
    find="اذا اويت الي فراشك فاقرا ايه الكرسي",
    ctxAr="لن يزال عليك من الله حافظ، ولا يقربك شيطان حتى تصبح. (سورة البقرة، الآية ٢٥٥)",
    ctxEn="A guardian from Allah stays with you and no devil comes near you until morning. (al-Baqarah 2:255)",
    practices=("sleep",))
add("tasbih_sleep", "sleep", "التسبيح والتحميد والتكبير عند النوم", "Tasbih, tahmid and takbir before sleep", "bukhari:3113",
    find="فكبرا الله اربعا وثلاثين", count=1,
    ctxAr="قال النبي ﷺ لعلي وفاطمة لما سألته خادمًا: «إذا أخذتما مضاجعكما فكبّرا الله أربعًا وثلاثين، واحمدا ثلاثًا وثلاثين، وسبّحا ثلاثًا وثلاثين، فإن ذلك خير لكما مما سألتماه».",
    ctxEn="To Ali and Fatimah, who had asked for a servant: “When you go to bed, say Allahu akbar 34 times, al-hamdu lillah 33 and subhan Allah 33; that is better for you than what you asked for.”",
    practices=("sleep",))
add("bismika_rabbi_wadatu", "sleep", "باسمك رب وضعت جنبي", "In Your name, my Lord, I lay down my side", "bukhari:6320",
    find_from="باسمك رب وضعت جنبي", find_to="بما تحفظ به عبادك الصالحين", find=None,
    ctxAr="إذا أوى أحدكم إلى فراشه فلينفض فراشه بداخلة إزاره، ثم ليقل ذلك.",
    ctxEn="When one of you goes to bed, let him dust off his bed, then say it.",
    practices=("sleep",))
add("qini_adhabak", "sleep", "اللهم قني عذابك يوم تبعث عبادك", "O Allah, protect me from Your punishment", "abudawud:5045",
    find="اللهم قني عذابك يوم تبعث عبادك", count=3,
    ctxAr="كان رسول الله ﷺ إذا أراد أن يرقد وضع يده اليمنى تحت خده ثم يقولها ثلاث مرار.",
    ctxEn="He ﷺ placed his right hand under his cheek before sleeping and said it three times.",
    practices=("sleep",))
add("last_baqarah", "sleep", "خواتيم سورة البقرة", "The last two verses of al-Baqarah", "bukhari:5009",
    find="من قرا بالايتين من اخر سوره البقره في ليله كفتاه",
    ctxAr="من قرأ الآيتين من آخر سورة البقرة في ليلة كفتاه. (البقرة ٢٨٥–٢٨٦)",
    ctxEn="Whoever recites the last two verses of al-Baqarah at night, they suffice him. (2:285–286)",
    practices=("sleep",))
add("nafth_muawwidhat", "sleep", "جمع الكفين والنفث فيهما بالمعوذات", "Reciting the protective surahs into the palms", "bukhari:5017",
    find="جمع كفيه ثم نفث فيهما", count=3,
    ctxAr="كان ﷺ يقرأ فيهما الإخلاص والفلق والناس، ثم يمسح بهما ما استطاع من جسده، يبدأ برأسه ووجهه، يفعل ذلك ثلاث مرات.",
    ctxEn="He ﷺ recited al-Ikhlas, al-Falaq and an-Nas into his palms and wiped over his body, three times.",
    practices=("sleep",))
add("atamana_wasaqana", "sleep", "الحمد لله الذي أطعمنا وسقانا", "Praise be to Allah who fed us and gave us drink", "muslim:2715",
    find="الحمد لله الذي اطعمنا وسقانا وكفانا واوانا فكم ممن لا كافي له ولا ميوي",
    ctxAr="كان رسول الله ﷺ إذا أوى إلى فراشه قال ذلك.",
    ctxEn="The Messenger of Allah ﷺ said it when he went to bed.",
    practices=("sleep",))

# ---- Waking ----
add("ahyana", "waking", "الحمد لله الذي أحيانا بعد ما أماتنا", "Praise be to Allah who gave us life after death", "bukhari:6312",
    find="الحمد لله الذي احيانا بعد ما اماتنا واليه النشور",
    ctxAr="كان النبي ﷺ إذا أوى إلى فراشه قال: «باسمك أموت وأحيا»، وإذا قام قال ذلك.",
    ctxEn="The Prophet ﷺ said “In Your name I die and live” when going to bed, and this when he got up.",
    practices=("waking",))
add("taarra_minal_layl", "waking", "من تعارّ من الليل", "Waking in the night", "bukhari:1154",
    find_from="لا اله الا الله وحده لا شريك له له الملك وله الحمد وهو علي كل شيء قدير الحمد لله", find_to="ولا قوه الا بالله", find=None,
    ctxAr="من تعارّ من الليل فقال ذلك، ثم قال: «اللهم اغفر لي» أو دعا استجيب له، فإن توضأ وصلى قبلت صلاته.",
    ctxEn="Whoever wakes at night and says it, then asks forgiveness or supplicates, is answered; if he makes wudu and prays, his prayer is accepted.",
    practices=("waking",))

# ---- After the prayer ----
add("astaghfirullah_salam", "after_prayer", "الاستغفار ثلاثًا واللهم أنت السلام", "Seeking forgiveness three times, then “O Allah, You are Peace”", "muslim:591a",
    find="اللهم انت السلام ومنك السلام تباركت ذا الجلال والاكرام", count=1,
    ctxAr="كان رسول الله ﷺ إذا انصرف من صلاته استغفر ثلاثًا وقال: «اللهم أنت السلام…». قال الوليد: فقلت للأوزاعي: كيف الاستغفار؟ قال: تقول: أستغفر الله، أستغفر الله.",
    ctxEn="On finishing his prayer he ﷺ asked forgiveness three times, then said “O Allah, You are Peace…”. Al-Awza'i explained the forgiveness: “Say: Astaghfirullah, astaghfirullah.”",
    practices=("after_prayer",))
add("la_mania", "after_prayer", "اللهم لا مانع لما أعطيت", "O Allah, none can withhold what You give", "bukhari:844",
    find_from="لا اله الا الله وحده لا شريك له له الملك وله الحمد وهو علي كل شيء قدير اللهم لا مانع لما اعطيت", find_to="ولا ينفع ذا الجد منك الجد", find=None,
    ctxAr="كان النبي ﷺ يقوله في دبر كل صلاة مكتوبة.",
    ctxEn="The Prophet ﷺ said it after every obligatory prayer.",
    practices=("after_prayer",))
add("lahu_nimah", "after_prayer", "لا إله إلا الله ولا نعبد إلا إياه", "There is no god but Allah, and we worship none but Him", "muslim:594a",
    find_from="لا اله الا الله وحده لا شريك له له الملك وله الحمد وهو علي كل شيء قدير لا حول ولا قوه الا بالله", find_to="ولو كره الكافرون", find=None,
    ctxAr="كان ابن الزبير يقولهن في دبر كل صلاة حين يسلم، وقال: كان رسول الله ﷺ يهلل بهن دبر كل صلاة.",
    ctxEn="Ibn az-Zubayr said them after each prayer, saying the Messenger of Allah ﷺ did so.",
    practices=("after_prayer",))
add("tasbih_33", "after_prayer", "التسبيح والتحميد والتكبير ثلاثًا وثلاثين", "Tasbih, tahmid and takbir thirty-three times each", "muslim:597a",
    find="من سبح الله في دبر كل صلاه ثلاثا وثلاثين", count=33,
    ctxAr="سبحان الله ٣٣، والحمد لله ٣٣، والله أكبر ٣٣، وتمام المائة: «لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير»؛ غفرت خطاياه وإن كانت مثل زبد البحر.",
    ctxEn="33 each, completing a hundred with the tahlil; sins are forgiven even if like the foam of the sea.",
    practices=("after_prayer",))
add("ainni_ala_dhikrik", "after_prayer", "اللهم أعني على ذكرك وشكرك وحسن عبادتك", "O Allah, help me to remember You", "abudawud:1522",
    find="اللهم اعني علي ذكرك وشكرك وحسن عبادتك",
    ctxAr="أوصى النبي ﷺ معاذًا ألا يدع أن يقول ذلك في دبر كل صلاة.",
    ctxEn="The Prophet ﷺ advised Muadh never to leave saying it after every prayer.",
    practices=("after_prayer",))
add("muawwidhat_after_prayer", "after_prayer", "المعوذات دبر كل صلاة", "The protective surahs after each prayer", "abudawud:1523",
    find="امرني رسول الله صلي الله عليه وسلم ان اقرا بالمعوذات دبر كل صلاه", drop="the narration names no surahs; which ones are meant is a matter of interpretation",
    ctxAr="قراءة الإخلاص والفلق والناس بعد كل صلاة.",
    ctxEn="Reciting al-Ikhlas, al-Falaq and an-Nas after every prayer.",
    practices=("after_prayer",))

# ---- In the prayer ----
add("istiftah_subhanaka", "in_prayer", "دعاء الاستفتاح: سبحانك اللهم وبحمدك", "Opening supplication: Glory be to You, O Allah", "tirmidhi:243",
    find="سبحانك اللهم وبحمدك وتبارك اسمك وتعالي جدك ولا اله غيرك",
    ctxAr="كان النبي ﷺ إذا افتتح الصلاة قال ذلك.",
    ctxEn="The Prophet ﷺ said it when he began the prayer.")
add("istiftah_baid", "in_prayer", "دعاء الاستفتاح: اللهم باعد بيني وبين خطاياي", "Opening supplication: O Allah, distance me from my sins", "bukhari:744",
    find_from="اللهم باعد بيني وبين خطاياي", find_to="بالماء والثلج والبرد", find=None,
    ctxAr="كان رسول الله ﷺ يسكت بين التكبير وبين القراءة إسكاتة يقول فيها ذلك.",
    ctxEn="He ﷺ paused between the takbir and the recitation and said it.")
add("ruku_sujud", "in_prayer", "سبحان ربي العظيم وسبحان ربي الأعلى", "Glory be to my Lord the Almighty, the Most High", "muslim:772",
    find="سبحان ربي العظيم", count=0,
    count_note="لم يُذكر في هذه الرواية عدد، وكان ركوعه وسجوده ﷺ طويلين قريبًا من قيامه.",
    ctxAr="في الركوع: «سبحان ربي العظيم»، وفي السجود: «سبحان ربي الأعلى».",
    ctxEn="In bowing: “Subhana rabbiya al-azim”; in prostration: “Subhana rabbiya al-a'la”.")
add("subhanaka_rabbana", "in_prayer", "سبحانك اللهم ربنا وبحمدك اللهم اغفر لي", "Glory be to You, O Allah our Lord, forgive me", "bukhari:794",
    find="سبحانك اللهم ربنا وبحمدك اللهم اغفر لي",
    ctxAr="كان النبي ﷺ يقول في ركوعه وسجوده ذلك.",
    ctxEn="The Prophet ﷺ said it in his bowing and prostration.")
add("tashahhud", "in_prayer", "التشهد", "The tashahhud", "bukhari:831",
    find_from="التحيات لله والصلوات والطيبات", find_to="واشهد ان محمدا عبده ورسوله", find=None,
    remove=("فانكم اذا قلتموها اصابت كل عبد لله صالح في السماء والارض",),
    ctxAr="قال النبي ﷺ: «فإذا صلى أحدكم فليقل…»، وقال عن «السلام علينا وعلى عباد الله الصالحين»: «فإنكم إذا قلتموها أصابت كل عبد لله صالح في السماء والأرض».",
    ctxEn="“When one of you prays, let him say…”; of the greeting upon the righteous he said it reaches every righteous servant in heaven and earth.")
add("salat_ibrahimiyya", "in_prayer", "الصلاة الإبراهيمية", "Blessings upon the Prophet in the prayer", "bukhari:3370",
    find_from="اللهم صل علي محمد وعلي ال محمد كما صليت علي ابراهيم", find_to="كما باركت علي ابراهيم وعلي ال ابراهيم انك حميد مجيد", find=None,
    ctxAr="لما سئل النبي ﷺ: كيف الصلاة عليكم أهل البيت؟ علّمهم ذلك.",
    ctxEn="When asked how to send blessings upon his household, he ﷺ taught them this.")
add("before_salam_four", "in_prayer", "الاستعاذة من أربع قبل السلام", "Seeking refuge from four things before the salam", "muslim:588a",
    find="اللهم اني اعوذ بك من عذاب جهنم ومن عذاب القبر ومن فتنه المحيا والممات ومن شر فتنه المسيح الدجال",
    ctxAr="قال رسول الله ﷺ: «إذا تشهد أحدكم فليستعذ بالله من أربع».",
    ctxEn="“When one of you says the tashahhud, let him seek refuge in Allah from four things.”")
add("zalamtu_nafsi", "in_prayer", "اللهم إني ظلمت نفسي ظلمًا كثيرًا", "O Allah, I have wronged myself greatly", "bukhari:834",
    patch=(("كَثِيرًا \ufffd\ufffdَلاَ", "كَثِيرًا وَلاَ"),),
    find="اللهم اني ظلمت نفسي ظلما كثيرا ولا يغفر الذنوب الا انت فاغفر لي مغفره من عندك وارحمني انك انت الغفور الرحيم",
    ctxAr="علّمه النبي ﷺ أبا بكر رضي الله عنه ليدعو به في صلاته.",
    ctxEn="Taught to Abu Bakr to supplicate with in his prayer.")

# ---- Adhan, wudu, mosque ----
add("after_wudu", "adhan_wudu_masjid", "الشهادتان بعد الوضوء", "The testimony after wudu", "muslim:234b",
    find="اشهد ان لا اله الا الله وحده لا شريك له واشهد ان محمدا عبده ورسوله",
    ctxAr="من توضأ فأحسن الوضوء ثم قال ذلك فتحت له أبواب الجنة الثمانية يدخل من أيها شاء.",
    ctxEn="Whoever makes wudu well then says it, the eight gates of Paradise are opened for him.")
add("dua_after_adhan", "adhan_wudu_masjid", "الدعاء بعد الأذان", "Supplication after the adhan", "bukhari:614",
    find="اللهم رب هذه الدعوه التامه والصلاه القايمه ات محمدا الوسيله والفضيله وابعثه مقاما محمودا الذي وعدته",
    ctxAr="من قال حين يسمع النداء ذلك حلت له شفاعتي يوم القيامة.",
    ctxEn="Whoever says it on hearing the call, intercession becomes due for him on the Day of Resurrection.")
add("masjid_enter_exit", "adhan_wudu_masjid", "دعاء دخول المسجد والخروج منه", "Entering and leaving the mosque", "muslim:713a",
    find="اللهم افتح لي ابواب رحمتك",
    ctxAr="إذا دخل أحدكم المسجد فليقل: «اللهم افتح لي أبواب رحمتك»، وإذا خرج فليقل: «اللهم إني أسألك من فضلك».",
    ctxEn="On entering: “O Allah, open for me the gates of Your mercy”; on leaving: “O Allah, I ask You of Your bounty”.")

# ---- Home, restroom, clothing ----
add("enter_khala", "home", "دعاء دخول الخلاء", "Entering the restroom", "bukhari:142",
    find="اللهم اني اعوذ بك من الخبث والخبايث",
    ctxAr="كان النبي ﷺ إذا دخل الخلاء قال ذلك.",
    ctxEn="The Prophet ﷺ said it when entering the restroom.")
add("exit_khala", "home", "دعاء الخروج من الخلاء: غفرانك", "Leaving the restroom", "abudawud:30",
    find="غفرانك",
    ctxAr="كان النبي ﷺ إذا خرج من الغائط قال: «غفرانك».",
    ctxEn="The Prophet ﷺ said “I ask Your forgiveness” on leaving the restroom.")
add("leave_home", "home", "دعاء الخروج من المنزل", "Leaving home", "abudawud:5095",
    find="بسم الله توكلت علي الله لا حول ولا قوه الا بالله",
    ctxAr="إذا خرج الرجل من بيته فقال ذلك، يقال حينئذ: هديت وكفيت ووقيت، فتتنحى له الشياطين.",
    ctxEn="Whoever says it on leaving home is told: you are guided, sufficed and protected.")
add("new_garment", "home", "دعاء لبس الثوب الجديد", "Wearing a new garment", "abudawud:4020",
    find_from="اللهم لك الحمد انت كسوتنيه", find_to="وشر ما صنع له", find=None,
    ctxAr="كان رسول الله ﷺ إذا استجد ثوبًا سماه باسمه ثم يقول ذلك.",
    ctxEn="When he ﷺ wore a new garment, he named it and said this.")

# ---- Food ----
add("bismillah_food", "food", "التسمية على الطعام", "Saying bismillah before eating", "abudawud:3767",
    find="بسم الله اوله واخره",
    ctxAr="إذا أكل أحدكم فليذكر اسم الله تعالى، فإن نسي أن يذكر اسم الله في أوله فليقل: «بسم الله أوله وآخره».",
    ctxEn="Mention Allah's name when eating; if you forget at the start, say “In the name of Allah at its beginning and end”.")
add("after_food", "food", "الحمد بعد الطعام", "Praise after eating", "tirmidhi:3458",
    find="الحمد لله الذي اطعمني هذا ورزقنيه من غير حول مني ولا قوه",
    ctxAr="من أكل طعامًا ثم قال ذلك غفر له ما تقدم من ذنبه.",
    ctxEn="Whoever eats and then says it, his previous sins are forgiven.")
add("dua_for_host", "food", "دعاء الضيف لأهل الطعام", "The guest's supplication for the host", "muslim:2042",
    find="اللهم بارك لهم في ما رزقتهم واغفر لهم وارحمهم", drop="a supplication for one occasion; keeps the library at 54",
    ctxAr="دعا به النبي ﷺ لمن أطعمه.",
    ctxEn="The Prophet ﷺ supplicated with it for those who fed him.")

# ---- Travel ----
add("travel", "travel", "دعاء السفر", "Supplication for travel", "muslim:1342",
    find_from="سبحان الذي سخر لنا هذا وما كنا له مقرنين", find_to="وسوء المنقلب في المال والاهل", find=None,
    ctxAr="كان رسول الله ﷺ إذا استوى على بعيره خارجًا إلى سفر كبّر ثلاثًا ثم قال ذلك، وإذا رجع قالهن وزاد: «آيبون تائبون عابدون لربنا حامدون».",
    ctxEn="On mounting for a journey he ﷺ said Allahu akbar three times, then this; on return he added “Returning, repenting, worshipping, praising our Lord.”")
add("stopping_place", "travel", "دعاء النزول في منزل", "Stopping at a place", "muslim:2708a",
    find="اعوذ بكلمات الله التامات من شر ما خلق",
    ctxAr="من نزل منزلًا ثم قال ذلك لم يضره شيء حتى يرتحل من منزله ذلك.",
    ctxEn="Whoever stops at a place and says it, nothing harms him until he leaves it.")

# ---- Distress ----
add("dua_karb", "distress", "دعاء الكرب", "Supplication in distress", "bukhari:6346",
    find="لا اله الا الله العظيم الحليم لا اله الا الله رب العرش العظيم لا اله الا الله رب السموات ورب الارض ورب العرش الكريم",
    ctxAr="كان النبي ﷺ يدعو به عند الكرب.",
    ctxEn="The Prophet ﷺ supplicated with it in times of distress.")
add("hamm_hazan", "distress", "الاستعاذة من الهم والحزن", "Seeking refuge from worry and grief", "bukhari:6369",
    find="اللهم اني اعوذ بك من الهم والحزن والعجز والكسل والجبن والبخل وضلع الدين وغلبه الرجال",
    ctxAr="كان النبي ﷺ يقول ذلك.",
    ctxEn="The Prophet ﷺ used to say it.")
add("dhun_nun", "distress", "دعوة ذي النون", "The supplication of Yunus", "tirmidhi:3505",
    find="لا اله الا انت سبحانك اني كنت من الظالمين",
    ctxAr="دعوة ذي النون إذ دعا وهو في بطن الحوت؛ لم يدع بها رجل مسلم في شيء قط إلا استجاب الله له.",
    ctxEn="The supplication of Dhun-Nun in the whale; no Muslim supplicates with it for anything but Allah answers him.")
add("hasbunallah", "distress", "حسبنا الله ونعم الوكيل", "Allah is sufficient for us", "bukhari:4563",
    find="حسبنا الله ونعم الوكيل",
    ctxAr="قالها إبراهيم عليه السلام حين ألقي في النار، وقالها محمد ﷺ حين قالوا: إن الناس قد جمعوا لكم.",
    ctxEn="Said by Ibrahim when thrown into the fire, and by Muhammad ﷺ when told the people had gathered against them.")
add("rahmataka_arju", "distress", "اللهم رحمتك أرجو", "O Allah, it is Your mercy I hope for", "abudawud:5090",
    find="اللهم رحمتك ارجو فلا تكلني الي نفسي طرفه عين واصلح لي شاني كله لا اله الا انت", drop="one grader (Zubair Ali Zai) grades it weak",
    ctxAr="دعوات المكروب.",
    ctxEn="The supplications of the one in distress.")

# ---- Remembrance, forgiveness and supplication ----
add("kalimatan", "dhikr", "سبحان الله العظيم، سبحان الله وبحمده", "Two words light on the tongue", "bukhari:6406",
    find="سبحان الله العظيم سبحان الله وبحمده",
    ctxAr="كلمتان خفيفتان على اللسان، ثقيلتان في الميزان، حبيبتان إلى الرحمن.",
    ctxEn="Two words light on the tongue, heavy on the scale, beloved to the Most Merciful.")
add("hawqala", "dhikr", "لا حول ولا قوة إلا بالله", "There is no might nor power except with Allah", "bukhari:6384",
    find="لا حول ولا قوه الا بالله",
    ctxAr="كنز من كنوز الجنة.",
    ctxEn="A treasure among the treasures of Paradise.")
add("istighfar_70", "dhikr", "أستغفر الله وأتوب إليه", "I seek Allah's forgiveness and repent to Him", "bukhari:6307",
    find="استغفر الله واتوب اليه", count=0, count_note="أكثر من سبعين مرة في اليوم.",
    ctxAr="قال رسول الله ﷺ: «والله إني لأستغفر الله وأتوب إليه في اليوم أكثر من سبعين مرة».",
    ctxEn="“By Allah, I seek forgiveness and repent to Him more than seventy times a day.”")
add("baqiyat", "dhikr", "سبحان الله والحمد لله ولا إله إلا الله والله أكبر", "The four most beloved words", "muslim:2137",
    find="سبحان الله والحمد لله ولا اله الا الله والله اكبر",
    ctxAr="أحب الكلام إلى الله أربع، لا يضرك بأيهن بدأت.",
    ctxEn="The most beloved words to Allah are four; it does not matter which you begin with.")
add("rabbighfir_100", "dhikr", "رب اغفر لي وتب علي", "My Lord, forgive me and accept my repentance", "abudawud:1516",
    find="رب اغفر لي وتب علي انك انت التواب الرحيم", count=100,
    ctxAr="كنا نعد لرسول الله ﷺ في المجلس الواحد مائة مرة.",
    ctxEn="It was counted for the Messenger of Allah ﷺ a hundred times in one sitting.")
add("rabbana_atina", "dhikr", "ربنا آتنا في الدنيا حسنة", "Our Lord, give us good in this world", "bukhari:6389",
    find="اللهم ربنا اتنا في الدنيا حسنه وفي الاخره حسنه وقنا عذاب النار",
    ctxAr="كان أكثر دعاء النبي ﷺ.",
    ctxEn="It was the Prophet's ﷺ most frequent supplication.")
add("afuww", "dhikr", "اللهم إنك عفو تحب العفو فاعف عني", "O Allah, You are Pardoning and love to pardon", "ibnmajah:3850",
    find="اللهم انك عفو تحب العفو فاعف عني", drop="one grader (Zubair Ali Zai) grades it weak",
    ctxAr="علّمه النبي ﷺ عائشة رضي الله عنها لتقوله إن علمت ليلة القدر.",
    ctxEn="Taught to Aishah to say if she knew which night was Laylat al-Qadr.")
