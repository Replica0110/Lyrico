package com.lonx.lyrico.ui.components.pro

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.base.ActionBottomSheet
import com.lonx.lyrico.ui.components.base.PillButton
import com.lonx.lyrico.ui.components.base.PillButtonDefaults
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.text.AppText
import com.lonx.lyrico.ui.components.text.AppTextTone
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon

internal enum class SponsorMethod(
    @StringRes val titleRes: Int,
    @DrawableRes val imageRes: Int,
    val filePrefix: String,
) {
    AFDIAN(R.string.sponsor_method_afdian, R.drawable.sponsor_afdian, "lyrico_afdian"),
    WECHAT(R.string.sponsor_method_wechat, R.drawable.sponsor_wechat, "lyrico_wechat");

    val icon get() = if (this == AFDIAN) LucideIcons.Heart else LucideIcons.MessageSquare
    val iconTone get() = if (this == AFDIAN) PreferenceIconTone.Appearance else PreferenceIconTone.Lyrics
    val extension get() = if (this == AFDIAN) "jpg" else "png"
    val mimeType get() = if (this == AFDIAN) "image/jpeg" else "image/png"
}

@Composable
internal fun SponsorQrCodeSheet(show: Boolean, method: SponsorMethod, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var permissionPending by rememberSaveable { mutableStateOf(false) }
    var pendingMethodName by rememberSaveable { mutableStateOf(SponsorMethod.AFDIAN.name) }
    val save: (SponsorMethod) -> Unit = { target ->
        if (!saving) {
            saving = true
            scope.launch {
                try {
                    saveSponsorImageToGallery(context, target)
                    Toast.makeText(context, R.string.sponsor_saved_to_gallery, Toast.LENGTH_SHORT).show()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.sponsor_save_failed, Toast.LENGTH_SHORT).show()
                } finally {
                    saving = false
                }
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionPending = false
        if (granted) save(SponsorMethod.valueOf(pendingMethodName))
        else Toast.makeText(context, R.string.sponsor_storage_permission_required, Toast.LENGTH_SHORT).show()
    }
    val saveHint = stringResource(R.string.sponsor_long_press_save)
    val linkColors = PillButtonDefaults.plainColors()
    ActionBottomSheet(
        show = show,
        title = stringResource(method.titleRes),
        onDismissRequest = onDismissRequest,
        content = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val imagePainter = painterResource(method.imageRes)
                Image(
                    painter = imagePainter,
                    contentDescription = stringResource(method.titleRes),
                    modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth()
                        .aspectRatio(imagePainter.intrinsicSize.width / imagePainter.intrinsicSize.height)
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(
                            enabled = !saving && !permissionPending,
                            onClick = {},
                            onLongClickLabel = saveHint,
                            onLongClick = {
                                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    pendingMethodName = method.name
                                    permissionPending = true
                                    permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                } else save(method)
                            },
                        ),
                )
                AppText(
                    if (saving) stringResource(R.string.sponsor_saving) else saveHint,
                    tone = AppTextTone.Secondary,
                )
                if (method == SponsorMethod.AFDIAN) {
                    PillButton(
                        text = stringResource(R.string.sponsor_open_afdian),
                        onClick = { uriHandler.openUri("https://ifdian.net/a/replica0110") },
                        colors = linkColors,
                        leading = { Icon(LucideIcons.OpenInNew, null, Modifier.size(18.dp), tint = linkColors.contentColor) },
                    )
                }
            }
        },
    )
}

@Suppress("DEPRECATION")
private suspend fun saveSponsorImageToGallery(context: Context, method: SponsorMethod) = withContext(Dispatchers.IO) {
    val filename = "${method.filePrefix}_${System.currentTimeMillis()}.${method.extension}"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, method.mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Lyrico")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Lyrico")
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create image directory")
            put(MediaStore.Images.Media.DATA, File(directory, filename).absolutePath)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("Cannot create gallery image")
    try {
        resolver.openOutputStream(uri)?.use { output ->
            context.resources.openRawResource(method.imageRes).use { input -> input.copyTo(output) }
        } ?: throw IOException("Cannot open gallery image")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val published = resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            if (published == 0) throw IOException("Cannot publish gallery image")
        }
    } catch (e: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        throw e
    }
}
