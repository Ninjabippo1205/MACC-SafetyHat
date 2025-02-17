package com.safetyhat.macc

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.safetyhat.macc.ui.theme.SafetyHatTheme

class MainActivity : ComponentActivity() {

    private val foregroundPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.SEND_SMS
    )

    private val requestForegroundPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allForegroundGranted = true
        permissions.entries.forEach {
            if (!it.value) {
                allForegroundGranted = false
                Log.d("PermissionCheck", "Permission denied: ${getPermissionFriendlyName(it.key)}")
            }
        }
        if (!allForegroundGranted) {
            Toast.makeText(
                this,
                "Some permissions were not granted. Some features may not be available.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SafetyHatTheme {
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                MainScreen(
                    isLandscape = isLandscape,
                    onSignIn = {
                        startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                        finish()
                    },
                    onRegister = {
                        startActivity(Intent(this@MainActivity, RegistrationActivity::class.java))
                        finish()
                    }
                )
            }
        }

        val taskDescription = ActivityManager.TaskDescription.Builder()
            .setLabel("SafetyHat")
            .setIcon(R.mipmap.safety_hat_foreground)
            .setPrimaryColor(getColor(R.color.miniature_background))
            .build()
        window.statusBarColor = getColor(R.color.status_bar_color)
        setTaskDescription(taskDescription)

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val foregroundPermissionsToRequest = foregroundPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (foregroundPermissionsToRequest.isNotEmpty()) {
            requestForegroundPermissionsLauncher.launch(foregroundPermissionsToRequest.toTypedArray())
        }
    }

    private fun getPermissionFriendlyName(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            Manifest.permission.CAMERA -> "Camera"
            Manifest.permission.ACCESS_FINE_LOCATION -> "Location"
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            Manifest.permission.SEND_SMS -> "Send SMS"
            else -> permission
        }
    }
}

/**
 * Composable function that dynamically adjusts layout based on orientation.
 */
@Composable
fun MainScreen(isLandscape: Boolean, onSignIn: () -> Unit, onRegister: () -> Unit) {
    if (isLandscape) {
        LandscapeLayout(onSignIn, onRegister)
    } else {
        PortraitLayout(onSignIn, onRegister)
    }
}

/**
 * Portrait mode layout - Column based UI
 */
@Composable
fun PortraitLayout(onSignIn: () -> Unit, onRegister: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(90.dp))

        Image(
            painter = painterResource(id = R.mipmap.safet_hat_big_foreground),
            contentDescription = stringResource(id = R.string.safety_hat_description),
            modifier = Modifier
                .size(width = 344.dp, height = 258.dp)
                .scale(1.2f),
            contentScale = ContentScale.Crop
        )

        Text(
            text = stringResource(id = R.string.safety_hat),
            fontSize = 45.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4B4B4B),
            modifier = Modifier.padding(top = 16.dp)
        )

        Spacer(modifier = Modifier.height(50.dp))

        ButtonsRow(onSignIn, onRegister)
    }
}

/**
 * Landscape mode layout - Row based UI
 */
@Composable
fun LandscapeLayout(onSignIn: () -> Unit, onRegister: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.mipmap.safet_hat_big_foreground),
                contentDescription = stringResource(id = R.string.safety_hat_description),
                modifier = Modifier
                    .size(width = 308.dp, height = 140.dp)
                    .scale(1.2f),
                contentScale = ContentScale.Crop
            )

            Text(
                text = stringResource(id = R.string.safety_hat),
                fontSize = 45.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4B4B4B),
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        ButtonsRow(onSignIn, onRegister)
    }
}

/**
 * Shared buttons row for both orientations
 */
@Composable
fun ButtonsRow(onSignIn: () -> Unit, onRegister: () -> Unit) {
    Row(
        modifier = Modifier
            .width(302.dp)
            .height(102.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onSignIn,
            modifier = Modifier
                .defaultMinSize(minWidth = 120.dp, minHeight = 60.dp),
            shape = RoundedCornerShape(8.dp),
            elevation = ButtonDefaults.elevation(4.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface)
        ) {
            Text(
                text = stringResource(id = R.string.login),
                fontSize = 20.sp,
                color = Color(0xFF2C2C2C)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Button(
            onClick = onRegister,
            modifier = Modifier
                .defaultMinSize(minWidth = 120.dp, minHeight = 60.dp),
            shape = RoundedCornerShape(8.dp),
            elevation = ButtonDefaults.elevation(4.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2C2C2C))
        ) {
            Text(
                text = stringResource(id = R.string.register),
                fontSize = 20.sp,
                color = Color.White
            )
        }
    }
}
