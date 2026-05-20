package com.example.beans.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceSheetScreen(
    groupedBalances: Map<String, Map<String, Map<String, BigDecimal>>>,
    onBack: () -> Unit
) {
    val accountTypeOrder = listOf("Assets", "Liabilities", "Equity", "Income", "Expenses")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Balance Sheet") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val orderedTypes = accountTypeOrder.filter { groupedBalances.containsKey(it) }

            for (accountType in orderedTypes) {
                val accounts = groupedBalances[accountType] ?: continue

                item(key = "header_$accountType") {
                    Text(
                        text = accountType,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                val sortedAccounts = accounts.entries.sortedBy { it.key }
                items(sortedAccounts, key = { "balance_${it.key}" }) { (account, currencies) ->
                    AccountBalanceRow(account = account, currencies = currencies)
                }

                // Type total
                item(key = "total_$accountType") {
                    val totals = mutableMapOf<String, BigDecimal>()
                    for ((_, currencies) in accounts) {
                        for ((currency, amount) in currencies) {
                            totals[currency] = (totals[currency] ?: BigDecimal.ZERO).add(amount)
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total $accountType",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            for ((currency, total) in totals) {
                                Text(
                                    text = "${total.toPlainString()} $currency",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = balanceColor(total)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun AccountBalanceRow(
    account: String,
    currencies: Map<String, BigDecimal>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = account,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            for ((currency, amount) in currencies) {
                Text(
                    text = "${amount.toPlainString()} $currency",
                    style = MaterialTheme.typography.bodyMedium,
                    color = balanceColor(amount)
                )
            }
        }
    }
}

@Composable
private fun balanceColor(amount: BigDecimal): androidx.compose.ui.graphics.Color {
    return when {
        amount > BigDecimal.ZERO -> MaterialTheme.colorScheme.primary
        amount < BigDecimal.ZERO -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
}
