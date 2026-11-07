
package com.alzatube.app

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.LibraryResult
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class LocalMusicService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var librarySession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer

        librarySession = MediaLibrarySession.Builder(
            this,
            exoPlayer,
            object : MediaLibrarySession.Callback {

                override fun onGetLibraryRoot(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    params: LibraryParams?
                ): ListenableFuture<LibraryResult<MediaItem>> {

                    val root = folder(ROOT_ID, "AlzaTube Auto Music")

                    return Futures.immediateFuture(
                        LibraryResult.ofItem(root, params)
                    )
                }

                override fun onGetChildren(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    parentId: String,
                    page: Int,
                    pageSize: Int,
                    params: LibraryParams?
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {

                    val items = if (parentId == ROOT_ID) {
                        listOf(
                            folder(ALL_ID, "All Songs"),
                            folder(MUSIC_ID, "Music folder"),
                            folder(DOWNLOADS_ID, "Download folder"),
                            folder(SDCARD_ID, "SD card music")
                        )
                    } else {
                        loadAudioItems(parentId)
                            .drop(page * pageSize)
                            .take(pageSize)
                    }

                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(
                            ImmutableList.copyOf(items),
                            params
                        )
                    )
                }
            }
        ).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibrarySession? {
        return librarySession
    }

    private fun folder(id: String, title: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        return checkSelfPermission(permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun loadAudioItems(category: String): List<MediaItem> {
        if (!hasAudioPermission()) return emptyList()

        val collection: Uri =
            if (Build.VERSION.SDK_INT >= 29) {
                MediaStore.Audio.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL
                )
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM
        )

        if (Build.VERSION.SDK_INT >= 29) {
            projection.add(MediaStore.Audio.Media.RELATIVE_PATH)
            projection.add(MediaStore.Audio.Media.VOLUME_NAME)
        }

        val result = mutableListOf<MediaItem>()

        try {
            contentResolver.query(
                collection,
                projection.toTypedArray(),
                null,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { cursor ->

                val idCol = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media._ID
                )
                val titleCol = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.TITLE
                )
                val artistCol = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ARTIST
                )
                val albumCol = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ALBUM
                )

                val pathCol =
                    if (Build.VERSION.SDK_INT >= 29)
                        cursor.getColumnIndex(
                            MediaStore.Audio.Media.RELATIVE_PATH
                        )
                    else -1

                val volumeCol =
                    if (Build.VERSION.SDK_INT >= 29)
                        cursor.getColumnIndex(
                            MediaStore.Audio.Media.VOLUME_NAME
                        )
                    else -1

                while (cursor.moveToNext()) {

                    val relativePath =
                        if (pathCol >= 0)
                            cursor.getString(pathCol).orEmpty()
                        else ""

                    val volumeName =
                        if (volumeCol >= 0)
                            cursor.getString(volumeCol).orEmpty()
                        else ""

                    val isSdCard =
                        Build.VERSION.SDK_INT >= 29 &&
                        volumeName.isNotBlank() &&
                        volumeName != MediaStore.VOLUME_EXTERNAL_PRIMARY

                    val include = when (category) {
                        MUSIC_ID ->
                            relativePath.startsWith("Music/", true)

                        DOWNLOADS_ID ->
                            relativePath.startsWith("Download/", true) ||
                            relativePath.startsWith("Downloads/", true)

                        SDCARD_ID -> isSdCard

                        else -> true
                    }

                    if (!include) continue

                    val id = cursor.getLong(idCol)
                    val title =
                        cursor.getString(titleCol) ?: "Unknown title"
                    val artist =
                        cursor.getString(artistCol) ?: "Unknown artist"
                    val album =
                        cursor.getString(albumCol) ?: ""

                    val itemUri = if (
                        Build.VERSION.SDK_INT >= 30 &&
                        volumeName.isNotBlank()
                    ) {
                        MediaStore.Audio.Media.getContentUri(
                            volumeName,
                            id
                        )
                    } else {
                        ContentUris.withAppendedId(collection, id)
                    }

                    result.add(
                        MediaItem.Builder()
                            .setMediaId(itemUri.toString())
                            .setUri(itemUri)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(title)
                                    .setArtist(artist)
                                    .setAlbumTitle(album)
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .build()
                            )
                            .build()
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }

        return result
    }

    override fun onDestroy() {
        librarySession?.release()
        librarySession = null

        player?.release()
        player = null

        super.onDestroy()
    }

    companion object {
        private const val ROOT_ID = "alzatube_root"
        private const val ALL_ID = "all_songs"
        private const val MUSIC_ID = "music_folder"
        private const val DOWNLOADS_ID = "downloads_folder"
        private const val SDCARD_ID = "sdcard_music"
    }
}
