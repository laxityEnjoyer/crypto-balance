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

/**
 * HTTP client for the TRON blockchain network.
 *
 * Provides two categories of on-chain balance queries:
 * - **TRX (native coin):** historical balance at a specific block, using the TRON Rosetta-compatible
 *   `POST /wallet/getaccountbalance` endpoint. Requires first resolving the block hash via
 *   `POST /wallet/getblock`.
 * - **TRC-20 tokens:** current balance via `POST /wallet/triggerconstantcontract` (ABI `balanceOf`).
 *   Block-level historical queries are not supported by the TronGrid API for TRC-20 contracts.
 *
 * Two separate [WebClient] instances are maintained:
 * - [webGrid] — connects to TronGrid (`tron.baseUrl`); used for TRC-20 calls and block hash resolution.
 * - [webNode] — connects to a TRON full node (`tron.trxBaseUrl`); used for historical TRX balance queries.
 *
 * @param builder     Spring [WebClient.Builder] injected by the framework.
 * @param baseUrl     TronGrid API base URL (default: `https://api.trongrid.io`).
 * @param apiKey      TronGrid Pro API key. Leave blank to use the public rate-limited tier.
 * @param trxBaseUrl  Full-node base URL for TRX balance queries (defaults to [baseUrl]).
 */
@Component
class TronGridClient(
    builder: WebClient.Builder,
    @Value("\${tron.baseUrl:https://api.trongrid.io}") private val baseUrl: String,
    @Value("\${tron.apiKey:}") private val apiKey: String,
    @Value("\${tron.trxBaseUrl:\${tron.baseUrl:https://api.trongrid.io}}") private val trxBaseUrl: String,
) {
    private val log = LoggerFactory.getLogger(TronGridClient::class.java)

    private val httpClient: HttpClient = HttpClient.create()
        .resolver(DefaultAddressResolverGroup.INSTANCE)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000)
        .responseTimeout(Duration.ofSeconds(20))

    /** WebClient targeting TronGrid — used for TRC-20 balance queries and block resolution. */
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

    /** WebClient targeting a TRON full node — used for historical TRX balance queries. */
    private val webNode: WebClient = builder
        .clientConnector(ReactorClientHttpConnector(httpClient))
        .baseUrl(trxBaseUrl)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build()

    /**
     * Returns the TRX (native coin) balance of [addressBase58] at the given [blockNumber].
     *
     * Internally resolves the block hash first, then calls `POST /wallet/getaccountbalance`.
     * Returns [BigInteger.ZERO] if the node returns an empty response (account not yet active
     * at that block) or if the balance field is absent.
     *
     * @throws RuntimeException if the TRON node returns an error in the response body.
     */
    suspend fun getTrxBalanceAt(addressBase58: String, blockNumber: Long): BigInteger {
        val hash = getBlockHash(blockNumber)

        val body = mapOf(
            "account_identifier" to mapOf("address" to addressBase58),
            "block_identifier" to mapOf("hash" to hash, "number" to blockNumber),
            "visible" to true
        )

        log.info("POST {}/wallet/getaccountbalance address={} block={}", trxBaseUrl, addressBase58, blockNumber)

        val resp = webNode.post()
            .uri("/wallet/getaccountbalance")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map::class.java)
            .awaitSingleOrNull()

        if (resp == null) {
            log.warn("Empty response from node for address={}", addressBase58)
            return BigInteger.ZERO
        }

        if (resp.containsKey("Error")) {
            log.error("TRON node error: {}", resp["Error"])
            throw RuntimeException("TRON node error: ${resp["Error"]}")
        }

        // The API omits the 'balance' field when the balance is 0.
        val balance = (resp["balance"] as? Number)?.toLong() ?: 0L
        return BigInteger.valueOf(balance)
    }

    /**
     * Resolves the block hash for the given [blockNumber] via TronGrid.
     *
     * The hash is required by `POST /wallet/getaccountbalance` to uniquely identify the block
     * (prevents ambiguity in case of chain reorganisations).
     *
     * @throws IllegalArgumentException if the API response does not contain a valid block hash.
     */
    private suspend fun getBlockHash(blockNumber: Long): String {
        val body = mapOf("id_or_num" to blockNumber.toString(), "detail" to false)

        log.info("POST {}/wallet/getblock block={}", baseUrl, blockNumber)

        val resp = webGrid.post()
            .uri("/wallet/getblock")
            .contentType(MediaType.APPLICATION_JSON)
            .headers { headers ->
                if (apiKey.isNotBlank()) headers.set("TRON-PRO-API-KEY", apiKey)
            }
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map::class.java)
            .timeout(Duration.ofSeconds(15))
            .awaitSingleOrNull()

        val hash = resp?.get("blockID") as? String
        require(!hash.isNullOrBlank()) { "Failed to resolve block hash for block $blockNumber" }
        return hash
    }

    /**
     * Returns the current TRC-20 token balance for [holderBase58] in the contract at [contractBase58].
     *
     * Calls `POST /wallet/triggerconstantcontract` with the ABI-encoded `balanceOf(address)` selector.
     * The result is decoded from the 32-byte hex word returned in `constant_result[0]`.
     *
     * Returns [BigInteger.ZERO] if the response is empty (e.g. contract not deployed or no balance).
     */
    suspend fun getTrc20Balance(contractBase58: String, holderBase58: String): BigInteger {
        val paramHex = abiEncodeAddressParameter(holderBase58)

        val body = mapOf(
            "contract_address" to contractBase58,
            "function_selector" to "balanceOf(address)",
            "parameter" to paramHex,
            "owner_address" to holderBase58,
            "visible" to true
        )

        log.info("POST {}/wallet/triggerconstantcontract contract={} holder={}", baseUrl, contractBase58, holderBase58)

        val resp = webGrid.post()
            .uri("/wallet/triggerconstantcontract")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map::class.java)
            .awaitSingleOrNull()

        val hex = (resp?.get("constant_result") as? List<*>)?.firstOrNull() as? String
        if (hex.isNullOrBlank()) {
            log.warn("Empty constant_result for contract={} holder={}", contractBase58, holderBase58)
            return BigInteger.ZERO
        }
        return BigInteger(hex, 16)
    }

    /**
     * ABI-encodes a TRON base58-encoded address as a 32-byte EVM word (right-padded with zeros,
     * address occupying bytes 12–31).
     *
     * TRON addresses use a 21-byte format: `0x41` prefix + 20-byte Ethereum-compatible address.
     * The prefix is stripped before packing into the ABI word.
     */
    private fun abiEncodeAddressParameter(addressBase58: String): String {
        val addrBytesWithPrefix = decodeBase58Check(addressBase58)
        require(addrBytesWithPrefix.size == 21 && addrBytesWithPrefix[0] == 0x41.toByte()) {
            "Invalid TRON address: expected 21 bytes with 0x41 prefix"
        }
        val addr20 = addrBytesWithPrefix.copyOfRange(1, 21)
        val word = ByteArray(32)
        System.arraycopy(addr20, 0, word, 12, 20)
        return word.joinToString(separator = "") { "%02x".format(it) }
    }

    /**
     * Decodes a Base58Check-encoded TRON address into its raw byte payload.
     *
     * Validates the 4-byte checksum (double-SHA-256 of the payload) and returns
     * the payload bytes (without the checksum suffix).
     *
     * @throws IllegalArgumentException if the input contains invalid Base58 characters,
     *         is too short for a checksum, or the checksum does not match.
     */
    private fun decodeBase58Check(input: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = BigInteger.ZERO
        val base = BigInteger.valueOf(58)
        for (ch in input) {
            val idx = alphabet.indexOf(ch)
            require(idx >= 0) { "Invalid Base58 character: $ch" }
            num = num.multiply(base).add(BigInteger.valueOf(idx.toLong()))
        }
        var bytes = num.toByteArray()
        if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) {
            bytes = bytes.copyOfRange(1, bytes.size)
        }
        val leadingZeros = input.takeWhile { it == '1' }.length
        val withLeading = ByteArray(leadingZeros) + bytes
        require(withLeading.size >= 4) { "Decoded value too short to contain a checksum" }
        val payload = withLeading.copyOfRange(0, withLeading.size - 4)
        val checksum = withLeading.copyOfRange(withLeading.size - 4, withLeading.size)
        val hash = sha256(sha256(payload))
        require(Arrays.equals(checksum, hash.copyOfRange(0, 4))) { "Base58Check checksum mismatch" }
        return payload
    }

    private fun sha256(data: ByteArray): ByteArray {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(data)
    }
}
