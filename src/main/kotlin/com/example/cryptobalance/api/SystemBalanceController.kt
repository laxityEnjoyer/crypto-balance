package com.example.cryptobalance.api

import com.example.cryptobalance.service.SystemBalanceService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/system")
class SystemBalanceController(private val systemService: SystemBalanceService) {

    // GET /system/{address}?block_number=47000005
    @GetMapping("/{address}")
    fun allTokensForAddress(
        @PathVariable address: String,
        @RequestParam("block_number") blockNumber: Long
    ) = systemService.balanceForAddress(address, blockNumber)

    // GET /system/{address}/{token}?block_number=47000005
    @GetMapping("/{address}/{token}")
    fun singleTokenForAddress(
        @PathVariable address: String,
        @PathVariable token: String,
        @RequestParam("block_number") blockNumber: Long
    ) = systemService.balanceForAddressToken(address, token, blockNumber)
}
