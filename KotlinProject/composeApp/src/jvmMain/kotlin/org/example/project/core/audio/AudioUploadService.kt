package org.example.project.core.audio

import io.github.jan.supabase.storage.storage
import org.example.project.core.config.SupabaseConfig
import java.io.File
import kotlin.time.Duration.Companion.days

/**
 * Service for uploading user pronunciation recordings to Supabase Storage
 */
class AudioUploadService {
    private val supabase = SupabaseConfig.client
    private val bucketName = "user-pronunciations"

    /**
     * Upload user's pronunciation recording to Supabase Storage
     *
     * @param audioFile The audio file to upload
     * @param userId The user's ID
     * @param wordId The vocabulary word ID
     * @return Result containing the public/signed URL of the uploaded file
     */
    suspend fun uploadPronunciationAudio(
        audioFile: File,
        userId: String,
        wordId: String,
    ): Result<String> {
        return try {
            println("Uploading pronunciation audio to Supabase Storage")

            if (!SupabaseConfig.isConfigured()) {
                throw Exception("Supabase is not configured")
            }

            if (!audioFile.exists()) {
                throw Exception("Audio file does not exist: ${audioFile.absolutePath}")
            }

            // Create storage path: user-pronunciations/{userId}/{wordId}_{timestamp}.wav
            val timestamp = System.currentTimeMillis()
            val fileName = "${wordId}_$timestamp.wav"
            val storagePath = "$userId/$fileName"

            println("Uploading file: $storagePath (${audioFile.length()} bytes)")

            // Read audio file
            val audioBytes = audioFile.readBytes()

            // Use Supabase Storage SDK
            val storage = supabase.storage.from(bucketName)

            // Try to create bucket if it doesn't exist
            // Note: Bucket creation requires service role key or admin privileges
            // If this fails, the bucket needs to be created manually in Supabase Dashboard
            try {
                supabase.storage.createBucket(bucketName)
                println("Created storage bucket: $bucketName")
            } catch (e: Exception) {
                // Bucket might already exist, or we don't have permissions to create it
                println("Bucket creation check: ${e.message}")
                println("If bucket doesn't exist, please create it manually in Supabase Dashboard")
            }

            // Upload the file (upsert = true to replace if exists)
            // Upload the file (upsert = true to replace if exists)
            // Note: If bucket doesn't exist, this will fail with NotFoundRestException
            val uploadResult =
                try {
                    storage.upload(
                        path = storagePath,
                        data = audioBytes,
                        upsert = true,
                    )
                } catch (e: io.github.jan.supabase.exceptions.NotFoundRestException) {
                    throw Exception(
                        "Storage bucket '$bucketName' not found. " +
                            "Please create it in Supabase Dashboard → Storage → New Bucket. " +
                            "Bucket name: '$bucketName', Public: false",
                    )
                }

            println("Upload result: $uploadResult")

            // Get public URL (or signed URL if bucket is private)
            // Using 365 days expiry for signed URLs
            val audioUrl =
                try {
                    // Try to create signed URL (works for both public and private buckets)
                    storage.createSignedUrl(
                        path = storagePath,
                        expiresIn = 365.days,
                    )
                } catch (e: Exception) {
                    // If signed URL fails, try to construct public URL manually
                    // Get Supabase URL from the client
                    val supabaseUrl = supabase.supabaseUrl
                    "$supabaseUrl/storage/v1/object/public/$bucketName/$storagePath"
                }

            println("Pronunciation audio uploaded successfully: $audioUrl")
            Result.success(audioUrl)
        } catch (e: Exception) {
            println("Failed to upload pronunciation audio: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Delete a pronunciation recording from Supabase Storage
     */
    suspend fun deletePronunciationAudio(audioUrl: String): Result<Unit> {
        return try {
            println("Deleting pronunciation audio from Supabase Storage")

            // Extract path from URL
            val path = extractPathFromUrl(audioUrl)
            if (path == null) {
                throw Exception("Invalid audio URL: $audioUrl")
            }

            println("Deleting file: $path")

            val storage = supabase.storage.from(bucketName)
            storage.delete(listOf(path))

            println("Pronunciation audio deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to delete pronunciation audio: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Extract storage path from Supabase Storage URL
     */
    private fun extractPathFromUrl(url: String): String? {
        // URL format: https://{project}.supabase.co/storage/v1/object/public/{bucket}/{path}
        // or: https://{project}.supabase.co/storage/v1/object/sign/{bucket}/{path}?token=...
        return try {
            val parts = url.split("/storage/v1/object/")
            if (parts.size < 2) return null

            val afterObject = parts[1]
            // Remove query parameters if present (for signed URLs)
            val withoutQuery = afterObject.split("?")[0]

            // Remove bucket name prefix
            val bucketPrefix = "$bucketName/"
            if (withoutQuery.startsWith(bucketPrefix)) {
                withoutQuery.substring(bucketPrefix.length)
            } else {
                withoutQuery
            }
        } catch (e: Exception) {
            println("Error extracting path from URL: ${e.message}")
            null
        }
    }
}
