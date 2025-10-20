package org.example.project.core.image

import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.storage.storage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.example.project.core.config.SupabaseConfig
import kotlin.time.Duration.Companion.days

class ImageUploadService {
    private val supabase = SupabaseConfig.client
    private val httpClient = HttpClient(CIO)

    private val storageEndpoint = "https://tgsivldflzyydwjgoqhd.supabase.co/storage/v1/object/public"
    private val bucketName = "profile-pictures"

    suspend fun uploadProfilePicture(imageBytes: ByteArray): Result<String> {
        return try {
            println("Uploading profile picture to Supabase Storage")

            if (!SupabaseConfig.isConfigured()) {
                throw Exception("Supabase is not configured")
            }

            val user = supabase.auth.currentUserOrNull()
            if (user == null) {
                throw Exception("No authenticated user found")
            }

            val fileExtension = getImageExtension(imageBytes)
            val fileName = "profile_${user.id}_${System.currentTimeMillis()}$fileExtension"

            println("Uploading file: $fileName (${imageBytes.size} bytes)")

            // Use Supabase Storage SDK
            val storage = supabase.storage.from(bucketName)

            // Try to create bucket if it doesn't exist (this will fail silently if it already exists)
            try {
                supabase.storage.createBucket(bucketName)
                println("Created storage bucket: $bucketName")
            } catch (e: Exception) {
                println("Bucket $bucketName already exists or creation failed: ${e.message}")
            }

            // Replace existing file with same name
            val uploadResult =
                storage.upload(
                    path = fileName,
                    data = imageBytes,
                    upsert = true,
                )

            println("Upload result: $uploadResult")

            // Get public URL (1 year expiry)
            val publicUrl =
                storage.createSignedUrl(
                    path = fileName,
                    expiresIn = 365.days,
                )

            println("Profile picture uploaded successfully: $publicUrl")
            Result.success(publicUrl)
        } catch (e: Exception) {
            println("Failed to upload profile picture: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteProfilePicture(imageUrl: String): Result<Unit> {
        return try {
            println("Deleting profile picture from Supabase Storage")

            val fileName = extractFileNameFromUrl(imageUrl)
            if (fileName == null) {
                throw Exception("Invalid image URL")
            }

            println("Deleting file: $fileName")

            // Use Supabase Storage SDK
            val storage = supabase.storage.from(bucketName)

            val deleteResult = storage.delete(listOf(fileName))

            println("Delete result: $deleteResult")
            println("Profile picture deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to delete profile picture: ${e.message}")
            Result.failure(e)
        }
    }

    private fun getImageExtension(imageBytes: ByteArray): String {
        return when {
            imageBytes.size >= 3 &&
                imageBytes[0] == 0xFF.toByte() &&
                imageBytes[1] == 0xD8.toByte() &&
                imageBytes[2] == 0xFF.toByte() -> ".jpg"
            imageBytes.size >= 4 &&
                imageBytes[0] == 0x89.toByte() &&
                imageBytes[1] == 0x50.toByte() &&
                imageBytes[2] == 0x4E.toByte() &&
                imageBytes[3] == 0x47.toByte() -> ".png"
            imageBytes.size >= 3 &&
                imageBytes[0] == 0x47.toByte() &&
                imageBytes[1] == 0x49.toByte() &&
                imageBytes[2] == 0x46.toByte() -> ".gif"
            imageBytes.size >= 2 &&
                imageBytes[0] == 0x42.toByte() &&
                imageBytes[1] == 0x4D.toByte() -> ".bmp"
            imageBytes.size >= 4 &&
                imageBytes[0] == 0x52.toByte() &&
                imageBytes[1] == 0x49.toByte() &&
                imageBytes[2] == 0x46.toByte() -> ".webp"
            else -> ".jpg"
        }
    }

    private fun extractFileNameFromUrl(url: String): String? {
        return try {
            val parts = url.split("/")
            parts.lastOrNull()?.split("?")?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun close() {
        httpClient.close()
    }
}
