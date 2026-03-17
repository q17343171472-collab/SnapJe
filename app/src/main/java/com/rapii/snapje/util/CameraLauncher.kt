package com.rapii.snapje.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for launching camera and handling photo capture.
 */
@Singleton
class CameraLauncher @Inject constructor() {

    private var activity: ComponentActivity? = null
    private var photoUri: Uri? = null
    private var onPhotoCaptured: ((Uri) -> Unit)? = null

    /**
     * Set the activity for camera operations.
     * Call this from your Activity or Composable context.
     */
    fun setActivity(activity: ComponentActivity) {
        this.activity = activity
    }

    /**
     * Create a camera launcher that handles the camera intent and returns the captured photo URI.
     * 
     * @param activity The activity to launch the camera from
     * @param onPhotoCaptured Callback when photo is captured successfully
     * @return ActivityResultLauncher for camera intent
     */
    fun createLauncher(
        activity: ComponentActivity,
        onPhotoCaptured: (Uri) -> Unit
    ): ActivityResultLauncher<Intent> {
        this.activity = activity
        this.onPhotoCaptured = onPhotoCaptured

        return activity.registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                photoUri?.let { uri ->
                    this.onPhotoCaptured?.invoke(uri)
                    L.d("CameraLauncher", "Photo captured: $uri")
                } ?: L.e("CameraLauncher", "Photo capture failed - no URI")
            } else {
                L.d("CameraLauncher", "Photo capture cancelled")
            }
        }
    }

    /**
     * Create an intent to launch the camera.
     * 
     * @param context Context for creating the FileProvider URI
     * @return Intent for capturing a photo
     */
    fun createCaptureIntent(context: Context): Intent {
        val photoFile = createImageFile(context)
        
        photoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )

        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    /**
     * Create a temporary file for storing captured photos.
     */
    private fun createImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    /**
     * Get the URI of the last captured photo.
     */
    fun getLastPhotoUri(): Uri? = photoUri

    /**
     * Clear the last photo URI.
     */
    fun clearPhotoUri() {
        photoUri = null
    }

    /**
     * Clean up resources.
     */
    fun clear() {
        activity = null
        onPhotoCaptured = null
        photoUri = null
    }
}
