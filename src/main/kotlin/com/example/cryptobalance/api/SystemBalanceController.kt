package com.example.cryptobalance.api

import com.example.cryptobalance.service.SystemBalanceService
import org.springframework.web.bind.annotation.*

/**
 * REST controller exposing off-chain (system-side) balance queries.
 *
 * All balances are calculated as the sum of transaction deltas recorded in Cassandra
 * up to and including the requested [block_number].
 *
 * Base path: `/system`
 */
@RestController
@RequestMapping("/system")
class SystemBalanceController(private val systemService: SystemBalanceService) {

    /**
     * Returns off-chain balances for all supported tokens at the given block height.
     *
     * `GET /system/{address}?block_number={blockNumber}`
     */
    @GetMapping("/{address}")
    fun allTokensForAddress(
        @PathVariable address: String,
        @RequestParam("block_number") blockNumber: Long
    ) = systemService.balanceForAddress(address, blockNumber)

    /**
     * Returns the off-chain balance for a specific token at the given block height.
     *
     * `GET /system/{address}/{token}?block_number={blockNumber}`
     */
    @GetMapping("/{address}/{token}")
    fun singleTokenForAddress(
        @PathVariable address: String,
        @PathVariable token: String,
        @RequestParam("block_number") blockNumber: Long
    ) = systemService.balanceForAddressToken(address, token, blockNumber)
}
