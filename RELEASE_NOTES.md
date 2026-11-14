0.9.1: fixes the build (the trial build type was renamed from "test" to "trial", and resource values are enabled for it).
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
