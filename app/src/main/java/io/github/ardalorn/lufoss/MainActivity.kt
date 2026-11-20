package io.github.ardalorn.lufoss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ardalorn.lufoss.ui.LufossScreen
import io.github.ardalorn.lufoss.ui.LufossTheme
import io.github.ardalorn.lufoss.ui.LufossViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LufossTheme {
                val vm: LufossViewModel = viewModel()
                LufossScreen(vm)
            }
        }
    }
}
