package com.example.cryptobalance.service

import com.example.cryptobalance.repo.TxRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.Instant

data class SystemTokenBalance(val token: String, val amount: BigInteger)

data class SystemBalanceResponse(
    val chain: String,                // zwracamy nazwę walletu
    val address: String,
    val block_number: Long,           // nazwa zgodna z JSON-em
    val balances: List<SystemTokenBalance>,
    val checkedAt: Instant = Instant.now()
)

@Service
class SystemBalanceService(
    private val repo: TxRepository,
    @Value("\${app.wallet-name:TRON}") private val defaultWallet: String
) {

    /**
     * Wszystkie tokeny dla adresu – lista tokenów pochodzi z konfiguracji lub stałej.
     * Jeśli nie masz jeszcze endpointu /system/{address} (bez tokena), możesz tymczasowo
     * zasilić to stałą listą, np. TRX i USDT.
     */
    fun balanceForAddress(address: String, blockNumber: Long): SystemBalanceResponse {
        val wallet = defaultWallet
        val tokens = listOf("TRX", "USDT")   // lub wczytanie z app.tokens

        val balances = tokens.map { t ->
            val sum = repo.sumAmountForAddress(wallet, address, t, blockNumber)
            SystemTokenBalance(token = t, amount = sum)
        }.filter { it.amount != BigInteger.ZERO } // opcjonalnie

        return SystemBalanceResponse(
            chain = wallet,
            address = address,
            block_number = blockNumber,
            balances = balances
        )
    }

    /**
     * Tylko jeden token dla adresu (endpoint /system/{address}/{token}).
     */
    fun balanceForAddressToken(address: String, token: String, blockNumber: Long): SystemBalanceResponse {
        val wallet = defaultWallet
        val sum = repo.sumAmountForAddress(wallet, address, token.uppercase(), blockNumber)

        return SystemBalanceResponse(
            chain = wallet,
            address = address,
            block_number = blockNumber,
            balances = listOf(SystemTokenBalance(token = token.uppercase(), amount = sum))
        )
    }
}
