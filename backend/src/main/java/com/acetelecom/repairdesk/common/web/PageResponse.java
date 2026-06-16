package com.acetelecom.repairdesk.common.web;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * CORE-004: Standard paged list envelope returned by all list endpoints (API.md §4).
 *
 * <pre>
 * {
 *   "content": [...],
 *   "page": 0,
 *   "size": 20,
 *   "totalElements": 123,
 *   "totalPages": 7,
 *   "sort": "createdAt,desc"
 * }
 * </pre>
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort) {

    public static <T> PageResponse<T> from(Page<T> page) {
        String sort = page.getSort().isSorted()
                ? page.getSort().toString()
                : "unsorted";
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                sort);
    }
}
