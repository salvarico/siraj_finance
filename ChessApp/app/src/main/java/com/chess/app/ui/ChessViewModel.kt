package com.chess.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chess.app.engine.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChessViewModel : ViewModel() {
    val engine = ChessEngine()

    var boardState by mutableStateOf(snapshotBoard())
        private set
    var turn by mutableStateOf(PieceColor.WHITE)
        private set
    var selectedSquare by mutableStateOf<Square?>(null)
        private set
    var validMoves by mutableStateOf<List<Square>>(emptyList())
        private set
    var lastMove by mutableStateOf<Move?>(null)
        private set
    var isCheck by mutableStateOf(false)
        private set
    var isGameOver by mutableStateOf(false)
        private set
    var isCheckmate by mutableStateOf(false)
        private set
    var isStalemate by mutableStateOf(false)
        private set
    var moveHistory by mutableStateOf<List<String>>(emptyList())
        private set
    var capturedWhite by mutableStateOf<List<PieceType>>(emptyList())
        private set
    var capturedBlack by mutableStateOf<List<PieceType>>(emptyList())
        private set
    var materialDiff by mutableIntStateOf(0)
        private set
    var clockWhite by mutableIntStateOf(600)
        private set
    var clockBlack by mutableIntStateOf(600)
        private set
    var clockRunning by mutableStateOf(false)
        private set
    var flipped by mutableStateOf(false)
        private set
    var showPromotion by mutableStateOf(false)
        private set
    var pendingPromoFrom by mutableStateOf<Square?>(null)
        private set
    var pendingPromoTo by mutableStateOf<Square?>(null)
        private set
    var kingInCheckSquare by mutableStateOf<Square?>(null)
        private set

    private var clockJob: Job? = null

    private fun snapshotBoard(): Array<Array<ChessPiece?>> {
        return engine.board.map { row -> row.map { it?.copy() }.toTypedArray() }.toTypedArray()
    }

    private fun refreshState() {
        boardState = snapshotBoard()
        turn = engine.turn
        lastMove = engine.lastMove
        isCheck = engine.inCheck(engine.turn)
        isGameOver = engine.isGameOver
        isCheckmate = isGameOver && isCheck
        isStalemate = isGameOver && !isCheck
        moveHistory = engine.moveHistory.toList()
        capturedWhite = engine.capturedWhite.toList()
        capturedBlack = engine.capturedBlack.toList()
        materialDiff = engine.materialDifference()
        clockWhite = engine.clockWhite
        clockBlack = engine.clockBlack
        kingInCheckSquare = if (isCheck) engine.findKing(engine.turn) else null

        if (isGameOver) stopClock()
    }

    fun onSquareClick(row: Int, col: Int) {
        if (isGameOver || showPromotion) return

        val sq = Square(row, col)

        // If a piece is selected and clicking on a valid move target
        if (selectedSquare != null && validMoves.any { it.row == row && it.col == col }) {
            val from = selectedSquare!!
            val piece = engine.piece(from.row, from.col)

            // Check for pawn promotion
            if (piece?.type == PieceType.PAWN && (row == 0 || row == 7)) {
                pendingPromoFrom = from
                pendingPromoTo = sq
                showPromotion = true
                return
            }

            engine.executeMove(from.row, from.col, row, col)
            selectedSquare = null
            validMoves = emptyList()

            if (!clockRunning && !engine.isGameOver) startClock()
            refreshState()
            return
        }

        // Select a piece of the current turn
        val piece = engine.piece(row, col)
        if (piece != null && piece.color == engine.turn) {
            val moves = engine.legalMoves(row, col)
            if (moves.isNotEmpty()) {
                selectedSquare = sq
                validMoves = moves
            } else {
                selectedSquare = null
                validMoves = emptyList()
            }
        } else {
            selectedSquare = null
            validMoves = emptyList()
        }
    }

    fun onPromotion(type: PieceType) {
        val from = pendingPromoFrom ?: return
        val to = pendingPromoTo ?: return
        engine.executeMove(from.row, from.col, to.row, to.col, type)
        showPromotion = false
        pendingPromoFrom = null
        pendingPromoTo = null
        selectedSquare = null
        validMoves = emptyList()

        if (!clockRunning && !engine.isGameOver) startClock()
        refreshState()
    }

    fun newGame() {
        stopClock()
        engine.reset()
        selectedSquare = null
        validMoves = emptyList()
        showPromotion = false
        pendingPromoFrom = null
        pendingPromoTo = null
        refreshState()
    }

    fun flipBoard() {
        flipped = !flipped
    }

    fun undo() {
        if (engine.undoStack.isEmpty() || isGameOver) return
        engine.undo()
        selectedSquare = null
        validMoves = emptyList()
        refreshState()
    }

    private fun startClock() {
        clockRunning = true
        clockJob = viewModelScope.launch {
            while (clockRunning && !engine.isGameOver) {
                delay(1000)
                if (engine.turn == PieceColor.WHITE) {
                    engine.clockWhite--
                    clockWhite = engine.clockWhite
                    if (engine.clockWhite <= 0) {
                        engine.clockWhite = 0
                        engine.isGameOver = true
                        refreshState()
                        stopClock()
                        return@launch
                    }
                } else {
                    engine.clockBlack--
                    clockBlack = engine.clockBlack
                    if (engine.clockBlack <= 0) {
                        engine.clockBlack = 0
                        engine.isGameOver = true
                        refreshState()
                        stopClock()
                        return@launch
                    }
                }
            }
        }
    }

    private fun stopClock() {
        clockRunning = false
        clockJob?.cancel()
        clockJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopClock()
    }
}
