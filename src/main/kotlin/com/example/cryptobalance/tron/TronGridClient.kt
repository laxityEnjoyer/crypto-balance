package com.example.cryptobalance.tron

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.bitcoinj.core.Base58
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigInteger

@Component
class TronGridClient(
    builder: WebClient.Builder,
    @Value("\${tron.baseUrl}") baseUrl: String,
    @Value("\${tron.apiKey:}") private val apiKey: String
) {
    private val web: WebClient = builder
        .baseUrl(baseUrl)
        // <-- bez nazwanych parametrów:
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .apply { if (apiKey.isNotBlank()) defaultHeader("TRON-PRO-API-KEY", apiKey) }
        .build()

    /** Zwraca saldo TRX (w SUN) z /v1/accounts/{address} */
    suspend fun getTrxBalance(addressBase58: String): BigInteger {
        val resp = web.get()
            .uri("/v1/accounts/{addr}", addressBase58)
            .retrieve()
            .bodyToMono(Map::class.java)
            .awaitSingleOrNull()

        val data0 = ((resp?.get("data") as? List<*>)?.firstOrNull() as? Map<*, *>)
        val balanceSun = (data0?.get("balance") as? Number)?.toLong() ?: 0L
        return BigInteger.valueOf(balanceSun)
    }

    /** Zwraca saldo tokenu TRC20 (BigInteger) przez /wallet/triggersmartcontract */
    suspend fun getTrc20Balance(contractBase58: String, holderBase58: String): BigInteger {
        val holderEthHex = tronBase58ToEthHex(holderBase58)
        val param = leftPad64(holderEthHex)

        val body = mapOf(
            "contract_address" to contractBase58,
            "owner_address" to holderBase58,
            "function_selector" to "balanceOf(address)",
            "parameter" to param,
            "visible" to true
        )

        val resp = web.post()
            .uri("/wallet/triggersmartcontract")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map::class.java)
            .awaitSingleOrNull()

        val hex = ((resp?.get("constant_result") as? List<*>)?.firstOrNull() as? String)
            ?.removePrefix("0x").orEmpty()

        return if (hex.isBlank()) BigInteger.ZERO else BigInteger(hex, 16)
    }

    // --- helpers ---
    private fun tronBase58ToEthHex(base58: String): String {
        val raw = Base58.decodeChecked(base58)               // wersja(1B) + payload(20B)
        val payload20 = raw.copyOfRange(raw.size - 20, raw.size)
        return payload20.joinToString("") { "%02x".format(it) }
    }

    private fun leftPad64(hex: String): String = hex.lowercase().padStart(64, '0')
}
