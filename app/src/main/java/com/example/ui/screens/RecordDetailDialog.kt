package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PersonRecord
import com.example.ui.components.HudButton
import com.example.ui.components.HudCard
import com.example.ui.components.triggerHudHaptic
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudBackground
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudBorderBright
import com.example.ui.theme.HudCyan
import com.example.ui.theme.HudGreen
import com.example.ui.theme.HudSurfaceElevated
import com.example.ui.theme.HudTextDim
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HudTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailDialog(
    record: PersonRecord,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val copyToClipboard = { label: String, text: String ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        triggerHudHaptic(context, 20)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        HudCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = HudCyan,
            backgroundColor = HudBackground,
            cornerLength = 16.dp,
            title = "CLASSIFIED RECORD // INDEX #${record.id}",
            titleColor = HudCyan,
            statusBadge = "VERIFIED",
            statusColor = HudGreen
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECORD TELEMETRY",
                        color = HudTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = HudTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                RecordFieldRow(
                    label = "NATIONAL CODE",
                    value = record.nationalCode.ifEmpty { "N/A" },
                    icon = Icons.Default.Fingerprint,
                    accentColor = HudCyan,
                    onCopy = { copyToClipboard("National Code", record.nationalCode) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                RecordFieldRow(
                    label = "CARD NUMBER",
                    value = formatCardNumber(record.cardNo),
                    icon = Icons.Default.CreditCard,
                    accentColor = HudAmber,
                    onCopy = { copyToClipboard("Card Number", record.cardNo) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                RecordFieldRow(
                    label = "FULL NAME",
                    value = record.fullName.ifEmpty { "N/A" },
                    icon = Icons.Default.Person,
                    accentColor = HudGreen,
                    onCopy = { copyToClipboard("Full Name", record.fullName) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                RecordFieldRow(
                    label = "BIRTH DATE",
                    value = record.birthDate.ifEmpty { "N/A" },
                    icon = Icons.Default.DateRange,
                    accentColor = HudTextSecondary,
                    onCopy = { copyToClipboard("Birth Date", record.birthDate) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                RecordFieldRow(
                    label = "MOBILE NUMBER",
                    value = record.mobile.ifEmpty { "N/A" },
                    icon = Icons.Default.Phone,
                    accentColor = HudCyan,
                    onCopy = { copyToClipboard("Mobile", record.mobile) }
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HudButton(
                        text = "COPY ALL",
                        onClick = {
                            val allData = """
                                NATIONAL_CODE: ${record.nationalCode}
                                CARD_NO: ${record.cardNo}
                                FULL_NAME: ${record.fullName}
                                BIRTH_DATE: ${record.birthDate}
                                MOBILE: ${record.mobile}
                            """.trimIndent()
                            copyToClipboard("Full Record", allData)
                        },
                        modifier = Modifier.weight(1f),
                        accentColor = HudCyan,
                        icon = Icons.Default.ContentCopy
                    )

                    HudButton(
                        text = "CLOSE",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        accentColor = HudBorderBright,
                        isSecondary = true
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordFieldRow(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    onCopy: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopy() },
        color = HudSurfaceElevated,
        shape = RoundedCornerShape(2.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, HudBorder)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                tint = HudTextSecondary,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = label,
                        color = HudTextDim,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp,
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = value,
                        color = HudTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.End
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatCardNumber(card: String): String {
    val clean = card.replace(" ", "")
    if (clean.length == 16) {
        return "${clean.substring(0, 4)} ${clean.substring(4, 8)} ${clean.substring(8, 12)} ${clean.substring(12, 16)}"
    }
    return card.ifEmpty { "N/A" }
}
