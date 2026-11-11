package one.calypsocloud.ezamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.calypsocloud.ezamp.data.Auth
import one.calypsocloud.ezamp.data.JellyfinApi
import one.calypsocloud.ezamp.data.LibraryStore
import one.calypsocloud.ezamp.data.Prefs
import one.calypsocloud.ezamp.data.PublicSystemInfo

/**
 * Onboarding, two steps:
 *   1. server address -> validated against /System/Info/Public (must actually be Jellyfin)
 *   2. username + password -> /Users/AuthenticateByName -> access token
 * No API key anywhere; the token is the app's own credential from here on.
 */
@Composable
fun LoginScreen(onDone: () -> Unit) {
    var step by remember { mutableStateOf(1) }
    var base by remember { mutableStateOf(Prefs.baseUrl ?: "http://192.168.1.50:8096") }
    var username by remember { mutableStateOf(Prefs.userName ?: "") }
    var password by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<PublicSystemInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun checkServer() {
        if (busy) return
        error = null
        busy = true
        scope.launch {
            try {
                val found = Auth.probe(base)
                info = found
                Prefs.baseUrl = Auth.normalize(base)
                Prefs.serverName = found.ServerName
                Prefs.serverVersion = found.Version
                step = 2
            } catch (e: Exception) {
                error = e.message ?: "Couldn't validate that server"
            } finally {
                busy = false
            }
        }
    }

    fun signIn() {
        if (busy) return
        error = null
        busy = true
        scope.launch {
            try {
                val res = Auth.login(base, username, password)
                val cleaned = Auth.normalize(base)
                Prefs.baseUrl = cleaned
                Prefs.token = res.AccessToken
                Prefs.userId = res.User.Id
                Prefs.userName = res.User.Name
                password = ""
                LibraryStore.setApi(JellyfinApi(cleaned, res.AccessToken))
                LibraryStore.start(res.User.Id)
                onDone()
            } catch (e: Exception) {
                error = e.message ?: "Sign-in failed"
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 26.dp),
    ) {
        Spacer(Modifier.height(72.dp))
        Text("ezamp", style = MaterialTheme.typography.displaySmall, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "Jellyfin, without the waiting.",
            style = MaterialTheme.typography.bodyMedium,
            color = Dim,
        )

        Spacer(Modifier.height(44.dp))
        StepRail(step)
        Spacer(Modifier.height(26.dp))

        if (step == 1) {
            Label("SERVER ADDRESS")
            Field(
                value = base,
                hint = "http://192.168.1.50:8096",
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
                onDone = { checkServer() },
                onChange = { base = it; error = null },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "The address you'd open in a browser. Plain http is fine on your own network.",
                style = MaterialTheme.typography.bodySmall,
                color = Mute,
            )
            Spacer(Modifier.height(26.dp))
            Action(
                if (busy) "CHECKING…" else "NEXT",
                enabled = base.isNotBlank() && !busy,
            ) { checkServer() }
        } else {
            info?.let { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.ServerName.ifBlank { "Jellyfin" },
                        style = MaterialTheme.typography.labelSmall,
                        color = Ez.Ok,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "JELLYFIN ${s.Version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Mute,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
            Label("USERNAME")
            Field(
                value = username,
                hint = "your Jellyfin user",
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                onDone = {},
                onChange = { username = it; error = null },
            )
            Spacer(Modifier.height(20.dp))
            Label("PASSWORD")
            Field(
                value = password,
                hint = "••••••••",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
                secret = true,
                onDone = { signIn() },
                onChange = { password = it; error = null },
            )
            Spacer(Modifier.height(26.dp))
            Action(
                if (busy) "SIGNING IN…" else "SIGN IN",
                enabled = username.isNotBlank() && !busy,
            ) { signIn() }
            Spacer(Modifier.height(22.dp))
            Text(
                "CHANGE SERVER",
                style = MaterialTheme.typography.labelSmall,
                color = Dim,
                modifier = Modifier
                    .clickable { step = 1; error = null }
                    .padding(vertical = 10.dp),
            )
        }

        error?.let {
            Spacer(Modifier.height(20.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = Ez.Bad,
            )
        }
    }
}

@Composable
private fun StepRail(step: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "1 SERVER",
            style = MaterialTheme.typography.labelSmall,
            color = if (step == 1) Accent else Mute,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .width(28.dp)
                .height(1.dp)
                .background(if (step > 1) Ez.Ok else Ez.LineStrong),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "2 SIGN IN",
            style = MaterialTheme.typography.labelSmall,
            color = if (step == 2) Accent else Mute,
        )
    }
}

@Composable
private fun Action(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (enabled) Accent else Mute,
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 14.dp, horizontal = 2.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Dim,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun Field(
    value: String,
    hint: String,
    keyboardType: KeyboardType = KeyboardType.Uri,
    imeAction: ImeAction = ImeAction.Next,
    secret: Boolean = false,
    onDone: () -> Unit,
    onChange: (String) -> Unit,
) {
    Column {
        Box {
            if (value.isEmpty()) {
                Text(
                    hint,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ez.LineStrong,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Ink),
                cursorBrush = SolidColor(Accent),
                visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onGo = { onDone() },
                    onNext = { onDone() },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Hairline()
    }
}
