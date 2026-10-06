package com.project.backend.services.ai;

import java.util.List;

import com.project.backend.dto.CitationDto;

public record RetrievedContext(
        List<CitationDto> citations,
        String contextText) {
}