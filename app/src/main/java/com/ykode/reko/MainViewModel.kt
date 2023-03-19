package com.ykode.reko

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.DistanceMatrixApi
import com.google.maps.GeoApiContext
import com.google.maps.model.DistanceMatrix
import com.google.maps.model.TravelMode
import com.google.maps.model.Unit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.TimeZone

class MainViewModel(context: Context): ViewModel() {
  data class State(
    val cameraPermissionGranted: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val capturedBitmap: Bitmap? = null,
    val isCaptured: Boolean = false,
    val isLoading: Boolean = false,
    val recognisedText: String = "",
    val distanceText: String = "",
    val timeText: String = "",
    val isPostingEnabled: Boolean = true,
  )

  private val _state = MutableStateFlow(State(
    cameraPermissionGranted = isPermissionGranted(context, Manifest.permission.CAMERA),
    locationPermissionGranted = isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION),
  ))

  val state = _state.asStateFlow()

  suspend fun saveStateToFirebase() {
    val db = FirebaseFirestore.getInstance()
    val stateData = hashMapOf(
      "timestamp" to FieldValue.serverTimestamp(),
      "timezone" to TimeZone.getDefault().id,
      "recognisedText" to state.value.recognisedText,
      "estTimeText" to state.value.timeText,
      "estDistance" to state.value.distanceText
    )
    db.collection("reko").add(stateData).await()
  }

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

          viewModelScope.launch(Dispatchers.IO) {
            try {
              val visionText = performOcr(context, savedUri)
              _state.value = _state.value.copy(recognisedText = visionText.text)

              getCurrentLocation(context)
            } catch (e: Exception) {
              Log.e("MLKit", "Failure processing Image", e)
            }
          }
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

  fun setRecognisedText(newText: String) {
    _state.value = _state.value.copy(recognisedText = newText)
  }

  fun setDistanceText(newText: String) {
    _state.value = _state.value.copy(distanceText = newText)
  }

  fun setTimeText(newText: String) {
    _state.value = _state.value.copy(timeText = newText)
  }

  private suspend fun getDistanceAndTime(origin: Location): Pair<String, String> {
    val destination = "Plaza Indonesia, Jakarta"
    val apiKey = BuildConfig.MAPS_API_KEY
    val geoApiContext = GeoApiContext.Builder()
      .apiKey(apiKey)
      .build()

    return withContext(Dispatchers.IO) {
      val distanceMatrix: DistanceMatrix = DistanceMatrixApi.newRequest(geoApiContext)
        .origins("${origin.latitude},${origin.longitude}")
        .destinations(destination)
        .mode(TravelMode.DRIVING)
        .units(Unit.METRIC)
        .await()

      val distance = distanceMatrix.rows[0].elements[0].distance.humanReadable
      val duration = distanceMatrix.rows[0].elements[0].duration.humanReadable

      Pair(distance, duration)
    }
  }

  suspend fun getCurrentLocation(context: Context) {
    val location = withContext(Dispatchers.IO) {
      getLastKnownLocation(context)
    }

    if (location != null) {
      val (distance, time) = getDistanceAndTime(location)
      _state.value = _state.value.copy(distanceText = distance, timeText = time)
    }
  }

  suspend fun getLastKnownLocation(context: Context): Location? {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      return null
    }

    return try {
      fusedLocationClient.lastLocation.await()
    } catch (e: Exception) {
      Log.e("LocationServices", "Failure to get location", e)
      null
    }
  }

  fun postState() {
    _state.value = _state.value.copy(isPostingEnabled = false)
    viewModelScope.launch {
      try {
        saveStateToFirebase()
      } catch (e: Exception) {
        Log.e("FIREBASE", "Error Saving", e)
      } finally {
        _state.value = _state.value.copy(isPostingEnabled = true)
      }
    }
  }
}