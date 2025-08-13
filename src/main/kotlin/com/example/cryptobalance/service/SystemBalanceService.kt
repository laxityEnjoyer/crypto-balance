package com.example.cryptobalance.repo

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigInteger

@Component
class TxRepository(
    private val session: CqlSession,
    @Value("\${app.wallet-name:TRON}") private val walletName: String
) {

    // SUM(amount) dla (wallet_name, token_name, address) do block_number <= H
    // Uwaga: używa ALLOW FILTERING (działa, ale może być cięższe).
    private val sumStmt: PreparedStatement = session.prepare(
        """
        SELECT amount FROM trx.transaction_address_amount
        WHERE wallet_name = ? AND token_name = ? AND block_number <= ? AND address = ?
        ALLOW FILTERING
        """.trimIndent()
    )

    // Lista tokenów, które występują dla danego address (w obrębie wallet_name)
    private val tokensStmt: PreparedStatement = session.prepare(
        """
        SELECT DISTINCT token_name FROM trx.transaction_address_amount
        WHERE wallet_name = ? AND address = ?
        ALLOW FILTERING
        """.trimIndent()
    )

    /** Zwraca listę tokenów (UPPERCASE) dla adresu. */
    fun tokensForAddress(address: String): List<String> {
        val rs = session.execute(tokensStmt.bind(walletName, address))
        return rs.map { it.getString("token_name") }
            .filterNotNull()
            .map { it.uppercase() }
            .distinct()
            .sorted()
    }

    /** Suma amount (Cassandra bigint) do podanego bloku włącznie. */
    fun sumAmount(address: String, token: String, upToBlock: Long): BigInteger {
        var sum = BigInteger.ZERO
        val rs = session.execute(sumStmt.bind(walletName, token.uppercase(), upToBlock, address))
        for (row in rs) {
            val v = row.getLong("amount") // bigint -> Long
            sum = sum.add(BigInteger.valueOf(v))
        }
        return sum
    }
}
