package com.ykode.reko

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.ykode.reko.ui.theme.RekoTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


@Composable
fun MainContent(viewModel: MainViewModel ) {
  val state = viewModel.state.collectAsState()
  val context = LocalContext.current
  Column {
    if (!state.value.locationPermissionGranted ||
        !state.value.cameraPermissionGranted) {
      AskPermissionContent(viewModel = viewModel, context = context)
    }
  }
}



@Composable
fun AskPermissionContent(viewModel: MainViewModel, context: Context) {
  val state = viewModel.state.collectAsState()

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions())
  {
    viewModel.onPermissionResult(context = context)
  }

  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Button(
          modifier = Modifier.width(200.dp),
          onClick = {
            val permissionsToRequest = mutableListOf<String>()
            if (!state.value.cameraPermissionGranted) {
              permissionsToRequest.add(android.Manifest.permission.CAMERA)
            }
            if (!state.value.locationPermissionGranted) {
              permissionsToRequest.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
          }
        ) {
          Text(text = stringResource(id = R.string.ask_cam_permission))
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun MainContentPreview(){
  val ctx = LocalContext.current
  val viewModel = MainViewModel(ctx)
  RekoTheme {
    MainContent(viewModel)
  }
}

fun isPermissionGranted(context: Context, permission: String): Boolean {
  return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

class MainViewModel(context: Context): ViewModel() {
  data class State(
    val cameraPermissionGranted: Boolean = false,
    val locationPermissionGranted: Boolean = false,
  )

  private val _state = MutableStateFlow(State(
    cameraPermissionGranted = isPermissionGranted(context, android.Manifest.permission.CAMERA),
    locationPermissionGranted = isPermissionGranted(context, android.Manifest.permission.ACCESS_FINE_LOCATION),
  ))

  val state = _state.asStateFlow()

  fun onPermissionResult(context: Context) {
    _state.value = State(
      cameraPermissionGranted = isPermissionGranted(context, android.Manifest.permission.CAMERA),
      locationPermissionGranted = isPermissionGranted(context, android.Manifest.permission.ACCESS_FINE_LOCATION),
    )
  }
}