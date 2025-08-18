package com.example.cryptobalance.repo

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import org.springframework.stereotype.Component
import java.math.BigInteger

/**
 * trx.transaction_address_amount(
 *  wallet_name text, token_name text, block_number bigint,
 *  address text, tx_hash text, amount bigint,
 *  PRIMARY KEY ((wallet_name, token_name), block_number, address, tx_hash)
 * )
 */
@Component
class TxRepository(private val sess: CqlSession) {

    // Sumujemy amount do block_number <= ? (po pełnym kluczu partycji)
    private val sumStmt: PreparedStatement = sess.prepare(
        """
        SELECT address, amount
        FROM trx.transaction_address_amount
        WHERE wallet_name = ? AND token_name = ? AND block_number <= ?
        """.trimIndent()
    )

    fun sumAmountForAddress(
        walletName: String,
        address: String,
        token: String,
        upToBlock: Long
    ): BigInteger {
        var sum = BigInteger.ZERO
        val rs = sess.execute(sumStmt.bind(walletName, token.uppercase(), upToBlock))
        // nie można dodać address do WHERE (bo jest po block_number),
        // więc filtrujemy po adresie już w kodzie:
        for (row in rs) {
            if (row.getString("address") == address) {
                sum = sum.add(BigInteger.valueOf(row.getLong("amount")))
            }
        }
        return sum
    }
}
