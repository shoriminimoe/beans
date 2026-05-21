package com.example.beans.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.example.beans.frecency.FrecencyTracker
import com.example.beans.model.Amount
import com.example.beans.model.Posting
import com.example.beans.model.Transaction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class PostingField(
    val account: String = "",
    val amount: String = "",
    val currency: String = "USD",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorScreen(
    existingTransaction: Transaction?,
    frecencyTracker: FrecencyTracker,
    allAccounts: Set<String>,
    allPayees: Set<String>,
    onSave: (Transaction) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEditing = existingTransaction != null
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    var date by remember {
        mutableStateOf(existingTransaction?.date?.format(formatter) ?: LocalDate.now().format(formatter))
    }
    var flag by remember { mutableStateOf(existingTransaction?.flag ?: "*") }
    var payee by remember { mutableStateOf(existingTransaction?.payee ?: "") }
    var narration by remember { mutableStateOf(existingTransaction?.narration ?: "") }
    var postings by remember {
        mutableStateOf(
            existingTransaction?.postings?.map { p ->
                PostingField(
                    account = p.account,
                    amount = p.amount?.value?.toPlainString() ?: "",
                    currency = p.amount?.currency ?: "USD",
                )
            } ?: listOf(PostingField(), PostingField()),
        )
    }
    var dateError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Date picker dialog
    if (showDatePicker) {
        val initialDate =
            try {
                LocalDate.parse(date, formatter)
            } catch (_: DateTimeParseException) {
                LocalDate.now()
            }
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    initialDate
                        .atStartOfDay(ZoneId.of("UTC"))
                        .toInstant()
                        .toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selected =
                            Instant
                                .ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                        date = selected.format(formatter)
                        dateError = false
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Transaction" else "New Transaction") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val parsedDate =
                                try {
                                    LocalDate.parse(date, formatter)
                                } catch (e: DateTimeParseException) {
                                    dateError = true
                                    return@TextButton
                                }
                            dateError = false

                            val txnPostings =
                                postings.filter { it.account.isNotBlank() }.map { pf ->
                                    val amount =
                                        if (pf.amount.isNotBlank()) {
                                            Amount(BigDecimal(pf.amount), pf.currency)
                                        } else {
                                            null
                                        }
                                    Posting(pf.account, amount)
                                }

                            val txn =
                                Transaction(
                                    date = parsedDate,
                                    flag = flag,
                                    payee = payee,
                                    narration = narration,
                                    postings = txnPostings,
                                    id =
                                        existingTransaction?.id ?: Transaction(
                                            parsedDate,
                                            flag,
                                            payee,
                                            narration,
                                            txnPostings,
                                        ).id,
                                )
                            onSave(txn)
                        },
                    ) {
                        Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Date field with picker button
            OutlinedTextField(
                value = date,
                onValueChange = {
                    date = it
                    dateError = false
                },
                label = { Text("Date (yyyy-MM-dd)") },
                isError = dateError,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
                    }
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = flag == "*",
                    onClick = { flag = "*" },
                    label = { Text("* Cleared") },
                )
                FilterChip(
                    selected = flag == "!",
                    onClick = { flag = "!" },
                    label = { Text("! Pending") },
                )
            }

            // Payee with autocomplete
            AutocompleteTextField(
                value = payee,
                onValueChange = { payee = it },
                label = "Payee",
                suggestions = { query -> frecencyTracker.suggestPayees(query) },
            )

            OutlinedTextField(
                value = narration,
                onValueChange = { narration = it },
                label = { Text("Narration") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text("Postings", style = MaterialTheme.typography.titleSmall)

            postings.forEachIndexed { index, posting ->
                PostingRow(
                    posting = posting,
                    frecencyTracker = frecencyTracker,
                    onUpdate = { updated ->
                        postings = postings.toMutableList().apply { set(index, updated) }
                    },
                    onRemove =
                        if (postings.size > 2) {
                            { postings = postings.toMutableList().apply { removeAt(index) } }
                        } else {
                            null
                        },
                    onDuplicate = {
                        postings = postings.toMutableList().apply { add(index + 1, posting.copy()) }
                    },
                )
            }

            TextButton(
                onClick = { postings = postings + PostingField() },
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Posting")
            }
        }
    }
}

@Composable
private fun PostingRow(
    posting: PostingField,
    frecencyTracker: FrecencyTracker,
    onUpdate: (PostingField) -> Unit,
    onRemove: (() -> Unit)?,
    onDuplicate: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AccountAutocompleteTextField(
                        value = posting.account,
                        onValueChange = { onUpdate(posting.copy(account = it)) },
                        frecencyTracker = frecencyTracker,
                    )
                }
                IconButton(onClick = onDuplicate) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate posting")
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Remove")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = posting.amount,
                    onValueChange = { onUpdate(posting.copy(amount = it)) },
                    label = { Text("Amount") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("(auto)") },
                )
                OutlinedTextField(
                    value = posting.currency,
                    onValueChange = { onUpdate(posting.copy(currency = it)) },
                    label = { Text("Currency") },
                    modifier = Modifier.width(100.dp),
                    singleLine = true,
                )
            }
        }
    }
}

/**
 * Generic autocomplete text field for payees.
 */
@Composable
fun AutocompleteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: (String) -> List<String>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentSuggestions =
        remember(value) {
            if (value.isNotEmpty()) suggestions(value).take(5) else emptyList()
        }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        DropdownMenu(
            expanded = expanded && currentSuggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            currentSuggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Account autocomplete that completes incrementally by colon-delimited segments.
 * Selecting "Expenses" fills "Expenses:" and shows the next level.
 */
@Composable
fun AccountAutocompleteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    frecencyTracker: FrecencyTracker,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentSuggestions =
        remember(value) {
            if (value.isEmpty()) {
                frecencyTracker.completeAccountIncremental("").take(8)
            } else {
                // If value ends with ":", show next level completions
                // Otherwise show completions matching current partial input
                val prefix =
                    if (value.endsWith(":")) {
                        value
                    } else {
                        value.substringBeforeLast(":", "").let { if (it.isEmpty()) "" else "$it:" }
                    }
                val completions = frecencyTracker.completeAccountIncremental(prefix)
                completions.filter { it.startsWith(value, ignoreCase = true) }.take(8)
            }
        }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text("Account") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("e.g. Expenses:Food") },
        )
        DropdownMenu(
            expanded = expanded && currentSuggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            currentSuggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        // Check if there are deeper accounts — if so, append ":"
                        val deeper = frecencyTracker.completeAccountIncremental("$suggestion:")
                        if (deeper.isNotEmpty()) {
                            onValueChange("$suggestion:")
                            // Keep dropdown open for next level
                        } else {
                            onValueChange(suggestion)
                            expanded = false
                        }
                    },
                )
            }
        }
    }
}
