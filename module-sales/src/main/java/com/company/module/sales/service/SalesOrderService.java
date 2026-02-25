package com.company.module.sales.service;

import com.company.core.exception.BusinessException;
import com.company.core.exception.ResourceNotFoundException;
import com.company.module.sales.dto.SalesOrderResponse;
import com.company.module.sales.dto.SalesOrderSaveRequest;
import com.company.module.sales.entity.OrderStatus;
import com.company.module.sales.entity.SalesOrder;
import com.company.module.sales.entity.SalesOrderLine;
import com.company.module.sales.repository.SalesOrderLineRepository;
import com.company.module.sales.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 영업 주문 관리 서비스
 * <p>
 * - 패키지: com.company.module.sales
 * - URL Prefix: /sales-api/**
 * - DB 테이블 Prefix: MOD_SALES_
 * - @Transactional은 이 Service 계층에서만 사용
 * - Core 보안/예외 처리 구조와 충돌하지 않도록 설계 (Core 소스 수정 없음)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;

    /**
     * 주문 목록 조회 (페이지네이션 + 검색)
     */
    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> getOrders(OrderStatus status, LocalDate fromDate,
                                               LocalDate toDate, String keyword,
                                               int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("orderDate").descending()
                .and(Sort.by("orderId").descending()));

        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        return salesOrderRepository
                .searchOrders(status, fromDate, toDate, kw, pageable)
                .map(SalesOrderResponse::new);
    }

    /**
     * 주문 상세 조회 (주문 라인 포함)
     */
    @Transactional(readOnly = true)
    public SalesOrderResponse getOrderDetail(Long orderId) {
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("영업 주문", orderId));

        SalesOrderResponse dto = new SalesOrderResponse(order);
        List<SalesOrderLine> lines = salesOrderLineRepository
                .findByOrder_OrderIdOrderByLineNumberAsc(orderId);
        dto.setOrderLines(lines);
        return dto;
    }

    /**
     * 주문 등록 / 수정
     * <p>
     * - 신규: orderId가 null이면 새 주문 생성 (주문번호 자동 채번)
     * - 수정: orderId가 있으면 DRAFT 상태인 경우만 수정 가능
     */
    @Transactional
    public SalesOrderResponse saveOrder(SalesOrderSaveRequest req,
                                         Long userId, String createdByName) {
        SalesOrder order;

        if (req.getOrderId() != null && req.getOrderId() > 0) {
            // 수정
            order = salesOrderRepository.findById(req.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("영업 주문", req.getOrderId()));
            order.updateInfo(req.getCustomerCode(), req.getCustomerName(),
                    req.getOrderDate(), req.getDeliveryDate(), req.getNote());
        } else {
            // 신규 등록
            String orderNumber = generateNextOrderNumber();
            order = SalesOrder.builder()
                    .orderNumber(orderNumber)
                    .customerCode(req.getCustomerCode())
                    .customerName(req.getCustomerName())
                    .orderDate(req.getOrderDate())
                    .deliveryDate(req.getDeliveryDate())
                    .note(req.getNote())
                    .createdByUserId(userId)
                    .createdByName(createdByName)
                    .build();
            salesOrderRepository.save(order);
        }

        // 주문 라인 처리
        if (req.getOrderLines() != null && !req.getOrderLines().isEmpty()) {
            // 기존 라인 전체 삭제 후 재등록 (단순 구현 - 운영 시 diff 처리 필요)
            salesOrderLineRepository.deleteAll(
                    salesOrderLineRepository.findByOrder_OrderIdOrderByLineNumberAsc(order.getOrderId())
            );

            AtomicInteger lineNum = new AtomicInteger(1);
            for (SalesOrderSaveRequest.OrderLineRequest lineReq : req.getOrderLines()) {
                SalesOrderLine line = SalesOrderLine.builder()
                        .order(order)
                        .lineNumber(lineNum.getAndIncrement())
                        .itemCode(lineReq.getItemCode())
                        .itemName(lineReq.getItemName())
                        .quantity(lineReq.getQuantity())
                        .unitPrice(lineReq.getUnitPrice())
                        .note(lineReq.getNote())
                        .build();
                salesOrderLineRepository.save(line);
                order.getOrderLines().add(line);
            }

            order.recalculateTotalAmount();
        }

        log.info("SalesOrder saved: id={}, orderNumber={}, customer={}",
                order.getOrderId(), order.getOrderNumber(), order.getCustomerName());

        SalesOrderResponse dto = new SalesOrderResponse(order);
        dto.setOrderLines(order.getOrderLines());
        return dto;
    }

    /**
     * 주문 확정 (DRAFT → CONFIRMED)
     */
    @Transactional
    public SalesOrderResponse confirmOrder(Long orderId) {
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("영업 주문", orderId));

        if (order.getOrderLines().isEmpty()) {
            throw new BusinessException("주문 라인이 없는 주문은 확정할 수 없습니다.");
        }

        order.confirm();
        log.info("SalesOrder confirmed: id={}, orderNumber={}", orderId, order.getOrderNumber());
        return new SalesOrderResponse(order);
    }

    /**
     * 주문 취소
     */
    @Transactional
    public SalesOrderResponse cancelOrder(Long orderId) {
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("영업 주문", orderId));

        order.cancel();
        log.info("SalesOrder cancelled: id={}, orderNumber={}", orderId, order.getOrderNumber());
        return new SalesOrderResponse(order);
    }

    /**
     * 주문 삭제 (DRAFT 상태만 가능)
     */
    @Transactional
    public void deleteOrder(Long orderId) {
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("영업 주문", orderId));

        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new BusinessException("임시저장 상태의 주문만 삭제할 수 있습니다.");
        }

        salesOrderRepository.delete(order);
        log.info("SalesOrder deleted: id={}, orderNumber={}", orderId, order.getOrderNumber());
    }

    /**
     * 다음 주문번호 생성 (ORD-000001 형식)
     */
    private String generateNextOrderNumber() {
        List<String> allNumbers = salesOrderRepository.findAllOrderNumbers();
        int maxNum = 0;
        for (String num : allNumbers) {
            try {
                int n = Integer.parseInt(num.substring(4));
                if (n > maxNum) maxNum = n;
            } catch (NumberFormatException ignored) { }
        }
        return String.format("ORD-%06d", maxNum + 1);
    }
}
