package com.example.cryptobalance.api

import com.example.cryptobalance.service.SystemBalanceService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/system")
class SystemBalanceController(private val svc: SystemBalanceService) {

    @GetMapping("/{address}")
    fun systemBalanceAddress(
        @PathVariable address: String,
        @RequestParam block: Long
    ) = svc.balanceForAddress(chain, address, block)

    @GetMapping("/{address}/{token}")
    fun systemBalanceAddressToken(
        @PathVariable address: String,
        @PathVariable token: String,
        @RequestParam block: Long
    ) = svc.balanceForAddressToken(chain, address, token.uppercase(), block_number)
}
