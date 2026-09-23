package com.example.whitelist.util;

import com.example.whitelist.common.BusinessException;
import java.util.Locale;

public record TimeSort(Field field, boolean ascending, boolean specified) {
    public enum Field {
        CREATED_AT,
        UPDATED_AT
    }

    public static TimeSort parse(String sortField, String sortOrder) {
        boolean hasField = sortField != null && !sortField.isBlank();
        boolean hasOrder = sortOrder != null && !sortOrder.isBlank();
        if (!hasField && !hasOrder) {
            return new TimeSort(Field.UPDATED_AT, false, false);
        }
        if (!hasField || !hasOrder) {
            throw new BusinessException(400, "排序字段和排序方向必须同时提供");
        }

        Field field = switch (sortField) {
            case "createdAt" -> Field.CREATED_AT;
            case "updatedAt" -> Field.UPDATED_AT;
            default -> throw new BusinessException(400, "不支持的排序字段");
        };
        boolean ascending = switch (sortOrder.toLowerCase(Locale.ROOT)) {
            case "asc", "ascending" -> true;
            case "desc", "descending" -> false;
            default -> throw new BusinessException(400, "不支持的排序方向");
        };
        return new TimeSort(field, ascending, true);
    }
}
