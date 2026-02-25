package com.company.module.sales.dto;

import com.company.module.sales.entity.OrderStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 영업 주문 목록 검색 요청 DTO
 */
@Getter
@Setter
public class SalesOrderSearchRequest {

    private String keyword;

    private OrderStatus status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    private int page = 0;

    private int size = 20;
}
