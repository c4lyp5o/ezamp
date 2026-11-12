package one.calypsocloud.ezamp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import one.calypsocloud.ezamp.data.LibraryStore
import one.calypsocloud.ezamp.data.Prefs
import one.calypsocloud.ezamp.player.PlayerHolder
import one.calypsocloud.ezamp.ui.EzampTheme
import one.calypsocloud.ezamp.ui.LoginScreen
import one.calypsocloud.ezamp.ui.Root

class MainActivity : ComponentActivity() {

    private lateinit var connectivity: android.net.ConnectivityManager
    private val networkCb = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            LibraryStore.setOnline(true)
        }
        override fun onLost(network: android.net.Network) {
            LibraryStore.setOnline(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        connectivity = getSystemService(android.net.ConnectivityManager::class.java)
        try {
            connectivity.registerDefaultNetworkCallback(networkCb)
        } catch (e: Exception) {
            android.util.Log.w("ezamp-sync", "network callback not registered: ${e.message}")
        }
        val active = connectivity.activeNetworkInfo
        LibraryStore.setOnline(active == null || active.isConnected)

        setContent {
            EzampTheme {
                var authed by remember {
                    mutableStateOf(Prefs.token != null && Prefs.userId != null)
                }

                val notif = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                if (authed) {
                    Root(
                        onSignOut = {
                            PlayerHolder.stop()
                            LibraryStore.signOut()
                            authed = false
                        },
                    )
                } else {
                    LoginScreen { authed = true }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LibraryStore.foreground()
    }

    override fun onDestroy() {
        try {
            connectivity.unregisterNetworkCallback(networkCb)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }
}
