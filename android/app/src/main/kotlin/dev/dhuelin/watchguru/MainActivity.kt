package dev.dhuelin.watchguru

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.dhuelin.watchguru.ui.WatchGuruApp
import dev.dhuelin.watchguru.ui.theme.WatchGuruTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge before setContent, so the first frame already accounts
        // for the system bars rather than shifting once insets arrive.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WatchGuruTheme {
                WatchGuruApp()
            }
        }
    }
}
