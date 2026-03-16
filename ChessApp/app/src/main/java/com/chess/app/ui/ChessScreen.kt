package com.chess.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chess.app.engine.*
import com.chess.app.ui.theme.ChessColors

@Composable
fun ChessScreen(vm: ChessViewModel = viewModel()) {
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    val cellSize = ((screenWidth - 48.dp) / 8).coerceAtMost(52.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChessColors.Background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top player (opponent - depends on flip)
        val topColor = if (vm.flipped) PieceColor.WHITE else PieceColor.BLACK
        val bottomColor = if (vm.flipped) PieceColor.BLACK else PieceColor.WHITE

        PlayerBar(
            color = topColor,
            clock = if (topColor == PieceColor.WHITE) vm.clockWhite else vm.clockBlack,
            isActive = vm.turn == topColor && !vm.isGameOver,
            clockRunning = vm.clockRunning,
            capturedPieces = if (topColor == PieceColor.WHITE) vm.capturedBlack else vm.capturedWhite,
            materialAdv = vm.materialDiff.let { if (topColor == PieceColor.WHITE) it else -it },
            capturedColor = topColor.opposite()
        )

        Spacer(Modifier.height(4.dp))

        // Status banner
        StatusBanner(vm)

        Spacer(Modifier.height(4.dp))

        // Board with coordinates
        BoardWithCoords(vm, cellSize)

        Spacer(Modifier.height(4.dp))

        // Bottom player
        PlayerBar(
            color = bottomColor,
            clock = if (bottomColor == PieceColor.WHITE) vm.clockWhite else vm.clockBlack,
            isActive = vm.turn == bottomColor && !vm.isGameOver,
            clockRunning = vm.clockRunning,
            capturedPieces = if (bottomColor == PieceColor.WHITE) vm.capturedBlack else vm.capturedWhite,
            materialAdv = vm.materialDiff.let { if (bottomColor == PieceColor.WHITE) it else -it },
            capturedColor = bottomColor.opposite()
        )

        Spacer(Modifier.height(8.dp))

        // Buttons
        ButtonRow(vm)

        Spacer(Modifier.height(8.dp))

        // Move history
        MoveHistoryPanel(vm.moveHistory)
    }

    // Promotion dialog
    if (vm.showPromotion) {
        PromotionDialog(
            color = vm.turn,
            onSelect = { vm.onPromotion(it) }
        )
    }
}

@Composable
fun PlayerBar(
    color: PieceColor,
    clock: Int,
    isActive: Boolean,
    clockRunning: Boolean,
    capturedPieces: List<PieceType>,
    materialAdv: Int,
    capturedColor: PieceColor
) {
    val borderColor = if (isActive) ChessColors.Accent else ChessColors.SurfaceBorder
    val clockBg = if (isActive && clockRunning) ChessColors.AccentDark else ChessColors.ClockBackground
    val clockTextColor = when {
        clock <= 30 && clockRunning -> ChessColors.Danger
        isActive && clockRunning -> ChessColors.Accent
        else -> ChessColors.TextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ChessColors.Surface)
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color indicator + name
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(if (color == PieceColor.WHITE) Color.White else Color(0xFF222222))
                .border(1.dp, Color(0xFF666666), CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (color == PieceColor.WHITE) "Blancas" else "Negras",
            color = ChessColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )

        Spacer(Modifier.width(12.dp))

        // Captured pieces
        val sorted = capturedPieces.sortedByDescending { it.value }
        val capturedStr = sorted.joinToString("") {
            ChessPiece(capturedColor, it).unicode
        }
        Text(
            text = capturedStr,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )

        if (materialAdv > 0) {
            Text(
                text = "+$materialAdv",
                color = ChessColors.Accent,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(Modifier.width(8.dp))
        }

        // Clock
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(clockBg)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = formatClock(clock),
                color = clockTextColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun StatusBanner(vm: ChessViewModel) {
    val (text, bgColor, textColor) = when {
        vm.isGameOver && vm.isCheckmate -> {
            val winner = if (vm.turn == PieceColor.WHITE) "Negras" else "Blancas"
            Triple("♔ ¡Jaque Mate! — Ganan $winner", Color(0x26FFD700), ChessColors.Gold)
        }
        vm.isGameOver && vm.isStalemate -> {
            Triple("½ — ½ Tablas (Ahogado)", Color(0x26FFD700), ChessColors.Gold)
        }
        vm.isGameOver && (vm.clockWhite <= 0 || vm.clockBlack <= 0) -> {
            val winner = if (vm.clockWhite <= 0) "Negras" else "Blancas"
            Triple("⏱ Tiempo agotado — Ganan $winner", Color(0x26FFD700), ChessColors.Gold)
        }
        vm.isCheck -> {
            val colorName = if (vm.turn == PieceColor.WHITE) "Blancas" else "Negras"
            Triple("⚠ ¡JAQUE! — Turno de $colorName", Color(0x33E94560), ChessColors.Danger)
        }
        else -> {
            val colorName = if (vm.turn == PieceColor.WHITE) "Blancas" else "Negras"
            Triple("Turno de $colorName", Color(0x1A4ECDC4), ChessColors.Accent)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun BoardWithCoords(vm: ChessViewModel, cellSize: Dp) {
    Column {
        Row {
            // Left margin for rank labels
            Spacer(Modifier.width(18.dp))
            // Board columns
            for (ci in 0..7) {
                val c = if (vm.flipped) 7 - ci else ci
                Box(
                    modifier = Modifier.size(cellSize),
                    contentAlignment = Alignment.Center
                ) {
                    // file labels on top (optional - we put on bottom)
                }
            }
        }

        for (ri in 0..7) {
            val r = if (vm.flipped) 7 - ri else ri
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Rank label
                Text(
                    text = "${8 - r}",
                    color = ChessColors.TextDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(18.dp),
                    textAlign = TextAlign.Center
                )
                for (ci in 0..7) {
                    val c = if (vm.flipped) 7 - ci else ci
                    ChessCell(vm, r, c, cellSize)
                }
            }
        }

        // File labels
        Row {
            Spacer(Modifier.width(18.dp))
            for (ci in 0..7) {
                val c = if (vm.flipped) 7 - ci else ci
                Box(
                    modifier = Modifier
                        .size(width = cellSize, height = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${"abcdefgh"[c]}",
                        color = ChessColors.TextDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun ChessCell(vm: ChessViewModel, row: Int, col: Int, size: Dp) {
    val isLight = (row + col) % 2 == 0
    val piece = vm.boardState.getOrNull(row)?.getOrNull(col)
    val isSelected = vm.selectedSquare?.row == row && vm.selectedSquare?.col == col
    val isValidMove = vm.validMoves.any { it.row == row && it.col == col }
    val isLastMoveSquare = vm.lastMove?.let {
        (it.from.row == row && it.from.col == col) || (it.to.row == row && it.to.col == col)
    } ?: false
    val isKingInCheck = vm.kingInCheckSquare?.row == row && vm.kingInCheckSquare?.col == col

    val bgColor = when {
        isSelected -> ChessColors.Selected
        isKingInCheck -> ChessColors.CheckRed
        isValidMove && piece != null -> ChessColors.CaptureHighlight
        isLastMoveSquare -> if (isLight) Color(0xFFF2F287) else Color(0xFFD4C34A)
        isLight -> ChessColors.LightSquare
        else -> ChessColors.DarkSquare
    }

    Box(
        modifier = Modifier
            .size(size)
            .background(bgColor)
            .clickable { vm.onSquareClick(row, col) },
        contentAlignment = Alignment.Center
    ) {
        // Valid move dot
        if (isValidMove && piece == null) {
            Box(
                modifier = Modifier
                    .size(size * 0.3f)
                    .clip(CircleShape)
                    .background(ChessColors.ValidMove)
            )
        }

        // Capture ring
        if (isValidMove && piece != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, ChessColors.Danger.copy(alpha = 0.6f))
            )
        }

        // Piece
        if (piece != null) {
            Text(
                text = piece.unicode,
                fontSize = (size.value * 0.65f).sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ButtonRow(vm: ChessViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        ChessButton("Nueva Partida") { vm.newGame() }
        ChessButton("Girar") { vm.flipBoard() }
        ChessButton("Deshacer") { vm.undo() }
    }
}

@Composable
fun ChessButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ChessColors.Surface)
            .border(1.5.dp, ChessColors.SurfaceBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = ChessColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun MoveHistoryPanel(moves: List<String>) {
    val listState = rememberLazyListState()

    LaunchedEffect(moves.size) {
        if (moves.isNotEmpty()) {
            listState.animateScrollToItem((moves.size - 1) / 2)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(ChessColors.Surface)
            .border(1.5.dp, ChessColors.SurfaceBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "MOVIMIENTOS",
            color = ChessColors.TextDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))

        val movePairs = moves.chunked(2)
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            items(movePairs.size) { idx ->
                val pair = movePairs[idx]
                Row(modifier = Modifier.padding(vertical = 1.dp)) {
                    Text(
                        text = "${idx + 1}.",
                        color = ChessColors.TextDim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.End
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = pair[0],
                        color = ChessColors.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.width(60.dp)
                    )
                    if (pair.size > 1) {
                        Text(
                            text = pair[1],
                            color = ChessColors.TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.width(60.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PromotionDialog(color: PieceColor, onSelect: (PieceType) -> Unit) {
    val pieces = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(ChessColors.Surface)
                .border(2.dp, ChessColors.Accent, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Elige promoción",
                color = ChessColors.Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (type in pieces) {
                    val piece = ChessPiece(color, type)
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ChessColors.SurfaceBorder)
                            .clickable { onSelect(type) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = piece.unicode,
                            fontSize = 38.sp
                        )
                    }
                }
            }
        }
    }
}

fun formatClock(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
