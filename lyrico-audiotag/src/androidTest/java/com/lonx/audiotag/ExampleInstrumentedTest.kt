package com.lonx.audiotag

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.taglib.Picture
import com.kyant.taglib.PropertyMap
import com.kyant.taglib.TagLib
import com.lonx.audiotag.internal.AudioFile
import com.lonx.audiotag.internal.MetadataResult
import com.lonx.audiotag.internal.TagLibJNI
import org.junit.Assert

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import kotlin.collections.single
import kotlin.collections.singleOrNull
import kotlin.text.isNotEmpty

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

@RunWith(AndroidJUnit4::class)
class Tests {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun test_all() {
//        read_and_write_m4a()
//        read_and_write_pictures_flac()
//        read_flac_multiple_pictures()
//        ensure_utf8()
//        bad_encoding()
        write_wav_metadata()
        read_wav_metadata()
    }
    @Test
    fun write_wav_metadata() {
        getFdFromAssets(context, "无名策 - 塞壬唱片-MSR.wav").use { fd ->

            val metadata = TagLib.getMetadata(fd.dup().detachFd())

            val propertyMap: PropertyMap = metadata?.propertyMap ?:  PropertyMap()

            val newTitle = "Test WAV Title"

            propertyMap["TITLE"] = arrayOf(newTitle)

            val saved = TagLib.savePropertyMap(
                fd.dup().detachFd(),
                propertyMap
            )

            Assert.assertTrue(saved)

            val newMetadata = TagLib.getMetadata(fd.dup().detachFd())

            Assert.assertEquals(
                newTitle,
                newMetadata?.propertyMap?.get("TITLE")?.single()
            )
        }
    }
    @Test
    fun read_wav_metadata() {
        getFdFromAssets(context, "无名策 - 塞壬唱片-MSR.wav").use { fd ->

            // 读取音频属性
            val properties = TagLib.getAudioProperties(fd.dup().detachFd())!!
            assertTrue(properties.length > 0)

            // 读取 metadata
            val metadata = TagLib.getMetadata(fd.dup().detachFd())!!

            // WAV 常见字段
            val title = metadata.propertyMap["TITLE"]?.singleOrNull()
            val artist = metadata.propertyMap["ARTIST"]?.singleOrNull()
            val album = metadata.propertyMap["ALBUM"]?.singleOrNull()

            println("TITLE = $title")
            println("ARTIST = $artist")
            println("ALBUM = $album")

            // 不一定存在
            if (title != null) {
                assertTrue(title.isNotEmpty())
            }

            // WAV 基本没有封面
            val pictures = TagLib.getPictures(fd.dup().detachFd())
            assertEquals(0, pictures.size)
        }
    }

    private fun getFdFromAssets(context: Context, fileName: String): ParcelFileDescriptor {
        val file = getFileFromAssets(context, fileName)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
    }

    private fun getFileFromAssets(context: Context, fileName: String): File {
        return File(context.cacheDir, fileName).apply {
            outputStream().use { cache ->
                context.assets.open(fileName).use { inputStream ->
                    inputStream.copyTo(cache)
                }
            }
        }
    }
}


class TagLibJNIAssetsTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun getFileFromAssets(context: Context, fileName: String): File {
        return File(context.cacheDir, fileName).apply {
            outputStream().use { cache ->
                context.assets.open(fileName).use { inputStream ->
                    inputStream.copyTo(cache)
                }
            }
        }
    }

    private fun getAudioFile(context: Context, fileName: String): AudioFile {
        val file = getFileFromAssets(context, fileName)
        return AudioFile(
            uri = android.net.Uri.fromFile(file),
            path = file.toPath(),
            modifiedMs = file.lastModified(),
            mimeType = when (file.extension.lowercase()) {
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "flac" -> "audio/flac"
                else -> "application/octet-stream"
            },
            size = file.length(),
            parent = null
        )
    }

    private fun testOpenAudio(context: Context, fileName: String) {
        val audioFile = getAudioFile(context, fileName)
        FileInputStream(audioFile.path.toFile()).use { fis ->
            val result = TagLibJNI.open(audioFile, fis)
            when (result) {
                is MetadataResult.Success -> {
                    assertNotNull("Metadata should not be null for $fileName", result.metadata)
                }
                MetadataResult.NoMetadata -> fail("No metadata found for $fileName")
                MetadataResult.NotAudio -> fail("$fileName is not recognized as audio")
                MetadataResult.ProviderFailed -> fail("Provider failed for $fileName")
            }
        }
    }

    @Test
    fun testWavReadWrite() {
        testOpenAudio(context, "无名策 - 塞壬唱片-MSR.wav")
    }

    @Test
    fun testFlacReadWrite() {
        testOpenAudio(context, "紫荆花盛开 - 李荣浩、梁咏琪.flac")
    }

    @Test
    fun testMp3ReadWrite() {
        testOpenAudio(context, "世末歌者 - 封茗囧菌、双笙 (陈元汐).mp3")
    }
}