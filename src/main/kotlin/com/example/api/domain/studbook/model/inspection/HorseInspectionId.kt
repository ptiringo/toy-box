package com.example.api.domain.studbook.model.inspection

import java.util.UUID
import org.jmolecules.ddd.annotation.ValueObject

/** 審査ID */
@ValueObject @JvmInline value class HorseInspectionId(val value: UUID)
