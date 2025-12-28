README — instrukcja uruchomienia i demonstracji projektu
========================================================

Nazwa projektu
--------------
crypto-balance — moduł monitorowania i weryfikacji sald tokenów w sieci TRON

Cel i zakres
------------
Celem projektu jest demonstracja działania wyspecjalizowanej usługi, która umożliwia:
1) wyliczenie salda off-chain na podstawie danych zdarzeniowych zapisanych w bazie Apache Cassandra,
2) pobranie odpowiadającego salda on-chain z sieci TRON za pośrednictwem API TronGrid,
3) porównanie obu wartości oraz rejestrację wyniku w bazie danych w celu dalszej analizy (w tym analizy historycznej).

Przyjęto następujące założenia:
• saldo off-chain jest obliczane jako suma zmian (delt) do wskazanego numeru bloku (parametr block_number),
• saldo on-chain pobierane jest z TRON:
  - dla TRX możliwy jest odczyt stanu z odniesieniem do numeru bloku,
  - dla tokenów TRC20 (np. USDT) zastosowano odczyt stanu bieżącego (ograniczenie opisane w pracy).

Projekt nie realizuje pełnego cyklu obsługi płatności (np. generowania adresów depozytowych, księgowania wpłat,
inicjowania wypłat), a stanowi komponent kontrolny wspierający proces rekoncyliacji danych off-chain i on-chain.


Wymagania środowiskowe
----------------------
• Java 17
• Docker oraz Docker Compose
• Dostęp do Internetu 


Uruchomienie środowiska (Cassandra)
-----------------------------------
1) Uruchomienie kontenera z Apache Cassandra
   W katalogu głównym projektu uruchomić:
   docker compose up -d

   Kontener: cassandra-trx
   Port CQL: 9042 (udostępniony na hoście)

2) Utworzenie schematu bazy danych (keyspace + tabele)
   Uwaga: aplikacja zakłada istnienie keyspace przed startem (konfiguracja sterownika Cassandra).

   docker exec -i cassandra-trx cqlsh < schema_full.cql

3) Wprowadzenie danych przykładowych (seed)
   Skrypt seed umożliwia przeprowadzenie demonstracji obliczania salda off-chain oraz porównania z on-chain:

   docker exec -i cassandra-trx cqlsh < seed_transactions_real.cql


Konfiguracja dostępu do TronGrid
--------------------------------
Aplikacja wymaga ustawienia klucza API TronGrid jako właściwości: tron.apiKey.

W projekcie wykorzystano mechanizm:
spring.config.import=optional:classpath:secrets.properties

Rekomendowana konfiguracja lokalna:
1) Utworzyć plik:
   src/main/resources/secrets.properties
2) Wpisać w nim:
   tron.apiKey=YOUR_TRONGRID_API_KEY

Uruchomienie aplikacji
----------------------
W katalogu głównym projektu:
./gradlew bootRun

Domyślny adres usługi:
http://localhost:8080


Interfejs API (scenariusze demonstracyjne)
------------------------------------------
W scenariuszach demonstracyjnych wykorzystywany jest parametr:
block_number — numer bloku, do którego wyliczane jest saldo off-chain.

1) Wyliczenie salda off-chain dla adresu (zestaw tokenów)
   GET /system/{address}?block_number=78757599

   Przykład:
   curl "http://localhost:8080/system/TWOJ_ADRES?block_number=78757599"

2) Wyliczenie salda off-chain dla adresu i wskazanego tokenu
   GET /system/{address}/{token}?block_number=78757599

   Przykład:
   curl "http://localhost:8080/system/TWOJ_ADRES/USDT?block_number=78757599"

3) Porównanie salda off-chain z on-chain oraz zapis wyniku do bazy
   GET /wallet/{address}/{token}?block_number=78757599

   Przykład:
   curl "http://localhost:8080/wallet/TWOJ_ADRES/USDT?block_number=78757599"

   Działanie endpointu:
   • obliczenie salda systemowego (off-chain) na podstawie delt zapisanych w Cassandrze,
   • pobranie salda on-chain przez TronGrid (TRX na bloku / TRC20 jako stan bieżący),
   • obliczenie różnicy (delta) oraz zapis wyniku do tabel kontrolnych,
   • zwrócenie odpowiedzi JSON zawierającej podsumowanie porównania.


Konfiguracja aplikacji (application.yml)
----------------------------------------
Najistotniejsze parametry:
• app.wallet-name  — identyfikator łańcucha zapisywany w bazie (domyślnie: TRON)
• app.tokens       — lista tokenów obsługiwanych przez moduł weryfikacji:
  "TRX,USDT:TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"

• cassandra.contactPoint — 127.0.0.1:9042
• cassandra.keyspace     — trx
• cassandra.localDc      — datacenter1

• tron.baseUrl, tron.trxBaseUrl — adresy API wykorzystywane do odczytu danych on-chain


Weryfikacja danych w bazie Cassandra
------------------------------------
Wejście do konsoli CQL:
docker exec -it cassandra-trx cqlsh

Wybrane tabele:
• trx.transaction_address_amount
  Dane zdarzeniowe (delty) wykorzystywane do obliczeń off-chain.

• trx.balance_mismatch_by_block
• trx.balance_mismatch_by_address
  Tabele wyników kontroli (rozbieżności) umożliwiające analizę po bloku i po adresie.

Przykładowe zapytanie:
SELECT * FROM trx.balance_mismatch_by_address
WHERE chain='TRON' AND address='TWOJ_ADRES'
LIMIT 20;


Najczęstsze problemy i ich rozwiązania
--------------------------------------
1) Komunikat: “Keyspace does not exist”
   Rozwiązanie: ponownie wykonać inicjalizację schematu:
   docker exec -i cassandra-trx cqlsh < schema_full.cql

2) Odpowiedzi 403 / ograniczenia TronGrid (rate limit)
   Przyczyna: brak lub niepoprawny klucz tron.apiKey.
   Rozwiązanie: skonfigurować tron.apiKey zgodnie z sekcją “Konfiguracja dostępu do TronGrid”.

3) Brak połączenia z Cassandrą
   Sprawdzić:
   • czy kontener cassandra-trx działa,
   • czy port 9042 jest dostępny,
   • czy contactPoint w application.yml wskazuje właściwy adres/port.

4) Interpretacja wyników dla tokenów TRC20
   Uwaga: dla TRC20 zastosowano odczyt stanu bieżącego, a nie historycznego na block_number.
   Konsekwencją jest możliwość wystąpienia różnic wynikających z natury dostępnych endpointów API,
   co należy uwzględnić podczas analizy wyników (opisano w części teoretycznej i projektowej pracy).
