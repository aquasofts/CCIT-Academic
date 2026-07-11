package edu.ccit.webvpn.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object WebVpnColors {
    val Shell = Color(0xFFF8F2F3)
    val Surface = Color(0xFFFCF8F8)
    val Card = Color(0xFFF6EFEF)
    val CardStrong = Color(0xFFE2CED2)
    val Ink = Color(0xFF2A2527)
    val InkMuted = Color(0xFF6E6266)
    val Stroke = Color(0xFFE4DADB)
    val Rose = Color(0xFFCCADB2)
    val Brown = Color(0xFF705D61)
    val Success = Color(0xFF74A824)
}

private val WebVpnColorScheme = lightColorScheme(
    primary = WebVpnColors.Brown,
    onPrimary = Color.White,
    secondary = WebVpnColors.Rose,
    onSecondary = WebVpnColors.Ink,
    background = WebVpnColors.Shell,
    onBackground = WebVpnColors.Ink,
    surface = WebVpnColors.Surface,
    onSurface = WebVpnColors.Ink,
    surfaceVariant = WebVpnColors.Card,
    onSurfaceVariant = WebVpnColors.InkMuted,
    outline = WebVpnColors.Stroke,
    tertiary = WebVpnColors.Success,
)

@Composable
fun WebVpnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WebVpnColorScheme,
        typography = WebVpnTypography,
        shapes = WebVpnShapes,
        content = content,
    )
}

fun Modifier.webVpnBackground(): Modifier = background(WebVpnColors.Shell)

@Composable
fun WebVpnCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = WebVpnColors.Surface),
        border = BorderStroke(1.dp, WebVpnColors.Stroke),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

@Composable
fun WebVpnPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = WebVpnColors.Brown,
            contentColor = Color.White,
            disabledContainerColor = WebVpnColors.CardStrong,
            disabledContentColor = WebVpnColors.InkMuted,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        content = content,
    )
}

@Composable
fun webVpnTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = WebVpnColors.Brown,
    unfocusedBorderColor = WebVpnColors.Stroke,
    focusedContainerColor = WebVpnColors.Surface,
    unfocusedContainerColor = WebVpnColors.Surface,
    cursorColor = WebVpnColors.Brown,
)
