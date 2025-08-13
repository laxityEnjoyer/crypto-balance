package com.example.cryptobalance.service

import com.example.cryptobalance.repo.TxRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.Instant

data class SystemTokenBalance(val token: String, val amount: BigInteger)
data class SystemBalanceResponse(
    val chain: String,             // tu: wallet_name z configu
    val address: String,
    val block_number: Long,
    val balances: List<SystemTokenBalance>,
    val checkedAt: Instant = Instant.now()
)

@Service
class SystemBalanceService(
    private val repo: TxRepository,
    @Value("\${app.wallet-name:TRON}") private val walletName: String
) {

    fun balanceForAddress(address: String, blockNumber: Long): SystemBalanceResponse {
        val tokens = repo.tokensForAddress(walletName, address)
        val balances = tokens.map { t ->
            val sum = repo.sumAmountForAddress(walletName, address, t, blockNumber)
            SystemTokenBalance(t, sum)
        }.sortedBy { it.token }
        return SystemBalanceResponse(walletName, address, blockNumber, balances)
    }

    fun balanceForAddressToken(address: String, token: String, blockNumber: Long): SystemBalanceResponse {
        val sum = repo.sumAmountForAddress(walletName, address, token.uppercase(), blockNumber)
        return SystemBalanceResponse(
            walletName, address, blockNumber,
            listOf(SystemTokenBalance(token.uppercase(), sum))
        )
    }
}
