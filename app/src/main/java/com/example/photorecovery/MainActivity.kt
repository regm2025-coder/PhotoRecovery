package com.example.photorecovery

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.SubcomposeAsyncImage

data class MediaItem(
    val uri: android.net.Uri,
    val name: String,
    val video: Boolean,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RecoveryScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryScreen() {
    val context = LocalContext.current
    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var selectedImage by remember { mutableStateOf<MediaItem?>(null) }
    var message by remember { mutableStateOf("افحص الوسائط المتاحة على جهازك") }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun scan() {
        val canReadImages = if (Build.VERSION.SDK_INT >= 33) {
            hasPermission(Manifest.permission.READ_MEDIA_IMAGES) ||
                (Build.VERSION.SDK_INT >= 34 &&
                    hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
        } else {
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val canReadVideos = if (Build.VERSION.SDK_INT >= 33) {
            hasPermission(Manifest.permission.READ_MEDIA_VIDEO) ||
                (Build.VERSION.SDK_INT >= 34 &&
                    hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
        } else {
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val collections = buildList {
            if (canReadImages) add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI to false)
            if (canReadVideos) add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI to true)
        }
        val found = mutableListOf<MediaItem>()

        try {
            collections.forEach { (collection, isVideo) ->
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                )
                context.contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC",
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        found.add(
                            MediaItem(
                                uri = ContentUris.withAppendedId(collection, id),
                                name = cursor.getString(nameColumn) ?: "ملف وسائط",
                                video = isVideo,
                            ),
                        )
                    }
                }
            }
            media = found
            message = if (found.isEmpty()) {
                "لم يُعثر على وسائط متاحة أو لم تُمنح الصلاحية"
            } else {
                "عدد الملفات المتاحة: ${found.size}"
            }
        } catch (_: SecurityException) {
            media = emptyList()
            message = "تعذّر الوصول إلى الوسائط. تحقق من الأذونات وحاول مرة أخرى."
        }
    }

    val permissions = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) {
            scan()
        } else {
            message = "يرجى السماح بالوصول إلى الصور أو الفيديو"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("استرجاع الصور والفيديو") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("اعثر على الصور ومقاطع الفيديو المتاحة على جهازك")
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (permissions.any(::hasPermission)) {
                        scan()
                    } else {
                        permissionLauncher.launch(permissions)
                    }
                },
            ) {
                Text("فحص الوسائط")
            }
            Spacer(Modifier.height(12.dp))
            Text(message)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(media) { item ->
                    Card(
                        modifier = Modifier.clickable {
                            if (item.video) {
                                openVideo(context, item)
                            } else {
                                selectedImage = item
                            }
                        },
                    ) {
                        Column {
                            SubcomposeAsyncImage(
                                model = item.uri,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                                loading = {
                                    MediaImagePlaceholder(loading = true)
                                },
                                error = {
                                    MediaImagePlaceholder(loading = false)
                                },
                            )
                            Column(Modifier.padding(10.dp)) {
                                Text(if (item.video) "فيديو" else "صورة")
                                Text(item.name, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }

    selectedImage?.let { item ->
        ImageViewerDialog(
            item = item,
            onDismiss = { selectedImage = null },
        )
    }
}

@Composable
private fun MediaImagePlaceholder(loading: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        } else {
            Text("تعذّر عرض الصورة", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ImageViewerDialog(item: MediaItem, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.name, modifier = Modifier.weight(1f), maxLines = 2)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 560.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SubcomposeAsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        loading = { MediaImagePlaceholder(loading = true) },
                        error = { MediaImagePlaceholder(loading = false) },
                    )
                }
            }
        }
    }
}

private fun openVideo(context: Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(item.uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "لا يوجد تطبيق مناسب لتشغيل الفيديو", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "تعذّر منح تطبيق التشغيل إذن قراءة الفيديو", Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
        Toast.makeText(context, "تعذّر فتح الفيديو", Toast.LENGTH_LONG).show()
    }
}
