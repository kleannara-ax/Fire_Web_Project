package com.company.module.sales.repository;

import com.company.module.sales.entity.OrderStatus;
import com.company.module.sales.entity.SalesOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 영업 주문 Repository
 * <p>
 * - DB 테이블 Prefix: MOD_SALES_
 * - @Transactional은 Service 계층에서만 사용 (Repository에서 사용 금지)
 * - NOTE: Pageable 쿼리에 countQuery 분리 필수 (HHH90003004 경고 방지)
 */
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    Optional<SalesOrder> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    /**
     * 주문 목록 검색 (페이징)
     * - 거래처코드, 거래처명, 주문번호 keyword 검색
     * - 상태, 주문일 범위 필터
     * - countQuery 분리: Pageable 사용 시 필수
     */
    @Query(value =
           "SELECT o FROM SalesOrder o " +
           "WHERE (:status IS NULL OR o.status = :status) " +
           "AND (:fromDate IS NULL OR o.orderDate >= :fromDate) " +
           "AND (:toDate IS NULL OR o.orderDate <= :toDate) " +
           "AND (:keyword IS NULL OR " +
           "     LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(o.customerCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(o.customerName) LIKE LOWER(CONCAT('%', :keyword, '%')))",
           countQuery =
           "SELECT COUNT(o) FROM SalesOrder o " +
           "WHERE (:status IS NULL OR o.status = :status) " +
           "AND (:fromDate IS NULL OR o.orderDate >= :fromDate) " +
           "AND (:toDate IS NULL OR o.orderDate <= :toDate) " +
           "AND (:keyword IS NULL OR " +
           "     LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(o.customerCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(o.customerName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<SalesOrder> searchOrders(
            @Param("status") OrderStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("keyword") String keyword,
            Pageable pageable);

    /** 다음 주문번호 계산용 */
    @Query("SELECT o.orderNumber FROM SalesOrder o WHERE o.orderNumber LIKE 'ORD-%'")
    List<String> findAllOrderNumbers();
}
