package org.example.project.core.auth

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Simple HTTP server to handle email verification callbacks for desktop app
 */
class EmailVerificationServer {
    
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var onVerificationCallback: ((String?) -> Unit)? = null
    
   
    fun startServer(port: Int = 3000, onVerification: (String?) -> Unit) {
        if (isRunning) return
        
        onVerificationCallback = onVerification
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                
                println("🌐 Email verification server started on port $port")
                println("📧 Waiting for email verification callback...")
                
                while (isRunning && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        handleRequest(clientSocket)
                    } catch (e: Exception) {
                        if (isRunning) {
                            println("❌ Error handling request: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("❌ Error starting server: ${e.message}")
                onVerificationCallback?.invoke(null)
            }
        }
    }
   
    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
            println("🛑 Email verification server stopped")
        } catch (e: Exception) {
            println("❌ Error stopping server: ${e.message}")
        }
    }
    
  
    private fun handleRequest(clientSocket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val writer = OutputStreamWriter(clientSocket.getOutputStream())
            
            val requestLine = reader.readLine()
            if (requestLine != null && requestLine.contains("GET")) {
                
                val urlPart = requestLine.split(" ")[1]
                println("📥 Received request: $urlPart")
                
                when {
                    urlPart.contains("/auth/callback") && urlPart.contains("access_token") -> {
                        // Handle JavaScript callback with access token
                        val accessToken = extractAccessTokenFromQuery(urlPart)
                        sendSuccessResponse(writer)
                        onVerificationCallback?.invoke(accessToken)
                        println("✅ Email verification callback received via JavaScript!")
                        
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(1000) 
                            stopServer()
                        }
                    }
                    urlPart.contains("/auth/callback") && urlPart.contains("error=") -> {
                        // Handle JavaScript callback with error
                        val error = extractErrorFromQuery(urlPart)
                        sendErrorResponse(writer, error)
                        println("❌ Email verification error via JavaScript: $error")
                        onVerificationCallback?.invoke(null)
                        
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(3000)
                            stopServer()
                        }
                    }
                    urlPart.contains("access_token") -> {
                        // Success case
                        val accessToken = extractAccessToken(urlPart)
                        sendSuccessResponse(writer)
                        onVerificationCallback?.invoke(accessToken)
                        println("✅ Email verification callback received!")
                        
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(1000) 
                            stopServer()
                        }
                    }
                    urlPart.contains("error=") -> {
                        // Error case - expired link, invalid token, etc.
                        val error = extractError(urlPart)
                        sendErrorResponse(writer, error)
                        println("❌ Email verification error: $error")
                        onVerificationCallback?.invoke(null)
                        
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(3000) // Give user time to read error
                            stopServer()
                        }
                    }
                    else -> {
                        sendWaitingResponse(writer)
                    }
                }
            }
            
            clientSocket.close()
            
        } catch (e: Exception) {
            println("❌ Error handling request: ${e.message}")
        }
    }
    
    private fun extractAccessToken(url: String): String? {
        return try {
            val tokenPart = when {
                url.contains("#access_token=") -> url.substringAfter("#access_token=")
                url.contains("?access_token=") -> url.substringAfter("?access_token=")
                url.contains("&access_token=") -> url.substringAfter("&access_token=")
                else -> return null
            }
            
            val token = tokenPart.split("&")[0]
            URLDecoder.decode(token, "UTF-8")
        } catch (e: Exception) {
            println("❌ Error extracting access token: ${e.message}")
            null
        }
    }
    
    private fun extractError(url: String): String {
        return try {
            val errorCode = if (url.contains("error_code=")) {
                URLDecoder.decode(url.substringAfter("error_code=").split("&")[0], "UTF-8")
            } else "unknown"
            
            val errorDescription = if (url.contains("error_description=")) {
                URLDecoder.decode(url.substringAfter("error_description=").split("&")[0], "UTF-8")
            } else "Unknown error"
            
            "$errorCode: $errorDescription"
        } catch (e: Exception) {
            "Error parsing error details"
        }
    }
    
    private fun extractAccessTokenFromQuery(url: String): String? {
        return try {
            if (url.contains("access_token=")) {
                val tokenPart = url.substringAfter("access_token=").split("&")[0]
                URLDecoder.decode(tokenPart, "UTF-8")
            } else null
        } catch (e: Exception) {
            println("❌ Error extracting access token from query: ${e.message}")
            null
        }
    }
    
    private fun extractErrorFromQuery(url: String): String {
        return try {
            val errorCode = if (url.contains("error_code=")) {
                URLDecoder.decode(url.substringAfter("error_code=").split("&")[0], "UTF-8")
            } else "unknown"
            
            val errorDescription = if (url.contains("error_description=")) {
                URLDecoder.decode(url.substringAfter("error_description=").split("&")[0], "UTF-8")
            } else "Unknown error"
            
            "$errorCode: $errorDescription"
        } catch (e: Exception) {
            "Error parsing error details from query"
        }
    }
    
    private fun sendSuccessResponse(writer: OutputStreamWriter) {
        val response = """
            HTTP/1.1 200 OK
            Content-Type: text/html
            Connection: close
            
            <!DOCTYPE html>
            <html>
            <head>
                <title>Email Verified!</title>
                <meta charset="UTF-8">
                <style>
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        margin: 0; padding: 0; height: 100vh;
                        display: flex; align-items: center; justify-content: center;
                    }
                    .container { 
                        background: white; padding: 2rem; border-radius: 1rem;
                        box-shadow: 0 20px 40px rgba(0,0,0,0.1); text-align: center;
                        max-width: 400px; margin: 2rem;
                    }
                    .success-icon { font-size: 4rem; margin-bottom: 1rem; }
                    h1 { color: #22c55e; margin: 0 0 1rem 0; }
                    p { color: #6b7280; margin-bottom: 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="success-icon">✅</div>
                    <h1>Email Verified!</h1>
                    <p>Your email has been successfully verified.</p>
                    <p>You can now close this tab and return to WordBridge.</p>
                </div>
                <script>
                    // Auto-close the tab after 3 seconds
                    setTimeout(() => {
                        window.close();
                    }, 3000);
                </script>
            </body>
            </html>
        """.trimIndent()
        
        writer.write(response)
        writer.flush()
    }
    
    /**
     * Send waiting response to browser
     */
    private fun sendWaitingResponse(writer: OutputStreamWriter) {
        val response = """
            HTTP/1.1 200 OK
            Content-Type: text/html
            Connection: close
            
            <!DOCTYPE html>
            <html>
            <head>
                <title>Waiting for Email Verification</title>
                <meta charset="UTF-8">
                <style>
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        margin: 0; padding: 0; height: 100vh;
                        display: flex; align-items: center; justify-content: center;
                    }
                    .container { 
                        background: white; padding: 2rem; border-radius: 1rem;
                        box-shadow: 0 20px 40px rgba(0,0,0,0.1); text-align: center;
                        max-width: 400px; margin: 2rem;
                    }
                    .waiting-icon { font-size: 4rem; margin-bottom: 1rem; }
                    h1 { color: #3b82f6; margin: 0 0 1rem 0; }
                    p { color: #6b7280; margin-bottom: 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="waiting-icon">⏳</div>
                    <h1>Waiting for Verification</h1>
                    <p>Click the verification link in your email to continue.</p>
                    <p>This page will update automatically.</p>
                </div>
                <script>
                    // Check for access token in URL fragment (after #)
                    function checkForAccessToken() {
                        const hash = window.location.hash;
                        console.log('Checking URL fragment:', hash);
                        
                        if (hash && hash.includes('access_token=')) {
                            // Extract access token
                            const tokenMatch = hash.match(/access_token=([^&]+)/);
                            if (tokenMatch) {
                                const accessToken = tokenMatch[1];
                                console.log('Found access token:', accessToken.substring(0, 20) + '...');
                                
                                // Send token to server
                                fetch('/auth/callback?access_token=' + encodeURIComponent(accessToken))
                                    .then(response => {
                                        console.log('Token sent to server successfully');
                                        // The server will handle the rest and show success page
                                    })
                                    .catch(error => {
                                        console.error('Error sending token to server:', error);
                                    });
                                
                                return true;
                            }
                        }
                        
                        if (hash && hash.includes('error=')) {
                            // Handle error case
                            const errorMatch = hash.match(/error=([^&]+)/);
                            const errorCodeMatch = hash.match(/error_code=([^&]+)/);
                            const errorDescMatch = hash.match(/error_description=([^&]+)/);
                            
                            if (errorMatch) {
                                const error = decodeURIComponent(errorMatch[1]);
                                const errorCode = errorCodeMatch ? decodeURIComponent(errorCodeMatch[1]) : '';
                                const errorDesc = errorDescMatch ? decodeURIComponent(errorDescMatch[1]) : '';
                                
                                console.log('Found error in URL:', error, errorCode, errorDesc);
                                
                                // Send error to server
                                fetch('/auth/callback?error=' + encodeURIComponent(error) + 
                                     '&error_code=' + encodeURIComponent(errorCode) + 
                                     '&error_description=' + encodeURIComponent(errorDesc))
                                    .catch(error => {
                                        console.error('Error sending error to server:', error);
                                    });
                                
                                return true;
                            }
                        }
                        
                        return false;
                    }
                    
                    // Check immediately when page loads
                    if (checkForAccessToken()) {
                        // Token found and sent, wait for server response
                        document.querySelector('h1').textContent = 'Email Verified!';
                        document.querySelector('.waiting-icon').textContent = '✅';
                        document.querySelector('p').innerHTML = 'Your email has been successfully verified.<br>You can now close this tab and return to WordBridge.';
                    } else {
                        // No token in URL, keep waiting
                        console.log('No access token found in URL fragment, waiting...');
                        
                        // Check again every 2 seconds for changes
                        setInterval(() => {
                            if (checkForAccessToken()) {
                                document.querySelector('h1').textContent = 'Email Verified!';
                                document.querySelector('.waiting-icon').textContent = '✅';
                                document.querySelector('p').innerHTML = 'Your email has been successfully verified.<br>You can now close this tab and return to WordBridge.';
                            }
                        }, 2000);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        
        writer.write(response)
        writer.flush()
    }
    
    /**
     * Send error response to browser for expired/invalid links
     */
    private fun sendErrorResponse(writer: OutputStreamWriter, error: String) {
        val response = """
            HTTP/1.1 200 OK
            Content-Type: text/html
            Connection: close
            
            <!DOCTYPE html>
            <html>
            <head>
                <title>Email Verification Error</title>
                <meta charset="UTF-8">
                <style>
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        margin: 0; padding: 0; height: 100vh;
                        display: flex; align-items: center; justify-content: center;
                    }
                    .container { 
                        background: white; padding: 2rem; border-radius: 1rem;
                        box-shadow: 0 20px 40px rgba(0,0,0,0.1); text-align: center;
                        max-width: 400px; margin: 2rem;
                    }
                    .error-icon { font-size: 4rem; margin-bottom: 1rem; }
                    h1 { color: #ef4444; margin: 0 0 1rem 0; }
                    p { color: #6b7280; margin-bottom: 1rem; }
                    .error-details { 
                        background: #fef2f2; padding: 1rem; border-radius: 0.5rem;
                        border-left: 4px solid #ef4444; margin: 1rem 0;
                        font-size: 0.9rem; color: #7f1d1d;
                    }
                    .btn {
                        background: #3b82f6; color: white; padding: 0.75rem 1.5rem;
                        border: none; border-radius: 0.5rem; cursor: pointer;
                        text-decoration: none; display: inline-block; margin-top: 1rem;
                    }
                    .btn:hover { background: #2563eb; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="error-icon">❌</div>
                    <h1>Verification Failed</h1>
                    <p>The email verification link has expired or is invalid.</p>
                    <div class="error-details">
                        <strong>Error:</strong> $error
                    </div>
                    <p>Please request a new verification email from the app.</p>
                    <button class="btn" onclick="window.close()">Close Tab</button>
                </div>
                <script>
                    // Auto-close the tab after 10 seconds
                    setTimeout(() => {
                        window.close();
                    }, 10000);
                </script>
            </body>
            </html>
        """.trimIndent()
        
        writer.write(response)
        writer.flush()
    }
}
