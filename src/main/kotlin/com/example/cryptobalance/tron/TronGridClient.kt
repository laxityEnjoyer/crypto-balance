package com.example.cryptobalance.tron

import kotlinx.coroutines.reactive.awaitSingle
import org.bitcoinj.core.Base58
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigInteger

@Component
class TronGridClient(
    builder: WebClient.Builder,
    @Value("\${app.tron.baseUrl}") baseUrl: String,
    @Value("\${app.tron.apiKey:}") private val apiKey: String?
) {
    private val web: WebClient = builder
        .baseUrl(baseUrl)
        .defaultHeaders { h -> if (!apiKey.isNullOrBlank()) h.add("TRON-PRO-API-KEY", apiKey) }
        .build()

    suspend fun getTrxBalance(addressBase58: String): BigInteger {
        val resp = web.get()
            .uri { it.path("/walletsolidity/getaccount").queryParam("address", addressBase58).build() }
            .retrieve()
            .bodyToMono(Map::class.java)
            .awaitSingle()

        val balance = (resp["balance"] as? Number)?.toLong() ?: 0L
        return BigInteger.valueOf(balance) // SUN (1e-6 TRX)
    }

    suspend fun getTrc20Balance(contractBase58: String, holderBase58: String): BigInteger {
        val param = balanceOfParam(holderBase58)
        val body = mapOf(
            "contract_address" to contractBase58,
            "function_selector" to "balanceOf(address)",
            "parameter" to param,
            "visible" to true
        )
        val resp = web.post()
            .uri("/walletsolidity/triggersmartcontract")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map::class.java)
            .awaitSingle()

        val hex = ((resp["constant_result"] as? List<*>)?.firstOrNull() as? String) ?: "0"
        return BigInteger(hex, 16)
    }

    private fun balanceOfParam(base58: String): String {
        val decoded = Base58.decodeChecked(base58)   // 21 bajtów: 0x41 + 20B adresu
        val addr20 = decoded.copyOfRange(1, 21)
        return addr20.joinToString("") { "%02x".format(it) }.padStart(64, '0')
    }
}
