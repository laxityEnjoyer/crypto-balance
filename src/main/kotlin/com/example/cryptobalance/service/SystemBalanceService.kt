package com.example.cryptobalance.service

import com.example.cryptobalance.repo.TxRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.Instant

/**
 * Off-chain balance for a single token, derived from stored transaction deltas.
 *
 * @property token  Token symbol (e.g. TRX, USDT).
 * @property amount Accumulated balance up to the requested block height.
 */
data class SystemTokenBalance(val token: String, val amount: BigInteger)

/**
 * Response model for off-chain balance queries.
 *
 * @property chain        Logical chain / wallet identifier stored in Cassandra.
 * @property address      TRON base58-encoded address.
 * @property block_number Block height used as the upper bound for delta aggregation.
 * @property balances     List of per-token balances. Tokens with a zero balance are excluded.
 * @property checkedAt    UTC timestamp of the query.
 */
data class SystemBalanceResponse(
    val chain: String,
    val address: String,
    val block_number: Long,
    val balances: List<SystemTokenBalance>,
    val checkedAt: Instant = Instant.now()
)

/**
 * Service for querying off-chain (system-side) token balances.
 *
 * Balances are computed as the sum of [amount_delta] values stored in Cassandra
 * for transactions up to and including the specified [blockNumber].
 */
@Service
class SystemBalanceService(
    private val repo: TxRepository,
    @Value("\${app.wallet-name:TRON}") private val defaultChain: String
) {

    /**
     * Returns off-chain balances for all supported tokens at the given [blockNumber].
     * Tokens with a zero balance are excluded from the response.
     */
    fun balanceForAddress(address: String, blockNumber: Long): SystemBalanceResponse {
        val chain = defaultChain
        val tokens = listOf("TRX", "USDT")

        val balances = tokens.map { t ->
            val sum = repo.sumAmountForAddress(chain, address, t, blockNumber)
            SystemTokenBalance(token = t, amount = sum)
        }.filter { it.amount != BigInteger.ZERO }

        return SystemBalanceResponse(
            chain = chain,
            address = address,
            block_number = blockNumber,
            balances = balances
        )
    }

    /**
     * Returns the off-chain balance for a single [token] at the given [blockNumber].
     */
    fun balanceForAddressToken(address: String, token: String, blockNumber: Long): SystemBalanceResponse {
        val chain = defaultChain
        val sum = repo.sumAmountForAddress(chain, address, token.uppercase(), blockNumber)

        return SystemBalanceResponse(
            chain = chain,
            address = address,
            block_number = blockNumber,
            balances = listOf(SystemTokenBalance(token = token.uppercase(), amount = sum))
        )
    }
}
