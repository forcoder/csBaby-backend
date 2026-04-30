package com.csbaby.kefu.data.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Security tests for data validation and sanitization.
 */
@RunWith(AndroidJUnit4::class)
class SecurityTest {

    @Test
    fun `test SQL injection prevention`() = runTest {
        // Given
        val sqlValidator = SqlInjectionValidator()

        val maliciousInputs = listOf(
            "'; DROP TABLE users; --",
            "SELECT * FROM users WHERE 1=1",
            "UNION SELECT password FROM users",
            "<script>alert('xss')</script>",
            "../../../etc/passwd",
            "%27%20OR%20%271%27=%271"
        )

        // When & Then - All should be detected as dangerous
        maliciousInputs.forEach { input ->
            assertFalse("SQL injection attempt should be blocked: $input",
                sqlValidator.isSafe(input))
            assertTrue("Should contain SQL injection indicators",
                sqlValidator.containsSqlInjection(input))
        }
    }

    @Test
    fun `test XSS attack prevention`() = runTest {
        // Given
        val xssValidator = XssValidator()

        val xssPayloads = listOf(
            "<script>alert('xss')</script>",
            "<img src=x onerror=alert('xss')>",
            "javascript:alert('xss')",
            "<svg onload=alert('xss')>",
            "<iframe src='javascript:alert(\"xss\")'></iframe>",
            "onmouseover=alert('xss')"
        )

        // When & Then - All should be detected as dangerous
        xssPayloads.forEach { payload ->
            assertFalse("XSS payload should be blocked: $payload",
                xssValidator.isValidInput(payload))
            assertTrue("Should detect XSS attempts",
                xssValidator.detectXss(payload))
        }
    }

    @Test
    fun `test API key security validation`() = runTest {
        // Given
        val keyValidator = ApiKeyValidator()

        val validKeys = listOf(
            "sk-1234567890abcdefghijklmnopqrstuvwxyz", // OpenAI format
            "sk-proj-test-key-123", // Project keys
            "claude-key-secret" // Claude format
        )

        val invalidKeys = listOf(
            "", // Empty
            "sk-", // Too short
            "invalid-format", // Wrong format
            "password123", // Not an API key
            null // Null value
        )

        // When & Then
        validKeys.forEach { key ->
            assertTrue("Valid API key should pass: $key",
                keyValidator.validateApiKey(key))
        }

        invalidKeys.forEach { key ->
            if (key != null) {
                assertFalse("Invalid API key should be rejected: $key",
                    keyValidator.validateApiKey(key))
            }
        }
    }

    @Test
    fun `test message content sanitization`() = runTest {
        // Given
        val sanitizer = MessageSanitizer()

        val maliciousMessages = listOf(
            "Hello<script>alert('xss')</script>",
            "DROP TABLE messages; --",
            "<img src='x' onerror='stealData()'>",
            "javascript:window.location='http://evil.com'",
            "SELECT * FROM user_data WHERE id=1",
            "../../../etc/passwd"
        )

        val normalMessages = listOf(
            "Hello, how are you?",
            "I need help with my order",
            "Thank you for your assistance",
            "Can you check my account?"
        )

        // When & Then
        maliciousMessages.forEach { message ->
            val sanitized = sanitizer.sanitize(message)
            assertFalse("Sanitized message should not contain script tags",
                sanitized.contains("<script>"))
            assertFalse("Sanitized message should not contain SQL keywords",
                sanitized.uppercase().contains("DROP"))
        }

        normalMessages.forEach { message ->
            val sanitized = sanitizer.sanitize(message)
            assertEquals("Normal message should remain unchanged",
                message, sanitized)
        }
    }

    @Test
    fun `test URL validation and sanitization`() = runTest {
        // Given
        val urlValidator = UrlValidator()

        val maliciousUrls = listOf(
            "javascript:alert('xss')",
            "data:text/html,<script>alert('xss')</script>",
            "ftp://malicious-site.com/exploit.exe",
            "file:///etc/passwd",
            "http://evil.com/shell.php?cmd=whoami",
            "https://phishing-site.com/login"
        )

        val safeUrls = listOf(
            "https://api.openai.com/v1/chat/completions",
            "https://api.anthropic.com/v1/messages",
            "https://open.bigmodel.cn/api/paas/v4/chat/completions"
        )

        // When & Then
        maliciousUrls.forEach { url ->
            assertFalse("Malicious URL should be blocked: $url",
                urlValidator.isSafeUrl(url))
            assertTrue("Should detect malicious schemes",
                urlValidator.hasDangerousScheme(url))
        }

        safeUrls.forEach { url ->
            assertTrue("Safe URL should be allowed: $url",
                urlValidator.isSafeUrl(url))
            assertFalse("Safe URLs should not have dangerous schemes",
                urlValidator.hasDangerousScheme(url))
        }
    }

    @Test
    fun `test rate limiting security`() = runTest {
        // Given
        val rateLimiter = SecureRateLimiter(
            maxRequestsPerMinute = 60,
            maxFailedAttempts = 5,
            lockoutDurationMs = 300000 // 5 minutes
        )

        val testUserId = "user_123"

        // When - Simulate rapid requests
        repeat(65) { index ->
            val result = rateLimiter.tryRequest(testUserId)
            if (index < 60) {
                assertTrue("First 60 requests should be allowed",
                    result.isAllowed)
            } else {
                assertFalse("Requests beyond limit should be blocked",
                    result.isAllowed)
                assertTrue("Should provide reason",
                    result.reason.isNotEmpty())
            }
        }

        // Test lockout after failed attempts
        repeat(6) { index ->
            rateLimiter.recordFailedAttempt(testUserId)
        }

        val lockedResult = rateLimiter.tryRequest(testUserId)
        assertFalse("User should be locked out after too many failures",
            lockedResult.isAllowed)
        assertEquals("LOCKED_OUT", lockedResult.reason)
    }

    @Test
    fun `test data encryption and decryption`() = runTest {
        // Given
        val encryptor = DataEncryptor("test-encryption-key-123")

        val sensitiveData = listOf(
            "API_KEY: sk-1234567890abcdefghijklmnopqrstuvwxyz",
            "Password: MySecretPassword123!",
            "Email: user@example.com",
            "Phone: +1-555-123-4567"
        )

        // When & Then
        sensitiveData.forEach { data ->
            val encrypted = encryptor.encrypt(data)
            assertNotNull("Encrypted data should not be null",
                encrypted)
            assertNotEquals("Encrypted data should differ from original",
                data, encrypted)

            val decrypted = encryptor.decrypt(encrypted)
            assertEquals("Decrypted data should match original",
                data, decrypted)
        }
    }

    @Test
    fun `test input length validation`() = runTest {
        // Given
        val validator = InputLengthValidator(
            maxMessageLength = 50000,
            minMessageLength = 1,
            maxNameLength = 100,
            maxUrlLength = 2048
        )

        // Test message length limits
        assertTrue("Short message should be valid",
            validator.isValidMessage("Hello"))
        assertTrue("Long message within limit should be valid",
            validator.isValidMessage("a".repeat(49999)))
        assertFalse("Empty message should be invalid",
            validator.isValidMessage(""))
        assertFalse("Overly long message should be invalid",
            validator.isValidMessage("a".repeat(50001)))

        // Test name length limits
        assertTrue("Short name should be valid",
            validator.isValidName("John"))
        assertFalse("Overly long name should be invalid",
            validator.isValidName("a".repeat(101)))
    }

    @Test
    fun `test error message security`() = runTest {
        // Given
        val errorHandler = SecureErrorHandler()

        // When & Then - Error messages should not leak sensitive information
        val exception = Exception("Database connection failed")
        val errorMessage = errorHandler.getSafeErrorMessage(exception)

        assertFalse("Error message should not contain technical details",
            errorMessage.contains("Database"))
        assertFalse("Error message should not expose file paths",
            errorMessage.contains("/"))
        assertTrue("Error message should be user-friendly",
            errorMessage.length < 100)
        assertTrue("Error message should not be empty",
            errorMessage.isNotEmpty())
    }

    @Test
    fun `test session token security`() = runTest {
        // Given
        val tokenGenerator = SecureTokenGenerator()
        val tokenValidator = SessionTokenValidator()

        // When
        val tokens = (1..10).map { tokenGenerator.generateSessionToken() }

        // Then - All tokens should meet security requirements
        tokens.forEach { token ->
            assertTrue("Token should be sufficiently random",
                token.length >= 32)
            assertFalse("Token should not contain predictable patterns",
                token.matches(Regex(".*(.)\\1{2,}.*"))) // No repeating chars
            assertTrue("Token should contain mixed character types",
                token.any { it.isLetter() } &&
                token.any { it.isDigit() })
        }

        // Test token validation
        val validToken = tokens.first()
        assertTrue("Generated token should be valid",
            tokenValidator.validateToken(validToken))

        val invalidTokens = listOf("", "short", "not-a-token")
        invalidTokens.forEach { invalidToken ->
            assertFalse("Invalid token should be rejected",
                tokenValidator.validateToken(invalidToken))
        }
    }

    // Helper classes for testing
    private class SqlInjectionValidator {
        fun isSafe(input: String): Boolean {
            val sqlPatterns = listOf(
                "(?i)(union|select|insert|delete|update|drop|create|alter)",
                "(--|#|/\*).*", // Comments
                "(;|\\|\\||&&)", // Statement separators
                "'.*'--", // String manipulation
                "(?i)(exec|execute|declare|cast|convert)"
            )
            return !sqlPatterns.any { input.matches(Regex(it)) }
        }

        fun containsSqlInjection(input: String): Boolean {
            return !isSafe(input)
        }
    }

    private class XssValidator {
        fun isValidInput(input: String): Boolean {
            val xssPatterns = listOf(
                "<script[^>]*>.*?</script>",
                "javascript:",
                "on\\w+\\s*=",
                "<iframe[^>]*>",
                "<object[^>]*>",
                "<embed[^>]*>"
            )
            return !xssPatterns.any { input.matches(Regex(it, RegexOption.CASE_INSENSITIVE)) }
        }

        fun detectXss(input: String): Boolean {
            return !isValidInput(input)
        }
    }

    private class ApiKeyValidator {
        fun validateApiKey(key: String): Boolean {
            if (key.isEmpty()) return false
            if (key.length < 10) return false

            // Check for common API key formats
            val openAiPattern = "^sk-[a-zA-Z0-9]{32,}$".toRegex()
            val claudePattern = "^[a-zA-Z0-9-_]+$".toRegex()
            val genericPattern = "^[a-zA-Z0-9_-]{10,}$".toRegex()

            return openAiPattern.matches(key) ||
                   claudePattern.matches(key) ||
                   genericPattern.matches(key)
        }
    }

    private class MessageSanitizer {
        fun sanitize(message: String): String {
            var sanitized = message

            // Remove script tags
            sanitized = sanitized.replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")

            // Remove event handlers
            sanitized = sanitized.replace(Regex("on\\w+\\s*=\\s*[\"'].*?[\"']", RegexOption.CASE_INSENSITIVE), "")

            // Remove javascript: protocol
            sanitized = sanitized.replace(Regex("javascript:", RegexOption.CASE_INSENSITIVE), "")

            // Remove potentially dangerous characters
            sanitized = sanitized.replace(Regex("[<>]"), "")

            return sanitized.trim()
        }
    }

    private class UrlValidator {
        fun isSafeUrl(url: String): Boolean {
            if (url.isEmpty()) return false

            // Check for dangerous schemes
            val dangerousSchemes = listOf("javascript:", "data:", "file:", "ftp:")
            val lowerUrl = url.lowercase()

            return !dangerousSchemes.any { lowerUrl.startsWith(it) }
        }

        fun hasDangerousScheme(url: String): Boolean {
            return !isSafeUrl(url)
        }
    }

    private class SecureRateLimiter(
        private val maxRequestsPerMinute: Int,
        private val maxFailedAttempts: Int,
        private val lockoutDurationMs: Long
    ) {
        private val requestTimes = mutableMapOf<String, MutableList<Long>>()
        private val failedAttempts = mutableMapOf<String, Int>()
        private val lockouts = mutableMapOf<String, Long>()

        data class RateLimitResult(
            val isAllowed: Boolean,
            val reason: String
        )

        fun tryRequest(userId: String): RateLimitResult {
            val now = System.currentTimeMillis()

            // Check if user is locked out
            val lockoutTime = lockouts[userId] ?: 0L
            if (now < lockoutTime) {
                return RateLimitResult(false, "LOCKED_OUT")
            }

            // Clean old requests (older than 1 minute)
            val userRequests = requestTimes.getOrPut(userId) { mutableListOf() }
            userRequests.removeAll { it < now - 60000 }

            if (userRequests.size >= maxRequestsPerMinute) {
                return RateLimitResult(false, "RATE_LIMITED")
            }

            userRequests.add(now)
            return RateLimitResult(true, "")
        }

        fun recordFailedAttempt(userId: String) {
            val attempts = failedAttempts.getOrPut(userId) { 0 }
            failedAttempts[userId] = attempts + 1

            if (attempts + 1 >= maxFailedAttempts) {
                lockouts[userId] = System.currentTimeMillis() + lockoutDurationMs
            }
        }
    }

    private class DataEncryptor(private val key: String) {
        fun encrypt(data: String): String {
            // Simple base64 encoding for demo - in production use proper encryption
            return android.util.Base64.encodeToString(data.toByteArray(), android.util.Base64.DEFAULT)
        }

        fun decrypt(encryptedData: String): String {
            return try {
                val decodedBytes = android.util.Base64.decode(encryptedData, android.util.Base64.DEFAULT)
                String(decodedBytes)
            } catch (e: Exception) {
                ""
            }
        }
    }

    private class InputLengthValidator(
        private val maxMessageLength: Int,
        private val minMessageLength: Int,
        private val maxNameLength: Int,
        private val maxUrlLength: Int
    ) {
        fun isValidMessage(message: String): Boolean {
            return message.length in minMessageLength..maxMessageLength
        }

        fun isValidName(name: String): Boolean {
            return name.length <= maxNameLength && name.isNotBlank()
        }

        fun isValidUrl(url: String): Boolean {
            return url.length <= maxUrlLength
        }
    }

    private class SecureErrorHandler {
        fun getSafeErrorMessage(exception: Throwable): String {
            val message = exception.message ?: "An error occurred"

            // Filter out sensitive technical information
            val safeMessages = listOf(
                "网络连接失败，请检查网络设置",
                "服务器暂时不可用，请稍后重试",
                "输入格式不正确，请检查后重试",
                "操作失败，请稍后重试"
            )

            return when {
                message.contains("network", ignoreCase = true) -> safeMessages[0]
                message.contains("database", ignoreCase = true) -> safeMessages[1]
                message.contains("format", ignoreCase = true) -> safeMessages[2]
                else -> safeMessages[3]
            }
        }
    }

    private class SecureTokenGenerator {
        fun generateSessionToken(): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            return (1..32)
                .map { chars.random() }
                .joinToString("")
        }
    }

    private class SessionTokenValidator {
        fun validateToken(token: String): Boolean {
            return token.length >= 32 &&
                   token.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        }
    }
}