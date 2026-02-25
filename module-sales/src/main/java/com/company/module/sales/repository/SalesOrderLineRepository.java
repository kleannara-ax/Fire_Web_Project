package com.company.module.sales.repository;

import com.company.module.sales.entity.SalesOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 영업 주문 상세 라인 Repository
 */
public interface SalesOrderLineRepository extends JpaRepository<SalesOrderLine, Long> {

    List<SalesOrderLine> findByOrder_OrderIdOrderByLineNumberAsc(Long orderId);
}
