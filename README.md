# MancsKiskönyv 🐾 – Digitális egészségügyi kiskönyv és pénzügyi követő házi kedvencek számára

A **MancsKiskönyv** egy modern, letisztult és rendkívül funkciógazdag Android-alkalmazás, amelyet arra terveztünk, hogy megkönnyítse a házi kedvencek (kutyák, macskák, lovak stb.) mindennapi ápolását, egészségügyi adatainak nyilvántartását és a velük kapcsolatos kiadások nyomon követését. 

Az alkalmazás kiemelkedő funkciója a **Gemini AI integráció**, amely segít a számlák beolvasásában (OCR), az orvosi leletek elemzésében, a tünetek felmérésében, valamint személyre szabott, fajtaspecifikus gondozási tanácsok nyújtásában.

---

## 📲 Gyors letöltés és telepítés telefonra (Android APK)

Ha szeretnéd az alkalmazást azonnal telepíteni a telefonodra fejlesztői környezet és számítógép nélkül, kövesd az alábbi lépéseket:

1. **Letöltés:** Nyisd meg az alábbi linket a telefonod böngészőjében az alkalmazás közvetlen letöltéséhez:
   👉 **[MancsKiskönyv APK Letöltése](https://github.com/SilverMoonFay/MancsKisk-nyv/raw/main/MancsKiskonyv.apk)**
2. **Telepítés:** Koppints a letöltött `MancsKiskonyv.apk` fájlra a telefonodon.
3. **Engedélyezés:** Ha a telefonod jelzi, hogy ismeretlen forrásból származik:
   * Engedélyezd a telepítést a beállításokban az adott alkalmazásnak (pl. Chrome vagy a Fájlkezelőd).
   * Ha a Google Play Protect rákérdez, válaszd a **"Telepítés mindenképpen"** (Install anyway) lehetőséget (ez teljesen normális a saját készítésű, nem Play Áruházból letöltött tesztalkalmazásoknál).
4. **Kész is!** Az alkalmazás ikonja megjelenik a kezdőképernyőn, és azonnal használhatod.

*(Megjegyzés számítógépes futtatáshoz: Ha PC-n szeretnéd futtatni, töltsd le az APK-t, és nyisd meg egy ingyenes Android emulátorban, mint például a BlueStacks vagy a NoxPlayer).*

---

## 🌟 Főbb funkciók

### 1. 🐱 Kedvencek profilja és növekedéskövetés
* **Részletes profilok:** Kedvenceid neve, fajtája, születési ideje, neme, chip száma és egyedi fényképe egy helyen tárolható.
* **Súlykövető grafikon:** Kövesd nyomon kedvenced súlyváltozását az idő múlásával, interaktív bejegyzésekkel és statisztikákkal.
* **Fajtaspecifikus Gemini tanácsok:** Kérj azonnali, mesterséges intelligencia által generált etetési, ápolási és oltási javaslatokat az adott fajtához.

### 2. 💰 Pénzügyek és költségkövetés (Gemini OCR támogatással)
* **Kiadások naplózása:** Rendszerezd a kiadásokat kategóriák szerint (étel, játék, orvos, felszerelés stb.).
* **Költségkeret (Budget):** Állíts be havi vagy éves limiteket, hogy ne lépd túl a tervezett összeget.
* **Gemini Számlabeolvasó (OCR):** Csak fotózd le az állatorvosi vagy bolti számlát, és a Gemini AI automatikusan felismeri az összeget, a dátumot és a tételeket, majd menti a kiadások közé!

### 3. 🏥 Egészségügyi kiskönyv és leletelemzés
* **Oltási könyv és gyógyszerek:** Naplózd a beadott és esedékes oltásokat, féreghajtásokat és egyéb kezeléseket.
* **Orvosi leletek elemzése (Medical OCR):** Töltsd fel vagy fotózd le az állatorvosi leletet, a Gemini pedig lefordítja az érthetetlen orvosi szakkifejezéseket, összefoglalja a diagnózist és javaslatot tesz a következő lépésekre.

### 4. 📅 Rutinfeladatok és emlékeztetők
* **Napi és heti teendők:** Hozz létre ismétlődő feladatokat (sétáltatás, etetés, fésülés, gyógyszer beadása).
* **Interaktív ellenőrző lista:** Pipáld ki a napi rutinokat, hogy mindig biztos lehess kedvenced tökéletes ellátásában.

### 5. 🚨 Sürgősségi asszisztens és tünetellenőrző
* **Gemini Tünetellenőrző:** Írd le vagy válaszd ki kedvenced tüneteit (pl. bágyadtság, hányás), a Gemini pedig azonnal felméri a vészhelyzet súlyosságát és lépésről lépésre elsősegély-útmutatót ad.
* **Sürgősségi kapcsolatok:** Tárold el a legközelebbi 0-24 órás állatklinika és az állatorvosod elérhetőségét az azonnali hívás funkcióval.

---

## 🛠️ Alkalmazott technológiák és architektúra

A MancsKiskönyv a legmodernebb Android-fejlesztési irányelveket és technológiákat alkalmazza:

* **Jetpack Compose:** Deklaratív, modern UI-keretrendszer Material Design 3 stílusjegyekkel, dinamikus sötét/világos móddal és reszponzív felületekkel.
* **MVVM architektúra:** Tiszta felelősségmegosztás a képernyő (UI), a ViewModel (állapotkezelés) és a Repository (adatforrás) között.
* **Room Database:** Helyi, offline-first SQLite adatbázis a kedvencek profiljainak, kiadásainak és kezeléseinek biztonságos, gyors tárolására.
* **Kotlin Coroutines & Flow:** Reaktív és aszinkron adatfolyamok a zökkenőmentes felhasználói élményért.
* **Retrofit & Ktor:** Biztonságos hálózati kommunikáció az API-k eléréséhez.
* **Gemini Pro API (Generative AI):** Fejlett szöveges és multimodális (kép + szöveg) mesterséges intelligencia integráció közvetlenül a Google-től.

---

## 🚀 Telepítés és fejlesztés indítása

1. **Klónozd a tárat:**
   ```bash
   git clone <repository_url>
   ```
2. **Nyisd meg Android Studio-ban:**
   Importáld a projektet a legfrissebb Android Studio verzióval.
3. **Konfiguráld a Gemini API kulcsot:**
   * Nyisd meg az **AI Studio Secrets** panelt vagy hozz létre egy `.env` fájlt a projekt gyökerében.
   * Add hozzá a saját API kulcsodat:
     ```env
     GEMINI_API_KEY=saját_gemini_api_kulcsod
     ```
4. **Futtasd az alkalmazást:**
   Válassz ki egy fizikai eszközt vagy emulátort, majd kattints a **Run** gombra!

---

## 📊 Fejlesztők és hozzájárulás

Az alkalmazás nyílt forráskódú, és örömmel fogadjuk a visszajelzéseket, hibajelentéseket és fejlesztési javaslatokat (Pull Request)!

🐾 *Mert a kedvenced egészsége a legnagyobb kincs!*
