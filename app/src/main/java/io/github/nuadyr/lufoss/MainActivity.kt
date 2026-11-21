package io.github.nuadyr.lufoss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nuadyr.lufoss.ui.LufossScreen
import io.github.nuadyr.lufoss.ui.LufossTheme
import io.github.nuadyr.lufoss.ui.LufossViewModel

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
