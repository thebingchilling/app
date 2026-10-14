package app.quartz.camera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.quartz.core.ui.icons.SymbolIcon
import app.quartz.core.ui.icons.Symbols
import app.quartz.core.ui.theme.QuartzTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuartzTheme(forceDark = true) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SymbolIcon(Symbols.photoCamera, null)
                    Text("Quartz")
                }
            }
        }
    }
}
