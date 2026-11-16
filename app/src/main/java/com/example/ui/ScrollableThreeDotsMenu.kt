package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark

/**
 * Scrollable 3-dots / overflow DropdownMenu with an extended professional length (740.dp)
 * while strictly respecting phone screen bounds and allowing smooth scrolling for all items.
 */
@Composable
fun ScrollableThreeDotsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxMenuHeight: Dp = 740.dp,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    border: BorderStroke = BorderStroke(1.dp, SurfaceCardBorder),
    containerColor: Color = SurfaceDark,
    content: @Composable ColumnScope.() -> Unit
) {
    LaunchedEffect(expanded) {
        if (expanded) {
            scrollState.scrollTo(0)
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        scrollState = scrollState,
        shape = RoundedCornerShape(16.dp),
        containerColor = containerColor,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        border = border,
        modifier = modifier
            .widthIn(min = 260.dp, max = 310.dp)
            .heightIn(max = maxMenuHeight),
        content = content
    )
}
