package com.example.cryptobalance.service

import com.example.cryptobalance.repo.TxRepository
import com.example.cryptobalance.tron.TronGridClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.Instant

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

// parser "TRX,USDT:Contract,USDC:Contract" → mapa token→contract (TRX ma null)
private fun parseTokens(src: String): Map<String, String?> =
    src.split(",")
        .mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
        .associate { part ->
            val p = part.split(":")
            val sym = p[0].trim().uppercase()
            val c = p.getOrNull(1)?.trim()?.ifBlank { null }
            sym to c
        }
        .let { map -> if (!map.containsKey("TRX")) map + ("TRX" to null) else map } // zawsze mamy TRX

@Service
class BalanceCheckService(
    private val repo: TxRepository,
    private val tron: TronGridClient,
    @Value("\${app.wallet-name:TRON}") private val walletName: String,
    @Value("\${app.tokens:TRX}") tokensProp: String
) {
    private val tokens: Map<String, String?> = parseTokens(tokensProp)

    suspend fun diffForAddressToken(address: String, token: String, blockNumber: Long): DiffResponse {
        val sym = token.uppercase()
        val system = repo.sumAmountForAddress(walletName, address, sym, blockNumber)
        val onchain = when (val contract = tokens[sym]) {
            null -> tron.getTrxBalanceAt(address, blockNumber)       // TRX na danym bloku
            else -> tron.getTrc20Balance(contract, address)          // TRC20 (bieżący stan)
        }
        val delta = onchain.subtract(system)
        return DiffResponse(walletName, address, sym, blockNumber, system, onchain, delta)
    }
}
