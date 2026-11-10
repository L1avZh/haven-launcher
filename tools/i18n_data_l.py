# Default Home app status (8 lines per language). Machine-assisted drafts, not native-reviewed.
# Order: home_status home_v_yes home_v_no home_v_na home_d_yes home_d_no home_d_na home_firmware
L = {}
def A(tag, s): L[tag] = s.strip('\n')
A('en', """
Default Home app
Haven
Not set
Unavailable
Haven is selected as the default Home app. Whether this device's Home button actually opens it depends on its firmware.
Haven is not the default Home app. Press OK to choose it in the system.
This device has no Home app chooser. See the installation guide for the manual steps.
Some TV boxes keep opening their own launcher even when another Home app is selected; Haven cannot change that.
""")
A('he', """
אפליקציית הבית כברירת מחדל
Haven
לא נבחר
לא זמין
Haven נבחר כאפליקציית הבית. האם כפתור הבית במכשיר פותח אותו בפועל תלוי בקושחה שלו.
Haven אינו אפליקציית הבית. לחץ OK כדי לבחור בו במערכת.
במכשיר זה אין בורר לאפליקציית הבית. ראה את מדריך ההתקנה לשלבים ידניים.
חלק ממתאמי הטלוויזיה ממשיכים לפתוח את המשגר שלהם גם כשנבחרה אפליקציית בית אחרת; Haven לא יכול לשנות זאת.
""")
A('fr', """
Application d'accueil par défaut
Haven
Non défini
Indisponible
Haven est l'application d'accueil par défaut. Que la touche Accueil de l'appareil l'ouvre réellement dépend de son firmware.
Haven n'est pas l'application d'accueil par défaut. Appuyez sur OK pour la choisir dans le système.
Cet appareil n'a pas de sélecteur d'application d'accueil. Voir le guide d'installation pour les étapes manuelles.
Certains boîtiers TV ouvrent toujours leur propre lanceur, même si une autre application d'accueil est choisie ; Haven ne peut pas y changer.
""")
A('de', """
Standard-Startbildschirm
Haven
Nicht festgelegt
Nicht verfügbar
Haven ist als Standard-Startbildschirm gewählt. Ob die Home-Taste des Geräts ihn tatsächlich öffnet, hängt von dessen Firmware ab.
Haven ist nicht der Standard-Startbildschirm. Drücken Sie OK, um ihn im System zu wählen.
Dieses Gerät hat keine Auswahl für den Startbildschirm. Die manuellen Schritte stehen in der Installationsanleitung.
Manche TV-Boxen öffnen weiterhin ihren eigenen Launcher, auch wenn ein anderer gewählt ist; Haven kann das nicht ändern.
""")
A('es', """
Aplicación de inicio predeterminada
Haven
Sin definir
No disponible
Haven es la aplicación de inicio predeterminada. Que el botón Inicio del dispositivo la abra de verdad depende de su firmware.
Haven no es la aplicación de inicio predeterminada. Pulsa OK para elegirla en el sistema.
Este dispositivo no tiene selector de aplicación de inicio. Consulta la guía de instalación para los pasos manuales.
Algunas cajas de TV siguen abriendo su propio launcher aunque se elija otra aplicación de inicio; Haven no puede cambiarlo.
""")
A('pt', """
Aplicativo de início padrão
Haven
Não definido
Indisponível
O Haven está selecionado como aplicativo de início padrão. Se o botão Início do aparelho o abre de fato depende do firmware.
O Haven não é o aplicativo de início padrão. Pressione OK para escolhê-lo no sistema.
Este aparelho não tem seletor de aplicativo de início. Veja o guia de instalação para os passos manuais.
Algumas TV boxes continuam abrindo o próprio launcher mesmo com outro aplicativo de início; o Haven não pode mudar isso.
""")
A('it', """
App Home predefinita
Haven
Non impostata
Non disponibile
Haven è selezionata come app Home predefinita. Se il tasto Home del dispositivo la apre davvero dipende dal firmware.
Haven non è l'app Home predefinita. Premi OK per sceglierla nel sistema.
Questo dispositivo non ha un selettore dell'app Home. Vedi la guida all'installazione per i passaggi manuali.
Alcuni box TV aprono comunque il proprio launcher anche se è scelta un'altra app Home; Haven non può cambiarlo.
""")
A('nl', """
Standaard startapp
Haven
Niet ingesteld
Niet beschikbaar
Haven is gekozen als standaard startapp. Of de Home-knop van het apparaat hem echt opent, hangt af van de firmware.
Haven is niet de standaard startapp. Druk op OK om hem in het systeem te kiezen.
Dit apparaat heeft geen keuze voor de startapp. Zie de installatiegids voor de handmatige stappen.
Sommige tv-boxen openen steeds hun eigen launcher, ook als een andere startapp is gekozen; Haven kan dat niet veranderen.
""")
A('pl', """
Domyślna aplikacja startowa
Haven
Nie ustawiono
Niedostępne
Haven jest wybrany jako domyślna aplikacja startowa. Czy przycisk Home urządzenia faktycznie ją otwiera, zależy od oprogramowania sprzętowego.
Haven nie jest domyślną aplikacją startową. Naciśnij OK, aby wybrać go w systemie.
To urządzenie nie ma wyboru aplikacji startowej. Kroki ręczne są w przewodniku instalacji.
Niektóre dekodery TV nadal otwierają własny launcher, nawet gdy wybrano inną aplikację; Haven nie może tego zmienić.
""")
A('ru', """
Приложение главного экрана по умолчанию
Haven
Не задано
Недоступно
Haven выбран приложением главного экрана по умолчанию. Откроет ли его кнопка «Домой» устройства, зависит от прошивки.
Haven не является приложением главного экрана по умолчанию. Нажмите OK, чтобы выбрать его в системе.
На этом устройстве нет выбора приложения главного экрана. Ручные шаги описаны в руководстве по установке.
Некоторые ТВ-приставки всё равно открывают свой лаунчер, даже если выбрано другое приложение; Haven не может это изменить.
""")
A('uk', """
Програма головного екрана за замовчуванням
Haven
Не задано
Недоступно
Haven вибрано програмою головного екрана за замовчуванням. Чи відкриє її кнопка «Додому» пристрою, залежить від прошивки.
Haven не є програмою головного екрана за замовчуванням. Натисніть OK, щоб вибрати її в системі.
На цьому пристрої немає вибору програми головного екрана. Ручні кроки є в посібнику зі встановлення.
Деякі ТВ-приставки все одно відкривають свій лаунчер, навіть якщо вибрано іншу програму; Haven не може цього змінити.
""")
A('sv', """
Standardstartapp
Haven
Inte angiven
Inte tillgänglig
Haven är vald som standardstartapp. Om enhetens Hem-knapp verkligen öppnar den beror på dess firmware.
Haven är inte standardstartapp. Tryck på OK för att välja den i systemet.
Den här enheten har ingen väljare för startapp. Se installationsguiden för de manuella stegen.
Vissa TV-boxar öppnar fortfarande sin egen launcher även när en annan startapp är vald; Haven kan inte ändra det.
""")
A('nb', """
Standard startapp
Haven
Ikke angitt
Ikke tilgjengelig
Haven er valgt som standard startapp. Om enhetens Hjem-knapp faktisk åpner den, avhenger av fastvaren.
Haven er ikke standard startapp. Trykk OK for å velge den i systemet.
Denne enheten har ingen velger for startapp. Se installasjonsveiledningen for de manuelle trinnene.
Noen TV-bokser åpner fortsatt sin egen launcher selv om en annen startapp er valgt; Haven kan ikke endre det.
""")
A('da', """
Standard startapp
Haven
Ikke angivet
Ikke tilgængelig
Haven er valgt som standard startapp. Om enhedens Hjem-knap rent faktisk åbner den, afhænger af firmwaren.
Haven er ikke standard startapp. Tryk OK for at vælge den i systemet.
Denne enhed har ingen vælger til startapp. Se installationsvejledningen for de manuelle trin.
Nogle tv-bokse åbner stadig deres egen launcher, selv når en anden startapp er valgt; Haven kan ikke ændre det.
""")
A('fi', """
Oletuskotisovellus
Haven
Ei asetettu
Ei saatavilla
Haven on valittu oletuskotisovellukseksi. Avaako laitteen Koti-painike sen todella, riippuu laiteohjelmistosta.
Haven ei ole oletuskotisovellus. Valitse se järjestelmässä painamalla OK.
Tässä laitteessa ei ole kotisovelluksen valintaa. Katso käsin tehtävät vaiheet asennusoppaasta.
Jotkin TV-boksit avaavat edelleen oman käynnistimensä, vaikka toinen kotisovellus olisi valittu; Haven ei voi muuttaa sitä.
""")
A('el', """
Προεπιλεγμένη εφαρμογή αρχικής οθόνης
Haven
Δεν έχει οριστεί
Μη διαθέσιμο
Το Haven είναι επιλεγμένο ως προεπιλεγμένη εφαρμογή αρχικής οθόνης. Το αν το κουμπί Home της συσκευής την ανοίγει πράγματι εξαρτάται από το υλικολογισμικό.
Το Haven δεν είναι η προεπιλεγμένη εφαρμογή αρχικής οθόνης. Πατήστε OK για να το επιλέξετε στο σύστημα.
Αυτή η συσκευή δεν έχει επιλογέα εφαρμογής αρχικής οθόνης. Δείτε τον οδηγό εγκατάστασης για τα χειροκίνητα βήματα.
Ορισμένα TV box ανοίγουν πάντα το δικό τους launcher ακόμη κι αν επιλεγεί άλλη εφαρμογή· το Haven δεν μπορεί να το αλλάξει.
""")
A('cs', """
Výchozí domovská aplikace
Haven
Nenastaveno
Nedostupné
Haven je vybrán jako výchozí domovská aplikace. Zda ji tlačítko Domů zařízení skutečně otevře, závisí na firmwaru.
Haven není výchozí domovská aplikace. Stiskněte OK a vyberte ji v systému.
Toto zařízení nemá výběr domovské aplikace. Ruční kroky najdete v instalační příručce.
Některé TV boxy stále otevírají vlastní launcher, i když je vybrána jiná aplikace; Haven to nemůže změnit.
""")
A('sk', """
Predvolená domovská aplikácia
Haven
Nenastavené
Nedostupné
Haven je vybraný ako predvolená domovská aplikácia. Či ju tlačidlo Domov zariadenia naozaj otvorí, závisí od firmvéru.
Haven nie je predvolená domovská aplikácia. Stlačte OK a vyberte ju v systéme.
Toto zariadenie nemá výber domovskej aplikácie. Manuálne kroky nájdete v inštalačnej príručke.
Niektoré TV boxy stále otvárajú vlastný launcher, aj keď je vybraná iná aplikácia; Haven to nemôže zmeniť.
""")
A('hu', """
Alapértelmezett kezdőalkalmazás
Haven
Nincs beállítva
Nem érhető el
A Haven van kiválasztva alapértelmezett kezdőalkalmazásnak. Hogy az eszköz Home gombja valóban megnyitja-e, a firmware-től függ.
A Haven nem az alapértelmezett kezdőalkalmazás. Nyomja meg az OK-t a rendszerben való kiválasztásához.
Ezen az eszközön nincs kezdőalkalmazás-választó. A kézi lépéseket a telepítési útmutató tartalmazza.
Néhány TV-box akkor is a saját launcherét nyitja meg, ha másik kezdőalkalmazás van kiválasztva; a Haven ezt nem változtathatja meg.
""")
A('ro', """
Aplicația Acasă implicită
Haven
Nesetat
Indisponibil
Haven este selectat ca aplicație Acasă implicită. Dacă butonul Acasă al dispozitivului o deschide efectiv depinde de firmware.
Haven nu este aplicația Acasă implicită. Apăsați OK pentru a o alege în sistem.
Acest dispozitiv nu are selector de aplicație Acasă. Vedeți ghidul de instalare pentru pașii manuali.
Unele TV box-uri își deschid în continuare propriul launcher chiar dacă e aleasă altă aplicație; Haven nu poate schimba asta.
""")
A('bg', """
Начално приложение по подразбиране
Haven
Не е зададено
Недостъпно
Haven е избрано като начално приложение по подразбиране. Дали бутонът „Начало“ на устройството го отваря наистина, зависи от фърмуера.
Haven не е началното приложение по подразбиране. Натиснете OK, за да го изберете в системата.
Това устройство няма избор на начално приложение. Ръчните стъпки са в ръководството за инсталиране.
Някои ТВ приставки продължават да отварят своя лаунчер, дори когато е избрано друго приложение; Haven не може да го промени.
""")
A('hr', """
Zadana početna aplikacija
Haven
Nije postavljeno
Nedostupno
Haven je odabran kao zadana početna aplikacija. Otvara li ga tipka Početna na uređaju doista, ovisi o firmveru.
Haven nije zadana početna aplikacija. Pritisnite OK da ga odaberete u sustavu.
Ovaj uređaj nema odabir početne aplikacije. Ručni koraci su u vodiču za instalaciju.
Neki TV uređaji i dalje otvaraju vlastiti launcher čak i kad je odabrana druga aplikacija; Haven to ne može promijeniti.
""")
A('sr', """
Подразумевана почетна апликација
Haven
Није подешено
Недоступно
Haven је изабран као подразумевана почетна апликација. Да ли га дугме Почетна на уређају заиста отвара, зависи од фирмвера.
Haven није подразумевана почетна апликација. Притисните OK да га изаберете у систему.
Овај уређај нема избор почетне апликације. Ручни кораци су у водичу за инсталацију.
Неки ТВ уређаји и даље отварају сопствени лаунчер чак и када је изабрана друга апликација; Haven то не може да промени.
""")
A('sl', """
Privzeta domača aplikacija
Haven
Ni nastavljeno
Ni na voljo
Haven je izbran kot privzeta domača aplikacija. Ali ga gumb Domov na napravi res odpre, je odvisno od vdelane programske opreme.
Haven ni privzeta domača aplikacija. Pritisnite OK, da ga izberete v sistemu.
Ta naprava nima izbire domače aplikacije. Ročni koraki so v navodilih za namestitev.
Nekatere televizijske škatle še vedno odprejo svoj zaganjalnik, tudi če je izbrana druga aplikacija; Haven tega ne more spremeniti.
""")
A('tr', """
Varsayılan Ana ekran uygulaması
Haven
Ayarlanmadı
Kullanılamıyor
Haven varsayılan Ana ekran uygulaması olarak seçili. Cihazın Ana Sayfa düğmesinin onu gerçekten açıp açmadığı yazılımına bağlıdır.
Haven varsayılan Ana ekran uygulaması değil. Sistemde seçmek için Tamam'a basın.
Bu cihazda Ana ekran uygulaması seçici yok. Elle yapılacak adımlar için kurulum kılavuzuna bakın.
Bazı TV kutuları başka bir Ana ekran uygulaması seçilse bile kendi başlatıcısını açar; Haven bunu değiştiremez.
""")
A('is', """
Sjálfgefið heimaforrit
Haven
Ekki stillt
Ekki tiltækt
Haven er valið sem sjálfgefið heimaforrit. Hvort Heim-hnappur tækisins opnar það í raun fer eftir vélbúnaðarhugbúnaði þess.
Haven er ekki sjálfgefið heimaforrit. Ýttu á OK til að velja það í kerfinu.
Þetta tæki er ekki með val á heimaforriti. Sjá uppsetningarleiðbeiningar fyrir handvirk skref.
Sumir sjónvarpskassar opna áfram eigin ræsiforrit þótt annað heimaforrit sé valið; Haven getur ekki breytt því.
""")
A('ga', """
Aip Baile réamhshocraithe
Haven
Gan socrú
Ar fáil
Roghnaíodh Haven mar an aip Baile réamhshocraithe. Braitheann sé ar dhaingneán an ghléis an osclaíonn an cnaipe Baile é i ndáiríre.
Níl Haven ar an aip Baile réamhshocraithe. Brúigh OK chun é a roghnú sa chóras.
Níl roghnóir aip Baile ar an ngléas seo. Féach an treoir suiteála le haghaidh céimeanna láimhe.
Osclaíonn roinnt boscaí teilifíse a lainseálaí féin fós fiú nuair a roghnaítear aip eile; ní féidir le Haven é sin a athrú.
""")
A('ca', """
Aplicació d'inici predeterminada
Haven
Sense definir
No disponible
Haven està seleccionada com a aplicació d'inici predeterminada. Que el botó Inici del dispositiu l'obri de debò depèn del seu firmware.
Haven no és l'aplicació d'inici predeterminada. Premeu OK per triar-la al sistema.
Aquest dispositiu no té selector d'aplicació d'inici. Consulteu la guia d'instal·lació per als passos manuals.
Algunes caixes de TV continuen obrint el seu propi launcher encara que s'esculli una altra aplicació; Haven no ho pot canviar.
""")
A('eu', """
Hasierako aplikazio lehenetsia
Haven
Ezarri gabe
Ez dago erabilgarri
Haven hasierako aplikazio lehenetsi gisa aukeratuta dago. Gailuaren Hasiera botoiak benetan irekitzen duen firmwarearen araberakoa da.
Haven ez da hasierako aplikazio lehenetsia. Sakatu OK sisteman aukeratzeko.
Gailu honek ez du hasierako aplikazioaren hautatzailerik. Ikusi instalazio-gida eskuzko urratsetarako.
TV kutxa batzuek beren launcherra irekitzen dute beste aplikazio bat aukeratuta ere; Havenek ezin du hori aldatu.
""")
A('cy', """
Ap Cartref diofyn
Haven
Heb ei osod
Ddim ar gael
Mae Haven wedi'i ddewis fel yr ap Cartref diofyn. Mae a yw botwm Cartref y ddyfais yn ei agor go iawn yn dibynnu ar ei firmware.
Nid Haven yw'r ap Cartref diofyn. Pwyswch OK i'w ddewis yn y system.
Does gan y ddyfais hon ddim dewisydd ap Cartref. Gweler y canllaw gosod am y camau â llaw.
Mae rhai blychau teledu'n dal i agor eu lansiwr eu hunain hyd yn oed pan ddewisir ap arall; ni all Haven newid hynny.
""")
A('ar', """
تطبيق الشاشة الرئيسية الافتراضي
Haven
غير محدد
غير متاح
تم اختيار Haven كتطبيق الشاشة الرئيسية الافتراضي. يعتمد فتح زر الرئيسية في الجهاز له فعلًا على البرنامج الثابت.
Haven ليس تطبيق الشاشة الرئيسية الافتراضي. اضغط OK لاختياره في النظام.
لا يوفّر هذا الجهاز أداة اختيار تطبيق الشاشة الرئيسية. راجع دليل التثبيت للخطوات اليدوية.
تواصل بعض أجهزة التلفاز فتح مشغّلها الخاص حتى عند اختيار تطبيق آخر؛ لا يستطيع Haven تغيير ذلك.
""")
A('fa', """
برنامه صفحه اصلی پیش‌فرض
Haven
تنظیم نشده
در دسترس نیست
Haven به‌عنوان برنامه صفحه اصلی پیش‌فرض انتخاب شده است. اینکه دکمه خانه دستگاه واقعاً آن را باز کند به سیستم‌افزار بستگی دارد.
Haven برنامه صفحه اصلی پیش‌فرض نیست. برای انتخاب آن در سیستم OK را بزنید.
این دستگاه انتخاب‌گر برنامه صفحه اصلی ندارد. مراحل دستی را در راهنمای نصب ببینید.
برخی جعبه‌های تلویزیون حتی با انتخاب برنامه دیگر، لانچر خودشان را باز می‌کنند؛ Haven نمی‌تواند این را تغییر دهد.
""")
A('ur', """
ڈیفالٹ ہوم ایپ
Haven
سیٹ نہیں
دستیاب نہیں
Haven بطور ڈیفالٹ ہوم ایپ منتخب ہے۔ آلے کا ہوم بٹن اسے واقعی کھولتا ہے یا نہیں، یہ اس کے فرم ویئر پر منحصر ہے۔
Haven ڈیفالٹ ہوم ایپ نہیں ہے۔ سسٹم میں منتخب کرنے کے لیے OK دبائیں۔
اس آلے میں ہوم ایپ منتخب کرنے کا اختیار نہیں۔ دستی مراحل کے لیے انسٹالیشن گائیڈ دیکھیں۔
کچھ ٹی وی باکس دوسری ایپ منتخب ہونے پر بھی اپنا لانچر کھولتے ہیں؛ Haven اسے تبدیل نہیں کر سکتا۔
""")
A('zh-CN', """
默认主屏幕应用
Haven
未设置
不可用
已选择 Haven 作为默认主屏幕应用。设备的主页键是否真的打开它，取决于其固件。
Haven 不是默认主屏幕应用。按“确定”在系统中选择它。
此设备没有主屏幕应用选择器。手动步骤请参阅安装指南。
部分电视盒子即使选择了其他主屏幕应用，仍会打开自带的启动器；Haven 无法改变这一点。
""")
A('zh-TW', """
預設主畫面應用程式
Haven
未設定
無法使用
已選擇 Haven 作為預設主畫面應用程式。裝置的主畫面鍵是否真的開啟它，取決於其韌體。
Haven 不是預設主畫面應用程式。按「確定」在系統中選擇它。
此裝置沒有主畫面應用程式選擇器。手動步驟請參閱安裝指南。
部分電視盒即使選擇了其他主畫面應用程式，仍會開啟自家的啟動器；Haven 無法改變這一點。
""")
A('ja', """
既定のホームアプリ
Haven
未設定
利用不可
Haven が既定のホームアプリに選択されています。端末のホームボタンが実際に開くかどうかは、ファームウェア次第です。
Haven は既定のホームアプリではありません。OK を押してシステムで選択してください。
この端末にはホームアプリの選択画面がありません。手動の手順はインストールガイドをご覧ください。
別のホームアプリを選んでも、独自のランチャーを開き続けるテレビボックスがあります。Haven には変更できません。
""")
A('ko', """
기본 홈 앱
Haven
설정 안 됨
사용할 수 없음
Haven이 기본 홈 앱으로 선택되어 있습니다. 기기의 홈 버튼이 실제로 이를 여는지는 펌웨어에 달려 있습니다.
Haven은 기본 홈 앱이 아닙니다. OK를 눌러 시스템에서 선택하세요.
이 기기에는 홈 앱 선택 기능이 없습니다. 수동 절차는 설치 가이드를 참고하세요.
일부 TV 박스는 다른 홈 앱을 선택해도 자체 런처를 계속 엽니다. Haven은 이를 바꿀 수 없습니다.
""")
A('hi', """
डिफ़ॉल्ट होम ऐप
Haven
सेट नहीं
उपलब्ध नहीं
Haven डिफ़ॉल्ट होम ऐप के रूप में चुना गया है। डिवाइस का होम बटन उसे सच में खोलता है या नहीं, यह उसके फ़र्मवेयर पर निर्भर है।
Haven डिफ़ॉल्ट होम ऐप नहीं है। सिस्टम में चुनने के लिए OK दबाएँ।
इस डिवाइस में होम ऐप चुनने की सुविधा नहीं है। मैन्युअल चरणों के लिए इंस्टॉलेशन गाइड देखें।
कुछ टीवी बॉक्स दूसरा होम ऐप चुनने पर भी अपना लॉन्चर खोलते हैं; Haven इसे बदल नहीं सकता।
""")
A('bn', """
ডিফল্ট হোম অ্যাপ
Haven
সেট করা নেই
উপলব্ধ নয়
Haven ডিফল্ট হোম অ্যাপ হিসেবে নির্বাচিত। ডিভাইসের হোম বোতাম সত্যিই এটি খোলে কি না তা তার ফার্মওয়্যারের উপর নির্ভর করে।
Haven ডিফল্ট হোম অ্যাপ নয়। সিস্টেমে বেছে নিতে OK চাপুন।
এই ডিভাইসে হোম অ্যাপ বাছাইয়ের ব্যবস্থা নেই। ম্যানুয়াল ধাপের জন্য ইনস্টলেশন গাইড দেখুন।
কিছু টিভি বক্স অন্য হোম অ্যাপ বেছে নিলেও নিজস্ব লঞ্চার খোলে; Haven তা বদলাতে পারে না।
""")
A('ta', """
இயல்புநிலை முகப்பு ஆப்
Haven
அமைக்கப்படவில்லை
கிடைக்கவில்லை
Haven இயல்புநிலை முகப்பு ஆப்பாகத் தேர்ந்தெடுக்கப்பட்டுள்ளது. சாதனத்தின் முகப்பு பொத்தான் அதை உண்மையில் திறக்குமா என்பது அதன் ஃபர்ம்வேரைப் பொறுத்தது.
Haven இயல்புநிலை முகப்பு ஆப் அல்ல. கணினியில் தேர்ந்தெடுக்க OK அழுத்தவும்.
இந்தச் சாதனத்தில் முகப்பு ஆப் தேர்வி இல்லை. கைமுறை படிகளுக்கு நிறுவல் வழிகாட்டியைப் பாருங்கள்.
சில டிவி பெட்டிகள் வேறு ஆப் தேர்ந்தெடுத்தாலும் தங்கள் சொந்த லாஞ்சரையே திறக்கும்; Haven அதை மாற்ற முடியாது.
""")
A('te', """
డిఫాల్ట్ హోమ్ యాప్
Haven
సెట్ చేయలేదు
అందుబాటులో లేదు
Haven డిఫాల్ట్ హోమ్ యాప్‌గా ఎంచుకోబడింది. పరికరం హోమ్ బటన్ దాన్ని నిజంగా తెరుస్తుందా అనేది దాని ఫర్మ్‌వేర్‌పై ఆధారపడి ఉంటుంది.
Haven డిఫాల్ట్ హోమ్ యాప్ కాదు. సిస్టమ్‌లో ఎంచుకోవడానికి OK నొక్కండి.
ఈ పరికరంలో హోమ్ యాప్ ఎంపిక లేదు. మాన్యువల్ దశల కోసం ఇన్‌స్టాలేషన్ గైడ్ చూడండి.
కొన్ని టీవీ బాక్సులు మరో యాప్ ఎంచుకున్నా తమ లాంచర్‌నే తెరుస్తాయి; Haven దాన్ని మార్చలేదు.
""")
A('th', """
แอปหน้าหลักเริ่มต้น
Haven
ยังไม่ได้ตั้งค่า
ไม่พร้อมใช้งาน
เลือก Haven เป็นแอปหน้าหลักเริ่มต้นแล้ว ปุ่มหน้าหลักของอุปกรณ์จะเปิดแอปนี้จริงหรือไม่ขึ้นอยู่กับเฟิร์มแวร์
Haven ไม่ใช่แอปหน้าหลักเริ่มต้น กด OK เพื่อเลือกในระบบ
อุปกรณ์นี้ไม่มีตัวเลือกแอปหน้าหลัก ดูขั้นตอนแบบแมนนวลในคู่มือการติดตั้ง
ทีวีบ็อกซ์บางรุ่นยังเปิดตัวเรียกใช้ของตัวเองแม้เลือกแอปอื่นแล้ว Haven เปลี่ยนเรื่องนี้ไม่ได้
""")
A('vi', """
Ứng dụng Màn hình chính mặc định
Haven
Chưa đặt
Không khả dụng
Haven đã được chọn làm ứng dụng Màn hình chính mặc định. Nút Home của thiết bị có thực sự mở nó hay không phụ thuộc vào firmware.
Haven không phải ứng dụng Màn hình chính mặc định. Nhấn OK để chọn trong hệ thống.
Thiết bị này không có bộ chọn ứng dụng Màn hình chính. Xem hướng dẫn cài đặt để biết các bước thủ công.
Một số TV box vẫn mở launcher riêng dù đã chọn ứng dụng khác; Haven không thể thay đổi điều đó.
""")
A('in', """
Aplikasi Beranda default
Haven
Belum diatur
Tidak tersedia
Haven dipilih sebagai aplikasi Beranda default. Apakah tombol Beranda perangkat benar-benar membukanya bergantung pada firmware.
Haven bukan aplikasi Beranda default. Tekan OK untuk memilihnya di sistem.
Perangkat ini tidak memiliki pemilih aplikasi Beranda. Lihat panduan instalasi untuk langkah manual.
Beberapa TV box tetap membuka launcher sendiri meski aplikasi lain dipilih; Haven tidak dapat mengubahnya.
""")
A('ms', """
Apl Utama lalai
Haven
Belum ditetapkan
Tidak tersedia
Haven dipilih sebagai apl Utama lalai. Sama ada butang Utama peranti benar-benar membukanya bergantung pada perisian tegarnya.
Haven bukan apl Utama lalai. Tekan OK untuk memilihnya dalam sistem.
Peranti ini tiada pemilih apl Utama. Lihat panduan pemasangan untuk langkah manual.
Sesetengah kotak TV masih membuka pelancar sendiri walaupun apl lain dipilih; Haven tidak dapat mengubahnya.
""")
A('fil', """
Default na Home app
Haven
Hindi nakatakda
Hindi available
Napili ang Haven bilang default na Home app. Kung talagang bubuksan ito ng Home button ng device ay depende sa firmware nito.
Hindi ang Haven ang default na Home app. Pindutin ang OK para piliin ito sa system.
Walang pamili ng Home app ang device na ito. Tingnan ang gabay sa pag-install para sa mga manu-manong hakbang.
Ang ilang TV box ay patuloy na nagbubukas ng sarili nilang launcher kahit may ibang napiling app; hindi ito mababago ng Haven.
""")
A('sw', """
Programu chaguomsingi ya Mwanzo
Haven
Haijawekwa
Haipatikani
Haven imechaguliwa kama programu chaguomsingi ya Mwanzo. Kama kitufe cha Mwanzo cha kifaa kinaifungua kweli kunategemea programu dhibiti yake.
Haven si programu chaguomsingi ya Mwanzo. Bonyeza OK ili kuichagua kwenye mfumo.
Kifaa hiki hakina kichaguzi cha programu ya Mwanzo. Tazama mwongozo wa usakinishaji kwa hatua za mikono.
Baadhi ya visanduku vya TV huendelea kufungua kizindua chao hata programu nyingine ikichaguliwa; Haven haiwezi kubadilisha hilo.
""")
A('af', """
Verstek-tuisprogram
Haven
Nie gestel nie
Nie beskikbaar nie
Haven is gekies as verstek-tuisprogram. Of die toestel se Tuis-knoppie dit werklik oopmaak, hang van sy firmware af.
Haven is nie die verstek-tuisprogram nie. Druk OK om dit in die stelsel te kies.
Hierdie toestel het nie 'n keuse vir tuisprogram nie. Sien die installasiegids vir die handmatige stappe.
Sommige TV-bokse maak steeds hul eie launcher oop selfs wanneer 'n ander program gekies is; Haven kan dit nie verander nie.
""")
A('am', """
ነባሪ የመነሻ መተግበሪያ
Haven
አልተዘጋጀም
አይገኝም
Haven እንደ ነባሪ የመነሻ መተግበሪያ ተመርጧል። የመሣሪያው የመነሻ አዝራር በእርግጥ ይከፍተው እንደሆነ በፊርምዌሩ ይወሰናል።
Haven ነባሪ የመነሻ መተግበሪያ አይደለም። በሲስተሙ ለመምረጥ OK ይጫኑ።
ይህ መሣሪያ የመነሻ መተግበሪያ መምረጫ የለውም። የእጅ ደረጃዎችን በመጫኛ መመሪያው ይመልከቱ።
አንዳንድ የቲቪ ሳጥኖች ሌላ መተግበሪያ ቢመረጥም የራሳቸውን ማስጀመሪያ ይከፍታሉ፤ Haven ይህን መቀየር አይችልም።
""")
A('zu', """
I-app yasekhaya ezenzakalelayo
Haven
Ayisethiwe
Ayitholakali
I-Haven ikhethwe njenge-app yasekhaya ezenzakalelayo. Ukuthi inkinobho yasekhaya yedivayisi iyivula ngempela kuncike kufirmware yayo.
I-Haven ayiyona i-app yasekhaya ezenzakalelayo. Cindezela i-OK ukuyikhetha esistimini.
Le divayisi ayinakho ukukhetha i-app yasekhaya. Bheka umhlahlandlela wokufaka ngezinyathelo zesandla.
Amanye amabhokisi e-TV asavula isiqalisi sawo ngisho nalapho kukhethwe enye i-app; i-Haven ayikwazi ukuguqula lokho.
""")
