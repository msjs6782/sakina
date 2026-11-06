# الرسوم: لوحات السماء

لوحات السماء الست في `app/src/main/res/drawable-nodpi/sky_*.webp` مولّدة بخدمة Higgsfield (نموذج GPT Image 2.5،
جودة عالية، 1344×752) بأسلوب واحد: رسم ناعم لمدينة صحراوية هادئة بقباب ومآذن بعيدة، دون نص أو أشخاص. لكل وقت
صورة بنفس التكوين، استُعملت لوحة المغرب الأولى مرجعًا للأسلوب.

| الوقت | الملف | المعرّف في Higgsfield |
|---|---|---|
| الفجر | `sky_fajr` | `59863dcf-eec3-4ad9-95a3-4cd0345f129b` |
| الضحى | `sky_morning` | `4396a3d2-6af0-4b91-aef8-09baed935182` |
| الظهر | `sky_noon` | `f60a2bf5-363b-4dc8-b789-a65fdc3eb9d3` |
| العصر | `sky_asr` | `d1259ace-5e52-484d-b8eb-184f556f217a` |
| المغرب | `sky_maghrib` | `979b9d8d-08e4-4c14-be6a-f213c74cc396` |
| الليل | `sky_night` | `d1ac059f-0c6a-4c8c-ae0f-0c7082e3d0d7` |

المرجع: `45953756-0302-4254-986a-e0074bdc2a9d`. الأصول PNG في فرع `asset-results` (مجلد `hf/`).

`tools/art/prepare.py` يحوّلها إلى WebP، ويقصّ لكل منها شريطًا 3:1 من الأسفل للويدجت العريضة، ويحسب الطبقة الداكنة
التي تحفظ تباين النص الأبيض 4.5:1 فوق الجزء الذي يغطيه النص (القيم في `ui/theme/SkyArt.kt`).

نص الطلب (المغرب، مع تغيير وصف الوقت لكل صورة):

> Using the reference image as the guide for style and composition, paint the same calm Arabian desert town with the
> same distant mosque domes and slender minarets small along the lower right and the same low dunes, in the same soft
> painterly style with gentle haze and minimal detail, at MAGHRIB just after sunset … Keep the large calm open sky
> across the upper part. No text, no letters, no calligraphy, no people, no birds, no watermark.

## المجموعة الثانية: رؤوس الشاشات والأيقونة وخلفيات الويدجت

بالنموذج نفسه (GPT Image 2.5، جودة عالية)، والرؤوس الأربعة بمرجع الأسلوب نفسه. تحوّلها `tools/art/prepare_more.py`.

| الملف | الاستعمال | المعرّف في Higgsfield |
|---|---|---|
| `art_quran` | رأس تبويب القرآن (مصحف على رحل عند نافذة الفجر) | `dad3adfa-c276-40d3-bd68-155a6cbdde08` |
| `art_adhkar` | رأس مكتبة الأذكار (صحن مسجد ومسبحة وقنديل) | `14f161dc-1ac6-4886-a28a-8fd74d843719` |
| `art_calendar` | خلفية اسم الشهر في التقويم (هلال فوق المدينة ليلًا) | `f15ac0f4-26fc-499d-b4e6-b6cbae3db4c1` |
| `art_welcome` | صفحة البداية (المدينة قبيل الغروب، طولية) | `3c0f19d9-3ef8-42c9-ac85-96cfe87f568a` |
| `ic_launcher_art` | خلفية أيقونة التطبيق (قبة ومئذنة وهلال)؛ طبقة monochrome للأيقونات الملوّنة بالثيم | `efd5758d-c28c-4144-bdfd-091f79e49bcc` |
| `tex_geometric` | خلفية ويدجت: زخرفة هندسية | `833446bf-582f-4738-b9e7-f418a59d4157` |
| `tex_paper` | خلفية ويدجت: ورق قديم | `770cdb8e-2eb4-489f-b66e-2dbb76a2c334` |
| `tex_night` | خلفية ويدجت: شبكة نجوم ذهبية على كحلي | `6b9d2b74-27c0-4bd8-9612-2781e6ec40cc` |

مفتاح «الرسومات» في المظهر يطفئ الرؤوس مع بطاقة الصلاة.

## المجموعة الثالثة: خلفيات الويدجت المجسّمة

بنموذج GPT Image 2.6 (جودة عالية، 2K). المشهد صورة أساس للمغرب، والأوقات الخمسة الأخرى تعديلات عليها بمرجعها
(«المشهد نفسه والكاميرا نفسها، ويتغيّر الوقت فقط»). تحوّلها `tools/art/prepare_3d.py` وتطبع طبقة الوضوح لكل وقت.

| الملف | الاستعمال | المعرّف في Higgsfield |
|---|---|---|
| `scene_maghrib` | مشهد مجسّم: مسجد ونخيل وكثبان وواحة، المغرب (الأساس) | `63b19fd1-fd8b-4dc9-8e82-7bc83df0abfe` |
| `scene_fajr` | المشهد نفسه، الفجر | `e9b1ffa4-9e96-4919-bc8e-19a43df825e4` |
| `scene_morning` | المشهد نفسه، الضحى | `89f96a8c-b20e-4d02-9271-cc84c63266dd` |
| `scene_noon` | المشهد نفسه، الظهر | `b6500715-41ad-46e6-99e3-e63c4063fa48` |
| `scene_asr` | المشهد نفسه، العصر | `24187a96-87f5-4a67-b198-449f680fd198` |
| `scene_night` | المشهد نفسه، الليل | `ffa2aff8-eb10-486f-b6ce-b1bd4c843b93` |
| `tex_ivory` | عاج منقوش بزخرفة نجمية | `aeed08e3-0a86-4645-9e21-7df67ea58477` |
| `tex_emerald` | شبكة ذهبية بارزة على زمرّد | `a3ee9939-f603-4b0a-922c-6851e0b6d65f` |
| `tex_marble` | رخام أبيض بعروق ذهبية | `6af68a12-6fb3-40ee-a81f-559275ca459d` |
| `tex_marble_navy` | رخام كحلي بعروق ذهبية | `44e43181-8e49-4d53-8a01-1071e1fe45be` |

ولكل وقت من المشهد شريط 3:1 من أسفله (`scene_*_wide`) للويدجت العريضة، كما في لوحات السماء.
