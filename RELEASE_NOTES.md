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
