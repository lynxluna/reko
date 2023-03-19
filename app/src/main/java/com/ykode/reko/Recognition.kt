package com.ykode.reko

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

suspend fun performOcr(context: Context, imageUri: Uri): Text {
  val inputImage = InputImage.fromFilePath(context, imageUri)
  val recogniser = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  return recogniser.process(inputImage).await()
}