package com.personal.mybrowser.browser

import android.app.role.RoleManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun DefaultBrowserOption() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val context = LocalContext.current
    val roleManager = remember(context) { context.getSystemService(RoleManager::class.java) }
    var roleHeld by remember(roleManager) {
        mutableStateOf(roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleHeld = roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
    }
    if (roleManager?.isRoleAvailable(RoleManager.ROLE_BROWSER) == true && !roleHeld) {
        TextButton(onClick = {
            launcher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
        }) { Text("Set as default browser") }
    }
}
