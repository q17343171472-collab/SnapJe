package com.rapii.snapje.util

/**
 * Validation utilities for user input.
 * Prevents security issues like path traversal and invalid filenames.
 */
object ValidationUtils {

    // Characters that are invalid in filenames on most filesystems
    private val INVALID_FILENAME_CHARS = setOf('<', '>', ':', '"', '/', '\\', '|', '?', '*')

    // Maximum filename length (varies by filesystem, but 255 is safe cross-platform)
    private const val MAX_FILENAME_LENGTH = 255

    // Minimum filename length
    private const val MIN_FILENAME_LENGTH = 1

    /**
     * Validates a filename for security and correctness.
     *
     * @param filename The filename to validate
     * @return [ValidationResult] containing success status and error message if invalid
     */
    fun validateFilename(filename: String): ValidationResult {
        // Check for empty/null
        if (filename.isBlank()) {
            return ValidationResult(false, "Name cannot be empty")
        }

        // Check minimum length
        if (filename.length < MIN_FILENAME_LENGTH) {
            return ValidationResult(false, "Name must be at least $MIN_FILENAME_LENGTH character(s)")
        }

        // Check maximum length
        if (filename.length > MAX_FILENAME_LENGTH) {
            return ValidationResult(false, "Name is too long (max $MAX_FILENAME_LENGTH characters)")
        }

        // Check for invalid characters
        val invalidChar = INVALID_FILENAME_CHARS.find { it in filename }
        if (invalidChar != null) {
            return ValidationResult(
                false,
                "Name contains invalid character: '$invalidChar'. " +
                "Invalid characters: ${INVALID_FILENAME_CHARS.joinToString(" ")}"
            )
        }

        // Check for path traversal attempts
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ValidationResult(false, "Path traversal is not allowed")
        }

        // Check for reserved names (Windows)
        val reservedNames = setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        )
        val nameWithoutExtension = filename.substringBeforeLast('.')
        if (nameWithoutExtension.uppercase() in reservedNames) {
            return ValidationResult(false, "Reserved filename not allowed")
        }

        // Check for leading/trailing spaces and dots (can cause issues on some systems)
        val trimmed = filename.trim()
        if (trimmed != filename) {
            return ValidationResult(false, "Name cannot start or end with spaces")
        }

        if (filename.startsWith('.') || filename.endsWith('.')) {
            return ValidationResult(false, "Name cannot start or end with a period")
        }

        return ValidationResult(true, null)
    }

    /**
     * Sanitizes a filename by removing/replacing invalid characters.
     * Use this to generate a default safe name.
     *
     * @param filename The original filename
     * @return Sanitized filename safe for use
     */
    fun sanitizeFilename(filename: String): String {
        return filename
            .filter { it !in INVALID_FILENAME_CHARS }
            .replace(Regex("[/\\\\]"), "-")  // Replace path separators
            .replace(Regex("\\.{2,}"), ".")  // Replace multiple dots with single
            .trim(' ', '.')
            .take(MAX_FILENAME_LENGTH)
    }

    /**
     * Validates an email address format.
     */
    fun validateEmail(email: String): ValidationResult {
        if (email.isBlank()) {
            return ValidationResult(false, "Email cannot be empty")
        }

        // Simple email regex pattern
        val emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        if (!email.matches(emailPattern.toRegex())) {
            return ValidationResult(false, "Invalid email format")
        }

        return ValidationResult(true, null)
    }

    /**
     * Result of a validation operation.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String?
    )
}
