package com.example.cryptobalance.repo

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import org.springframework.stereotype.Component
import java.math.BigInteger

@Component
class TxRepository(private val sess: CqlSession) {

    // Bierzemy wiersze do H z partycji (wallet_name, token_name), adres filtrujemy w kodzie
    private val sumStmt: PreparedStatement = sess.prepare(
        """
        SELECT address, amount
        FROM trx.transaction_address_amount
        WHERE wallet_name = ? AND token_name = ? AND block_number <= ?
        """.trimIndent()
    )

    fun sumAmountForAddress(walletName: String, address: String, token: String, upToBlock: Long): BigInteger {
        var sum = BigInteger.ZERO
        val rs = sess.execute(sumStmt.bind(walletName, token.uppercase(), upToBlock))
        for (row in rs) {
            if (row.getString("address") == address) {
                val v = row.getLong("amount")           // bigint → Long
                sum = sum.add(BigInteger.valueOf(v))    // sumujemy w BigInteger
            }
        }
        return sum
    }
}
