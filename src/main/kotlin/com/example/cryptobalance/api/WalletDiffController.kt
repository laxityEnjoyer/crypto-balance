package com.example.cryptobalance.api

import com.example.cryptobalance.service.BalanceCheckService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/wallet")
class WalletDiffController(private val checkService: BalanceCheckService) {

    // GET /wallet/{address}/{token}?block_number=47000005
    @GetMapping("/{address}/{token}")
    suspend fun diffForAddressToken(
        @PathVariable address: String,
        @PathVariable token: String,
        @RequestParam("block_number") blockNumber: Long
    ) = checkService.diffForAddressToken(address, token, blockNumber)
}
