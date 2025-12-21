-- Keyspace
CREATE KEYSPACE IF NOT EXISTS trx
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
AND durable_writes = true;

-- Transakcje per (chain,address,token_name) z klastrowaniem po block_number
CREATE TABLE IF NOT EXISTS trx.transaction_address_amount (
    chain text,
    address text,
    token_name text,
    block_number bigint,
    tx_hash text,
    amount_delta varint,    -- w najmniejszych jednostkach (TRX/USDT -> 1e-6)
    block_ts timestamp,
    PRIMARY KEY ((chain, address, token_name), block_number, tx_hash)
) WITH CLUSTERING ORDER BY (block_number ASC, tx_hash ASC);

-- Rozjazdy: snapshot per blok (szybkie listowanie "top N")
CREATE TABLE IF NOT EXISTS trx.balance_mismatch_by_block (
    chain text,
    block_h bigint,
    abs_delta varint,
    address text,
    token_name text,
    system_balance varint,
    onchain_balance varint,
    delta varint,
    checked_at timestamp,
    run_id uuid,
    PRIMARY KEY ((chain, block_h), abs_delta, address, token_name)
) WITH CLUSTERING ORDER BY (abs_delta DESC, address ASC, token_name ASC);

-- Rozjazdy: historia danego adresu
CREATE TABLE IF NOT EXISTS trx.balance_mismatch_by_address (
    chain text,
    address text,
    block_h bigint,
    token_name text,
    system_balance varint,
    onchain_balance varint,
    delta varint,
    checked_at timestamp,
    run_id uuid,
    PRIMARY KEY ((chain, address), block_h, token_name)
) WITH CLUSTERING ORDER BY (block_h DESC, token_name ASC);
