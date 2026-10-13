package com.campuscompanion.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * One page of a list, in the shape every paged endpoint returns (docs/09-api-contract.md section
 * 5): the items, then where this page sits in the whole list.
 */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {

  public static <T> PageResponse<T> of(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
