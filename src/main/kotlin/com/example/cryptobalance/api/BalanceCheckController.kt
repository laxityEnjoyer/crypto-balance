package com.example.cryptobalance.api

import com.example.cryptobalance.service.BalanceCheckService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/check")
class BalanceCheckController(private val svc: BalanceCheckService) {

    @GetMapping("/{chain}/{address}")
    suspend fun checkAddress(
        @PathVariable chain: String,
        @PathVariable address: String,
        @RequestParam block: Long,
        @RequestParam(defaultValue = "1000") epsilon: Long,
        @RequestParam(defaultValue = "false") persist: Boolean
    ) = svc.checkAddress(chain, address, block, epsilon, persist)

    @GetMapping("/{chain}/{address}/{token}")
    suspend fun checkAddressToken(
        @PathVariable chain: String,
        @PathVariable address: String,
        @PathVariable token: String,
        @RequestParam block: Long,
        @RequestParam(defaultValue = "1000") epsilon: Long,
        @RequestParam(defaultValue = "false") persist: Boolean
    ) = svc.checkAddressToken(chain, address, token, block, epsilon, persist)
}
