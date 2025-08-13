package com.example.cryptobalance.service

import com.example.cryptobalance.repo.TxRepository
import com.example.cryptobalance.tron.TronGridClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

data class TokenCfg(val symbol: String, val contract: String? = null, val decimals: Int)
data class TokenResult(val token: String, val system: BigInteger, val onchain: BigInteger, val delta: BigInteger, val persisted: Boolean)
data class CheckResponse(val chain: String, val address: String, val blockH: Long, val results: List<TokenResult>, val checkedAt: Instant = Instant.now())

@Service
class BalanceCheckService(
    private val repo: TxRepository,
    private val tron: TronGridClient,
    @Value("\${app.chain}") private val chain: String,
    @Value("\${app.tokens}") private val tokensYaml: List<Map<String, Any>>
) {
    private val tokens: List<TokenCfg> = tokensYaml.map {
        TokenCfg(
            symbol = it["symbol"] as String,
            contract = it["contract"] as String?,
            decimals = (it["decimals"] as Number).toInt()
        )
    }

    suspend fun checkAddress(chainParam: String, address: String, blockH: Long, epsilon: Long, persist: Boolean): CheckResponse {
        require(chainParam.equals(chain, ignoreCase = true)) { "Unsupported chain: $chainParam" }
        val results = tokens.map { t ->
            val system = repo.sumAmount(chain, address, t.symbol, blockH)
            val onchain = if (t.contract == null) tron.getTrxBalance(address) else tron.getTrc20Balance(t.contract, address)
            val delta = onchain.subtract(system)
            var persisted = false
            if (persist && delta.abs() > BigInteger.valueOf(epsilon)) {
                val runId = UUID.randomUUID()
                repo.saveMismatchByBlock(chain, blockH, delta.abs(), address, t.symbol, system, onchain, delta, runId)
                repo.saveMismatchByAddress(chain, address, blockH, t.symbol, system, onchain, delta, runId)
                persisted = true
            }
            TokenResult(t.symbol, system, onchain, delta, persisted)
        }
        return CheckResponse(chain, address, blockH, results)
    }

    suspend fun checkAddressToken(chainParam: String, address: String, token: String, blockH: Long, epsilon: Long, persist: Boolean): CheckResponse {
        require(chainParam.equals(chain, ignoreCase = true)) { "Unsupported chain: $chainParam" }
        val t = tokens.first { it.symbol.equals(token, true) }
        val system = repo.sumAmount(chain, address, t.symbol, blockH)
        val onchain = if (t.contract == null) tron.getTrxBalance(address) else tron.getTrc20Balance(t.contract, address)
        val delta = onchain.subtract(system)
        var persisted = false
        if (persist && delta.abs() > BigInteger.valueOf(epsilon)) {
            val runId = UUID.randomUUID()
            repo.saveMismatchByBlock(chain, blockH, delta.abs(), address, t.symbol, system, onchain, delta, runId)
            repo.saveMismatchByAddress(chain, address, blockH, t.symbol, system, onchain, delta, runId)
            persisted = true
        }
        return CheckResponse(chain, address, blockH, listOf(TokenResult(t.symbol, system, onchain, delta, persisted)))
    }
}
