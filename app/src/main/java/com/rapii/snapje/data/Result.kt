package com.rapii.snapje.data

/**
 * Generic result wrapper for data operations.
 * Provides a type-safe way to handle loading, success, and error states.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String = exception.message ?: "Unknown error") : Result<Nothing>()
    object Loading : Result<Nothing>()

    /**
     * Returns true if this is a Success result
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * Returns true if this is an Error result
     */
    val isError: Boolean get() = this is Error

    /**
     * Returns true if this is a Loading result
     */
    val isLoading: Boolean get() = this is Loading

    /**
     * Get the data if Success, otherwise null
     */
    fun getOrNull(): T? = (this as? Success)?.data

    /**
     * Get the error if Error, otherwise null
     */
    fun exceptionOrNull(): Throwable? = (this as? Error)?.exception

    /**
     * Transform the data if Success
     */
    fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> Loading
    }

    /**
     * Execute action on Success
     */
    fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * Execute action on Error
     */
    fun onError(action: (Throwable, String) -> Unit): Result<T> {
        if (this is Error) action(exception, message)
        return this
    }

    fun getOrDefault(defaultValue: @UnsafeVariance T): T = when (this) {
        is Success -> data
        else -> defaultValue
    }

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun <T> failure(exception: Throwable): Result<T> = Error(exception)

        /**
         * Wrap a suspending operation in a Result
         */
        suspend fun <T> runCatching(block: suspend () -> T): Result<T> = try {
            Success(block())
        } catch (e: Exception) {
            Error(e)
        }
    }
}

/**
 * Extension to convert a Flow of Results to a UI state
 */
fun <T> Result<T>.toUiState(): UiState<T> = when (this) {
    is Result.Success -> UiState.Success(data)
    is Result.Error -> UiState.Error(message)
    is Result.Loading -> UiState.Loading
}

/**
 * UI state representation for screens
 */
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
    object Empty : UiState<Nothing>()
}
