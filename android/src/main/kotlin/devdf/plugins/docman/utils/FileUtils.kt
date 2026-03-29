package devdf.plugins.docman.utils

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import java.io.File

object FileUtils {


    private const val TAG: String = "FilePickerUtils"
    private const val PRIMARY_VOLUME_NAME: String = "primary"

    fun getMimeTypes(allowedExtensions: ArrayList<String>?): Array<String>? {
        if (allowedExtensions.isNullOrEmpty()) {
            return null
        }

        val mimes = ArrayList<String>()

        for (i in allowedExtensions.indices) {
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(allowedExtensions[i])
            if (mime == null) {
                Log.w(TAG, "Custom file type " + allowedExtensions[i] + " is unsupported and will be ignored.")
                continue
            }

            mimes.add(mime)
        }
        Log.d(TAG, "Allowed file extensions mimes: $mimes")
        return mimes.toTypedArray<String>()
    }

    fun getFileName(uri: Uri, context: Context): String? {
        var result: String? = null

        try {
            if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                try {
                    if (cursor != null && cursor.moveToFirst()) {
                        result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    }
                } finally {
                    cursor!!.close()
                }
            }
            if (result == null) {
                result = uri.path
                val cut = result!!.lastIndexOf('/')
                if (cut != -1) {
                    result = result.substring(cut + 1)
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to handle file name: $ex")
        }

        return result
    }

    fun clearCache(context: Context): Boolean {
        try {
            val cacheDir = File(context.cacheDir.toString() + "/file_picker/")
            val files = cacheDir.listFiles()

            if (files != null) {
                for (file in files) {
                    file.delete()
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "There was an error while clearing cached files: $ex")
            return false
        }
        return true
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    fun getFullPathFromTreeUri(treeUri: Uri?, con: Context): String? {
        if (treeUri == null) {
            return null
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (isDownloadsDocument(treeUri)) {
                val docId = DocumentsContract.getDocumentId(treeUri)
                val extPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
                if (docId == "downloads") {
                    return extPath
                } else if (docId.matches("^ms[df]\\:.*".toRegex())) {
                    val fileName = getFileName(treeUri, con)
                    return "$extPath/$fileName"
                } else if (docId.startsWith("raw:")) {
                    val rawPath = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                    return rawPath
                }
                return null
            }
        }

        val volumeId = getVolumeIdFromTreeUri(treeUri)
        var volumePath = getVolumePath(volumeId, con)

        // Workaround in the case of volume id = home, https://github.com/miguelpruivo/flutter_file_picker/issues/692
        if (volumeId != null && volumeId == "home") {
            volumePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).path
        }

        if (volumePath == null) {
            // return more volumes, from mass storage
            volumePath = getVolumePathNewApi(volumeId, con)
        }

        if (volumePath == null) {
            return File.separator
        }

        if (volumePath.endsWith(File.separator)) volumePath = volumePath.substring(0, volumePath.length - 1)

        var documentPath = getDocumentPathFromTreeUri(treeUri)

        if (documentPath!!.endsWith(File.separator)) documentPath = documentPath.substring(0, documentPath.length - 1)

        return if (documentPath.length > 0) {
            if (documentPath.startsWith(File.separator)) {
                volumePath + documentPath
            } else {
                volumePath + File.separator + documentPath
            }
        } else {
            volumePath
        }
    }

    private fun getDirectoryPath(storageVolumeClazz: Class<*>, storageVolumeElement: Any): String? {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val getPath = storageVolumeClazz.getMethod("getPath")
                return getPath.invoke(storageVolumeElement) as String
            }

            val getDirectory = storageVolumeClazz.getMethod("getDirectory")
            val f = getDirectory.invoke(storageVolumeElement) as File
            if (f != null) return f.path
        } catch (ex: Exception) {
            return null
        }
        return null
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun getVolumePath(volumeId: String?, context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        try {
            val mStorageManager =
                context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val storageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
            val getVolumeList = mStorageManager.javaClass.getMethod("getVolumeList")
            val getUuid = storageVolumeClazz.getMethod("getUuid")
            val isPrimary = storageVolumeClazz.getMethod("isPrimary")
            val result = getVolumeList.invoke(mStorageManager) ?: return null

            val length = java.lang.reflect.Array.getLength(result)
            for (i in 0 until length) {
                val storageVolumeElement = java.lang.reflect.Array.get(result, i)
                val uuid = getUuid.invoke(storageVolumeElement) as String
                val primary = isPrimary.invoke(storageVolumeElement) as Boolean

                // primary volume?
                if (primary != null && PRIMARY_VOLUME_NAME == volumeId) {
                    return getDirectoryPath(storageVolumeClazz, storageVolumeElement)
                }

                // other volumes?
                if (uuid != null && uuid == volumeId) {
                    return getDirectoryPath(storageVolumeClazz, storageVolumeElement)
                }
            }
            // not found.
            return null
        } catch (ex: Exception) {
            return null
        }
    }

    private fun getVolumePathNewApi(volumeId: String?, context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        try {
            val mStorageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                for (storageVolume in mStorageManager.storageVolumes) {
                    val uuid = storageVolume.uuid
                    // primary volume?
                    if (PRIMARY_VOLUME_NAME == volumeId) {
                        return getDirectoryPath(storageVolume.javaClass, storageVolume)
                    }
                    // other volumes?
                    if (uuid != null && uuid == volumeId) {
                        return getDirectoryPath(storageVolume.javaClass, storageVolume)
                    }
                }
            }
            // not found.
            return null
        } catch (ex: Exception) {
            return null
        }
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getVolumeIdFromTreeUri(treeUri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return if (split.size > 0) split[0]
        else null
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getDocumentPathFromTreeUri(treeUri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val split: Array<String?> = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return if ((split.size >= 2) && (split[1] != null)) split[1]
        else File.separator
    }
}
