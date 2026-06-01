package com.example.cryptobalance.repo

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import org.springframework.stereotype.Component
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

/**
 * Repository responsible for persisting reconciliation audit records.
 *
 * Records are written to two denormalised Cassandra tables to support
 * efficient querying by either block height or wallet address:
 *
 * - `trx.balance_mismatch_by_block`   — partitioned by `(chain, block_h)`;
 *   clustering by `abs_delta DESC` surfaces the largest discrepancies first.
 * - `trx.balance_mismatch_by_address` — partitioned by `(chain, address)`;
 *   clustering by `block_h DESC` provides a chronological audit trail per address.
 *
 * Records with a zero delta (balances in sync) are silently skipped.
 */
@Component
class MismatchRepository(private val sess: CqlSession) {

    private val insertByBlock: PreparedStatement = sess.prepare(
        """
        INSERT INTO trx.balance_mismatch_by_block
        (chain, block_h, abs_delta, address, token_name,
         system_balance, onchain_balance, delta, checked_at, run_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
    )

    private val insertByAddress: PreparedStatement = sess.prepare(
        """
        INSERT INTO trx.balance_mismatch_by_address
        (chain, address, block_h, token_name,
         system_balance, onchain_balance, delta, checked_at, run_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
    )

    /**
     * Persists a reconciliation result to both audit tables.
     *
     * If [onchainBalance] equals [systemBalance] (delta = 0), the record is not written —
     * only actual discrepancies are stored for auditability.
     *
     * Both inserts share the same [runId] so that records from a single reconciliation
     * run can be correlated across tables.
     *
     * @param chain          Logical chain identifier (e.g. "TRON").
     * @param blockH         Block height at which the reconciliation was performed.
     * @param address        TRON base58-encoded wallet address.
     * @param tokenName      Token symbol (will be uppercased).
     * @param systemBalance  Off-chain balance from Cassandra delta aggregation.
     * @param onchainBalance On-chain balance from the TRON network.
     * @param checkedAt      Timestamp of the reconciliation run (defaults to now).
     * @param runId          Unique identifier for this reconciliation run (defaults to random UUID).
     */
    fun persistMismatch(
        chain: String,
        blockH: Long,
        address: String,
        tokenName: String,
        systemBalance: BigInteger,
        onchainBalance: BigInteger,
        checkedAt: Instant = Instant.now(),
        runId: UUID = UUID.randomUUID()
    ) {
        val token = tokenName.uppercase()
        val delta = onchainBalance.subtract(systemBalance)
        if (delta == BigInteger.ZERO) return

        val absDelta = delta.abs()

        sess.execute(
            insertByBlock.bind(
                chain,
                java.lang.Long.valueOf(blockH),
                absDelta,
                address,
                token,
                systemBalance,
                onchainBalance,
                delta,
                checkedAt,
                runId
            )
        )

        sess.execute(
            insertByAddress.bind(
                chain,
                address,
                java.lang.Long.valueOf(blockH),
                token,
                systemBalance,
                onchainBalance,
                delta,
                checkedAt,
                runId
            )
        )
    }
}
