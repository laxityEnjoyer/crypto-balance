-- ============================================================
-- Keyspace
-- ============================================================
CREATE KEYSPACE IF NOT EXISTS trx
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
AND durable_writes = true;

-- ============================================================
-- Transaction delta table
--
-- Stores net amount changes (deltas) per transaction.
-- Partition key (chain, address, token_name) co-locates all
-- deltas for a given address/token pair, enabling efficient
-- range scans by block_number.
-- ============================================================
CREATE TABLE IF NOT EXISTS trx.transaction_address_amount (
    chain        text,
    address      text,
    token_name   text,
    block_number bigint,
    tx_hash      text,
    amount_delta varint,
    PRIMARY KEY ((chain, address, token_name), block_number, tx_hash)
) WITH CLUSTERING ORDER BY (block_number ASC, tx_hash ASC);

-- ============================================================
-- Reconciliation audit table — query by block
--
-- Stores discrepancies found during reconciliation runs.
-- Clustering by abs_delta DESC surfaces the largest mismatches
-- first when scanning a given block.
-- ============================================================
CREATE TABLE IF NOT EXISTS trx.balance_mismatch_by_block (
    chain           text,
    block_h         bigint,
    abs_delta       varint,
    address         text,
    token_name      text,
    system_balance  varint,
    onchain_balance varint,
    delta           varint,
    checked_at      timestamp,
    run_id          uuid,
    PRIMARY KEY ((chain, block_h), abs_delta, address, token_name)
) WITH CLUSTERING ORDER BY (abs_delta DESC, address ASC, token_name ASC);

-- ============================================================
-- Reconciliation audit table — query by address
--
-- Stores the same discrepancy records as above, denormalised
-- for efficient per-address historical lookups.
-- Clustering by block_h DESC returns the most recent checks first.
-- ============================================================
CREATE TABLE IF NOT EXISTS trx.balance_mismatch_by_address (
    chain           text,
    address         text,
    block_h         bigint,
    token_name      text,
    system_balance  varint,
    onchain_balance varint,
    delta           varint,
    checked_at      timestamp,
    run_id          uuid,
    PRIMARY KEY ((chain, address), block_h, token_name)
) WITH CLUSTERING ORDER BY (block_h DESC, token_name ASC);
