package com.example.axognition.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class Player { WHITE, BLACK;
    fun other() = if (this == WHITE) BLACK else WHITE
    val label get() = if (this == WHITE) "White" else "Black"
}

private enum class PieceType { KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN }
private data class ChessPiece(val type: PieceType, val player: Player) {
    val symbol: String get() = when (player) {
        Player.WHITE -> when (type) { PieceType.KING -> "♔"; PieceType.QUEEN -> "♕"; PieceType.ROOK -> "♖"; PieceType.BISHOP -> "♗"; PieceType.KNIGHT -> "♘"; PieceType.PAWN -> "♙" }
        Player.BLACK -> when (type) { PieceType.KING -> "♚"; PieceType.QUEEN -> "♛"; PieceType.ROOK -> "♜"; PieceType.BISHOP -> "♝"; PieceType.KNIGHT -> "♞"; PieceType.PAWN -> "♟" }
    }
}

private data class Square(val row: Int, val column: Int) {
    fun isOnBoard() = row in 0..7 && column in 0..7
}

private data class ChessMove(val from: Square, val to: Square, val isCastle: Boolean = false, val isEnPassant: Boolean = false)
private data class CastlingRights(val whiteKingSide: Boolean = true, val whiteQueenSide: Boolean = true, val blackKingSide: Boolean = true, val blackQueenSide: Boolean = true)
private data class StoreGame(
    val title: String,
    val studio: String,
    val genre: String,
    val rating: String,
    val artwork: String,
    val accent: Color
)

private val installedGames = listOf(
    StoreGame("Chess", "Axognition", "Strategy", "4.9", "♞", Color(0xFF3F51B5)),
    StoreGame("Sky Glide", "Blue Finch", "Arcade", "4.7", "✈", Color(0xFF0288D1)),
    StoreGame("Pixel Pals", "Tiny Cloud", "Casual", "4.6", "☻", Color(0xFF7B1FA2)),
    StoreGame("Word Garden", "Bloom Labs", "Word", "4.8", "✿", Color(0xFF388E3C)),
    StoreGame("Orbit Dash", "Nova Studio", "Action", "4.5", "◉", Color(0xFFE65100)),
    StoreGame("Sudoku Daily", "Paper Kite", "Puzzle", "4.8", "#", Color(0xFF455A64))
)

private val discoverGames = listOf(
    StoreGame("Number Quest", "Bright Mind", "Education", "4.9", "⅓", Color(0xFF00695C)),
    StoreGame("Word Sprint", "Letterbox", "Word", "4.7", "Aa", Color(0xFF8E24AA)),
    StoreGame("Memory Match", "Clever Fox", "Puzzle", "4.8", "◈", Color(0xFFD81B60)),
    StoreGame("Racing Lines", "Neon Road", "Racing", "4.6", "➟", Color(0xFF1565C0)),
    StoreGame("Farm Friends", "Willow Works", "Simulation", "4.7", "♧", Color(0xFF558B2F)),
    StoreGame("Cosmic Blocks", "Tangent Games", "Arcade", "4.5", "▦", Color(0xFF4527A0)),
    StoreGame("Mini Golf Club", "Green Flag", "Sports", "4.6", "⚑", Color(0xFF00897B)),
    StoreGame("Paint Pop", "Sunny Side", "Creative", "4.8", "✦", Color(0xFFFF8F00)),
    StoreGame("Logic Loop", "Nimble", "Puzzle", "4.7", "∞", Color(0xFF5E35B1))
)

private fun initialBoard(): Map<Square, ChessPiece> = buildMap {
    val order = listOf(PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN, PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK)
    order.forEachIndexed { column, type ->
        put(Square(0, column), ChessPiece(type, Player.BLACK)); put(Square(7, column), ChessPiece(type, Player.WHITE))
        put(Square(1, column), ChessPiece(PieceType.PAWN, Player.BLACK)); put(Square(6, column), ChessPiece(PieceType.PAWN, Player.WHITE))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(onBack: () -> Unit) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var playingChess by rememberSaveable { mutableStateOf(false) }
    var installedExtras by rememberSaveable { mutableStateOf(setOf<String>()) }

    if (playingChess) {
        ChessGame(onBack = { playingChess = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Games", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.height(42.dp)) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, modifier = Modifier.height(42.dp), text = { Text("Installed") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, modifier = Modifier.height(42.dp), text = { Text("Discover") })
            }
            if (selectedTab == 0) {
                InstalledGames(
                    extras = installedExtras,
                    onPlayChess = { playingChess = true }
                )
            } else {
                DiscoverGames(
                    installed = installedExtras,
                    onInstall = { installedExtras = installedExtras + it }
                )
            }
        }
    }
}

@Composable
private fun InstalledGames(extras: Set<String>, onPlayChess: () -> Unit) {
    val games = installedGames + extras.map { title -> discoverGames.firstOrNull { it.title == title } ?: StoreGame(title, "Axognition", "Game", "New", "✦", MaterialTheme.colorScheme.primary) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 152.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            StoreHeader("My games", "${games.size} installed", "Your library")
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            FeaturedGameCard(onPlayChess)
        }
        items(games, key = { it.title }) { game ->
            StoreGameCard(game = game, action = if (game.title == "Chess") "Play" else "Open", onAction = { if (game.title == "Chess") onPlayChess() })
        }
    }
}

@Composable
private fun DiscoverGames(installed: Set<String>, onInstall: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 152.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            StoreHeader("Discover", "Hand-picked games for you", "Game store")
        }
        items(discoverGames, key = { it.title }) { game ->
            val isInstalled = game.title in installed
            StoreGameCard(
                game = game,
                action = if (isInstalled) "Installed" else "Install",
                enabled = !isInstalled,
                onAction = { onInstall(game.title) }
            )
        }
    }
}

@Composable
private fun StoreHeader(title: String, subtitle: String, overline: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(overline.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeaturedGameCard(onPlayChess: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF172554)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF6366F1)), contentAlignment = Alignment.Center) {
                Text("♞", fontSize = 45.sp, color = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Continue playing", color = Color(0xFFC7D2FE), style = MaterialTheme.typography.labelLarge)
                Text("Chess", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("A classic battle of strategy", color = Color(0xFFE0E7FF), style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onPlayChess) { Icon(Icons.Default.PlayArrow, null); Text("Play") }
        }
    }
}

@Composable
private fun StoreGameCard(game: StoreGame, action: String, enabled: Boolean = true, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(
                Modifier.fillMaxWidth().height(106.dp).clip(RoundedCornerShape(15.dp)).background(game.accent),
                contentAlignment = Alignment.Center
            ) {
                Text(game.artwork, fontSize = 50.sp, color = Color.White)
                Text(game.genre, Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x33000000)).padding(horizontal = 7.dp, vertical = 3.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text(game.title, maxLines = 1, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(game.studio, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(5.dp))
            Text("★ ${game.rating}   •   ${game.genre}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            if (action == "Play") {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(action) }
            } else {
                OutlinedButton(onClick = onAction, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    if (enabled) Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(4.dp)); Text(action)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChessGame(onBack: () -> Unit) {
    var board by remember { mutableStateOf(initialBoard()) }
    var turn by remember { mutableStateOf(Player.WHITE) }
    var selected by remember { mutableStateOf<Square?>(null) }
    var rights by remember { mutableStateOf(CastlingRights()) }
    var enPassantTarget by remember { mutableStateOf<Square?>(null) }
    var pendingPromotion by remember { mutableStateOf<ChessMove?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val legalMoves = selected?.let { square ->
        legalMoves(board, square, rights, enPassantTarget).filter { board[square]?.player == turn }
    }.orEmpty()

    fun finishMove(move: ChessMove, promotion: PieceType? = null) {
        val piece = board[move.from] ?: return
        val nextBoard = boardAfterMove(board, move, promotion)
        board = nextBoard
        rights = updateCastlingRights(rights, board, move)
        enPassantTarget = if (piece.type == PieceType.PAWN && kotlin.math.abs(move.to.row - move.from.row) == 2) Square((move.to.row + move.from.row) / 2, move.from.column) else null
        selected = null
        pendingPromotion = null
        val nextTurn = turn.other()
        turn = nextTurn
        val nextMoves = allLegalMoves(nextBoard, nextTurn, rights, enPassantTarget)
        message = when {
            nextMoves.isEmpty() && isInCheck(nextBoard, nextTurn) -> "Checkmate — ${nextTurn.other().label} wins"
            nextMoves.isEmpty() -> "Stalemate — draw"
            isInCheck(nextBoard, nextTurn) -> "Check"
            else -> null
        }
    }

    fun onSquareTapped(square: Square) {
        if (message?.startsWith("Checkmate") == true || message?.startsWith("Stalemate") == true || pendingPromotion != null) return
        val currentSelection = selected
        if (currentSelection != null && legalMoves.any { it.to == square }) {
            val move = legalMoves.first { it.to == square }
            val movingPiece = board[move.from]!!
            if (movingPiece.type == PieceType.PAWN && move.to.row in listOf(0, 7)) {
                pendingPromotion = move
                selected = null
            } else finishMove(move)
        } else if (board[square]?.player == turn) {
            selected = square
        } else {
            selected = null
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Chess", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back to games") } },
            actions = { TextButton(onClick = { board = initialBoard(); turn = Player.WHITE; selected = null; rights = CastlingRights(); enPassantTarget = null; pendingPromotion = null; message = null }) { Text("New game") } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message ?: "${turn.label}'s turn", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            ChessBoard(board, selected, legalMoves.map { it.to }.toSet(), ::onSquareTapped)
            Spacer(Modifier.height(14.dp))
            Text("Tap a piece, then tap a highlighted square. Castling, en passant and promotion are supported.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    pendingPromotion?.let { move ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Choose promotion") },
            text = { Text("Your pawn reached the last rank. Choose its new piece.") },
            confirmButton = {
                Row {
                    listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach { type ->
                        TextButton(onClick = { finishMove(move, type) }) { Text(ChessPiece(type, turn).symbol, fontSize = 26.sp) }
                    }
                }
            }
        )
    }
}

@Composable
private fun ChessBoard(board: Map<Square, ChessPiece>, selected: Square?, targets: Set<Square>, onTap: (Square) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val boardSize = minOf(maxWidth, 460.dp)
        Column(Modifier.size(boardSize).border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp))) {
            repeat(8) { row ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(8) { column ->
                        val square = Square(row, column)
                        val isLight = (row + column) % 2 == 0
                        val base = if (isLight) Color(0xFFF0D9B5) else Color(0xFFB58863)
                        val highlight = when { square == selected -> Color(0xFFF6E05E); square in targets -> Color(0xFF8FCB7A); else -> base }
                        Box(
                            Modifier.weight(1f).fillMaxHeight().background(highlight).clickable { onTap(square) },
                            contentAlignment = Alignment.Center
                        ) {
                            board[square]?.let { Text(it.symbol, fontSize = 34.sp, color = if (it.player == Player.WHITE) Color.White else Color(0xFF1A1A1A)) }
                            if (square in targets && board[square] == null) Box(Modifier.size(12.dp).background(Color(0x99000000), CircleShape))
                        }
                    }
                }
            }
        }
    }
}

private fun legalMoves(board: Map<Square, ChessPiece>, from: Square, rights: CastlingRights, enPassant: Square?): List<ChessMove> {
    val piece = board[from] ?: return emptyList()
    return pseudoMoves(board, from, piece, rights, enPassant).filter { move -> !isInCheck(boardAfterMove(board, move), piece.player) }
}

private fun allLegalMoves(board: Map<Square, ChessPiece>, player: Player, rights: CastlingRights, enPassant: Square?) =
    board.filterValues { it.player == player }.keys.flatMap { legalMoves(board, it, rights, enPassant) }

private fun pseudoMoves(board: Map<Square, ChessPiece>, from: Square, piece: ChessPiece, rights: CastlingRights, enPassant: Square?): List<ChessMove> = buildList {
    fun addIfValid(to: Square) { if (to.isOnBoard() && board[to]?.player != piece.player) add(ChessMove(from, to)) }
    fun slide(directions: List<Pair<Int, Int>>) { directions.forEach { (dr, dc) -> var to = Square(from.row + dr, from.column + dc); while (to.isOnBoard()) { val occupant = board[to]; if (occupant == null) add(ChessMove(from, to)) else { if (occupant.player != piece.player) add(ChessMove(from, to)); break }; to = Square(to.row + dr, to.column + dc) } } }
    when (piece.type) {
        PieceType.PAWN -> {
            val direction = if (piece.player == Player.WHITE) -1 else 1
            val startRow = if (piece.player == Player.WHITE) 6 else 1
            val one = Square(from.row + direction, from.column)
            if (one.isOnBoard() && board[one] == null) { add(ChessMove(from, one)); val two = Square(from.row + 2 * direction, from.column); if (from.row == startRow && board[two] == null) add(ChessMove(from, two)) }
            listOf(-1, 1).forEach { dc -> val capture = Square(from.row + direction, from.column + dc); if (capture.isOnBoard() && (board[capture]?.player == piece.player.other() || capture == enPassant)) add(ChessMove(from, capture, isEnPassant = capture == enPassant)) }
        }
        PieceType.KNIGHT -> listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1).forEach { addIfValid(Square(from.row + it.first, from.column + it.second)) }
        PieceType.BISHOP -> slide(listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1))
        PieceType.ROOK -> slide(listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1))
        PieceType.QUEEN -> slide(listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1))
        PieceType.KING -> {
            listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1).forEach { addIfValid(Square(from.row + it.first, from.column + it.second)) }
            val row = if (piece.player == Player.WHITE) 7 else 0
            val kingSide = if (piece.player == Player.WHITE) rights.whiteKingSide else rights.blackKingSide
            val queenSide = if (piece.player == Player.WHITE) rights.whiteQueenSide else rights.blackQueenSide
            if (!isInCheck(board, piece.player)) {
                if (kingSide && board[Square(row, 5)] == null && board[Square(row, 6)] == null && !isSquareAttacked(board, Square(row, 5), piece.player.other()) && !isSquareAttacked(board, Square(row, 6), piece.player.other())) add(ChessMove(from, Square(row, 6), isCastle = true))
                if (queenSide && board[Square(row, 1)] == null && board[Square(row, 2)] == null && board[Square(row, 3)] == null && !isSquareAttacked(board, Square(row, 3), piece.player.other()) && !isSquareAttacked(board, Square(row, 2), piece.player.other())) add(ChessMove(from, Square(row, 2), isCastle = true))
            }
        }
    }
}

private fun boardAfterMove(board: Map<Square, ChessPiece>, move: ChessMove, promotion: PieceType? = null): Map<Square, ChessPiece> = board.toMutableMap().apply {
    val piece = remove(move.from) ?: return@apply
    if (move.isEnPassant) remove(Square(move.from.row, move.to.column))
    put(move.to, if (promotion != null) ChessPiece(promotion, piece.player) else piece)
    if (move.isCastle) { val rookFrom = Square(move.from.row, if (move.to.column == 6) 7 else 0); val rookTo = Square(move.from.row, if (move.to.column == 6) 5 else 3); remove(rookFrom)?.let { put(rookTo, it) } }
}

private fun updateCastlingRights(rights: CastlingRights, board: Map<Square, ChessPiece>, move: ChessMove): CastlingRights {
    val moving = board[move.from]
    return rights.copy(
        whiteKingSide = rights.whiteKingSide && move.from != Square(7, 4) && move.from != Square(7, 7) && move.to != Square(7, 7),
        whiteQueenSide = rights.whiteQueenSide && move.from != Square(7, 4) && move.from != Square(7, 0) && move.to != Square(7, 0),
        blackKingSide = rights.blackKingSide && move.from != Square(0, 4) && move.from != Square(0, 7) && move.to != Square(0, 7),
        blackQueenSide = rights.blackQueenSide && move.from != Square(0, 4) && move.from != Square(0, 0) && move.to != Square(0, 0)
    )
}

private fun isInCheck(board: Map<Square, ChessPiece>, player: Player): Boolean =
    board.entries.firstOrNull { it.value.player == player && it.value.type == PieceType.KING }?.key?.let { isSquareAttacked(board, it, player.other()) } ?: true

private fun isSquareAttacked(board: Map<Square, ChessPiece>, square: Square, by: Player): Boolean = board.any { (from, piece) ->
    if (piece.player != by) false else when (piece.type) {
        PieceType.PAWN -> kotlin.math.abs(from.column - square.column) == 1 && square.row - from.row == if (by == Player.WHITE) -1 else 1
        PieceType.KNIGHT -> (kotlin.math.abs(from.row - square.row) to kotlin.math.abs(from.column - square.column)) in setOf(1 to 2, 2 to 1)
        PieceType.KING -> kotlin.math.max(kotlin.math.abs(from.row - square.row), kotlin.math.abs(from.column - square.column)) == 1
        PieceType.BISHOP -> attacksAlong(board, from, square, listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1))
        PieceType.ROOK -> attacksAlong(board, from, square, listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1))
        PieceType.QUEEN -> attacksAlong(board, from, square, listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1))
    }
}

private fun attacksAlong(board: Map<Square, ChessPiece>, from: Square, target: Square, directions: List<Pair<Int, Int>>): Boolean = directions.any { (dr, dc) ->
    var current = Square(from.row + dr, from.column + dc)
    while (current.isOnBoard()) { if (current == target) return@any true; if (board[current] != null) break; current = Square(current.row + dr, current.column + dc) }
    false
}
