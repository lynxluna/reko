package com.ykode.reko

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.ykode.reko.ui.theme.RekoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext


@Composable
fun MainContent(viewModel: MainViewModel ) {
  val state = viewModel.state.collectAsState()
  val context = LocalContext.current
  val lifeCycleOwner = LocalLifecycleOwner.current

  ConstraintLayout(
    modifier = Modifier
      .fillMaxSize()
      .animateContentSize(animationSpec = tween(300))
  ) {
    val (cameraPreviewRef, permissionBoxRef, captureButtonRef) = createRefs()
    Box(
      modifier = Modifier
        .fillMaxSize()
        .constrainAs(cameraPreviewRef) {
          top.linkTo(parent.top)
          bottom.linkTo(parent.bottom)
          start.linkTo(parent.start)
          end.linkTo(parent.end)
        },
      contentAlignment = Alignment.Center
    ) {
      CameraPreviewContent(context = context, lifeCycleOwner= lifeCycleOwner, viewModel = viewModel )
    }
    // Capture Button
    FloatingActionButton(
      onClick = { /*TODO*/ },
      modifier = Modifier
        .size(72.dp)
        .constrainAs(captureButtonRef) {
          bottom.linkTo(parent.bottom, margin = 16.dp)
          start.linkTo(parent.start, margin = 16.dp)
          end.linkTo(parent.end, margin = 16.dp)
        },
      elevation = FloatingActionButtonDefaults.elevation(4.dp)
    ) {
      Icon(painter = painterResource(id = R.drawable.text_recognition), 
        contentDescription = "Capture and Recognise",
        modifier = Modifier.size(48.dp)
      )
    }

    // Permission box
    if (!state.value.locationPermissionGranted || !state.value.cameraPermissionGranted) {
      AskPermissionContent(
        viewModel = viewModel,
        context = context,
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White)
          .constrainAs(permissionBoxRef) {
            bottom.linkTo(parent.bottom)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
          }
      )
    }
  }
}

@Composable
fun CameraPreviewContent(viewModel: ViewModel, context: Context, lifeCycleOwner: LifecycleOwner) {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val preview = androidx.camera.core.Preview.Builder().build()

        LaunchedEffect(Unit) {
          val cameraProvider = withContext(Dispatchers.IO) {
            cameraProviderFuture.get()
          }

          val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

          cameraProvider.bindToLifecycle(
            lifeCycleOwner,
            cameraSelector,
            preview
          )
        }

        AndroidView(factory = {ctx->
          val pv = PreviewView(ctx)
          preview.setSurfaceProvider(pv.surfaceProvider)
          pv
        }, modifier = Modifier.fillMaxSize())
}


@Composable
fun AskPermissionContent(viewModel: MainViewModel, context: Context, modifier: Modifier = Modifier) {
  val state = viewModel.state.collectAsState()

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions())
  {
    viewModel.onPermissionResult(context = context)
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
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