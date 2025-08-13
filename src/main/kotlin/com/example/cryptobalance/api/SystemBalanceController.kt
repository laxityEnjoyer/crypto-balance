package com.example.cryptobalance.api

import com.example.cryptobalance.service.SystemBalanceService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/system")
class SystemBalanceController(private val svc: SystemBalanceService) {

    @GetMapping("/{chain}/{address}")
    fun systemBalanceAddress(
        @PathVariable chain: String,
        @PathVariable address: String,
        @RequestParam block: Long
    ) = svc.balanceForAddress(chain, address, block)

    @GetMapping("/{chain}/{address}/{token}")
    fun systemBalanceAddressToken(
        @PathVariable chain: String,
        @PathVariable address: String,
        @PathVariable token: String,
        @RequestParam block: Long
    ) = svc.balanceForAddressToken(chain, address, token, block)

}
