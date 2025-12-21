package com.example.cryptobalance.repo

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import org.springframework.stereotype.Component
import java.math.BigInteger

/**
 * trx.transaction_address_amount(
 *  chain text,
 *  address text,
 *  token_name text,
 *  block_number bigint,
 *  tx_hash text,
 *  amount_delta varint,
 *  block_ts timestamp,
 *  PRIMARY KEY ((chain, address, token_name), block_number, tx_hash)
 * )
 */
@Component
class TxRepository(private val sess: CqlSession) {

    // Pobieramy delty do wskazanego bloku w obrębie jednej partycji (chain+address+token_name)
    private val deltasUpToBlockStmt: PreparedStatement = sess.prepare(
        """
        SELECT amount_delta
        FROM trx.transaction_address_amount
        WHERE chain = ? AND address = ? AND token_name = ? AND block_number <= ?
        """.trimIndent()
    )

    fun sumAmountForAddress(
        chain: String,
        address: String,
        token: String,
        upToBlock: Long
    ): BigInteger {
        var sum = BigInteger.ZERO
        val rs = sess.execute(
            deltasUpToBlockStmt.bind(chain, address, token.uppercase(), upToBlock)
        )
        for (row in rs) {
            sum = sum.add(row.getBigInteger("amount_delta") ?: BigInteger.ZERO)
        }
        return sum
    }
}
