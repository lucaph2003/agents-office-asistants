package com.lucapodesta.agentoffice.infrastructure.web;

import com.lucapodesta.agentoffice.application.dto.PageResult;
import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    static <T> PageResponse<T> from(PageResult<T> result) {
        return new PageResponse<>(result.content(), result.page(), result.size(), result.totalElements(),
                result.totalPages());
    }
}
