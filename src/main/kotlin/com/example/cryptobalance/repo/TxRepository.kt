package com.example.cryptobalance.repo

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import org.springframework.stereotype.Component
import java.math.BigInteger

/**
 * Repository for reading transaction delta records from Cassandra.
 *
 * Maps to the `trx.transaction_address_amount` table:
 * ```
 * CREATE TABLE trx.transaction_address_amount (
 *   chain      text,
 *   address    text,
 *   token_name text,
 *   block_number bigint,
 *   tx_hash    text,
 *   amount_delta varint,
 *   PRIMARY KEY ((chain, address, token_name), block_number, tx_hash)
 * ) WITH CLUSTERING ORDER BY (block_number ASC, tx_hash ASC);
 * ```
 * The partition key `(chain, address, token_name)` ensures all deltas for a given
 * address/token pair are co-located, making range scans by block height efficient.
 */
@Component
class TxRepository(private val sess: CqlSession) {

    /**
     * Prepared statement: selects all [amount_delta] values within a single partition
     * up to and including the specified block number.
     */
    private val deltasUpToBlockStmt: PreparedStatement = sess.prepare(
        """
        SELECT amount_delta
        FROM trx.transaction_address_amount
        WHERE chain = ? AND address = ? AND token_name = ? AND block_number <= ?
        """.trimIndent()
    )

    /**
     * Computes the off-chain balance for an [address]/[token] pair by summing all
     * [amount_delta] values recorded up to [upToBlock] (inclusive).
     *
     * @param chain     Logical chain identifier (e.g. "TRON").
     * @param address   TRON base58-encoded wallet address.
     * @param token     Token symbol in uppercase (e.g. "TRX", "USDT").
     * @param upToBlock Upper block height bound for the aggregation.
     * @return Accumulated balance as [BigInteger]. Returns [BigInteger.ZERO] if no records exist.
     */
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
