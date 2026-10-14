package cz.kisiala.pocasi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cz.kisiala.pocasi.ui.AppRoot
import cz.kisiala.pocasi.ui.theme.PocasiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as PocasiApp).container
        setContent {
            PocasiTheme {
                AppRoot(container)
            }
        }
    }
}
