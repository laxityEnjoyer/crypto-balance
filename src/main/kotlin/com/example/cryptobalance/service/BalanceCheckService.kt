package com.example.cryptobalance.service

import com.example.cryptobalance.repo.TxRepository
import com.example.cryptobalance.tron.TronGridClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.Instant

@Service
class BalanceCheckService(
    private val repo: TxRepository,
    private val tron: TronGridClient,
    // nazwa portfela z Twojej tabeli (partition key); domyślnie TRON
    @Value("\${app.wallet-name:TRON}") private val walletName: String,
    // prosty config kontraktów: "USDT:TCN...,USDC:TEk..."
    @Value("\${app.tokens:}") private val tokensProp: String?
) {
    data class TokenCfg(val symbol: String, val contract: String?, val decimals: Int = 6)

    private val known: Map<String, TokenCfg> by lazy {
        parseTokens(tokensProp).associateBy { it.symbol.uppercase() }
    }

    private fun parseTokens(src: String?): List<TokenCfg> {
        if (src.isNullOrBlank()) return emptyList()
        return src.split(',').mapNotNull {
            val p = it.trim().split(':')
            if (p.size >= 2) TokenCfg(p[0].trim().uppercase(), p[1].trim()) else null
        }
    }

    data class DiffOneTokenResponse(
        val address: String,
        val token: String,
        val block: Long,
        val system: BigInteger,
        val onchain: BigInteger,
        val delta: BigInteger,
        val checkedAt: Instant = Instant.now()
    )

    suspend fun diffOne(address: String, token: String, block: Long, contractOverride: String?): DiffOneTokenResponse {
        val symbol = token.uppercase()

        // 1) system (Cassandra) – suma do H włącznie
        val system = repo.sumAmountForAddress(walletName, address, symbol, block)

        // 2) on-chain (Tron)
        val onchain = if (symbol == "TRX") {
            tron.getTrxBalance(address)
        } else {
            val contract = contractOverride ?: known[symbol]?.contract
            require(!contract.isNullOrBlank()) {
                "Brak kontraktu dla tokena $symbol. Podaj ?contract=... albo skonfiguruj app.tokens."
            }
            tron.getTrc20Balance(contract, address)
        }

        // 3) delta
        val delta = onchain.subtract(system)

        return DiffOneTokenResponse(address, symbol, block, system, onchain, delta)
    }
}
