package com.company.module.sales.controller;

import com.company.core.common.ApiResponse;
import com.company.module.sales.dto.*;
import com.company.module.sales.service.SalesOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * 영업 주문 관리 API Controller
 * <p>
 * - 패키지: com.company.module.sales
 * - URL Prefix: /sales-api/**
 * - DB Prefix: MOD_SALES_
 * - Core Security/Exception 구조 그대로 활용 (core 수정 없음)
 * - Controller-Service-Repository 계층 구조 유지
 */
@RestController
@RequestMapping("/sales-api/orders")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    /**
     * GET /sales-api/orders
     * 주문 목록 조회 (검색/필터/페이지네이션)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<SalesOrderResponse>>> getOrders(
            SalesOrderSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(salesOrderService.getOrders(request)));
    }

    /**
     * GET /sales-api/orders/{orderId}
     * 주문 상세 조회 (주문 라인 포함)
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> getOrderDetail(
            @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success(salesOrderService.getOrderDetail(orderId)));
    }

    /**
     * POST /sales-api/orders
     * 주문 등록/수정
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SalesOrderResponse>> saveOrder(
            @Valid @RequestBody SalesOrderSaveRequest request,
            Principal principal) {
        // TODO: Principal에서 userId 조회 필요 시 UserService 연동
        SalesOrderResponse response = salesOrderService.saveOrder(request, null, principal.getName());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /sales-api/orders/{orderId}/confirm
     * 주문 확정
     */
    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> confirmOrder(@PathVariable Long orderId) {
        salesOrderService.confirmOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * POST /sales-api/orders/{orderId}/cancel
     * 주문 취소
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(@PathVariable Long orderId) {
        salesOrderService.cancelOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * DELETE /sales-api/orders/{orderId}
     * 주문 삭제 (임시저장 상태만, Admin 전용)
     */
    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(@PathVariable Long orderId) {
        salesOrderService.deleteOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
