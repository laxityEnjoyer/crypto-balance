package com.example.cryptobalance.tron

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigInteger

@Component
class TronGridClient(
    builder: WebClient.Builder,
    @Value("\${tron.baseUrl:https://api.trongrid.io}") private val baseUrl: String,
    @Value("\${tron.apiKey:}") private val apiKey: String,
) {
    private val web: WebClient = builder
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeaders { headers ->
            if (apiKey.isNotBlank()) {
                headers.add("TRON-PRO-API-KEY", apiKey)
            }
        }
        .build()

    /** Saldo TRX (w SUN) z /v1/accounts/{address} */
    suspend fun getTrxBalance(addressBase58: String): BigInteger {
        val resp = web.get()
            .uri("/v1/accounts/{addr}", addressBase58)
            .retrieve()
            .bodyToMono(Map::class.java)
            .awaitSingleOrNull()

        val balance = (resp?.get("balance") as? Number)?.toLong() ?: 0L
        return BigInteger.valueOf(balance)
    }

    /** Saldo TRC20 przez /wallet/triggerconstantcontract -> hex -> BigInteger */
    suspend fun getTrc20Balance(contractBase58: String, holderBase58: String): BigInteger {
        val body = mapOf(
            "contract_address" to contractBase58,
            "function_selector" to "balanceOf(address)",
            "parameter" to holderBase58, // 'visible=true' pozwala używać base58
            "visible" to true
        )

        val resp = web.post()
            .uri("/wallet/triggerconstantcontract")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map::class.java)
            .awaitSingleOrNull()

        val hex = (resp?.get("constant_result") as? List<*>)?.firstOrNull() as? String
        if (hex.isNullOrBlank()) return BigInteger.ZERO
        return BigInteger(hex, 16)
    }
}
