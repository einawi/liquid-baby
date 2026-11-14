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
