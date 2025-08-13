package com.example.cryptobalance.repo

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import org.springframework.stereotype.Component
import java.math.BigInteger

/**
 * Pracujemy na tabeli:
 * trx.transaction_address_amount(
 *   wallet_name text, token_name text, block_number bigint,
 *   address text, tx_hash text, amount bigint,
 *   PRIMARY KEY ((wallet_name, token_name), block_number, address, tx_hash)
 * )
 */
@Component
class TxRepository(private val sess: CqlSession) {

    // sumujemy amount do block_number <= ?
    private val sumStmt: PreparedStatement = sess.prepare(
        """
        SELECT address, amount
        FROM trx.transaction_address_amount
        WHERE wallet_name = ? AND token_name = ? AND block_number <= ?
        """.trimIndent()
    )

    // lista tokenów, gdzie występuje dany address (devowo: ALLOW FILTERING)
    private val tokensStmt: PreparedStatement = sess.prepare(
        """
        SELECT DISTINCT token_name, address
        FROM trx.transaction_address_amount
        WHERE wallet_name = ? AND address = ? ALLOW FILTERING
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
        for (row in rs) {
            if (row.getString("address") == address) {
                val v = row.getLong("amount") // bigint
                sum = sum.add(BigInteger.valueOf(v))
            }
        }
        return sum
    }

    fun tokensForAddress(walletName: String, address: String): List<String> {
        val rs = sess.execute(tokensStmt.bind(walletName, address))
        return rs.map { it.getString("token_name")!! }.toList().distinct().sorted()
    }
}
