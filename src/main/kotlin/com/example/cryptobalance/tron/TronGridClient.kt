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
import java.time.Duration
import io.netty.channel.ChannelOption
import io.netty.resolver.DefaultAddressResolverGroup
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import reactor.netty.http.client.HttpClient

@Component
class TronGridClient(
    builder: WebClient.Builder,
    @Value("\${tron.baseUrl:https://api.trongrid.io}") private val baseUrl: String,
    @Value("\${tron.apiKey:}") private val apiKey: String,
    @Value("\${tron.trxBaseUrl:\${tron.baseUrl:https://api.trongrid.io}}") private val trxBaseUrl: String,
) {
    private val log = LoggerFactory.getLogger(TronGridClient::class.java)

    private val httpClient: HttpClient = HttpClient.create()
        .resolver(DefaultAddressResolverGroup.INSTANCE) // użyj systemowego DNS resolvera
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000) // connect timeout
        .responseTimeout(Duration.ofSeconds(20)) // timeout odpowiedzi

    // WebClient do TronGrid (TRC20 itp.)
    private val webGrid: WebClient = builder
        .clientConnector(ReactorClientHttpConnector(httpClient))
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeaders { headers ->
            if (apiKey.isNotBlank()) {
                headers.add("TRON-PRO-API-KEY", apiKey)
            }
        }
        .build()

    // WebClient do noda (TRX z blokiem po hash/number)
    private val webNode: WebClient = builder
        .clientConnector(ReactorClientHttpConnector(httpClient))
        .baseUrl(trxBaseUrl)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build()

    // TRX: saldo na konkretnym bloku z użyciem block hash + number
    suspend fun getTrxBalanceAt(addressBase58: String, blockNumber: Long): BigInteger {
        val hash = getBlockHash(blockNumber)

        val body = mapOf(
            "account_identifier" to mapOf(
                "address" to addressBase58
            ),
            "block_identifier" to mapOf(
                "hash" to hash,
                "number" to blockNumber
            ),
            "visible" to true
        )

        log.info("POST {} /wallet/getaccountbalance body={}", trxBaseUrl, body)

        val resp = webNode.post()
            .uri("/wallet/getaccountbalance")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map::class.java)
            .awaitSingleOrNull()

        if (resp == null) {
            log.warn("Brak odpowiedzi z węzła dla adresu $addressBase58")
            return BigInteger.ZERO
        }

        // API Node'a zwraca 'balance' tylko jeśli jest > 0.
        // Czasem zwraca błąd w JSON, np. {"Error": "..."}
        if (resp.containsKey("Error")) {
            log.error("Błąd z API Tron: ${resp["Error"]}")
            throw RuntimeException("Tron Node Error: ${resp["Error"]}")
        }

        val balance = (resp["balance"] as? Number)?.toLong() ?: 0L
        return BigInteger.valueOf(balance)
    }

    // Pobranie hash bloku po numerze przez baseUrl (Trongrid)
    private suspend fun getBlockHash(blockNumber: Long): String {
        val body = mapOf(
            "id_or_num" to blockNumber.toString(),
            "detail" to false
        )
        log.info("POST {} /wallet/getblock body={}", baseUrl, body)

        val resp = webGrid.post()
            .uri("/wallet/getblock")
            .contentType(MediaType.APPLICATION_JSON)
            .headers { headers ->
                if (apiKey.isNotBlank()) {
                    headers.set("TRON-PRO-API-KEY", apiKey)
                }
            }
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map::class.java)
            .timeout(Duration.ofSeconds(15)) // czas na odpowiedź z bloku
            .awaitSingleOrNull()

        val hash = resp?.get("blockID") as? String
        require(!hash.isNullOrBlank()) { "Nie udało się pobrać hash dla bloku $blockNumber" }
        return hash
    }

    // Saldo TRC20 przez /wallet/triggerconstantcontract -> hex -> BigInteger (bieżący stan)
    suspend fun getTrc20Balance(contractBase58: String, holderBase58: String): BigInteger {
        val paramHex = abiEncodeAddressParameter(holderBase58)

        val body = mapOf(
            "contract_address" to contractBase58,
            "function_selector" to "balanceOf(address)",
            "parameter" to paramHex,
            "owner_address" to holderBase58,
            "visible" to true
        )

        log.info("POST {} /wallet/triggerconstantcontract body={}", baseUrl, body)

        val resp = webGrid.post()
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

    private fun abiEncodeAddressParameter(addressBase58: String): String {
        val addrBytesWithPrefix = decodeBase58Check(addressBase58)
        require(addrBytesWithPrefix.size == 21 && addrBytesWithPrefix[0] == 0x41.toByte()) {
            "Invalid Tron address"
        }
        val addr20 = addrBytesWithPrefix.copyOfRange(1, 21)
        val word = ByteArray(32)
        System.arraycopy(addr20, 0, word, 12, 20)
        return word.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun decodeBase58Check(input: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = BigInteger.ZERO
        val base = BigInteger.valueOf(58)
        for (ch in input) {
            val idx = alphabet.indexOf(ch)
            require(idx >= 0) { "Invalid Base58 char: $ch" }
            num = num.multiply(base).add(BigInteger.valueOf(idx.toLong()))
        }
        var bytes = num.toByteArray()
        if (bytes.size > 0 && bytes[0] == 0.toByte()) {
            bytes = bytes.copyOfRange(1, bytes.size)
        }
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
