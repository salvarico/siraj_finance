package com.chess.app.engine

enum class PieceColor { WHITE, BLACK;
    fun opposite() = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val value: Int, val symbol: String) {
    PAWN(1, ""),
    KNIGHT(3, "C"),
    BISHOP(3, "A"),
    ROOK(5, "T"),
    QUEEN(9, "D"),
    KING(0, "R")
}

data class ChessPiece(val color: PieceColor, val type: PieceType) {
    val unicode: String get() = when (color) {
        PieceColor.WHITE -> when (type) {
            PieceType.KING -> "♔"; PieceType.QUEEN -> "♕"; PieceType.ROOK -> "♖"
            PieceType.BISHOP -> "♗"; PieceType.KNIGHT -> "♘"; PieceType.PAWN -> "♙"
        }
        PieceColor.BLACK -> when (type) {
            PieceType.KING -> "♚"; PieceType.QUEEN -> "♛"; PieceType.ROOK -> "♜"
            PieceType.BISHOP -> "♝"; PieceType.KNIGHT -> "♞"; PieceType.PAWN -> "♟"
        }
    }
}

data class Square(val row: Int, val col: Int) {
    fun inBounds() = row in 0..7 && col in 0..7
    val algebraic: String get() = "${"abcdefgh"[col]}${8 - row}"
}

data class Move(val from: Square, val to: Square, val promotion: PieceType? = null)

data class CastlingRights(
    var whiteKingSide: Boolean = true,
    var whiteQueenSide: Boolean = true,
    var blackKingSide: Boolean = true,
    var blackQueenSide: Boolean = true
) {
    fun copy() = CastlingRights(whiteKingSide, whiteQueenSide, blackKingSide, blackQueenSide)
}

data class GameState(
    val board: Array<Array<ChessPiece?>>,
    val turn: PieceColor,
    val castling: CastlingRights,
    val enPassantTarget: Square?,
    val lastMove: Move?,
    val moveHistory: List<String>,
    val capturedWhite: List<PieceType>,
    val capturedBlack: List<PieceType>,
    val clockWhite: Int,
    val clockBlack: Int,
    val isGameOver: Boolean
) {
    fun deepCopy() = GameState(
        board = board.map { row -> row.map { it?.copy() }.toTypedArray() }.toTypedArray(),
        turn = turn,
        castling = castling.copy(),
        enPassantTarget = enPassantTarget?.copy(),
        lastMove = lastMove?.copy(),
        moveHistory = moveHistory.toList(),
        capturedWhite = capturedWhite.toList(),
        capturedBlack = capturedBlack.toList(),
        clockWhite = clockWhite,
        clockBlack = clockBlack,
        isGameOver = isGameOver
    )
}

class ChessEngine {
    var board = Array(8) { arrayOfNulls<ChessPiece>(8) }
    var turn = PieceColor.WHITE
    var castling = CastlingRights()
    var enPassantTarget: Square? = null
    var lastMove: Move? = null
    var moveHistory = mutableListOf<String>()
    var capturedWhite = mutableListOf<PieceType>() // white pieces captured
    var capturedBlack = mutableListOf<PieceType>() // black pieces captured
    var clockWhite = 600
    var clockBlack = 600
    var isGameOver = false
    val undoStack = mutableListOf<GameState>()

    init { reset() }

    fun reset() {
        val backRank = arrayOf(
            PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
            PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
        )
        board = Array(8) { arrayOfNulls(8) }
        for (c in 0..7) {
            board[0][c] = ChessPiece(PieceColor.BLACK, backRank[c])
            board[1][c] = ChessPiece(PieceColor.BLACK, PieceType.PAWN)
            board[6][c] = ChessPiece(PieceColor.WHITE, PieceType.PAWN)
            board[7][c] = ChessPiece(PieceColor.WHITE, backRank[c])
        }
        turn = PieceColor.WHITE
        castling = CastlingRights()
        enPassantTarget = null
        lastMove = null
        moveHistory.clear()
        capturedWhite.clear()
        capturedBlack.clear()
        clockWhite = 600
        clockBlack = 600
        isGameOver = false
        undoStack.clear()
    }

    fun piece(r: Int, c: Int): ChessPiece? =
        if (r in 0..7 && c in 0..7) board[r][c] else null

    fun findKing(color: PieceColor): Square? {
        for (r in 0..7) for (c in 0..7)
            if (board[r][c]?.color == color && board[r][c]?.type == PieceType.KING)
                return Square(r, c)
        return null
    }

    private fun attackSquares(r: Int, c: Int, p: ChessPiece): List<Square> {
        val moves = mutableListOf<Square>()
        fun addIf(tr: Int, tc: Int): Boolean {
            if (tr !in 0..7 || tc !in 0..7) return false
            if (board[tr][tc]?.color == p.color) return false
            moves.add(Square(tr, tc))
            return board[tr][tc] == null
        }
        fun slide(dirs: List<Pair<Int, Int>>) {
            for ((dr, dc) in dirs)
                for (i in 1..7)
                    if (!addIf(r + dr * i, c + dc * i)) break
        }

        when (p.type) {
            PieceType.PAWN -> {
                val dir = if (p.color == PieceColor.WHITE) -1 else 1
                for (dc in listOf(-1, 1)) {
                    val tr = r + dir; val tc = c + dc
                    if (tr in 0..7 && tc in 0..7) moves.add(Square(tr, tc))
                }
            }
            PieceType.KNIGHT -> {
                for ((dr, dc) in listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1))
                    addIf(r + dr, c + dc)
            }
            PieceType.BISHOP -> slide(listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1))
            PieceType.ROOK -> slide(listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1))
            PieceType.QUEEN -> slide(listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1))
            PieceType.KING -> {
                for ((dr, dc) in listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1))
                    addIf(r + dr, c + dc)
            }
        }
        return moves
    }

    fun isAttacked(r: Int, c: Int, byColor: PieceColor): Boolean {
        for (rr in 0..7) for (cc in 0..7) {
            val p = board[rr][cc] ?: continue
            if (p.color == byColor && attackSquares(rr, cc, p).any { it.row == r && it.col == c })
                return true
        }
        return false
    }

    fun rawMoves(r: Int, c: Int): List<Square> {
        val p = board[r][c] ?: return emptyList()
        val moves = mutableListOf<Square>()
        val op = p.color.opposite()

        fun addIf(tr: Int, tc: Int): Boolean {
            if (tr !in 0..7 || tc !in 0..7) return false
            if (board[tr][tc]?.color == p.color) return false
            moves.add(Square(tr, tc))
            return board[tr][tc] == null
        }
        fun slide(dirs: List<Pair<Int, Int>>) {
            for ((dr, dc) in dirs)
                for (i in 1..7)
                    if (!addIf(r + dr * i, c + dc * i)) break
        }

        when (p.type) {
            PieceType.PAWN -> {
                val dir = if (p.color == PieceColor.WHITE) -1 else 1
                val startRow = if (p.color == PieceColor.WHITE) 6 else 1
                if (r + dir in 0..7 && board[r + dir][c] == null) {
                    moves.add(Square(r + dir, c))
                    if (r == startRow && board[r + 2 * dir][c] == null)
                        moves.add(Square(r + 2 * dir, c))
                }
                for (dc in listOf(-1, 1)) {
                    val tr = r + dir; val tc = c + dc
                    if (tr in 0..7 && tc in 0..7) {
                        if (board[tr][tc]?.color == op) moves.add(Square(tr, tc))
                        if (enPassantTarget?.row == tr && enPassantTarget?.col == tc)
                            moves.add(Square(tr, tc))
                    }
                }
            }
            PieceType.KNIGHT ->
                for ((dr, dc) in listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1))
                    addIf(r + dr, c + dc)
            PieceType.BISHOP -> slide(listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1))
            PieceType.ROOK -> slide(listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1))
            PieceType.QUEEN -> slide(listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1))
            PieceType.KING -> {
                for ((dr, dc) in listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1))
                    addIf(r + dr, c + dc)
                if (!isAttacked(r, c, op)) {
                    val row = if (p.color == PieceColor.WHITE) 7 else 0
                    if (r == row && c == 4) {
                        val ks = if (p.color == PieceColor.WHITE) castling.whiteKingSide else castling.blackKingSide
                        val qs = if (p.color == PieceColor.WHITE) castling.whiteQueenSide else castling.blackQueenSide
                        if (ks && board[row][5] == null && board[row][6] == null
                            && board[row][7]?.type == PieceType.ROOK && board[row][7]?.color == p.color
                            && !isAttacked(row, 5, op) && !isAttacked(row, 6, op))
                            moves.add(Square(row, 6))
                        if (qs && board[row][3] == null && board[row][2] == null && board[row][1] == null
                            && board[row][0]?.type == PieceType.ROOK && board[row][0]?.color == p.color
                            && !isAttacked(row, 3, op) && !isAttacked(row, 2, op))
                            moves.add(Square(row, 2))
                    }
                }
            }
        }
        return moves
    }

    fun inCheck(color: PieceColor): Boolean {
        val k = findKing(color) ?: return false
        return isAttacked(k.row, k.col, color.opposite())
    }

    fun legalMoves(r: Int, c: Int): List<Square> {
        val p = board[r][c] ?: return emptyList()
        return rawMoves(r, c).filter { m ->
            val captured = board[m.row][m.col]
            val orig = board[r][c]
            board[m.row][m.col] = orig
            board[r][c] = null

            var epCapPiece: ChessPiece? = null
            var epR = -1; var epC = -1
            if (orig?.type == PieceType.PAWN && enPassantTarget?.row == m.row && enPassantTarget?.col == m.col) {
                epR = r; epC = m.col
                epCapPiece = board[epR][epC]
                board[epR][epC] = null
            }

            val ok = !inCheck(p.color)

            board[r][c] = orig
            board[m.row][m.col] = captured
            if (epCapPiece != null) board[epR][epC] = epCapPiece
            ok
        }
    }

    fun hasAnyLegalMove(color: PieceColor): Boolean {
        for (r in 0..7) for (c in 0..7)
            if (board[r][c]?.color == color && legalMoves(r, c).isNotEmpty())
                return true
        return false
    }

    private fun buildSAN(fr: Int, fc: Int, tr: Int, tc: Int, p: ChessPiece, captured: Boolean, promoType: PieceType?): String {
        if (p.type == PieceType.KING && kotlin.math.abs(tc - fc) == 2)
            return if (tc == 6) "O-O" else "O-O-O"

        val sb = StringBuilder()
        val files = "abcdefgh"

        if (p.type != PieceType.PAWN) {
            sb.append(p.type.symbol)
            // Disambiguation
            val others = mutableListOf<Square>()
            for (r in 0..7) for (c in 0..7) {
                if ((r != fr || c != fc) && board[r][c]?.color == p.color && board[r][c]?.type == p.type) {
                    if (legalMoves(r, c).any { it.row == tr && it.col == tc })
                        others.add(Square(r, c))
                }
            }
            if (others.isNotEmpty()) {
                if (others.all { it.col != fc }) sb.append(files[fc])
                else if (others.all { it.row != fr }) sb.append(8 - fr)
                else { sb.append(files[fc]); sb.append(8 - fr) }
            }
        } else if (captured) {
            sb.append(files[fc])
        }

        if (captured) sb.append('x')
        sb.append(files[tc])
        sb.append(8 - tr)
        if (promoType != null) { sb.append('='); sb.append(promoType.symbol) }
        return sb.toString()
    }

    private fun saveState() {
        undoStack.add(GameState(
            board = board.map { row -> row.map { it?.copy() }.toTypedArray() }.toTypedArray(),
            turn = turn,
            castling = castling.copy(),
            enPassantTarget = enPassantTarget?.copy(),
            lastMove = lastMove?.copy(),
            moveHistory = moveHistory.toList(),
            capturedWhite = capturedWhite.toList(),
            capturedBlack = capturedBlack.toList(),
            clockWhite = clockWhite,
            clockBlack = clockBlack,
            isGameOver = isGameOver
        ))
    }

    fun executeMove(fr: Int, fc: Int, tr: Int, tc: Int, promoType: PieceType? = null): Boolean {
        saveState()
        val p = board[fr][fc] ?: return false
        var captured = board[tr][tc]
        val isEPCapture = p.type == PieceType.PAWN && enPassantTarget?.row == tr && enPassantTarget?.col == tc

        val san = buildSAN(fr, fc, tr, tc, p, captured != null || isEPCapture, promoType)

        // En passant capture
        if (isEPCapture) {
            captured = board[fr][tc]
            board[fr][tc] = null
        }

        // Track captured
        if (captured != null) {
            if (captured.color == PieceColor.WHITE) capturedWhite.add(captured.type)
            else capturedBlack.add(captured.type)
        }

        // En passant target
        enPassantTarget = if (p.type == PieceType.PAWN && kotlin.math.abs(tr - fr) == 2)
            Square((fr + tr) / 2, fc) else null

        // Castling move rook
        if (p.type == PieceType.KING && kotlin.math.abs(tc - fc) == 2) {
            if (tc == 6) { board[fr][5] = board[fr][7]; board[fr][7] = null }
            else { board[fr][3] = board[fr][0]; board[fr][0] = null }
        }

        // Update castling rights
        if (p.type == PieceType.KING) {
            if (p.color == PieceColor.WHITE) { castling.whiteKingSide = false; castling.whiteQueenSide = false }
            else { castling.blackKingSide = false; castling.blackQueenSide = false }
        }
        if (p.type == PieceType.ROOK) {
            val homeRow = if (p.color == PieceColor.WHITE) 7 else 0
            if (fr == homeRow && fc == 0) {
                if (p.color == PieceColor.WHITE) castling.whiteQueenSide = false else castling.blackQueenSide = false
            }
            if (fr == homeRow && fc == 7) {
                if (p.color == PieceColor.WHITE) castling.whiteKingSide = false else castling.blackKingSide = false
            }
        }

        // Move piece
        board[tr][tc] = p
        board[fr][fc] = null

        // Promotion
        if (p.type == PieceType.PAWN && (tr == 0 || tr == 7)) {
            board[tr][tc] = ChessPiece(p.color, promoType ?: PieceType.QUEEN)
        }

        lastMove = Move(Square(fr, fc), Square(tr, tc), promoType)
        turn = turn.opposite()

        // Finalize SAN
        var finalSAN = san
        if (inCheck(turn)) {
            finalSAN += if (hasAnyLegalMove(turn)) "+" else "#"
        }
        moveHistory.add(finalSAN)

        if (!hasAnyLegalMove(turn)) isGameOver = true

        return true
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val state = undoStack.removeAt(undoStack.lastIndex)
        board = state.board
        turn = state.turn
        castling = state.castling
        enPassantTarget = state.enPassantTarget
        lastMove = state.lastMove
        moveHistory = state.moveHistory.toMutableList()
        capturedWhite = state.capturedWhite.toMutableList()
        capturedBlack = state.capturedBlack.toMutableList()
        clockWhite = state.clockWhite
        clockBlack = state.clockBlack
        isGameOver = state.isGameOver
        return true
    }

    fun materialDifference(): Int {
        val whiteCaptures = capturedBlack.sumOf { it.value }
        val blackCaptures = capturedWhite.sumOf { it.value }
        return whiteCaptures - blackCaptures
    }
}
