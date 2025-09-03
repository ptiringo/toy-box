package com.example.api.domain

import java.time.LocalDateTime

@Suppress("unused")
class Command<T>(val t: T, val timestamp: LocalDateTime)
