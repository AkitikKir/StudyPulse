package com.growl.studypulse

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.growl.studypulse.domain.ReviewGrade
import com.growl.studypulse.ui.StudyPulseUiState
import com.growl.studypulse.ui.StudyPulseViewModel
import com.growl.studypulse.ui.StudyPulseViewModelFactory
import com.growl.studypulse.ui.theme.StudyPulseTheme
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    private val viewModel: StudyPulseViewModel by viewModels {
        StudyPulseViewModelFactory(
            repository = AppContainer.repository(applicationContext),
            firebaseSyncManager = AppContainer.firebaseSyncManager()
        )
    }

    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var authStatus by mutableStateOf<String?>(null)
    private var googleSignInClient: GoogleSignInClient? = null

    private val notificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val googleAuthLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            runCatching { task.getResult(ApiException::class.java) }
                .onSuccess { account ->
                    val idToken = account.idToken
                    if (idToken.isNullOrBlank()) {
                        authStatus = "Google sign-in: idToken missing"
                        return@onSuccess
                    }
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    firebaseAuth.signInWithCredential(credential)
                        .addOnSuccessListener {
                            authStatus = "Google auth ok"
                            viewModel.syncToCloud()
                        }
                        .addOnFailureListener { e ->
                            authStatus = "Google auth error: ${e.message ?: "unknown"}"
                        }
                }
                .onFailure { e ->
                    authStatus = "Google sign-in failed: ${e.message ?: "unknown"}"
                }
        }

    private val csvImportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val csv = BufferedReader(InputStreamReader(stream)).readText()
                    viewModel.importCsv(csv)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationsIfNeeded()
        configureGoogleSignIn()

        setContent {
            StudyPulseTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    StudyPulseApp(
                        viewModel = viewModel,
                        onImportCsvClick = { csvImportLauncher.launch(arrayOf("text/*", "text/csv")) },
                        onGoogleAuthClick = ::signInWithGoogle,
                        onGithubAuthClick = ::signInWithGithub,
                        authStatus = authStatus
                    )
                }
            }
        }
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun configureGoogleSignIn() {
        val id = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (id == 0) {
            authStatus = "Google auth not configured (default_web_client_id missing)"
            return
        }
        val webClientId = getString(id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun signInWithGoogle() {
        val client = googleSignInClient
        if (client == null) {
            authStatus = "Google auth not configured"
            return
        }
        googleAuthLauncher.launch(client.signInIntent)
    }

    private fun signInWithGithub() {
        val provider = OAuthProvider.newBuilder("github.com").apply {
            scopes = listOf("read:user", "user:email")
        }.build()

        firebaseAuth.startActivityForSignInWithProvider(this, provider)
            .addOnSuccessListener {
                authStatus = "GitHub auth ok"
                viewModel.syncToCloud()
            }
            .addOnFailureListener { e ->
                authStatus = "GitHub auth error: ${e.message ?: "unknown"}"
            }
    }
}

@Composable
private fun StudyPulseApp(
    viewModel: StudyPulseViewModel,
    onImportCsvClick: () -> Unit,
    onGoogleAuthClick: () -> Unit,
    onGithubAuthClick: () -> Unit,
    authStatus: String?
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.refreshDueCards()
        viewModel.refreshGamification()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                HeroHeader(uiState = uiState)
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                StudyTabs(selectedTab = selectedTab, onSelect = { selectedTab = it })

                if (selectedTab == 0) {
                    CreateCardScreen(
                        state = uiState,
                        onFrontChange = viewModel::updateFront,
                        onBackChange = viewModel::updateBack,
                        onImageUriChange = viewModel::updateImageUri,
                        onAdd = viewModel::addCard,
                        onAutofill = viewModel::autofillWithAi,
                        onImportCsv = onImportCsvClick,
                        onSyncCloud = viewModel::syncToCloud,
                        onGoogleAuth = onGoogleAuthClick,
                        onGithubAuth = onGithubAuthClick,
                        authStatus = authStatus
                    )
                } else {
                    SessionScreen(
                        state = uiState,
                        onToggleAnswer = viewModel::toggleAnswerVisibility,
                        onGrade = viewModel::rateCurrentCard,
                        onRefresh = {
                            viewModel.refreshDueCards()
                            viewModel.refreshGamification()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuroraBackdrop() {
    val transition = rememberInfiniteTransition(label = "aurora")
    val shiftX by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shift_x"
    )
    val shiftY by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 19000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shift_y"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val height = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF071018),
                        Color(0xFF0E2430),
                        Color(0xFF102C24)
                    )
                )
            )
            drawCircle(
                color = Color(0x993BA7FF),
                radius = width * 0.52f,
                center = Offset(width * shiftX, height * 0.2f)
            )
            drawCircle(
                color = Color(0x7742D3A0),
                radius = width * 0.58f,
                center = Offset(width * 0.2f, height * shiftY)
            )
            drawCircle(
                color = Color(0x66FFD16B),
                radius = width * 0.34f,
                center = Offset(width * (1f - shiftX * 0.6f), height * 0.85f)
            )
        }
    }
}

@Composable
private fun HeroHeader(uiState: StudyPulseUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "StudyPulse",
            style = MaterialTheme.typography.displaySmall,
            color = Color(0xFFEFF6E0)
        )
        Text(
            text = "5 минут. Каждый день. Без забывания.",
            color = Color(0xFFD2F8E9),
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBadge(title = "Due", value = uiState.dueCount.toString(), tint = Color(0xFFFFA94D))
            StatBadge(title = "Streak", value = "${uiState.currentStreakDays}d", tint = Color(0xFF57D3B4))
            StatBadge(title = "Today", value = uiState.reviewsToday.toString(), tint = Color(0xFF7CC7FF))
        }
        val streakProgress = (uiState.currentStreakDays.coerceAtMost(7)) / 7f
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Weekly Momentum", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Best streak: ${uiState.bestStreakDays} дней",
                        color = Color(0xFFD3F4FF),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                CircularProgressIndicator(
                    progress = { streakProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(48.dp),
                    color = Color(0xFF57D3B4),
                    trackColor = Color(0x33FFFFFF),
                    strokeWidth = 5.dp
                )
            }
        }
    }
}

@Composable
private fun StatBadge(title: String, value: String, tint: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.18f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(text = title, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
            Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun StudyTabs(selectedTab: Int, onSelect: (Int) -> Unit) {
    TabRow(
        selectedTabIndex = selectedTab,
        containerColor = Color.Transparent,
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        Tab(selected = selectedTab == 0, onClick = { onSelect(0) }, text = { Text("Конструктор") })
        Tab(selected = selectedTab == 1, onClick = { onSelect(1) }, text = { Text("Сессия") })
    }
}

@Composable
private fun CreateCardScreen(
    state: StudyPulseUiState,
    onFrontChange: (String) -> Unit,
    onBackChange: (String) -> Unit,
    onImageUriChange: (String) -> Unit,
    onAdd: () -> Unit,
    onAutofill: () -> Unit,
    onImportCsv: () -> Unit,
    onSyncCloud: () -> Unit,
    onGoogleAuth: () -> Unit,
    onGithubAuth: () -> Unit,
    authStatus: String?
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard {
                OutlinedTextField(
                    value = state.frontInput,
                    onValueChange = onFrontChange,
                    label = { Text("Термин") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.backInput,
                    onValueChange = onBackChange,
                    label = { Text("Определение") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.imageUriInput,
                    onValueChange = onImageUriChange,
                    label = { Text("Image URI (опционально)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onAdd, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2FC99A))) {
                        Text("Сохранить")
                    }
                    Button(onClick = onAutofill, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3BA7FF))) {
                        Text(if (state.aiLoading) "AI..." else "AI Заполнить")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onImportCsv,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF39C4A))
                ) {
                    Text(if (state.isImporting) "Импорт..." else "Импорт CSV")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onSyncCloud,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E7CFF))
                ) {
                    Text(if (state.isSyncingCloud) "Sync..." else "Sync Firebase")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onGoogleAuth,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1574C))
                    ) {
                        Text("Google Auth")
                    }
                    Button(
                        onClick = onGithubAuth,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3440))
                    ) {
                        Text("GitHub Auth")
                    }
                }
                state.aiStatus?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE7F7FF), modifier = Modifier.padding(top = 8.dp))
                }
                state.importStatus?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFE0BC), modifier = Modifier.padding(top = 4.dp))
                }
                state.cloudStatus?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE6DDFF), modifier = Modifier.padding(top = 4.dp))
                }
                authStatus?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFD9D0), modifier = Modifier.padding(top = 4.dp))
                }
                if (state.aiLoading || state.isImporting || state.isSyncingCloud) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 10.dp).size(20.dp), strokeWidth = 2.dp)
                }
            }
        }

        item {
            if (state.achievements.isNotEmpty()) {
                GlassCard {
                    Text("Достижения", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.achievements.take(3).forEach { title ->
                            AssistChip(onClick = {}, label = { Text(title) })
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Колода (${state.cards.size})",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFEFF6E0),
                modifier = Modifier.padding(start = 6.dp)
            )
        }

        items(state.cards, key = { it.id }) { card ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = card.front, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = card.back, color = Color(0xFFD6ECEA))
                }
            }
        }
        item { Spacer(modifier = Modifier.height(90.dp)) }
    }
}

@Composable
private fun SessionScreen(
    state: StudyPulseUiState,
    onToggleAnswer: () -> Unit,
    onGrade: (ReviewGrade) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text("Обновить очередь")
        }

        if (state.currentCard == null) {
            GlassCard {
                Text(
                    text = "На сейчас нет карточек к повторению",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            return
        }

        val rotation by animateFloatAsState(
            targetValue = if (state.isAnswerVisible) 180f else 0f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            label = "card_rotation"
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .rotate(rotation)
                .clickable { onToggleAnswer() },
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AnimatedContent(targetState = state.isAnswerVisible, label = "card_side") { isAnswer ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0x333BA7FF))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(if (isAnswer) "Ответ" else "Вопрос", color = Color(0xFFBEE8FF))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isAnswer) state.currentCard.back else state.currentCard.front,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Тап для переворота",
                            modifier = Modifier.alpha(0.8f).padding(top = 10.dp),
                            color = Color(0xFFD5F1E8),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (state.isAnswerVisible) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onGrade(ReviewGrade.HARD) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA7358))) {
                    Text("Сложно")
                }
                Button(onClick = { onGrade(ReviewGrade.OK) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2FC99A))) {
                    Text("Понял")
                }
                Button(onClick = { onGrade(ReviewGrade.EASY) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3BA7FF))) {
                    Text("Легко")
                }
            }
        }

        state.lastReviewMessage?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD9F2FF))
        }
    }
}

@Composable
private fun GlassCard(content: @Composable Column.() -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1FFFFFFF))
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}
