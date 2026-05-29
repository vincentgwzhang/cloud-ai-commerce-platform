package com.vincent.aiservice.dto;

import java.util.List;

public record AskResponse(
        String answer,
        List<SourceRef> sources,
        String model
) {
}
