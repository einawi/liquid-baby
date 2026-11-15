0.9.11 (build 78): warning for 720p with a high frame rate; test folder ignored by git.
- Choosing 720p together with 20 fps or more shows a notice: the picture may stutter on this phone, so 15 fps gives a smoother picture.
- The test folder is no longer added to the repository by mistake.

0.9.11 (build 78): تحذير عند 720p مع معدل فريمات عالٍ، وتجاهل مجلد الاختبار في git.
- اختيار 720p مع 20 فريم أو أكثر يعرض تنبيهاً: الصورة قد تتقطّع على هذا الجوال، و15 فريم أسلس.
- مجلد الاختبار ما يدخل المستودع بالخطأ.

0.9.10 (build 77): the largest accepted message is now 3 MB (was 1.5 MB), for larger pictures.

0.9.10 (build 77): أكبر رسالة مقبولة صارت 3 MB (كانت 1.5 MB)، لصور أكبر.

0.9.9 (build 76): stopping a recording no longer stalls the app.
- The picture and sound feeds no longer wait for the recorder while it closes; they skip the work at once.
- Closing runs under its own lock, so the live connection keeps reading during the save.

0.9.9 (build 76): إيقاف التسجيل ما يعود يوقف التطبيق.
- مجاري الصورة والصوت ما تنتظر المسجّل أثناء الإغلاق؛ تتجاوز العمل فوراً.
- الإغلاق يعمل بقفل خاص، فيبقى الاتصال المباشر يقرأ أثناء الحفظ.

0.9.8 (build 75): stopping a recording gives immediate feedback and saves in the background.
- A message appears at once when you stop; saving and encryption run on low-priority background threads.
- Diagnostics records how long stopping and encryption took, to confirm the cause if a pause remains.

0.9.8 (build 75): إيقاف التسجيل يعطي تأكيداً فورياً ويحفظ في الخلفية.
- رسالة تظهر فوراً عند الإيقاف، والحفظ والتشفير يعملان في خيوط خلفية منخفضة الأولوية.
- التشخيص يسجّل مدة الإيقاف والتشفير، لتأكيد السبب إذا بقي أي توقف.

0.9.7 (build 74): the save button in the player uses a download icon instead of the quality icon.

0.9.7 (build 74): زر الحفظ في المشغّل صار أيقونة تنزيل بدل أيقونة الجودة.

0.9.6 (build 73): visible touch feedback on every button.
- Every button shows a ripple and a small press shrink when touched, so you can tell whether a tap was received.
- Applies to the main screen, camera, full screen, recordings, videos, and the player. The talk button shrinks while pressed.

0.9.6 (build 73): تأثير بصري عند لمس أي زر.
- كل زر يظهر تموّجاً وانكماشاً بسيطاً عند اللمس، فتعرف إذا وصلت اللمسة.
- يشمل الشاشة الرئيسية، والكاميرا، وملء الشاشة، والتسجيلات، والفيديوهات، والمشغّل. زر التحدث ينكمش أثناء الضغط.

0.9.5 (build 72): a better video player.
- Play and pause buttons, a seek bar, and current and total time.
- The picture keeps its proportions; loading indicator while the video opens.
- The screen stays on only while a video plays, and playback pauses when you leave.
- Share and save buttons on the player screen.

0.9.5 (build 72): مشغّل فيديو أفضل.
- أزرار تشغيل وإيقاف، وشريط تقدّم، والوقت الحالي والكلي.
- الصورة تحافظ على نسبها، ومؤشر تحميل أثناء فتح الفيديو.
- الشاشة تبقى مضاءة فقط أثناء التشغيل، ويتوقف الفيديو عند الخروج.
- أزرار مشاركة وحفظ داخل شاشة المشغّل.

0.9.4 (build 71): steadier connection after a Wi-Fi or router change.
- Fixed: when the parent restarted its connection, the old connection loop kept running next to the new one, so two connections kept replacing each other and dropping every few seconds.
- Each restart now replaces the old loop cleanly.

0.9.4 (build 71): اتصال أثبت بعد تغيّر الواي فاي أو الراوتر.
- تم إصلاح: عند إعادة بدء اتصال الأب، كانت الحلقة القديمة تستمر مع الجديدة، فيتبادل الاتصالان الطرد كل بضع ثوانٍ.
- كل إعادة بدء تستبدل الحلقة القديمة بشكل نظيف.

0.9.3 (build 70): recorded video works again (no more black video), and a crash when the child service stops is fixed.
- Frames are written using the encoder's own layout, so every picture reaches the video.
- Fixed a crash when the child phone's monitoring is stopped before it started.
- Pairing and reconnecting through the network announcement are confirmed working.

0.9.3 (build 70): الفيديو المسجّل يشتغل من جديد (بدون شاشة سوداء)، وإصلاح انهيار عند إيقاف خدمة الطفل.
- الصور تُكتب حسب تخطيط المرمّز نفسه، فتدخل كل الصور الفيديو.
- إصلاح انهيار عند إيقاف مراقبة الطفل قبل أن تبدأ.
- الإقران وإعادة الاتصال عبر إعلان الشبكة يعملان.

0.9.2 (build 69): fixes the striped, distorted recorded video.
- Cause: the recorder wrote each frame as one block, but the encoder's rows can be wider than the picture, so every row slid into the next.
- Fixed: each row is written at the encoder's own row layout.

0.9.2 (build 69): إصلاح الفيديو المسجّل المقطّع والمشوّه.
- السبب: كان المسجّل يكتب الصورة كتلة واحدة، لكن صفوف المرمّز أعرض من الصورة، فتتراكب الصفوف.
- الإصلاح: كل صف يُكتب حسب تخطيط المرمّز نفسه.

0.9.2 (build 68): pairing also finds the child through the network announcement.
- The pairing screen lists child phones found by the broadcast search and by the network announcement, and logs both results.

0.9.2 (build 68): الإقران يجد الطفل أيضاً عبر إعلان الشبكة.
- شاشة الإقران تعرض جوالات الأطفال من البحث بالبث ومن إعلان الشبكة، وتسجّل نتيجة كل منهما.

0.9.2 (build 67): the parent logs the result of the network announcement search.
- Diagnostics shows "Network announcement found the child phone at ..." or "not announced", so we can confirm it works.

0.9.2 (build 67): الأب يسجّل نتيجة البحث بإعلان الشبكة.
- التشخيص يعرض "وجد جوال الطفل عند ..." أو "غير معلن"، للتأكد أنه يشتغل.

0.9.2 (build 66): the most reliable way to find the child phone after its address changes.
- The child phone announces itself on the network under its identity (Android's built-in service discovery).
- The parent looks it up by that identity and gets its current address. Order: last known addresses, then the network announcement, then the broadcast search. Every device is verified by its key before it is used.

0.9.2 (build 66): أكثر طريقة استقراراً لإيجاد جوال الطفل بعد تغيّر عنوانه.
- جوال الطفل يعلن نفسه على الشبكة بهويته (خدمة الاكتشاف المدمجة في أندرويد).
- الأب يبحث عنه بهويته ويأخذ عنوانه الحالي. الترتيب: آخر العناوين المعروفة، ثم إعلان الشبكة، ثم البحث بالبث. وكل جهاز يُتحقق منه بمفتاحه قبل الاستخدام.

0.9.2 (build 65): reconnects after the child phone's address or network changes, without re-pairing.
- The parent tries the child's last addresses, but accepts a device only if it proves it is the paired child. Any other device at a reused address is skipped and forgotten.
- If none of the addresses match, the parent searches the network for the paired child by its identity.
- Re-pairing is needed only if the pairing keys themselves were removed.

0.9.2 (build 65): إعادة الاتصال بعد تغيّر عنوان جوال الطفل أو الشبكة، بدون إعادة الاقتران.
- الأب يجرّب آخر عناوين الطفل، ويقبل الجهاز فقط إذا أثبت أنه الطفل المقترن. أي جهاز آخر على عنوان مُعاد استخدامه يُتجاوز وينحذف.
- إذا ما تطابقت العناوين، يبحث الأب عن الطفل المقترن في الشبكة بمعرّفه.
- الاقتران يحتاج إعادة فقط إذا انحذفت مفاتيح الاقتران نفسها.

0.9.2 (build 64): recorded video keeps the live picture's shape.
- Recording uses the same size and proportions as the live picture, so the saved video, the gallery and shared copies match the live view.
- If the picture changes shape during recording, it is fitted inside the frame (black edges), never stretched.

0.9.2 (build 64): الفيديو المسجّل يحافظ على شكل الصورة المباشرة.
- التسجيل يأخذ نفس الحجم والنسب الظاهرة في البث المباشر، فيتطابق الفيديو المحفوظ والمعرض والمشاركة.
- إذا تغيّر شكل الصورة أثناء التسجيل، تُوضع داخل الإطار بأطراف سوداء بدل التمدد.

0.9.2 (build 63): fixes black, empty videos.
- Cause: the sound encoder's output was only read at the end, so the video file could not start during recording, and the pictures were dropped (log: 332 pictures in, 1 written).
- Fixed: sound output is read during recording too, so every picture is written.

0.9.2 (build 63): إصلاح الفيديو الأسود والفارغ.
- السبب: مخرجات مرمّز الصوت كانت تُقرأ فقط عند الإيقاف، فالملف ما يبدأ أثناء التسجيل، وتسقط الصور (السجل: 332 صورة دخلت، 1 كُتبت).
- الإصلاح: قراءة مخرجات الصوت أثناء التسجيل أيضاً، فتنكتب كل الصور.

0.9.2 (build 62): recording diagnostics (pictures counted at each stage).
- Diagnostics now shows how many pictures arrived during recording, how many reached the video encoder, and how many were dropped.

0.9.2 (build 62): تشخيص التسجيل (عدّ الصور في كل مرحلة).
- التشخيص يعرض عدد الصور التي وصلت أثناء التسجيل، وكم منها دخل المرمّز، وكم سقط.

0.9.2 (build 61): correct video sizes and recording diagnostics.
- Video sizes show kilobytes under 1 MB (previously a 5-second video showed 0 MB).
- Diagnostics now records frames written, sound packets, file size, video dimensions, and playback errors.

0.9.2 (build 61): حجم الفيديو يظهر بشكل صحيح، وتشخيص أدق للتسجيل.
- الأحجام أقل من 1 MB تظهر بالكيلوبايت (كان فيديو 5 ثوانٍ يظهر 0 MB).
- التشخيص يسجّل الفريمات المكتوبة، وحزم الصوت، وحجم الملف، وأبعاد الفيديو، وأخطاء التشغيل.

0.9.2 (build 60): fixes empty recordings, adds sharing.
- Fixed: a recording could be saved empty (0 MB, black picture) when the stop ran out of time. Video and sound now finish together, and an empty file is never saved.
- New: each video has a share button. Share it (for example to WhatsApp), or save it to the gallery as a normal MP4 in Movies/LiquidBaby.
- Empty videos from older builds remain in the list; delete them.

0.9.2 (build 60): إصلاح التسجيلات الفارغة، وإضافة المشاركة.
- تم إصلاح: كان الفيديو يُحفظ فارغاً (0 MB وشاشة سوداء) عند انتهاء وقت الإيقاف. الآن الفيديو والصوت ينتهيان معاً، ولا يُحفظ أي ملف فارغ.
- جديد: زر مشاركة لكل فيديو. شاركه (مثل واتساب)، أو احفظه في المعرض كفيديو MP4 عادي في Movies/LiquidBaby.
- الفيديوهات الفارغة من النسخ السابقة تبقى في القائمة؛ احذفها.

0.9.2 (build 59): you can see and play your saved videos.
- Recordings screen: a new "Videos (encrypted)" section lists saved videos, with play and delete.
- The video plays inside the app. The decrypted copy is kept only while playing and is deleted afterwards.

0.9.2 (build 59): تقدر تشوف وتشغّل الفيديوهات المحفوظة.
- شاشة التسجيلات: قسم جديد "الفيديوهات (مشفّرة)" يعرض الفيديوهات المحفوظة مع تشغيل وحذف.
- الفيديو يشتغل داخل التطبيق. النسخة المفكوكة تبقى فقط أثناء التشغيل وتُحذف بعده.

0.9.2 (build 58): videos are saved encrypted.
- Fixed: recordings stopped before saving because the phone's key does not accept an app-chosen IV. The key now creates it, as intended.
- An unencrypted recording is deleted if encryption fails, so none is left on the phone.

0.9.2 (build 58): الفيديو يُحفظ مشفّراً.
- تم إصلاح: كان التسجيل ينتهي بدون حفظ لأن مفتاح الجوال ما يقبل IV من التطبيق. الآن المفتاح يولّده كما هو مقصود.
- إذا فشل التشفير يُحذف التسجيل الأصلي، فما يبقى فيديو بدون تشفير على الجوال.

0.9.2 (build 57): the recorder stop no longer freezes the app.
- Stopping the recording runs in the background, with a 4-second limit on every wait.
- Build 56 was published before this fix; please update to build 57.

0.9.2 (build 57): إيقاف التسجيل ما يعود يجمّد التطبيق.
- الإيقاف يصير في الخلفية، وكل انتظار بحد أقصى 4 ثوانٍ.
- نسخة 56 نُشرت قبل هذا الإصلاح، فحدّث إلى 57.

0.9.2: video recorder (new, starts only when you press the record button).
- Record button in the camera screen and in full screen. It turns red and shows a stop icon while recording.
- Records the picture as shown (zoom and quality included), room sound while you hear it, and your microphone while you talk.
- Stops when you press the button, when you leave the camera screen (you are asked first), or after 10 minutes. Saved encrypted on the parent phone.
- Recording problems are logged in Diagnostics and do not stop the live view or the connection.
- Fixed: pressing stop no longer freezes the app. Stopping runs in the background, with a 4-second limit.
- New feature, tested on a phone only as far as the build compiles: if you see lag or a crash while recording, tell us in Diagnostics.
- Video list and playback: next version.

0.9.2: مسجّل الفيديو (جديد، يبدأ فقط عند الضغط على زر التسجيل).
- زر التسجيل في شاشة الكاميرا وملء الشاشة. يصير أحمر وتظهر أيقونة إيقاف أثناء التسجيل.
- يسجّل الصورة كما تظهر (مع الزوم والجودة)، وصوت الغرفة عندما تسمعه، وميكروفونك أثناء التحدث.
- يتوقف عند الضغط عليه، أو عند الخروج من شاشة الكاميرا (يسألك أولاً)، أو بعد 10 دقائق. يُحفظ مشفّراً على جوال الأب.
- مشاكل التسجيل تُسجَّل في التشخيص ولا توقف البث المباشر ولا الاتصال.
- تم إصلاح: الضغط على إيقاف التسجيل ما يعود يجمّد التطبيق. الإيقاف يصير في الخلفية بحد أقصى 4 ثوانٍ.
- ميزة جديدة: إذا لاحظت تأخيراً أو توقفاً أثناء التسجيل، اكتب لنا ما صار ومعه سطر التشخيص.
- قائمة الفيديوهات والتشغيل: الإصدار القادم.

Work in progress: video recorder, connected to the record buttons (camera and full screen). Test it with the Trial build workflow, not the public release.
- New: VideoRecorder (H.264 video from the frames on screen, AAC audio mixed by time, MP4 output, 10-minute limit) and VideoVault (chunked AES-GCM encryption, so long videos never load into memory).
- New: GitHub "Trial build" workflow (manual). It builds Liquid Baby Trial and uploads the APK as an artifact. It does not publish a release.
- Connected: record buttons, room sound (when heard), the parent's microphone while talking, stop when leaving, 10-minute limit, encrypted saving.
- Not yet: the video list and playback.

قيد التطوير: مسجّل الفيديو مربوط بأزرار التسجيل (شاشة الكاميرا وملء الشاشة). جرّبه من بناء Trial وليس من الإصدار العام.
- جديد: VideoRecorder (فيديو H.264 من الصور المعروضة، وصوت AAC مختلط حسب الوقت، وملف MP4، وحد 10 دقائق) و VideoVault (تشفير AES-GCM على أجزاء حتى لا تُحمَّل الفيديوهات الطويلة في الذاكرة).
- جديد: سير عمل GitHub "Trial build" يدوياً، يبني Liquid Baby Trial ويرفع الـ APK كملف مرفق، بدون إصدار.
- مربوط: أزرار التسجيل، وصوت الغرفة (عند سماعه)، وميكروفون الأب أثناء التحدث، والإيقاف عند الخروج، وحد 10 دقائق، والحفظ مشفّراً.
- لم يُربط بعد: قائمة الفيديوهات والتشغيل.

0.9.1: fixes the build (the trial build type was renamed from "test" to "trial", resource values are enabled for it, and IS_TEST is defined for every build type).
- Gradle does not accept build type names starting with "test". The trial app is now assembleTrial and installs as Liquid Baby Trial.
- Same features as 0.9.0.

0.9.1: إصلاح البناء (نوع البناء التجريبي صار اسمه trial بدل test).
- Gradle ما يقبل أسماء أنواع البناء التي تبدأ بـ test. التطبيق التجريبي صار assembleTrial ويتثبّت باسم Liquid Baby Trial.
- نفس مميزات 0.9.0.

0.9.0: all current features in one build (no video recording yet).
- Parent: status Stopped, Connecting, Connected, Disconnected; Hold/Tap talk button shared with the camera screen.
- Live camera: quality Low, Normal, 960×540, 720p; frame rate 10 to 30 fps chosen by the parent; night mode icon; room sound mute; flash; switch camera; rotate picture.
- Full screen: separate layer, two-finger zoom (1x to 4x), icon bar for flash, quality, room sound, talk, night mode, frame rate, and record (record shows a message; not built yet).
- Child: 3 most recent clips kept, backlog sent one at a time, encrypted clips on the phone, low-battery mode, Wi-Fi address shown.
- Trial build type (assembleTrial) is included for the recorder work; it does not auto-update.

0.9.0: كل المميزات الحالية في بناء واحد (بدون تسجيل الفيديو بعد).
- الأب: حالات متوقف، جارٍ الاتصال، متصل، انقطع الاتصال؛ زر التحدث بمفتاح ضغط/نقرة مشترك مع شاشة الكاميرا.
- الكاميرا: جودة منخفضة، عادية، 960×540، 720p؛ معدل فريمات من 10 إلى 30 يختاره الأب؛ أيقونة الوضع الليلي؛ كتم صوت الغرفة؛ فلاش؛ تبديل الكاميرا؛ تدوير الصورة.
- ملء الشاشة: طبقة مستقلة، تكبير بإصبعين (من 1x إلى 4x)، وشريط أيقونات للفلاش والجودة وصوت الغرفة والتحدث والوضع الليلي ومعدل الفريمات والتسجيل (التسجيل يعرض رسالة، ولم يُبنَ بعد).
- الطفل: يحتفظ بآخر 3 تسجيلات، يرسل المتراكم واحداً واحداً، وتسجيلات مشفّرة على الجوال، ووضع توفير البطارية، وعرض عنوان الواي فاي.
- نوع البناء التجريبي (assembleTest) موجود للعمل على المسجّل، ولا يتحقق من التحديثات.

0.8.15: separate test app for the video recorder (set up; the recorder itself comes next).
- New build type "test": installs as "Liquid Baby Test" next to the main app (package com.liquid.liquidbaby.test), with the same signing key.
- The test app does not check for updates from the main releases.
- Build it with: gradlew assembleTest  (output in app/build/outputs/apk/test/).

0.8.15: تطبيق تجريبي منفصل لمسجّل الفيديو (الإعداد جاهز، والمسجّل نفسه هو الخطوة القادمة).
- نوع بناء "test": يتثبّت باسم "Liquid Baby Test" بجانب التطبيق الأصلي، بنفس مفتاح التوقيع.
- التطبيق التجريبي ما يتحقق من تحديثات الإصدارات الرسمية.
- البناء: gradlew assembleTest.

0.8.14: full-screen bar fits the screen, Full screen button styled, frame rate up to 30.
- Full-screen icons share the width evenly and are round; none is cut off.
- The Full screen button has the same colour and style as the other buttons.
- Frame rate dropdown and full-screen icon go from 10 to 30 fps. Default stays 15. The A9 may not hold 25 to 30 fps; the measured rate is not shown, so check for lag or drops.

0.8.14: شريط ملء الشاشة يناسب الشاشة، وزر ملء الشاشة بنفس الشكل، ومعدل الفريمات حتى 30.
- أيقونات ملء الشاشة دائرية وبعرض متساوٍ، ولا تُقص أي واحدة.
- زر ملء الشاشة بنفس لون وشكل الأزرار الأخرى.
- القائمة وأيقونة ملء الشاشة من 10 إلى 30 fps. الافتراضي يبقى 15. جوال A9 قد لا يثبت 25 إلى 30 fps.

0.8.13: frame rate chosen by the parent (10 to 20, default 15).
- A dropdown on the camera screen and an icon in full screen set the target pictures per second.
- The child phone paces its pictures from that target. Both phones should run 0.8.13; an older child ignores the setting.

0.8.13: معدل الفريمات يختاره الأب (من 10 إلى 20، والافتراضي 15).
- قائمة منسدلة في شاشة الكاميرا، وأيقونة في ملء الشاشة، لتحديد عدد الصور في الثانية.
- جوال الطفل يضبط الصور حسب هذا الهدف. الجوالان لازم يكونان على 0.8.13؛ الطفل القديم يتجاهل الإعداد.

0.8.12: night-mode icon, shared talk button on the main screen, frame rate targets, measured picture rate.
- Night mode icon next to the camera buttons and in the full-screen bar.
- The main screen uses the same talk control as the camera screen.
- Frame rate targets raised: about 20 fps (Low), 15 fps (Normal and 960×540), 14 fps (720p). The camera screen shows the measured rate in the status line.

0.8.12: أيقونة الوضع الليلي، وزر التحدث المشترك في الشاشة الرئيسية، ومعدل إطارات أعلى.
- أيقونة الوضع الليلي بجانب أزرار الكاميرا وفي شريط ملء الشاشة.
- الشاشة الرئيسية تستخدم نفس زر التحدث في شاشة الكاميرا.
- الأهداف: حوالي 20 إطاراً (منخفضة)، و15 (عادية و960×540)، و14 (720p). شاشة الكاميرا تعرض المعدل الفعلي.

0.8.11: status words, shared talk button, full-screen layer with pinch zoom and icons.
- Parent status: Stopped, Connecting, Connected, Disconnected (with seconds since the drop).
- The talk button on the camera screen is the same control as the main screen (Hold/Tap toggle, glow, labels).
- Full screen: a separate layer that takes every touch. Two fingers zoom (1x to 4x). Icons along the bottom: flash, quality menu, room sound (mute), talk, and record. Long-press an icon to see its name.
- Not in this build yet: video recording itself. The record icon shows a message until the recorder is built.

0.8.11: حالات الاتصال، وزر التحدث المشترك، وملء الشاشة بتكبير بإصبعين وأيقونات.
- حالة الأب: متوقف، جارٍ الاتصال، متصل، انقطع الاتصال (مع عدد الثواني منذ الانقطاع).
- زر التحدث في شاشة الكاميرا هو نفس الزر في الشاشة الرئيسية (مفتاح ضغط/نقرة، توهج، نصوص).
- ملء الشاشة: طبقة مستقلة تستقبل كل اللمس. إصبعان للتكبير (من 1x إلى 4x). أيقونات أسفل الشاشة: الفلاش، قائمة الجودة، صوت الغرفة (كتم)، التحدث، والتسجيل. اضغط مطولاً على أي أيقونة لرؤية اسمها.
- غير موجود بعد: تسجيل الفيديو نفسه. أيقونة التسجيل تعرض رسالة حتى يُبنى المسجّل.

0.8.10: full-screen picture no longer upside down.
- The Rotate button in full screen turns only the picture, in 90-degree steps. The phone's own orientation is not changed.

0.8.10: الصورة في ملء الشاشة ما تنقلب.
- زر التدوير في ملء الشاشة يدوّر الصورة فقط، بخطوات 90 درجة، ولا يغيّر اتجاه الجوال.

0.8.9: live camera quality levels, full screen with microphone, manual rotation.
- Quality: Low, Normal, 960×540, 720p, shown side by side. The chosen one has a tick.
- Full screen button: black screen with the picture, a Back button (top left), a Rotate button (top right), and a microphone (bottom right) that works like the talk button (hold or tap, as set).
- Rotation is manual: the screen turns only when you press Rotate.
- Frame limit raised from 300 KB to 1.5 MB, so 720p frames are accepted.
- After a slow spell, the picture returns to the quality you chose.

0.8.9: مستويات جودة الكاميرا، وملء الشاشة مع الميكروفون، والتدوير اليدوي.
- الجودة: منخفضة، عادية، 960×540، 720p، بجانب بعض، والمختار عليه علامة صح.
- زر ملء الشاشة: شاشة سوداء فيها الصورة، وزر رجوع (أعلى اليسار)، وزر تدوير (أعلى اليمين)، وميكروفون (أسفل اليمين) يعمل مثل زر التحدث (ضغط أو نقرة حسب الإعداد).
- التدوير يدوي: الشاشة تدور فقط عند الضغط على تدوير.
- رفع حد الإطار من 300 KB إلى 1.5 MB، ليقبل إطارات 720p.
- بعد فترة بطء ترجع الصورة للجودة التي اخترتها.

0.8.8: the Hold/Tap toggle and the talk button no longer overlap in touch.
- The talk button is no longer clickable itself. The whole area is handled by its frame, and the small toggle, which is a child, takes touches on its own area first.

0.8.8: مفتاح الضغط/النقرة وزر التحدث ما يتداخلان في اللمس.
- زر التحدث ما عاد يستقبل اللمس بنفسه. المنطقة كلها يديرها الإطار، والمفتاح الصغير (وهو عنصر داخل الإطار) يستقبل لمسه أولاً.

0.8.7: the Hold/Tap toggle no longer triggers the talk button; smoother screen changes.
- Touches on the small toggle stay with the toggle; the big talk button ignores them.
- Each mode change is logged in Diagnostics.
- Screens slide up and fade in (300 ms), and fade out (220 ms).

0.8.7: المفتاح الصغير ما يشغّل زر التحدث الكبير، وانتقال أنعم بين الشاشات.
- اللمس على المفتاح الصغير يبقى له، وزر التحدث الكبير يتجاهله.
- كل تبديل للوضع يُسجَّل في التشخيص.
- الشاشات تصعد وتظهر بتلاشٍ (300 ملّي ثانية)، وتختفي بتلاشٍ (220 ملّي ثانية).

0.8.6: the update screen shows only the newest release notes.
- Notes are cut to the newest version's section, in both languages, when they are shown.

0.8.6: شاشة التحديث تعرض ملاحظات آخر إصدار فقط.
- الملاحظات تُقتطع إلى قسم آخر إصدار (بالإنجليزي والعربي) قبل عرضها.

0.8.5: small Hold/Tap toggle in the corner of the talk button.
- A small slider switch with the label Hold or Tap sits in the top corner of the talk button. The knob slides and the track takes the accent colour in Tap mode.

0.8.5: مفتاح صغير بشكل زر منزلق في زاوية زر التحدث، يتبدّل بين ضغط ونقرة.

0.8.4: the Hold/Tap switch works.
- The switch inside the talk button is now a tappable pill labelled Hold or Tap. Tapping it switches the mode.

0.8.4: مفتاح الضغط/النقرة يعمل.
- المفتاح داخل زر التحدث صار زراً صغيراً قابلاً للنقر، مكتوب عليه ضغط أو نقرة، ويبدّل الوضع عند الضغط عليه.

0.8.3: new Wi-Fi help.
- Child screen shows its Wi-Fi address, so you can type it on the parent phone if discovery is blocked.
- When the child phone is not found, the parent shows the likely causes (Guest mode, AP/client isolation, VPN) and a button to type the address.
- Each scan writes its result to Diagnostics.

0.8.3: مساعدة عند تغيير الواي فاي.
- شاشة الطفل تعرض عنوان الواي فاي، فتقدر تكتبه في جوال الأب إذا كان الاكتشاف محجوباً.
- إذا ما لقى الأب جوال الطفل، يعرض الأسباب المحتملة (وضع الضيوف، عزل الأجهزة AP/Client isolation، الـ VPN) وزر لكتابة العنوان.
- كل بحث يُسجَّل نتيجته في التشخيص.

0.8.2: faster screen changes and more visual effects.
- Pairing data is read once when a screen opens, not every second (no repeated key decryption).
- The recordings list updates only when a recording is added or deleted.
- Returning to a screen no longer rebuilds it unless the language, theme or role changed.
- Settings re-draws in place instead of restarting the activity.
- Screens fade in and out.
- Entrance animation plays once per screen, not on every rebuild.
- Status colour cross-fades; the talk button glows while talking.
- Build time is logged in Diagnostics.

0.8.2: تنقّل أسرع وتأثيرات بصرية أكثر.
- بيانات الاقتران تُقرأ مرة عند فتح الشاشة، وليس كل ثانية.
- قائمة التسجيلات تتحدّث فقط عند إضافة أو حذف تسجيل.
- الرجوع للشاشة ما يعيد بناءها إلا إذا تغيّرت اللغة أو المظهر أو الدور.
- الإعدادات تتحدّث في مكانها بدل إعادة تشغيل الشاشة.
- الشاشات تظهر وتختفي بتلاشٍ ناعم.
- التأثير المتدرج للبطاقات يعمل مرة واحدة لكل شاشة.
- لون الحالة يتحول بسلاسة، وزر التحدث يتوهج أثناء الحديث.
- زمن بناء الشاشة يُسجَّل في التشخيص.

0.8.1: tap-to-talk switch inside the talk button, smoother visuals, faster connection status.
- The tap/hold switch sits inside the talk button (top end).
- Dots and the sound meter glide between values. Buttons show a touch ripple.
- Parent notices a dead link within about 25 seconds, so the green dot turns off sooner. Battery and temperature dots turn grey when the child's status is older than 45 seconds.

0.8.1: مفتاح "اضغط" داخل زر التحدث، وتأثيرات أنعم، وحالة اتصال أسرع.
- مفتاح الضغط/الاستمرار داخل زر التحدث (في الطرف العلوي).
- النقاط ومقياس الصوت تتحرك بسلاسة، والأزرار تظهر تأثير اللمس.
- يكتشف الأب الانقطاع خلال حوالي 25 ثانية، فتنطفئ النقطة الخضراء أسرع. نقاط البطارية والحرارة تصير رمادية إذا كانت حالة الطفل أقدم من 45 ثانية.

0.8.0: correctness, clarity, alerts, recordings, diagnostics, accessibility.

- Child card shows the child's address, not this phone's.
- One set of status words on both screens: Connected, Searching, Stopped.
- Engine messages shown on screen are in the chosen language.
- Settings in three groups: Appearance and language, Alerts, Advanced.
- First-run steps after choosing a role.
- Start/Stop is coloured when it is the action to take.
- Each kind of alert has its own channel (battery, temperature, disconnect), so each can be muted.
- Disconnect alert time: 60, 100 or 180 seconds.
- Recordings grouped by Today, Yesterday, This week, Earlier. Delete older than 1, 7 or 30 days, or delete all, with confirmation. Clearer empty state.
- Diagnostics: filter by All, Errors only, or Connection only. Copy and Share remove lines that mention secrets and long hex strings.
- Clear message when pairing keys cannot be read.
- Touch targets for the play and delete buttons are 48dp.

0.8.0: الصحة والوضوح والتنبيهات والتسجيلات والتشخيص وإمكانية الوصول.
- بطاقة الطفل تعرض عنوان الطفل، وليس عنوان هذا الجوال.
- كلمات حالة واحدة على الشاشتين: متصل، جارٍ البحث، متوقف.
- الرسائل المعروضة على الشاشة باللغة المختارة.
- الإعدادات في ثلاث مجموعات: المظهر واللغة، التنبيهات، متقدم.
- خطوات أولى بعد اختيار الدور.
- زر ابدأ/أوقف بلون واضح عندما يكون هو الإجراء المطلوب.
- لكل نوع تنبيه قناة خاصة (البطارية، الحرارة، الانقطاع) يمكن كتمها.
- وقت تنبيه الانقطاع: 60 أو 100 أو 180 ثانية.
- التسجيلات مجمّعة: اليوم، أمس، هذا الأسبوع، أقدم. حذف الأقدم من يوم أو 7 أو 30، أو حذف الكل، مع تأكيد.
- التشخيص: تصفية الكل، الأخطاء فقط، أو الاتصال فقط. النسخ والمشاركة يحذفان الأسطر التي فيها أسرار.
- رسالة واضحة عند تعذر قراءة مفاتيح الاقتران.
- أزرار التشغيل والحذف بحجم 48dp على الأقل.

0.7.9: tap-to-talk option.

- Parent screen: a small switch above the talk button chooses between "hold to talk" (default) and "tap to talk".
- Tap mode: tap once to start talking, tap again to stop. Talking stops by itself after 60 seconds, and when you leave the screen.

0.7.9: خيار "اضغط للتحدث".
- شاشة الأب: زر صغير فوق زر التحدث يختار بين "اضغط مع الاستمرار" (الافتراضي) و"اضغط للتحدث".
- وضع الضغط: اضغط مرة للبدء، ومرة للإيقاف. يتوقف الحديث وحده بعد 60 ثانية، وعند الخروج من الشاشة.

0.7.8: improvements round.

- Clips are stored encrypted on the phone (key in the Android Keystore). Clips from older builds still play.
- The child phone sends stored clips one at a time, with a pause, and waits while a parent watches live.
- The parent lowers the picture quality automatically when pictures are slow, and restores it after 20 good seconds.
- Child phone log: each disconnect names the parent and the reason, and shows how many clips are waiting.
- Low battery on the child phone (under 20%, not charging) lowers the picture rate. A battery line is logged every 30 minutes.
- Repeated errors (broadcasts, addresses, pictures) are logged at most every 10 to 30 seconds.
- Retention and rate-limit logic moved to small tested helpers (Logic.kt).
- Build workflow for AIY reads the signing key from a secret, not from the repository.

0.7.8: تحسينات متعددة.
- التسجيلات محفوظة مشفّرة على الجوال (المفتاح في Android Keystore). التسجيلات القديمة تشتغل عادي.
- جوال الطفل يرسل التسجيلات المتراكمة واحداً واحداً مع فترة راحة، ويتوقف أثناء المشاهدة المباشرة.
- جوال الأب يخفض جودة الصورة تلقائياً إذا تأخرت، ويرجعها بعد 20 ثانية من الصور الجيدة.
- سجل جوال الطفل: كل انقطاع يذكر اسم الأب والسبب وعدد التسجيلات المنتظرة.
- البطارية المنخفضة (أقل من 20% وغير شاحن) تخفض معدل الصور. يُكتب سطر للبطارية كل 30 دقيقة.
- الأخطاء المتكررة تُكتب مرة كل 10 إلى 30 ثانية كحد أقصى.
- منطق الحفظ وحدود السجل صار في مساعدات صغيرة مختبرة (Logic.kt).
- سير عمل بناء AIY يقرأ مفتاح التوقيع من Secret، ومو من المستودع.
