package com.example.cryptobalance.service

import com.example.cryptobalance.repo.TxRepository
import com.example.cryptobalance.tron.TronGridClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.Instant

/**
 * Response model representing the reconciliation result for a single address/token/block combination.
 *
 * @property wallet     Logical wallet identifier (chain name).
 * @property address    TRON base58-encoded address.
 * @property token      Token symbol (e.g. TRX, USDT).
 * @property block_number Block height used as the reconciliation anchor.
 * @property system     Off-chain balance derived from stored transaction deltas.
 * @property onchain    On-chain balance fetched from the TRON network.
 * @property delta      Difference: onchain − system. Zero means balances are in sync.
 * @property checkedAt  UTC timestamp of the reconciliation run.
 */
data class DiffResponse(
    val wallet: String,
    val address: String,
    val token: String,
    val block_number: Long,
    val system: BigInteger,
    val onchain: BigInteger,
    val delta: BigInteger,
    val checkedAt: Instant = Instant.now()
)

/**
 * Parses a comma-separated token configuration string into a symbol → contract-address map.
 *
 * Format: `"TRX,USDT:TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"`
 * TRX has no contract address (native coin), so its value is mapped to null.
 * TRX is always included even if omitted from the configuration string.
 */
private fun parseTokens(src: String): Map<String, String?> =
    src.split(",")
        .mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
        .associate { part ->
            val p = part.split(":")
            val sym = p[0].trim().uppercase()
            val contract = p.getOrNull(1)?.trim()?.ifBlank { null }
            sym to contract
        }
        .let { map -> if (!map.containsKey("TRX")) map + ("TRX" to null) else map }

/**
 * Core reconciliation service.
 *
 * Compares the off-chain balance (sum of stored transaction deltas up to a given block)
 * against the on-chain balance retrieved from the TRON network, then persists the result
 * for historical analysis.
 */
@Service
class BalanceCheckService(
    private val repo: TxRepository,
    private val tron: TronGridClient,
    private val mismatchRepo: com.example.cryptobalance.repo.MismatchRepository,
    @Value("\${app.wallet-name:TRON}") private val walletName: String,
    @Value("\${app.tokens:TRX}") tokensProp: String
) {
    private val log = org.slf4j.LoggerFactory.getLogger(BalanceCheckService::class.java)
    private val tokens: Map<String, String?> = parseTokens(tokensProp)

    /**
     * Runs a reconciliation check for the given [address] and [token] at [blockNumber].
     *
     * Steps:
     * 1. Reads the off-chain balance from Cassandra (sum of deltas ≤ blockNumber).
     * 2. Fetches the on-chain balance from TRON:
     *    - TRX: historical balance at the exact block.
     *    - TRC-20: current contract balance (block-level historical queries are not supported by the API).
     * 3. Persists the reconciliation record to the mismatch audit tables.
     *
     * @return [DiffResponse] containing both balances and their difference.
     */
    suspend fun diffForAddressToken(address: String, token: String, blockNumber: Long): DiffResponse {
        val sym = token.uppercase()

        log.info("Running reconciliation for address={} token={} blockNumber={}", address, sym, blockNumber)

        val system = repo.sumAmountForAddress(walletName, address, sym, blockNumber)
        val onchain = when (val contract = tokens[sym]) {
            null -> tron.getTrxBalanceAt(address, blockNumber)   // TRX: historical balance at block
            else -> tron.getTrc20Balance(contract, address)      // TRC-20: current balance (API limitation)
        }
        val delta = onchain.subtract(system)

        mismatchRepo.persistMismatch(
            chain = walletName,
            blockH = blockNumber,
            address = address,
            tokenName = sym,
            systemBalance = system,
            onchainBalance = onchain
        )

        return DiffResponse(walletName, address, sym, blockNumber, system, onchain, delta)
    }
}
