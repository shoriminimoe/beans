package com.example.beans.model

import java.math.BigDecimal

/**
 * One row of an account register: a transaction, its net effect on the account
 * (per currency), and the running balance after it (per currency).
 */
data class RegisterEntry(
    val transaction: Transaction,
    val change: Map<String, BigDecimal>,
    val balance: Map<String, BigDecimal>,
)
