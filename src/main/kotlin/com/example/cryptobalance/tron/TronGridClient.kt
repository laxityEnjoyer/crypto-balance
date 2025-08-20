package com.example.cryptobalance.tron

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigInteger
import java.util.Base64
import java.util.Arrays
import kotlin.experimental.and

@Component
class TronGridClient(
    builder: WebClient.Builder,
    @Value("\${tron.baseUrl:https://api.trongrid.io}") private val baseUrl: String,
    @Value("\${tron.apiKey:}") private val apiKey: String,
) {
    private val log = LoggerFactory.getLogger(TronGridClient::class.java)

    private val web: WebClient = builder
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeaders { headers ->
            if (apiKey.isNotBlank()) {
                headers.add("TRON-PRO-API-KEY", apiKey)
            }
        }
        .build()

    /// Saldo TRX (w SUN) z /v1/accounts/{address}
    suspend fun getTrxBalance(addressBase58: String): BigInteger {
        val resp = web.get()
            .uri("/v1/accounts/{addr}", addressBase58)
            .retrieve()
            .bodyToMono(Map::class.java)
            .awaitSingleOrNull()

        val balance = (resp?.get("balance") as? Number)?.toLong() ?: 0L
        return BigInteger.valueOf(balance)
    }

    // Saldo TRC20 przez /wallet/triggerconstantcontract -> hex -> BigInteger
    suspend fun getTrc20Balance(contractBase58: String, holderBase58: String): BigInteger {
        // ABI-encoded parameter dla balanceOf(address)
        val paramHex = abiEncodeAddressParameter(holderBase58)

        val body = mapOf(
            "contract_address" to contractBase58,
            "function_selector" to "balanceOf(address)",
            "parameter" to paramHex,
            "owner_address" to holderBase58, // opcjonalnie: adres wywołującego (Base58, bo visible=true)
            "visible" to true
        )

        log.info("POST {} /wallet/triggerconstantcontract body={}", baseUrl, body)

        val resp = web.post()
            .uri("/wallet/triggerconstantcontract")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map::class.java)
            .awaitSingleOrNull()

        val hex = (resp?.get("constant_result") as? List<*>)?.firstOrNull() as? String
        if (hex.isNullOrBlank()) {
            log.warn("Empty constant_result for contract={}, holder={}", contractBase58, holderBase58)
            return BigInteger.ZERO
        }
        return BigInteger(hex, 16)
    }

    // Helper: Base58Check → 20 bajtów adresu (bez prefiksu 0x41), potem ABI (32 bajty, lewostronne zera)
    private fun abiEncodeAddressParameter(addressBase58: String): String {
        val addrBytesWithPrefix = decodeBase58Check(addressBase58)
        require(addrBytesWithPrefix.size == 21 && addrBytesWithPrefix[0] == 0x41.toByte()) {
            "Invalid Tron address"
        }
        val addr20 = addrBytesWithPrefix.copyOfRange(1, 21) // 20 bytes, bez 0x41
        val word = ByteArray(32)
        // skopiuj 20 bajtów na koniec 32-bajtowego słowa
        System.arraycopy(addr20, 0, word, 12, 20)
        return word.joinToString(separator = "") { "%02x".format(it) }
    }

    // Minimalna implementacja Base58Check dla Tron (BTC alphabet)
    private fun decodeBase58Check(input: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = BigInteger.ZERO
        val base = BigInteger.valueOf(58)
        for (ch in input) {
            val idx = alphabet.indexOf(ch)
            require(idx >= 0) { "Invalid Base58 char: $ch" }
            num = num.multiply(base).add(BigInteger.valueOf(idx.toLong()))
        }
        // liczba w big-endian
        var bytes = num.toByteArray()
        if (bytes.size > 0 && bytes[0] == 0.toByte()) {
            // usuń znak
            bytes = bytes.copyOfRange(1, bytes.size)
        }
        // liczba wiodących '1' -> wiodące zera
        val leadingZeros = input.takeWhile { it == '1' }.length
        val withLeading = ByteArray(leadingZeros) + bytes
        require(withLeading.size >= 4) { "Too short for checksum" }
        val payload = withLeading.copyOfRange(0, withLeading.size - 4)
        val checksum = withLeading.copyOfRange(withLeading.size - 4, withLeading.size)
        val hash = sha256(sha256(payload))
        require(Arrays.equals(checksum, hash.copyOfRange(0, 4))) { "Bad checksum" }
        return payload
    }

    private fun sha256(data: ByteArray): ByteArray {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(data)
    }
}
