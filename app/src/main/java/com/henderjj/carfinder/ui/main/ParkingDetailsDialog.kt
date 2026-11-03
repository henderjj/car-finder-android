package com.henderjj.carfinder.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.henderjj.carfinder.R

@Composable
fun ParkingDetailsDialog(
    initialLevel: String?,
    initialSection: String?,
    initialSpace: String?,
    onSave: (level: String?, section: String?, space: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var level by rememberSaveable(initialLevel) { mutableStateOf(initialLevel.orEmpty()) }
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection.orEmpty()) }
    var space by rememberSaveable(initialSpace) { mutableStateOf(initialSpace.orEmpty()) }

    val keyboard = remember {
        KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Next
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = level,
                    onValueChange = { level = it.take(16) },
                    label = { Text(stringResource(R.string.details_field_level)) },
                    singleLine = true,
                    keyboardOptions = keyboard,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = section,
                    onValueChange = { section = it.take(16) },
                    label = { Text(stringResource(R.string.details_field_section)) },
                    singleLine = true,
                    keyboardOptions = keyboard,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = space,
                    onValueChange = { space = it.take(16) },
                    label = { Text(stringResource(R.string.details_field_space)) },
                    singleLine = true,
                    keyboardOptions = keyboard.copy(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(level.trim(), section.trim(), space.trim())
            }) { Text(stringResource(R.string.details_save)) }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.details_cancel))
                }
                TextButton(onClick = { onSave(null, null, null) }) {
                    Text(stringResource(R.string.details_clear))
                }
            }
        }
    )
}
