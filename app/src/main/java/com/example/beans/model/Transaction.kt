package com.example.beans.model

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class Amount(
    val value: BigDecimal,
    val currency: String,
)

data class Posting(
    val account: String,
    val amount: Amount?,
)

data class Transaction(
    val date: LocalDate,
    val flag: String,
    val payee: String,
    val narration: String,
    val postings: List<Posting>,
    val id: String = UUID.randomUUID().toString(),
)
