# Säkerhetsanalys (Steg 4) – Individuell Labb 2k5

Baserat på en genomgång av kodbasen (`AiClientService`, `AiController`) mot
OWASP Top 10 har följande grundläggande säkerhetsbrister identifierats.

---

## 1. A01:2021 – Broken Access Control

**Var:** `AiController.ask()` – endpointen `POST /api/ai/ask`

**Problem:**
Endpointen saknar helt autentisering eller auktorisering. Vem som helst som
känner till URL:en kan anropa den, utan begränsning.

**Varför är det farligt?**
Varje anrop till endpointen triggar ett anrop vidare till OpenAI:s API, vilket
kostar pengar per anrop. En illasinnad person (eller ett automatiserat skript)
skulle kunna skicka ett stort antal anrop och tömma vår AI-budget/kvot helt,
utan att vi märker det förrän en oväntat hög faktura dyker upp.

**Åtgärd (Steg 5):**
Endpointen skyddas nu med en enkel API-nyckel som måste skickas med i en
HTTP-header (`X-API-Key`). Anrop utan giltig nyckel avvisas med
`401 Unauthorized` innan något AI-anrop görs.

---

## 2. A04:2021 – Insecure Design (obegränsad indatastorlek / resursuttömning)

**Var:** `AiController.AskRequest` (record med fältet `message`)

**Problem:**
Fältet `message` valideras bara med `@NotBlank` (dvs. att det inte är tomt).
Det finns ingen övre gräns för hur lång texten får vara.

**Varför är det farligt?**
En anropare kan skicka en extremt lång text (t.ex. hundratusentals tecken) i
varje anrop. Detta:
- Skickar en onödigt stor och dyr förfrågan vidare till OpenAI varje gång.
- Kan belasta serverns minne/nätverk om flera sådana anrop görs samtidigt,
  vilket i värsta fall kan användas för en enkel överbelastningsattack (DoS).

**Åtgärd (Steg 5):**
Ett maxvärde (`@Size(max = 2000)`) har lagts till på `message`-fältet.
Anrop med för lång text avvisas automatiskt av Bean Validation med
`400 Bad Request`, innan något AI-anrop görs.

---

## Sammanfattning

| # | OWASP-kategori | Brist | Åtgärd |
|---|-----------------|-------|--------|
| 1 | A01: Broken Access Control | Öppen endpoint utan autentisering | API-nyckel via `X-API-Key`-header |
| 2 | A04: Insecure Design | Ingen övre gräns på indatastorlek | `@Size(max = 2000)` på `message` |