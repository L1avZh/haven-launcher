# Settings sections (9 lines per language). Machine-assisted drafts, not native-reviewed.
# Order: sec_home d_sec_home sec_char d_sec_char sec_remote d_sec_remote sec_system d_sec_system widgets_on_fmt
M = {}
def A(tag, s): M[tag] = s.strip('\n')
A('en', """
Home screen
Look, widgets and screensaver
Character & atmosphere
Companion, day and night
Remote & Home app
Remote buttons and the Home setting
System
Language, time and reset
%1$s on
""")
A('he', """
מסך הבית
מראה, ווידג'טים ושומר מסך
דמות ואווירה
בן לוויה, יום ולילה
שלט ואפליקציית בית
כפתורי השלט והגדרת הבית
מערכת
שפה, שעה ואיפוס
%1$s פעילים
""")
A('fr', """
Écran d'accueil
Apparence, widgets et écran de veille
Personnage et ambiance
Compagnon, jour et nuit
Télécommande et accueil
Boutons et réglage de l'accueil
Système
Langue, heure et réinitialisation
%1$s actifs
""")
A('de', """
Startbildschirm
Aussehen, Widgets und Bildschirmschoner
Figur & Atmosphäre
Begleiter, Tag und Nacht
Fernbedienung & Home-App
Tasten und Home-Einstellung
System
Sprache, Uhrzeit und Zurücksetzen
%1$s aktiv
""")
A('es', """
Pantalla de inicio
Aspecto, widgets y salvapantallas
Personaje y ambiente
Compañero, día y noche
Mando e inicio
Botones y ajuste de inicio
Sistema
Idioma, hora y restablecer
%1$s activos
""")
A('pt', """
Ecrã principal
Aspeto, widgets e proteção de ecrã
Personagem e ambiente
Companheiro, dia e noite
Comando e Home
Botões e definição de Home
Sistema
Idioma, hora e repor
%1$s ativos
""")
A('it', """
Schermata Home
Aspetto, widget e salvaschermo
Personaggio e atmosfera
Compagno, giorno e notte
Telecomando e Home
Tasti e impostazione Home
Sistema
Lingua, ora e ripristino
%1$s attivi
""")
A('nl', """
Startscherm
Uiterlijk, widgets en screensaver
Personage en sfeer
Metgezel, dag en nacht
Afstandsbediening en Home-app
Knoppen en Home-instelling
Systeem
Taal, tijd en resetten
%1$s aan
""")
A('pl', """
Ekran główny
Wygląd, widżety i wygaszacz
Postać i atmosfera
Towarzysz, dzień i noc
Pilot i aplikacja startowa
Przyciski i ustawienie ekranu głównego
System
Język, czas i reset
Włączone: %1$s
""")
A('ru', """
Главный экран
Вид, виджеты и заставка
Персонаж и атмосфера
Спутник, день и ночь
Пульт и главное приложение
Кнопки и настройка главного экрана
Система
Язык, время и сброс
Включено: %1$s
""")
A('uk', """
Головний екран
Вигляд, віджети та заставка
Персонаж і атмосфера
Супутник, день і ніч
Пульт і головний застосунок
Кнопки та налаштування головного екрана
Система
Мова, час і скидання
Увімкнено: %1$s
""")
A('sv', """
Startskärm
Utseende, widgetar och skärmsläckare
Karaktär och stämning
Följeslagare, dag och natt
Fjärrkontroll och startapp
Knappar och startinställning
System
Språk, tid och återställning
%1$s på
""")
A('nb', """
Startskjerm
Utseende, widgeter og skjermsparer
Figur og stemning
Følgesvenn, dag og natt
Fjernkontroll og startapp
Knapper og startinnstilling
System
Språk, tid og nullstilling
%1$s på
""")
A('da', """
Startskærm
Udseende, widgets og pauseskærm
Figur og stemning
Følgesvend, dag og nat
Fjernbetjening og startapp
Knapper og startindstilling
System
Sprog, tid og nulstilling
%1$s til
""")
A('fi', """
Aloitusnäyttö
Ulkoasu, widgetit ja näytönsäästäjä
Hahmo ja tunnelma
Kumppani, päivä ja yö
Kaukosäädin ja kotisovellus
Painikkeet ja kotiasetus
Järjestelmä
Kieli, aika ja nollaus
%1$s käytössä
""")
A('el', """
Αρχική οθόνη
Εμφάνιση, widgets και προφύλαξη οθόνης
Χαρακτήρας και ατμόσφαιρα
Σύντροφος, μέρα και νύχτα
Τηλεχειριστήριο και αρχική
Κουμπιά και ρύθμιση αρχικής
Σύστημα
Γλώσσα, ώρα και επαναφορά
%1$s ενεργά
""")
A('cs', """
Domovská obrazovka
Vzhled, widgety a spořič
Postava a atmosféra
Společník, den a noc
Ovladač a domovská aplikace
Tlačítka a nastavení domovské obrazovky
Systém
Jazyk, čas a obnovení
Zapnuto: %1$s
""")
A('sk', """
Domovská obrazovka
Vzhľad, widgety a šetrič
Postava a atmosféra
Spoločník, deň a noc
Ovládač a domovská aplikácia
Tlačidlá a nastavenie domovskej obrazovky
Systém
Jazyk, čas a obnovenie
Zapnuté: %1$s
""")
A('hu', """
Főképernyő
Megjelenés, widgetek és képernyővédő
Karakter és hangulat
Társ, nappal és éjszaka
Távirányító és kezdőalkalmazás
Gombok és kezdőképernyő-beállítás
Rendszer
Nyelv, idő és visszaállítás
%1$s bekapcsolva
""")
A('ro', """
Ecran principal
Aspect, widgeturi și economizor
Personaj și atmosferă
Companion, zi și noapte
Telecomandă și aplicația Home
Butoane și setarea Home
Sistem
Limbă, oră și resetare
%1$s active
""")
A('bg', """
Начален екран
Вид, джаджи и скрийнсейвър
Герой и атмосфера
Спътник, ден и нощ
Дистанционно и начално приложение
Бутони и настройка на началния екран
Система
Език, час и нулиране
Включени: %1$s
""")
A('hr', """
Početni zaslon
Izgled, widgeti i čuvar zaslona
Lik i atmosfera
Pratitelj, dan i noć
Daljinski i početna aplikacija
Tipke i postavka početnog zaslona
Sustav
Jezik, vrijeme i poništavanje
Uključeno: %1$s
""")
A('sr', """
Početni ekran
Izgled, vidžeti i čuvar ekrana
Lik i atmosfera
Pratilac, dan i noć
Daljinski i početna aplikacija
Tasteri i podešavanje početnog ekrana
Sistem
Jezik, vreme i resetovanje
Uključeno: %1$s
""")
A('sl', """
Domači zaslon
Videz, pripomočki in ohranjevalnik
Lik in vzdušje
Spremljevalec, dan in noč
Daljinec in domača aplikacija
Gumbi in nastavitev domačega zaslona
Sistem
Jezik, čas in ponastavitev
Vklopljeno: %1$s
""")
A('tr', """
Ana ekran
Görünüm, widget'lar ve ekran koruyucu
Karakter ve atmosfer
Yoldaş, gündüz ve gece
Kumanda ve Ana ekran uygulaması
Düğmeler ve Ana ekran ayarı
Sistem
Dil, saat ve sıfırlama
%1$s açık
""")
A('is', """
Heimaskjár
Útlit, græjur og skjáhvíla
Persóna og andrúmsloft
Förunautur, dagur og nótt
Fjarstýring og heimaforrit
Hnappar og stilling heimaforrits
Kerfi
Tungumál, tími og endurstilling
%1$s í gangi
""")
A('ga', """
Scáileán baile
Cuma, giuirléidí agus spárálaí scáileáin
Carachtar agus atmaisféar
Compánach, lá agus oíche
Iargúlta agus aip Baile
Cnaipí agus socrú an Bhaile
Córas
Teanga, am agus athshocrú
%1$s ar siúl
""")
A('ca', """
Pantalla d'inici
Aspecte, ginys i estalvi de pantalla
Personatge i ambient
Company, dia i nit
Comandament i inici
Botons i ajust d'inici
Sistema
Idioma, hora i restabliment
%1$s actius
""")
A('eu', """
Hasierako pantaila
Itxura, widgetak eta pantaila-babeslea
Pertsonaia eta giroa
Lagun, eguna eta gaua
Urrutiko agagintea eta hasiera
Botoiak eta hasierako ezarpena
Sistema
Hizkuntza, ordua eta berrezarpena
%1$s aktibo
""")
A('cy', """
Sgrin gartref
Golwg, teclynnau a chadw sgrin
Cymeriad ac awyrgylch
Cydymaith, dydd a nos
Teclyn rheoli ac ap Cartref
Botymau a gosodiad Cartref
System
Iaith, amser ac ailosod
%1$s ymlaen
""")
A('ar', """
الشاشة الرئيسية
المظهر والأدوات وشاشة التوقف
الشخصية والأجواء
الرفيق والنهار والليل
جهاز التحكم وتطبيق الرئيسية
الأزرار وإعداد الرئيسية
النظام
اللغة والوقت وإعادة الضبط
%1$s قيد التشغيل
""")
A('fa', """
صفحه اصلی
ظاهر، ابزارک‌ها و محافظ صفحه
شخصیت و فضا
همراه، روز و شب
کنترل و برنامه خانه
دکمه‌ها و تنظیم خانه
سیستم
زبان، ساعت و بازنشانی
%1$s روشن
""")
A('ur', """
ہوم اسکرین
ظاہری شکل، ویجٹس اور اسکرین سیور
کردار اور ماحول
ساتھی، دن اور رات
ریموٹ اور ہوم ایپ
بٹن اور ہوم کی ترتیب
سسٹم
زبان، وقت اور ری سیٹ
%1$s آن
""")
A('zh-CN', """
主屏幕
外观、小组件和屏保
角色与氛围
伙伴、白天与夜晚
遥控器与主屏幕应用
按键与主屏幕设置
系统
语言、时间和重置
已开启 %1$s 个
""")
A('zh-TW', """
主畫面
外觀、小工具和螢幕保護程式
角色與氛圍
夥伴、白天與夜晚
遙控器與主畫面應用程式
按鍵與主畫面設定
系統
語言、時間與重設
已開啟 %1$s 個
""")
A('ja', """
ホーム画面
外観、ウィジェット、スクリーンセーバー
キャラクターと雰囲気
コンパニオン、昼と夜
リモコンとホームアプリ
ボタンとホームの設定
システム
言語、時刻、リセット
%1$s 件オン
""")
A('ko', """
홈 화면
모양, 위젯, 화면 보호기
캐릭터 및 분위기
동반자, 낮과 밤
리모컨 및 홈 앱
버튼 및 홈 설정
시스템
언어, 시간, 초기화
%1$s개 켜짐
""")
A('hi', """
होम स्क्रीन
रूप, विजेट और स्क्रीनसेवर
किरदार और माहौल
साथी, दिन और रात
रिमोट और होम ऐप
बटन और होम सेटिंग
सिस्टम
भाषा, समय और रीसेट
%1$s चालू
""")
A('bn', """
হোম স্ক্রিন
চেহারা, উইজেট ও স্ক্রিনসেভার
চরিত্র ও পরিবেশ
সঙ্গী, দিন ও রাত
রিমোট ও হোম অ্যাপ
বোতাম ও হোম সেটিং
সিস্টেম
ভাষা, সময় ও রিসেট
%1$s চালু
""")
A('ta', """
முகப்புத் திரை
தோற்றம், விட்ஜெட்டுகள், திரைக்காப்பு
கதாபாத்திரம் & சூழல்
துணை, பகல் & இரவு
ரிமோட் & முகப்பு ஆப்
பொத்தான்கள் & முகப்பு அமைப்பு
முறைமை
மொழி, நேரம், மீட்டமை
%1$s இயக்கத்தில்
""")
A('te', """
హోమ్ స్క్రీన్
రూపం, విడ్జెట్లు, స్క్రీన్‌సేవర్
పాత్ర & వాతావరణం
తోడు, పగలు & రాత్రి
రిమోట్ & హోమ్ యాప్
బటన్లు & హోమ్ సెట్టింగ్
సిస్టమ్
భాష, సమయం, రీసెట్
%1$s ఆన్
""")
A('th', """
หน้าจอหลัก
รูปลักษณ์ วิดเจ็ต และภาพพักหน้าจอ
ตัวละครและบรรยากาศ
เพื่อนคู่ใจ กลางวันและกลางคืน
รีโมตและแอปหน้าหลัก
ปุ่มและการตั้งค่าหน้าหลัก
ระบบ
ภาษา เวลา และรีเซ็ต
เปิดอยู่ %1$s
""")
A('vi', """
Màn hình chính
Giao diện, tiện ích và trình bảo vệ màn hình
Nhân vật và không khí
Bạn đồng hành, ngày và đêm
Điều khiển và ứng dụng chủ
Nút bấm và cài đặt màn hình chính
Hệ thống
Ngôn ngữ, giờ và đặt lại
Đang bật: %1$s
""")
A('in', """
Layar utama
Tampilan, widget, dan screensaver
Karakter & suasana
Pendamping, siang dan malam
Remote & aplikasi Home
Tombol dan pengaturan Home
Sistem
Bahasa, waktu, dan reset
%1$s aktif
""")
A('ms', """
Skrin utama
Rupa, widget dan penyelamat skrin
Watak & suasana
Teman, siang dan malam
Alat kawalan & apl Utama
Butang dan tetapan Utama
Sistem
Bahasa, masa dan set semula
%1$s aktif
""")
A('fil', """
Home screen
Hitsura, mga widget at screensaver
Karakter at ambiance
Kasama, araw at gabi
Remote at Home app
Mga button at setting ng Home
System
Wika, oras at i-reset
%1$s naka-on
""")
A('sw', """
Skrini ya kwanza
Mwonekano, wijeti na kiokoa skrini
Mhusika na mazingira
Mwenzi, mchana na usiku
Rimoti na programu ya Mwanzo
Vitufe na mpangilio wa Mwanzo
Mfumo
Lugha, saa na kuweka upya
%1$s zimewashwa
""")
A('af', """
Tuisskerm
Voorkoms, legstukke en skermsparer
Karakter en atmosfeer
Metgesel, dag en nag
Afstandbeheer en Tuis-toep
Knoppies en Tuis-instelling
Stelsel
Taal, tyd en herstel
%1$s aan
""")
A('am', """
መነሻ ማያ
መልክ፣ ምግባሮች እና ማያ ማቆያ
ገጸ ባህሪ እና ድባብ
ጓደኛ፣ ቀን እና ሌሊት
ሪሞት እና የመነሻ መተግበሪያ
አዝራሮች እና የመነሻ ቅንብር
ስርዓት
ቋንቋ፣ ሰዓት እና ዳግም ማስጀመር
%1$s በርተዋል
""")
A('zu', """
Isikrini sasekhaya
Ukubukeka, amawijethi nesigcini sesikrini
Isiqephu nomkhathi
Umlingani, imini nobusuku
Isilawuli kude nohlelo lokusebenza lweKhaya
Izinkinobho nesilungiselelo seKhaya
Isistimu
Ulimi, isikhathi nokusetha kabusha
%1$s ivuliwe
""")
