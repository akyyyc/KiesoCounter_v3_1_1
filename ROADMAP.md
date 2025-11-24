# 🗺️ KiesoCounter - Fejlesztési Roadmap

## 📊 Jelenlegi verzió: v0.3.5

### ✅ Implementált funkciók
- 6 kategória + Egyéb kezelése
- Színes napi/havi grafikonok
- Trend háromszögek (🔺🔻🔵⚠️) előző munkanaphoz képest
- BINGÓ mód (előző nap számainak megjelenítése)
- Okos gyorsgombok (dinamikus top 3 gyakori szám)
- Export/Import CSV
- Átlátszó dialógusok
- Admin panel (tesztadatok, törlések)
- Bal oldali gombok (egykezes használat)

---

## 🔥 MAGAS PRIORITÁS

### 1. ⚙️ Beállítások menü
**Idő:** 8-10 óra | **Nehézség:** Közepes

- Dialógus átlátszóság csúszka (50-100%)
- Okos gombok időtartam (1, 7, 14, 30 nap)
- Utolsó munkanap keresési mélység (7, 14, 30, 60 nap)
- Értesítések kapcsoló (később)
- Dark mode kapcsoló (később)

### 2. 📅 ComposeCalendar + Javított naptár
**Idő:** 5-6 óra | **Nehézség:** Közepes

- Félkövér napok ahol van adat
- Kis ikon/pont a napok alatt (•)
- Jobb hónap navigáció
- Testreszabható megjelenés

### 3. 📦 Egyéb kategória csoportosítással
**Idő:** 16-20 óra | **Nehézség:** Magas

**Probléma:** Bizonytalan kiesők ideiglenes tárolása (zajos, paszta hiány, zárolt), majd később áthelyezés a helyes kategóriába.

**Funkciók:**
- Csoportok létrehozása Egyéb kategórián belül
- Számok hozzáadása csoportokhoz
- Egyedi szám áthelyezése (long press → menü)
- Tömeges kijelölés és áthelyezés
- Teljes csoport áthelyezése
- Átmozgatott számok jelölése (sárga)

### 4. 📊 Statisztikák képernyő
**Idő:** 6-8 óra | **Nehézség:** Közepes

- Heti/havi átlagok kategóriánként
- Trendek (javulás/romlás jelzése)
- Rekordok (legjobb/legrosszabb nap, max/min értékek)
- Kördiagram (kategóriák aránya)

### 5. 💾 Backup emlékeztető
**Idő:** 3-4 óra | **Nehézség:** Alacsony

- Heti/havi emlékeztető beállítás
- Notification: "Készíts biztonsági mentést!"
- Gyors export gomb a notificationban
- Utolsó backup dátuma

---

## 🌟 KÖZEPES PRIORITÁS

### 6. 📝 Megjegyzések funkció
**Idő:** 4-5 óra | **Nehézség:** Közepes | **Függ:** ComposeCalendar

- Napi megjegyzés hozzáadása (pl. "Ma rossz volt a gép")
- 💬 ikon a naptárban ahol van megjegyzés
- Szerkesztés/törlés
- Keresés megjegyzésekben

### 7. ☁️ Firebase szinkronizálás (3+ fő megosztás)
**Idő:** 10-13 óra | **Nehézség:** Magas

- Firebase Realtime Database integráció
- Munkacsapat (workspace) rendszer
- Megosztási kód + QR kód generálás
- Valós idejű szinkronizálás
- Offline működés + auto-sync
- **Költség:** INGYENES (~45 évig 3 fő esetén)

### 8. 🔄 Továbbfejlesztett visszavonás
**Idő:** 2-3 óra | **Nehézség:** Alacsony-Közepes

- Utolsó 3-5 tétel visszavonása
- "Visszavonás előzmények" lista

---

## 🔮 TÁVLATI CÉLOK

### 9. 🕐 Többszöri készletszámolás naponta
**Idő:** 8-10 óra | **Nehézség:** Magas | **Feltétel:** Ha szükséges lesz

- Műszak kezdete / közepe / vége külön választása
- Külön kezelés típusonként
- Összehasonlítások konfigurálhatósága

### 10. 📄 PDF/Excel riport generálás
**Idő:** 6-8 óra | **Nehézség:** Közepes-Magas

- Havi riport export PDF-be
- Részletesebb Excel export (formázás, képletek)

### 11. 🏭 Műszakok támogatása (nappalos/éjszakás)
**Idő:** 10-12 óra | **Nehézség:** Magas | **Feltétel:** Ha többen használnák

- Nappalos/éjszakás műszak választás
- Külön statisztikák műszakonként

### 12. 🔔 Értesítések
**Idő:** 2-3 óra | **Nehézség:** Alacsony

- Napi emlékeztető: "Ne felejts el adatot rögzíteni!"
- Beállítható időpont

### 13. 🌓 Dark mode kapcsoló
**Idő:** 1-2 óra | **Nehézség:** Alacsony

- Kézi dark/light mode kapcsoló
- Jelenlegi: követi a rendszer beállítást

### 14. 🔍 Keresés funkció
**Idő:** 3-4 óra | **Nehézség:** Közepes

- Keresés konkrét számra
- Melyik napon lett beírva egy adott érték

---

## 📅 Javasolt megvalósítási sorrend

### 1. fázis (rövid távú - 2-3 hét)
1. **ComposeCalendar** → Naptár javítás (5-6 óra)
2. **Beállítások menü** → Központi beállítások (8-10 óra)
3. **Backup emlékeztető** → Adatvédelem (3-4 óra)

**Összesen:** ~16-20 óra

### 2. fázis (középtávú - 1-2 hónap)
1. **Egyéb csoportosítás** → Legnagyobb haszon (16-20 óra)
2. **Statisztikák** → Hosszú távú elemzés (6-8 óra)
3. **Megjegyzések** → Kontextus hozzáadása (4-5 óra)

**Összesen:** ~26-33 óra

### 3. fázis (hosszú távú)
- **Firebase szinkronizálás** - ha többen használják
- **Továbbfejlesztések** - igény szerint

---

## 🎯 Következő lépések

**Mit válasszunk?**

1. ✅ **ComposeCalendar** - gyors siker, látványos eredmény
2. ✅ **Beállítások menü** - hasznos, központi hely
3. ✅ **Egyéb csoportosítás** - legnagyobb valós haszon, de hosszabb munka

---

## 📝 Megjegyzések

- A becsült idők **tiszta munkaidőt** jelentenek
- **Reális idő:** +20-30% (debuggolás, finomhangolás)
- **Prioritások változhatnak** a valós használat során
- **Feedback alapján** módosítható a roadmap

---

**Utoljára frissítve:** 2024.11.24  
**Verzió:** v0.3.5  
**Következő tervezett verzió:** v0.4.0 (ComposeCalendar + Beállítások)
