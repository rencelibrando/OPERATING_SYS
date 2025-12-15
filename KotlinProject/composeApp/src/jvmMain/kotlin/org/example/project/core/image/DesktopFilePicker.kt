package org.example.project.core.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

class DesktopFilePicker {
    companion object {
        /**
         * Pick a file with specific extensions
         * @param title Dialog title
         * @param allowedExtensions List of allowed file extensions (without dots)
         * @return Full path to selected file, or null if cancelled
         */
        fun pickFile(
            title: String = "Select File",
            allowedExtensions: List<String> = listOf("*")
        ): String? {
            return try {
                val fileChooser = JFileChooser()
                fileChooser.dialogTitle = title
                
                // Set up file filter if specific extensions are provided
                if (allowedExtensions.isNotEmpty() && !allowedExtensions.contains("*")) {
                    val filter = FileNameExtensionFilter(
                        "Allowed Files (${allowedExtensions.joinToString(", ")})",
                        *allowedExtensions.toTypedArray()
                    )
                    fileChooser.fileFilter = filter
                }
                
                val result = fileChooser.showOpenDialog(null)
                
                if (result == JFileChooser.APPROVE_OPTION) {
                    val selectedFile = fileChooser.selectedFile
                    if (selectedFile.exists() && selectedFile.isFile) {
                        selectedFile.absolutePath
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                println("❌ Error selecting file: ${e.message}")
                null
            }
        }
    }
    
    fun selectImage(): ByteArray? {
        return try {
            val fileChooser = JFileChooser()

            val imageFilter =
                FileNameExtensionFilter(
                    "Image Files",
                    "jpg",
                    "jpeg",
                    "png",
                    "gif",
                    "bmp",
                    "webp",
                )
            fileChooser.fileFilter = imageFilter

            fileChooser.dialogTitle = "Select Profile Picture"

            val result = fileChooser.showOpenDialog(null)

            if (result == JFileChooser.APPROVE_OPTION) {
                val selectedFile = fileChooser.selectedFile
                if (selectedFile.exists() && selectedFile.isFile) {
                    selectedFile.readBytes()
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            println("❌ Error selecting image: ${e.message}")
            null
        }
    }

    fun selectImageSimple(): ByteArray? {
        return try {
            val dialog = FileDialog(null as Frame?, "Select Profile Picture", FileDialog.LOAD)

            dialog.setFilenameFilter { _, name ->
                val lowerName = name.lowercase()
                lowerName.endsWith(".jpg") ||
                    lowerName.endsWith(".jpeg") ||
                    lowerName.endsWith(".png") ||
                    lowerName.endsWith(".gif") ||
                    lowerName.endsWith(".bmp") ||
                    lowerName.endsWith(".webp")
            }

            dialog.isVisible = true

            val fileName = dialog.file
            val directory = dialog.directory

            if (fileName != null && directory != null) {
                val file = File(directory, fileName)
                if (file.exists() && file.isFile) {
                    file.readBytes()
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            println("❌ Error selecting image: ${e.message}")
            null
        }
    }
}

@Composable
fun rememberDesktopFilePicker(): DesktopFilePicker {
    return remember { DesktopFilePicker() }
}
