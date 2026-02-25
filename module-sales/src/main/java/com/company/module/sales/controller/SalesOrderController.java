package com.company.module.sales.controller;

import com.company.core.common.ApiResponse;
import com.company.module.sales.dto.SalesOrderResponse;
import com.company.module.sales.dto.SalesOrderSaveRequest;
import com.company.module.sales.entity.OrderStatus;
import com.company.module.sales.service.SalesOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;

/**
 * 영업 주문 관리 API Controller
 * <p>
 * - 패키지: com.company.module.sales.controller
 * - URL Prefix: /sales-api/**
 * - DB 테이블 Prefix: MOD_SALES_
 * - Core 보안/예외 처리 구조를 그대로 사용 (Core 소스 수정 없음)
 * - @Transactional은 Service 계층에서만 사용 (Controller에서 사용 금지)
 */
@RestController
@RequestMapping("/sales-api/orders")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    /**
     * GET /sales-api/orders
     * 주문 목록 조회 (페이지네이션 + 검색 + 필터)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<SalesOrderResponse>>> getList(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<SalesOrderResponse> result = salesOrderService.getOrders(
                status, fromDate, toDate, q, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * GET /sales-api/orders/{id}
     * 주문 상세 조회 (주문 라인 포함)
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(salesOrderService.getOrderDetail(id)));
    }

    /**
     * POST /sales-api/orders
     * 주문 등록 / 수정
     * - 신규: orderId 없음
     * - 수정: orderId 포함 (DRAFT 상태만 가능)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SalesOrderResponse>> save(
            @Valid @RequestBody SalesOrderSaveRequest request,
            Principal principal) {
        // TODO: Principal에서 userId 조회 (UserService 연동)
        SalesOrderResponse response = salesOrderService.saveOrder(
                request, null, principal.getName());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /sales-api/orders/{id}/confirm
     * 주문 확정 (DRAFT → CONFIRMED)
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(salesOrderService.confirmOrder(id)));
    }

    /**
     * POST /sales-api/orders/{id}/cancel
     * 주문 취소
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(salesOrderService.cancelOrder(id)));
    }

    /**
     * DELETE /sales-api/orders/{id}
     * 주문 삭제 (DRAFT 상태만 / Admin 전용)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        salesOrderService.deleteOrder(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
