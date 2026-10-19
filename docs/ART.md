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
