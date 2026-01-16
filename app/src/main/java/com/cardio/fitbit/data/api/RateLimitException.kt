package com.cardio.fitbit.data.api

class RateLimitException(message: String, val retryAfterSeconds: Int = 3600) : Exception(message)
