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
    @Value("\${app.wallet-name:TRON}") private val defaultChain: String
) {

    fun balanceForAddress(address: String, blockNumber: Long): SystemBalanceResponse {
        val chain = defaultChain
        val tokens = listOf("TRX", "USDT")

        val balances = tokens.map { t ->
            val sum = repo.sumAmountForAddress(chain, address, t, blockNumber)
            SystemTokenBalance(token = t, amount = sum)
        }.filter { it.amount != BigInteger.ZERO }

        return SystemBalanceResponse(
            chain = chain,
            address = address,
            block_number = blockNumber,
            balances = balances
        )
    }

    fun balanceForAddressToken(address: String, token: String, blockNumber: Long): SystemBalanceResponse {
        val chain = defaultChain
        val sum = repo.sumAmountForAddress(chain, address, token.uppercase(), blockNumber)

        return SystemBalanceResponse(
            chain = chain,
            address = address,
            block_number = blockNumber,
            balances = listOf(SystemTokenBalance(token = token.uppercase(), amount = sum))
        )
    }
}

