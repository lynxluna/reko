package com.ykode.reko

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.ykode.reko.ui.theme.RekoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun MainContent(viewModel: MainViewModel) {
  val state = viewModel.state.collectAsState()
  val context = LocalContext.current
  val lifeCycleOwner = LocalLifecycleOwner.current
  val imageCapture = ImageCapture.Builder()
    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
    .build()

  ConstraintLayout(
    modifier = Modifier
      .fillMaxSize()
      .animateContentSize(animationSpec = tween(300))
  ) {
    val (cameraPreviewRef, permissionBoxRef, captureButtonRef) = createRefs()

    if (!state.value.isCaptured) {
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
        key(state.value.isCaptured) {
          CameraPreviewContent(
            context = context,
            lifeCycleOwner = lifeCycleOwner,
            imageCapture = imageCapture
          )
        }
      }
      // Capture Button
      FloatingActionButton(
        onClick = { viewModel.captureImage(context, imageCapture) },
        modifier = Modifier
          .size(72.dp)
          .constrainAs(captureButtonRef) {
            bottom.linkTo(parent.bottom, margin = 16.dp)
            start.linkTo(parent.start, margin = 16.dp)
            end.linkTo(parent.end, margin = 16.dp)
          },
        elevation = FloatingActionButtonDefaults.elevation(4.dp)
      ) {
        Icon(
          painter = painterResource(id = R.drawable.text_recognition),
          contentDescription = "Capture and Recognise",
          modifier = Modifier.size(48.dp)
        )
      }

      if (state.value.isLoading) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .wrapContentSize(Alignment.Center)
        ) {
          CircularProgressIndicator()
        }
      }

    } else {
      // If is not captured
      CapturedContent(viewModel = viewModel)
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
fun CameraPreviewContent(context: Context,
                         lifeCycleOwner: LifecycleOwner,
                         imageCapture: ImageCapture) {

  val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
  val preview = androidx.camera.core.Preview.Builder().build()

  val cameraProvider = remember(cameraProviderFuture) {
    mutableStateOf<ProcessCameraProvider?>(null)
  }

  LaunchedEffect(Unit) {
    val provider = withContext(Dispatchers.IO) {
      cameraProviderFuture.get()
    }

    cameraProvider.value = provider

    provider.unbindAll()

    val cameraSelector =
      CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

    provider.bindToLifecycle(
      lifeCycleOwner,
      cameraSelector,
      preview,
      imageCapture
    )
  }

  DisposableEffect(lifeCycleOwner) {
    onDispose {
      if (lifeCycleOwner.lifecycle.currentState == Lifecycle.State.CREATED) {
        cameraProvider.value?.unbindAll()
      }
    }
  }

  AndroidView(factory = { ctx ->
    val pv = PreviewView(ctx)
    preview.setSurfaceProvider(pv.surfaceProvider)
    pv
  }, modifier = Modifier.fillMaxSize())
}

@Composable
fun CapturedContent(viewModel: MainViewModel) {
  val state = viewModel.state.collectAsState()
  val screenHeight = LocalConfiguration.current.screenHeightDp.dp
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier.verticalScroll(scrollState)
  ) {
    val bitmap = state.value.capturedBitmap
    if (bitmap != null) {
      Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "captured image",
        modifier = Modifier
          .height((screenHeight * 1 / 3).coerceAtMost(bitmap.height.dp))
          .fillMaxWidth()
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth()
    ){
      Button(
        onClick = { viewModel.resetCapture() },
        modifier = Modifier
          .padding(16.dp)
          .weight(1f)
      ) {
        Text(text = "Recapture")
      }
      Button(
        onClick = {viewModel.postState()},

        modifier = Modifier
          .padding(16.dp)
          .weight(1f),
        enabled = state.value.isPostingEnabled
      ) {
        Text(text = "Post")
      }
    }

    TextField(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 4.dp),
      value = state.value.distanceText , onValueChange = {viewModel.setDistanceText(it)}, label = { Text(text = "Distance")})
    TextField(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 4.dp),
      value = state.value.timeText , onValueChange = {viewModel.setTimeText(it)}, label = { Text(text = "Estimated Time")})

    if (state.value.recognisedText.isNotEmpty()) {
      TextField(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 4.dp),
        value = state.value.recognisedText,
        onValueChange = {viewModel.setRecognisedText(it) },
        maxLines = Int.MAX_VALUE,
        label = { Text(text = "Recognised Text") }
      )
    }
  }
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



