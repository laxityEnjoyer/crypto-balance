package com.example.cryptobalance.api

import com.example.cryptobalance.service.BalanceCheckService
import org.springframework.web.bind.annotation.*

/**
 * REST controller that triggers on-chain/off-chain reconciliation checks.
 *
 * Each request fetches both the system-side (Cassandra) and on-chain (TronGrid) balances,
 * computes their difference, persists the audit record, and returns the result.
 *
 * Base path: `/wallet`
 */
@RestController
@RequestMapping("/wallet")
class WalletDiffController(private val checkService: BalanceCheckService) {

    /**
     * Runs a reconciliation check for the specified address, token, and block height.
     *
     * `GET /wallet/{address}/{token}?block_number={blockNumber}`
     *
     * @return [com.example.cryptobalance.service.DiffResponse] with system balance,
     *         on-chain balance, and their delta.
     */
    @GetMapping("/{address}/{token}")
    suspend fun diffForAddressToken(
        @PathVariable address: String,
        @PathVariable token: String,
        @RequestParam("block_number") blockNumber: Long
    ) = checkService.diffForAddressToken(address, token, blockNumber)
}
