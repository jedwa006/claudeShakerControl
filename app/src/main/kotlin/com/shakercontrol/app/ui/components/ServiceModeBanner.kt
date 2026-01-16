package com.shakercontrol.app.ui.components

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shakercontrol.app.ui.theme.SemanticColors
import com.shakercontrol.app.ui.theme.ShakerControlTheme

/**
 * Service mode warning banner.
 * Spec: docs/dashboard-sec-v1.md section 12.2 and docs/ui-copy-labels-v1.md section 6.1
 */
@Composable
fun ServiceModeBanner(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SemanticColors.WarningContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = SemanticColors.Warning,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Service mode is on. Manual overrides are visible.",
            style = MaterialTheme.typography.bodyMedium,
            color = SemanticColors.Warning
        )
    }
}

@Preview(widthDp = 800)
@Composable
private fun ServiceModeBannerPreview() {
    ShakerControlTheme {
        ServiceModeBanner()
    }
}
