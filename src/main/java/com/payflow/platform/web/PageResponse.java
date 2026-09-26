package com.payflow.platform.web;

import com.payflow.shared.application.PageResult;

import java.util.List;
import java.util.function.Function;

/** Offset-pagination envelope used by list endpoints. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <S, T> PageResponse<T> from(PageResult<S> result, Function<S, T> mapper) {
        return new PageResponse<>(result.items().stream().map(mapper).toList(), result.page(), result.size(),
                result.totalItems(), result.totalPages());
    }
}
