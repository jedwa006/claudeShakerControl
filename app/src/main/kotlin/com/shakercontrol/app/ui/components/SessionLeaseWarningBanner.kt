package com.shakercontrol.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shakercontrol.app.domain.model.SessionLeaseStatus
import com.shakercontrol.app.ui.theme.SemanticColors
import com.shakercontrol.app.ui.theme.ShakerControlTheme

/**
 * Warning banner displayed when the session lease is approaching expiry or has expired.
 * This indicates that the HMI may lose its ability to control the machine.
 */
@Composable
fun SessionLeaseWarningBanner(
    sessionLeaseStatus: SessionLeaseStatus,
    modifier: Modifier = Modifier
) {
    val shouldShow = sessionLeaseStatus == SessionLeaseStatus.WARNING ||
            sessionLeaseStatus == SessionLeaseStatus.EXPIRED

    AnimatedVisibility(
        visible = shouldShow,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        val backgroundColor = when (sessionLeaseStatus) {
            SessionLeaseStatus.EXPIRED -> SemanticColors.Critical.copy(alpha = 0.15f)
            SessionLeaseStatus.WARNING -> SemanticColors.Warning.copy(alpha = 0.15f)
            else -> SemanticColors.Warning.copy(alpha = 0.15f)
        }

        val contentColor = when (sessionLeaseStatus) {
            SessionLeaseStatus.EXPIRED -> SemanticColors.Critical
            else -> SemanticColors.Warning
        }

        val message = when (sessionLeaseStatus) {
            SessionLeaseStatus.EXPIRED -> "Session expired. Commands may not be accepted. Check connection."
            SessionLeaseStatus.WARNING -> "Session approaching timeout. Heartbeat may be delayed."
            else -> ""
        }

        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Preview(widthDp = 800)
@Composable
private fun SessionLeaseWarningBannerWarningPreview() {
    ShakerControlTheme {
        SessionLeaseWarningBanner(
            sessionLeaseStatus = SessionLeaseStatus.WARNING
        )
    }
}

@Preview(widthDp = 800)
@Composable
private fun SessionLeaseWarningBannerExpiredPreview() {
    ShakerControlTheme {
        SessionLeaseWarningBanner(
            sessionLeaseStatus = SessionLeaseStatus.EXPIRED
        )
    }
}
