package com.company.module.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 영업 주문 등록/수정 요청 DTO
 */
@Getter
@Setter
public class SalesOrderSaveRequest {

    private Long orderId;  // null이면 신규 등록

    @NotBlank(message = "거래처 코드를 입력하세요.")
    @Size(max = 50, message = "거래처 코드는 50자 이하여야 합니다.")
    private String customerCode;

    @NotBlank(message = "거래처명을 입력하세요.")
    @Size(max = 200, message = "거래처명은 200자 이하여야 합니다.")
    private String customerName;

    @NotNull(message = "주문일을 입력하세요.")
    private LocalDate orderDate;

    private LocalDate deliveryDate;

    @Size(max = 1000, message = "비고는 1000자 이하여야 합니다.")
    private String note;

    @Valid
    private List<OrderLineRequest> orderLines = new ArrayList<>();

    @Getter
    @Setter
    public static class OrderLineRequest {

        private Long lineId;  // null이면 신규 라인

        @NotBlank(message = "품목 코드를 입력하세요.")
        @Size(max = 50, message = "품목 코드는 50자 이하여야 합니다.")
        private String itemCode;

        @NotBlank(message = "품목명을 입력하세요.")
        @Size(max = 200, message = "품목명은 200자 이하여야 합니다.")
        private String itemName;

        @NotNull(message = "수량을 입력하세요.")
        @DecimalMin(value = "0.01", message = "수량은 0보다 커야 합니다.")
        private BigDecimal quantity;

        @NotNull(message = "단가를 입력하세요.")
        @DecimalMin(value = "0", message = "단가는 0 이상이어야 합니다.")
        private BigDecimal unitPrice;

        @Size(max = 500)
        private String note;
    }
}
