package org.example.project.core.export

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.domain.model.LanguageProgress
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.awt.Desktop
import java.net.URI

/**
 * Service for exporting and sharing progress data.
 * Supports PNG image export and shareable links.
 */
class ProgressExportService {

    /**
     * Exports progress as a PNG image.
     * @param imageBitmap Screenshot of progress card
     * @param progress Progress data for filename
     * @return File path to saved image
     */
    suspend fun exportToPNG(
        imageBitmap: ImageBitmap,
        progress: LanguageProgress
    ): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val fileName = "progress_${progress.language.code}_$timestamp.png"
            
            // Save to user's documents folder
            val documentsFolder = File(System.getProperty("user.home"), "Documents/WordBridge_Exports")
            documentsFolder.mkdirs()
            
            val file = File(documentsFolder, fileName)
            
            // Convert ImageBitmap to Skia Bitmap and encode as PNG
            val skiaBitmap = imageBitmap.asSkiaBitmap()
            val image = org.jetbrains.skia.Image.makeFromBitmap(skiaBitmap)
            val bytes = image.encodeToData(EncodedImageFormat.PNG)
                ?: throw Exception("Failed to encode image")
            
            file.writeBytes(bytes.bytes)
            
            println("[ProgressExport] ✅ Exported to: ${file.absolutePath}")
            
            file.absolutePath
        }
    }

    /**
     * Exports progress as HTML report.
     */
    suspend fun exportToHTML(
        progress: LanguageProgress,
        userName: String = "Student"
    ): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val fileName = "progress_report_${progress.language.code}_$timestamp.html"
            
            val documentsFolder = File(System.getProperty("user.home"), "Documents/WordBridge_Exports")
            documentsFolder.mkdirs()
            
            val file = File(documentsFolder, fileName)
            
            val html = generateHTMLReport(progress, userName)
            file.writeText(html)
            
            println("[ProgressExport] ✅ HTML report exported to: ${file.absolutePath}")
            
            file.absolutePath
        }
    }

    /**
     * Opens exported file in system default viewer.
     */
    suspend fun openFile(filePath: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (file.exists() && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file)
                println("[ProgressExport] ✅ Opened file: $filePath")
            } else {
                throw Exception("File not found or Desktop not supported")
            }
        }
    }

    /**
     * Generates shareable summary text for clipboard.
     */
    fun generateShareableText(progress: LanguageProgress, userName: String = "I"): String {
        val languageFlag = getLanguageFlag(progress.language.code)
        
        return buildString {
            appendLine("$languageFlag ${progress.language.displayName} Learning Progress $languageFlag")
            appendLine("")
            appendLine("📚 Lessons: ${progress.lessonsCompleted}/${progress.totalLessons} (${progress.lessonsProgressPercentage}%)")
            appendLine("💬 Conversation Sessions: ${progress.conversationSessions}")
            appendLine("📖 Vocabulary: ${progress.vocabularyWords} words")
            appendLine("⏱️ Practice Time: ${progress.formattedTime}")
            
            if (progress.voiceAnalysis.hasScores) {
                appendLine("")
                appendLine("🎤 Voice Analysis:")
                if (progress.voiceAnalysis.overall > 0) {
                    appendLine("  Overall: ${progress.voiceAnalysis.overall.toInt()}/100")
                }
                if (progress.voiceAnalysis.pronunciation > 0) {
                    appendLine("  Pronunciation: ${progress.voiceAnalysis.pronunciation.toInt()}/100")
                }
                if (progress.voiceAnalysis.fluency > 0) {
                    appendLine("  Fluency: ${progress.voiceAnalysis.fluency.toInt()}/100")
                }
            }
            
            appendLine("")
            appendLine("🌟 Keep learning with WordBridge!")
        }
    }

    /**
     * Copies text to system clipboard.
     */
    suspend fun copyToClipboard(text: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val stringSelection = java.awt.datatransfer.StringSelection(text)
            clipboard.setContents(stringSelection, null)
            
            println("[ProgressExport] ✅ Copied to clipboard (${text.length} chars)")
        }
    }

    /**
     * Generates shareable link (placeholder for future web integration).
     */
    fun generateShareableLink(
        progress: LanguageProgress,
        userId: String
    ): String {
        // Future: Upload to cloud and generate short link
        val params = "lang=${progress.language.code}&" +
                "lessons=${progress.lessonsCompleted}&" +
                "sessions=${progress.conversationSessions}&" +
                "vocab=${progress.vocabularyWords}&" +
                "score=${progress.voiceAnalysis.averageScore.toInt()}"
        
        return "https://wordbridge.app/share?$params"
    }

    /**
     * Shares via system share dialog (platform-specific).
     */
    suspend fun shareViaSystem(
        title: String,
        text: String,
        filePath: String? = null
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            // Desktop doesn't have a unified share sheet like mobile
            // Copy to clipboard as fallback
            copyToClipboard(text).getOrThrow()
            
            // Open file if provided
            filePath?.let { openFile(it).getOrThrow() }
            
            println("[ProgressExport] ✅ Shared (copied to clipboard)")
        }
    }

    private fun generateHTMLReport(
        progress: LanguageProgress,
        userName: String
    ): String {
        val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"))
        val languageFlag = getLanguageFlag(progress.language.code)
        
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${progress.language.displayName} Progress Report</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            padding: 40px 20px;
            min-height: 100vh;
        }
        .container {
            max-width: 800px;
            margin: 0 auto;
            background: white;
            border-radius: 16px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            overflow: hidden;
        }
        .header {
            background: linear-gradient(135deg, #8B5CF6 0%, #6366F1 100%);
            color: white;
            padding: 40px;
            text-align: center;
        }
        .header h1 {
            font-size: 2.5em;
            margin-bottom: 10px;
        }
        .header p {
            opacity: 0.9;
            font-size: 1.1em;
        }
        .content {
            padding: 40px;
        }
        .metric-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 20px;
            margin: 30px 0;
        }
        .metric-card {
            background: #F9FAFB;
            border-radius: 12px;
            padding: 24px;
            text-align: center;
        }
        .metric-icon {
            font-size: 2em;
            margin-bottom: 8px;
        }
        .metric-value {
            font-size: 1.8em;
            font-weight: bold;
            color: #1F2937;
            margin: 8px 0;
        }
        .metric-label {
            color: #6B7280;
            font-size: 0.9em;
        }
        .scores-section {
            margin-top: 40px;
            padding: 24px;
            background: #F3F4F6;
            border-radius: 12px;
        }
        .score-bar {
            margin: 16px 0;
        }
        .score-label {
            display: flex;
            justify-content: space-between;
            margin-bottom: 8px;
            font-size: 0.95em;
        }
        .progress-bar {
            height: 12px;
            background: #E5E7EB;
            border-radius: 6px;
            overflow: hidden;
        }
        .progress-fill {
            height: 100%;
            background: linear-gradient(90deg, #8B5CF6, #6366F1);
            transition: width 0.3s ease;
        }
        .footer {
            text-align: center;
            padding: 30px;
            color: #6B7280;
            border-top: 1px solid #E5E7EB;
        }
        @media print {
            body { background: white; padding: 0; }
            .container { box-shadow: none; }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>$languageFlag ${progress.language.displayName} Learning Progress</h1>
            <p>$userName's Progress Report • $date</p>
        </div>
        
        <div class="content">
            <div class="metric-grid">
                <div class="metric-card">
                    <div class="metric-icon">📚</div>
                    <div class="metric-value">${progress.lessonsCompleted}/${progress.totalLessons}</div>
                    <div class="metric-label">Lessons Completed</div>
                </div>
                
                <div class="metric-card">
                    <div class="metric-icon">💬</div>
                    <div class="metric-value">${progress.conversationSessions}</div>
                    <div class="metric-label">Conversation Sessions</div>
                </div>
                
                <div class="metric-card">
                    <div class="metric-icon">📖</div>
                    <div class="metric-value">${progress.vocabularyWords}</div>
                    <div class="metric-label">Vocabulary Words</div>
                </div>
                
                <div class="metric-card">
                    <div class="metric-icon">⏱️</div>
                    <div class="metric-value">${progress.formattedTime}</div>
                    <div class="metric-label">Practice Time</div>
                </div>
            </div>
            
            ${if (progress.voiceAnalysis.hasScores) """
            <div class="scores-section">
                <h2 style="margin-bottom: 20px;">🎤 Voice Analysis Scores</h2>
                
                ${generateScoreBar("Overall", progress.voiceAnalysis.overall)}
                ${generateScoreBar("Pronunciation", progress.voiceAnalysis.pronunciation)}
                ${generateScoreBar("Fluency", progress.voiceAnalysis.fluency)}
                ${generateScoreBar("Grammar", progress.voiceAnalysis.grammar)}
                ${generateScoreBar("Vocabulary", progress.voiceAnalysis.vocabulary)}
                ${generateScoreBar("Accuracy", progress.voiceAnalysis.accuracy)}
            </div>
            """ else ""}
        </div>
        
        <div class="footer">
            <p>Generated by <strong>WordBridge</strong> - Your Language Learning Companion</p>
            <p style="margin-top: 8px; font-size: 0.9em;">Keep up the great work! 🌟</p>
        </div>
    </div>
</body>
</html>
        """.trimIndent()
    }

    private fun generateScoreBar(label: String, score: Double): String {
        if (score <= 0) return ""
        
        val scoreInt = score.toInt()
        val color = when {
            score >= 80 -> "#10B981"
            score >= 60 -> "#F59E0B"
            score >= 40 -> "#FF8C42"
            else -> "#EF4444"
        }
        
        return """
            <div class="score-bar">
                <div class="score-label">
                    <span>$label</span>
                    <span style="font-weight: bold; color: $color;">$scoreInt/100</span>
                </div>
                <div class="progress-bar">
                    <div class="progress-fill" style="width: ${score}%; background: $color;"></div>
                </div>
            </div>
        """.trimIndent()
    }

    private fun getLanguageFlag(code: String): String {
        return when (code) {
            "ko" -> "🇰🇷"
            "zh" -> "🇨🇳"
            "fr" -> "🇫🇷"
            "de" -> "🇩🇪"
            "es" -> "🇪🇸"
            else -> "🌍"
        }
    }
}
