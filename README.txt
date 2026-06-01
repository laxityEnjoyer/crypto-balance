README — Setup and Demonstration Guide
=======================================

Project Name
------------
crypto-balance — Off-chain / On-chain balance reconciliation module for the TRON network

Purpose and Scope
-----------------
This service demonstrates a specialised reconciliation component that:

1. Computes an off-chain balance from event-sourced transaction deltas stored in Apache Cassandra.
2. Fetches the corresponding on-chain balance from the TRON network via the TronGrid API.
3. Compares both values, records the result in audit tables, and exposes it via a REST API.

Key design decisions:
  - Off-chain balances are derived by summing amount_delta records up to a specified block_number.
  - On-chain balance retrieval differs by token type:
      TRX (native coin): historical balance at the exact block, using block hash resolution.
      TRC-20 tokens (e.g. USDT): current contract balance — historical queries are not
      supported by the TronGrid API; this limitation is documented in the thesis.
  - Audit records are written to two denormalised Cassandra tables for efficient lookups
    by block height and by wallet address.

This module does not implement a full payment gateway lifecycle (address provisioning,
deposit ingestion, withdrawal initiation). It is a control component that supports the
off-chain / on-chain reconciliation process described in the thesis.


Environment Requirements
------------------------
  - Java 17
  - Docker and Docker Compose
  - Internet access (required for TronGrid API calls)


Starting the Environment (Cassandra)
--------------------------------------
1) Start the Cassandra container
   From the project root directory run:

   docker compose up -d

   Container name : cassandra-trx
   CQL port       : 9042 (exposed on the host)

2) Create the database schema (keyspace + tables)
   Note: the application expects the keyspace to exist before startup
   (required by the Cassandra driver session binding).

   docker exec -i cassandra-trx cqlsh < schema_full.cql

3) Load the seed data
   The seed script inserts representative transaction records that allow
   demonstrating off-chain balance calculation and on-chain comparison:

   docker exec -i cassandra-trx cqlsh < seed_transactions_real.cql


TronGrid API Key Configuration
--------------------------------
The application requires a TronGrid API key set as the property: tron.apiKey

The project uses the following import mechanism:
  spring.config.import=optional:classpath:secrets.properties

Recommended local setup:
  1) Create the file:
       src/main/resources/secrets.properties
  2) Add the following line:
       tron.apiKey=YOUR_TRONGRID_API_KEY


Running the Application
------------------------
From the project root directory:

  ./gradlew bootRun

Default service address:
  http://localhost:8080


REST API — Demonstration Scenarios
------------------------------------
All endpoints accept block_number as a query parameter. It defines the upper block height
bound used for off-chain delta aggregation.

1) Off-chain balance for an address (all supported tokens)
   GET /system/{address}?block_number=78757599

   Example:
   curl "http://localhost:8080/system/YOUR_ADDRESS?block_number=78757599"

2) Off-chain balance for a specific token
   GET /system/{address}/{token}?block_number=78757599

   Example:
   curl "http://localhost:8080/system/YOUR_ADDRESS/USDT?block_number=78757599"

3) Reconciliation check (off-chain vs. on-chain) with audit record persistence
   GET /wallet/{address}/{token}?block_number=78757599

   Example:
   curl "http://localhost:8080/wallet/YOUR_ADDRESS/USDT?block_number=78757599"

   Endpoint behaviour:
     - Reads the system (off-chain) balance from Cassandra delta records.
     - Fetches the on-chain balance via TronGrid (TRX at block / TRC-20 current state).
     - Computes the delta (onchain − system) and writes the audit record to Cassandra.
     - Returns a JSON response with both balances and their difference.


Application Configuration (application.yml)
---------------------------------------------
Key configuration properties:

  app.wallet-name  — chain identifier written to Cassandra (default: TRON)
  app.tokens       — comma-separated list of supported tokens with optional contract addresses:
                     "TRX,USDT:TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"

  cassandra.contactPoint — host:port of the Cassandra node (default: 127.0.0.1:9042)
  cassandra.keyspace     — target keyspace (default: trx)
  cassandra.localDc      — local datacenter name for the driver (default: datacenter1)

  tron.baseUrl    — TronGrid API base URL (default: https://api.trongrid.io)
  tron.trxBaseUrl — Full-node URL for historical TRX balance queries (defaults to tron.baseUrl)


Inspecting the Cassandra Audit Tables
---------------------------------------
Enter the CQL shell:
  docker exec -it cassandra-trx cqlsh

Relevant tables:

  trx.transaction_address_amount
    Event-sourced delta records used for off-chain balance calculation.

  trx.balance_mismatch_by_block
    Reconciliation results indexed by block height.
    Clustering by abs_delta DESC surfaces the largest discrepancies first.

  trx.balance_mismatch_by_address
    Reconciliation results indexed by wallet address.
    Provides a chronological audit trail per address.

Example query:
  SELECT * FROM trx.balance_mismatch_by_address
  WHERE chain='TRON' AND address='YOUR_ADDRESS'
  LIMIT 20;


Common Issues and Resolutions
-------------------------------
1) Error: "Keyspace does not exist"
   Re-apply the schema initialisation:
   docker exec -i cassandra-trx cqlsh < schema_full.cql

2) HTTP 403 / TronGrid rate limit exceeded
   Cause: missing or invalid tron.apiKey.
   Resolution: configure tron.apiKey as described in the "TronGrid API Key Configuration" section.

3) Cannot connect to Cassandra
   Check:
     - The cassandra-trx container is running: docker ps
     - Port 9042 is accessible on the host.
     - cassandra.contactPoint in application.yml points to the correct host and port.

4) TRC-20 balance discrepancies
   Note: TRC-20 balances are fetched as the current on-chain state, not the historical state
   at block_number. Differences may therefore arise from transactions that occurred after the
   specified block. This limitation is described in the thesis.
