package com.project.backend.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CreateChatSessionRequest(
        @NotNull UUID repositoryId,
        String title) {
}