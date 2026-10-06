package com.project.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionResponse(
        UUID id,
        UUID repositoryId,
        String title,
        Instant createdAt) {
}
