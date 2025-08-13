package com.example.cryptobalance.repo

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import org.springframework.stereotype.Component
import java.math.BigInteger
import java.util.UUID

@Component
class TxRepository(session: CqlSession) {

    private val sess: CqlSession = session

    // === SELECTY ===
    private val sumStmt: PreparedStatement = sess.prepare(
        "SELECT amount_delta FROM trx.transaction_address_amount " +
                "WHERE chain=? AND address=? AND token_symbol=? AND block_number <= ?"
    )

    private val tokensStmt: PreparedStatement = sess.prepare(
        "SELECT DISTINCT chain, address, token_symbol " +
                "FROM trx.transaction_address_amount " +
                "WHERE chain=? AND address=? ALLOW FILTERING"
    )

    fun sumAmount(chain: String, address: String, token: String, upToBlock: Long): BigInteger {
        var sum = BigInteger.ZERO
        val rs = sess.execute(sumStmt.bind(chain, address, token, upToBlock))
        for (row in rs) {
            val v: BigInteger? = row.get("amount_delta", BigInteger::class.java)
            sum = sum.add(v ?: BigInteger.ZERO)
        }
        return sum
    }

    fun tokensForAddress(chain: String, address: String): List<String> {
        val rs = sess.execute(tokensStmt.bind(chain, address))
        return rs.mapNotNull { it.getString("token_symbol") }.toList()
    }

    // === INSERty rozjazdów ===
    private val insertMismatchByBlock: PreparedStatement = sess.prepare(
        "INSERT INTO trx.balance_mismatch_by_block " +
                "(chain, block_h, abs_delta, address, token_symbol, system_balance, onchain_balance, delta, checked_at, run_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, toTimestamp(now()), ?)"
    )

    private val insertMismatchByAddress: PreparedStatement = sess.prepare(
        "INSERT INTO trx.balance_mismatch_by_address " +
                "(chain, address, block_h, token_symbol, system_balance, onchain_balance, delta, checked_at, run_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, toTimestamp(now()), ?)"
    )

    fun saveMismatchByBlock(
        chain: String,
        blockH: Long,
        absDelta: BigInteger,
        address: String,
        token: String,
        system: BigInteger,
        onchain: BigInteger,
        delta: BigInteger,
        runId: UUID
    ) {
        sess.execute(
            insertMismatchByBlock.bind(
                chain, blockH, absDelta, address, token, system, onchain, delta, runId
            )
        )
    }

    fun saveMismatchByAddress(
        chain: String,
        address: String,
        blockH: Long,
        token: String,
        system: BigInteger,
        onchain: BigInteger,
        delta: BigInteger,
        runId: UUID
    ) {
        sess.execute(
            insertMismatchByAddress.bind(
                chain, address, blockH, token, system, onchain, delta, runId
            )
        )
    }
}
