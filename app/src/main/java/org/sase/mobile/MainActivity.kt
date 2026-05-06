package org.sase.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.sase.mobile.ui.SaseMobileApp
import org.sase.mobile.ui.theme.SaseMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SaseMobileTheme {
                SaseMobileApp()
            }
        }
    }
}
