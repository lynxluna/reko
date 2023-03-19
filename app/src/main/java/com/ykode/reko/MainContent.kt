package com.ykode.reko

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.ykode.reko.ui.theme.RekoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File


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
          .fillMaxWidth()
          .aspectRatio(1f)
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
            viewModel = viewModel,
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
      CapturedContent(viewModel = viewModel, context = context)
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
fun CameraPreviewContent(viewModel: MainViewModel,
                         context: Context,
                         lifeCycleOwner: LifecycleOwner,
                         imageCapture: ImageCapture) {

  val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
  val preview = androidx.camera.core.Preview.Builder().build()

  val state = viewModel.state.collectAsState()

  LaunchedEffect(Unit) {
    val cameraProvider = withContext(Dispatchers.IO) {
      cameraProviderFuture.get()
    }

    cameraProvider.unbindAll()

    val cameraSelector =
      CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

    cameraProvider.bindToLifecycle(
      lifeCycleOwner,
      cameraSelector,
      preview,
      imageCapture
    )
  }

  AndroidView(factory = { ctx ->
    val pv = PreviewView(ctx)
    preview.setSurfaceProvider(pv.surfaceProvider)
    pv
  }, modifier = Modifier.fillMaxSize())
}

@Composable
fun CapturedContent(viewModel: MainViewModel, context: Context, modifier: Modifier = Modifier) {
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
    Button(
      onClick = { viewModel.resetCapture() },
      modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth()
    ) {
      Text(text = "Capture Again")
    }
    TextField(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 4.dp),
      value = "88km" , onValueChange = {}, label = { Text(text = "Distance")})
    TextField(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 4.dp),
      value = "2 Menit" , onValueChange = {}, label = { Text(text = "Estimated Time")})
    if (state.value.recognisedText.isNotEmpty()) {
      TextField(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 4.dp),
        value = state.value.recognisedText,
        onValueChange = {}, maxLines = Int.MAX_VALUE,
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

class MainViewModel(context: Context): ViewModel() {
  data class State(
    val cameraPermissionGranted: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val capturedBitmap: Bitmap? = null,
    val isCaptured: Boolean = false,
    val isLoading: Boolean = false,
    val recognisedText: String = "",
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

  private fun getOutputDirectory(context: Context): File {
    val mediaDir = File(context.filesDir, "cepeto").apply { mkdirs() }
    return if (mediaDir.exists()) mediaDir else context.filesDir
  }


  fun captureImage(context: Context, imageCapture: ImageCapture) {
    val outputDirectory = getOutputDirectory(context)
    val file = File.createTempFile("cpt-img", ".jpg", outputDirectory)

    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
    val executor = ContextCompat.getMainExecutor(context)
    _state.value = _state.value.copy(isLoading =  true)

    imageCapture.takePicture(
      outputOptions,
      executor,
      object: ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
          val savedUri = outputFileResults.savedUri ?: Uri.fromFile(file)
          val bitmap = BitmapFactory.decodeFile(savedUri.path)

          _state.value = _state.value.copy(capturedBitmap = bitmap, isLoading = false, isCaptured = true)
        }

        override fun onError(exception: ImageCaptureException) {
          _state.value = _state.value.copy(isLoading = false, capturedBitmap = null, isCaptured = false)
          Log.e("CEPETO", "Failure capture", exception)
        }
      }
    )
  }

  fun resetCapture() {
    _state.value = _state.value.copy(isLoading = false, capturedBitmap = null, isCaptured = false)
  }
}

