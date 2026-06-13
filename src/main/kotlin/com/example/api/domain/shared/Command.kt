package com.example.api.domain.shared

import java.time.LocalDateTime

@Suppress("unused") class Command<T>(val t: T, val timestamp: LocalDateTime)
