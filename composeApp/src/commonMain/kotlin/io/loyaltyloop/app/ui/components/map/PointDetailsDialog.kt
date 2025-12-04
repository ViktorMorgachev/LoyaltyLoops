package io.loyaltyloop.app.ui.components.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.WeekDay
import io.loyaltyloop.shared.models.WeeklyScheduleDto
import org.jetbrains.compose.resources.stringResource
import loyaltyloop.composeapp.generated.resources.*

@Composable
fun PointDetailsDialog(
    point: TradingPointDto,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = point.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(end = 32.dp)
                            .align(Alignment.CenterStart)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-8).dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status & Type Chips
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isOpen = point.isOpenNow == true
                    
                    Surface(
                        color = if (isOpen) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isOpen) stringResource(Res.string.common_open) else stringResource(Res.string.common_closed),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isOpen) Color(0xFF2E7D32) else Color(0xFFC62828),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(getLabelResource(point.type)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                // Address
                DetailRow(
                    icon = Icons.Default.LocationOn,
                    title = stringResource(Res.string.point_details_address),
                    content = point.address ?: stringResource(Res.string.point_no_address)
                )

                // Schedule
                point.schedule?.let { schedule ->
                    Spacer(modifier = Modifier.height(12.dp))
                    ScheduleSection(schedule)
                }

                // Contacts
                if (!point.contactPhone.isNullOrEmpty() || !point.contactLink.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ContactsSection(
                        phone = point.contactPhone,
                        link = point.contactLink,
                        onLinkClick = { uriHandler.openUri(it) }
                    )
                }

                // Info
                if (!point.additionalInfo.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow(
                        icon = Icons.Default.Info,
                        title = stringResource(Res.string.point_details_info),
                        content = point.additionalInfo!!
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, title: String, content: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ScheduleSection(schedule: WeeklyScheduleDto) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(Res.string.point_details_schedule),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            WeekDay.entries.forEach { day ->
                val workingDay = schedule.days.find { it.day == day }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(getDayLabelResource(day)),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (workingDay != null) FontWeight.Bold else FontWeight.Normal,
                        color = if (workingDay != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(40.dp)
                    )
                    
                    if (workingDay != null && workingDay.intervals.isNotEmpty()) {
                        val intervalsStr = workingDay.intervals.joinToString(", ") { "${it.opensAt} - ${it.closesAt}" }
                        Text(
                            text = intervalsStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                         Text(
                            text = stringResource(Res.string.point_details_closed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactsSection(phone: String?, link: String?, onLinkClick: (String) -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Default.Phone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(Res.string.point_details_contacts),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (!phone.isNullOrEmpty()) {
                OutlinedButton(
                    onClick = { onLinkClick("tel:$phone") },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(phone, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            
            if (!link.isNullOrEmpty()) {
                val (res, color) = getSocialLinkData(link)
                OutlinedButton(
                    onClick = { onLinkClick(link) },
                    border = BorderStroke(1.dp, color),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                     Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                         val icon = when {
                             link.contains("t.me") -> Icons.AutoMirrored.Filled.Send
                             link.contains("wa.me") -> Icons.Default.Call
                             else -> Icons.Default.Share
                         }
                         Icon(icon, null, modifier = Modifier.size(16.dp))
                         Spacer(modifier = Modifier.width(8.dp))
                         Text(stringResource(res), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                     }
                }
            }
        }
    }
}

// Helpers
fun getDayLabelResource(day: WeekDay): org.jetbrains.compose.resources.StringResource {
    return when(day) {
        WeekDay.MONDAY -> Res.string.point_details_day_mon
        WeekDay.TUESDAY -> Res.string.point_details_day_tue
        WeekDay.WEDNESDAY -> Res.string.point_details_day_wed
        WeekDay.THURSDAY -> Res.string.point_details_day_thu
        WeekDay.FRIDAY -> Res.string.point_details_day_fri
        WeekDay.SATURDAY -> Res.string.point_details_day_sat
        WeekDay.SUNDAY -> Res.string.point_details_day_sun
    }
}

fun getSocialLinkData(url: String): Pair<org.jetbrains.compose.resources.StringResource, Color> {
    return when {
        url.contains("t.me") || url.contains("telegram") -> Res.string.point_details_social_telegram to Color(0xFF229ED9)
        url.contains("wa.me") || url.contains("whatsapp") -> Res.string.point_details_social_whatsapp to Color(0xFF25D366)
        url.contains("instagram") -> Res.string.point_details_social_instagram to Color(0xFFE1306C)
        else -> Res.string.point_details_info to Color.Gray
    }
}
