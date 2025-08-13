DESC KEYSPACE trx;
DESC TABLE trx.transaction_address_amount;

-- Pobierz transakcje do bloku H dla adresu+tokena (przykład H=47000005):
SELECT block_number, tx_hash, amount_delta, block_ts
FROM trx.transaction_address_amount
WHERE chain='TRON'
  AND address='TDqSquXBgUCLYvYC4XZgrprLK589dkhSCf'
  AND token='USDT'
  AND block_number <= 47000005;

-- Rozjazdy na danym bloku (po załadowaniu własnych wyników do tabel mismatch):
SELECT * FROM trx.balance_mismatch_by_block
WHERE chain='TRON' AND block_h=47000005
LIMIT 50;

-- Historia rozjazdów konkretnego adresu:
SELECT * FROM trx.balance_mismatch_by_address
WHERE chain='TRON' AND address='TDqSquXBgUCLYvYC4XZgrprLK589dkhSCf'
LIMIT 50;
