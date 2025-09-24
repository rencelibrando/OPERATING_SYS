package org.example.project.core.image

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.github.jan.supabase.gotrue.auth
import org.example.project.core.config.SupabaseConfig
import java.util.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put


class ImageUploadService {
    
    private val supabase = SupabaseConfig.client
    private val httpClient = HttpClient(CIO)
    
    private val storageEndpoint = "https://tgsivldflzyydwjgoqhd.supabase.co/storage/v1/object/public"
    private val bucketName = "profile-pictures"
    
    suspend fun uploadProfilePicture(imageBytes: ByteArray): Result<String> {
        return try {
            println("📸 Uploading profile picture to Supabase Storage")
            
            if (!SupabaseConfig.isConfigured()) {
                throw Exception("Supabase is not configured")
            }
            
            val user = supabase.auth.currentUserOrNull()
            if (user == null) {
                throw Exception("No authenticated user found")
            }
            
            val fileExtension = getImageExtension(imageBytes)
            val fileName = "profile_${user.id}_${System.currentTimeMillis()}$fileExtension"
            
            println("📁 Uploading file: $fileName (${imageBytes.size} bytes)")
            
            val accessToken = supabase.auth.currentAccessTokenOrNull()
            if (accessToken == null) {
                throw Exception("No access token available. User may not be authenticated.")
            }
            
            
            val uploadUrl = "$storageEndpoint/$bucketName/$fileName"
            
            
            val contentType = when (fileExtension) {
                ".jpg", ".jpeg" -> "image/jpeg"
                ".png" -> "image/png"
                ".gif" -> "image/gif"
                ".bmp" -> "image/bmp"
                ".webp" -> "image/webp"
                else -> "image/jpeg" 
            }
            
            println("🌐 Uploading to: $uploadUrl")
            println("🔑 Using access token: ${accessToken.take(20)}...")
            println("📋 Content-Type: $contentType")
            println("📊 File size: ${imageBytes.size} bytes")
            
            
            val response = httpClient.post(uploadUrl) {
                headers {
                    append("Authorization", "Bearer $accessToken")
                    append("Content-Type", contentType)
                    append("Cache-Control", "max-age=3600")
                }
                setBody(imageBytes)
            }
            
            println("📡 Response status: ${response.status}")
            println("📡 Response headers: ${response.headers}")
            
            
            if (response.status.value in 200..299) {
                
                val publicUrl = "https://tgsivldflzyydwjgoqhd.supabase.co/storage/v1/object/public/$bucketName/$fileName"
                println("✅ Profile picture uploaded successfully: $publicUrl")
                Result.success(publicUrl)
            } else {
                val errorBody = response.bodyAsText()
                throw Exception("Upload failed with status ${response.status.value}: $errorBody")
            }
            
        } catch (e: Exception) {
            println("❌ Failed to upload profile picture: ${e.message}")
            Result.failure(e)
        }
    }
    

    suspend fun deleteProfilePicture(imageUrl: String): Result<Unit> {
        return try {
            println("🗑️ Deleting profile picture from Supabase Storage")
            
            
            val fileName = extractFileNameFromUrl(imageUrl)
            if (fileName == null) {
                throw Exception("Invalid image URL")
            }
            
            println("📁 Deleting file: $fileName")
            
            
            val accessToken = supabase.auth.currentAccessTokenOrNull()
            if (accessToken == null) {
                throw Exception("No access token available. User may not be authenticated.")
            }
            
            
            val deleteUrl = "$storageEndpoint/$bucketName/$fileName"
            
            println("🌐 Deleting from: $deleteUrl")
            println("🔑 Using access token: ${accessToken.take(20)}...")
            
            
            val response = httpClient.delete(deleteUrl) {
                headers {
                    append("Authorization", "Bearer $accessToken")
                }
            }
            
            
            if (response.status.value in 200..299) {
                println("✅ Profile picture deleted successfully")
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                throw Exception("Delete failed with status ${response.status.value}: $errorBody")
            }
            
        } catch (e: Exception) {
            println("❌ Failed to delete profile picture: ${e.message}")
            Result.failure(e)
        }
    }
    
    private fun getImageExtension(imageBytes: ByteArray): String {
        return when {
            imageBytes.size >= 3 && imageBytes[0] == 0xFF.toByte() && imageBytes[1] == 0xD8.toByte() && imageBytes[2] == 0xFF.toByte() -> ".jpg"
            imageBytes.size >= 4 && imageBytes[0] == 0x89.toByte() && imageBytes[1] == 0x50.toByte() && imageBytes[2] == 0x4E.toByte() && imageBytes[3] == 0x47.toByte() -> ".png"
            imageBytes.size >= 3 && imageBytes[0] == 0x47.toByte() && imageBytes[1] == 0x49.toByte() && imageBytes[2] == 0x46.toByte() -> ".gif"
            imageBytes.size >= 2 && imageBytes[0] == 0x42.toByte() && imageBytes[1] == 0x4D.toByte() -> ".bmp"
            imageBytes.size >= 4 && imageBytes[0] == 0x52.toByte() && imageBytes[1] == 0x49.toByte() && imageBytes[2] == 0x46.toByte() -> ".webp"
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
