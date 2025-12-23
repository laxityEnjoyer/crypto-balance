package com.example.cryptobalance.repo

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import org.springframework.stereotype.Component
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

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
                checkedAt,   // <-- Instant, NIE Date
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
                checkedAt,   // <-- Instant, NIE Date
                runId
            )
        )
    }
}
