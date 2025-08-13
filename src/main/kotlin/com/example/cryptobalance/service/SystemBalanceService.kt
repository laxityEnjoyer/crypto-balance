package com.example.cryptobalance.service

import com.example.cryptobalance.repo.TxRepository
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.Instant

data class SystemTokenBalance(val token: String, val amount: BigInteger)
data class SystemBalanceResponse(
    val chain: String,
    val address: String,
    val blockH: Long,
    val balances: List<SystemTokenBalance>,
    val checkedAt: Instant = Instant.now()
)

@Service
class SystemBalanceService(private val repo: TxRepository) {

    fun balanceForAddress(chain: String, address: String, blockH: Long): SystemBalanceResponse {
        val tokens = repo.tokensForAddress(chain, address)
        val balances = tokens.map { t ->
            val sum = repo.sumAmount(chain, address, t, blockH)
            SystemTokenBalance(t, sum)
        }.sortedBy { it.token }
        return SystemBalanceResponse(chain, address, blockH, balances)
    }

    fun balanceForAddressToken(chain: String, address: String, token: String, blockH: Long): SystemBalanceResponse {
        val sum = repo.sumAmount(chain, address, token.uppercase(), blockH)
        return SystemBalanceResponse(chain, address, blockH, listOf(SystemTokenBalance(token.uppercase(), sum)))
    }
}
