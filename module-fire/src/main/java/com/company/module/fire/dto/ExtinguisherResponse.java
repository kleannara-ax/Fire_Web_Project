package com.company.module.fire.dto;

import com.company.module.fire.entity.Extinguisher;
import com.company.module.fire.entity.ExtinguisherInspection;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 소화기 목록/상세 응답 DTO
 * <p>
 * 기존 ASP.NET: VwExtinguisherList + DetailsModalVm 통합
 */
@Getter
public class ExtinguisherResponse {

    private final Long extinguisherId;
    private final String serialNumber;
    private final String buildingName;
    private final String floorName;
    private final String extinguisherType;
    private final LocalDate installDate;
    private final int replacementCycleYears;
    private final LocalDate replacementDueDate;
    private final int quantity;
    private final String note;
    private final String imagePath;
    private final BigDecimal x;
    private final BigDecimal y;
    private final LocalDateTime createdAt;

    // 최종 점검 정보
    private LocalDate lastInspectionDate;
    private String lastInspectorName;
    private Boolean lastIsFaulty;
    private String lastFaultReason;

    // 점검 이력 (상세 조회 시)
    private List<InspectionRow> inspections;

    public ExtinguisherResponse(Extinguisher e) {
        this.extinguisherId = e.getExtinguisherId();
        this.serialNumber = e.getSerialNumber();
        this.buildingName = e.getBuilding() != null ? e.getBuilding().getBuildingName() : null;
        this.floorName = e.getFloor() != null ? e.getFloor().getFloorName() : null;
        this.extinguisherType = e.getExtinguisherType();
        this.installDate = e.getInstallDate();
        this.replacementCycleYears = e.getReplacementCycleYears();
        this.replacementDueDate = e.getReplacementDueDate();
        this.quantity = e.getQuantity();
        this.note = e.getNote();
        this.imagePath = e.getImagePath();
        this.x = e.getX();
        this.y = e.getY();
        this.createdAt = e.getCreatedAt();
    }

    public void setLastInspection(ExtinguisherInspection inspection) {
        if (inspection != null) {
            this.lastInspectionDate = inspection.getInspectionDate();
            this.lastInspectorName = inspection.getInspectedByName();
            this.lastIsFaulty = inspection.isFaulty();
            this.lastFaultReason = inspection.getFaultReason();
        }
    }

    public void setInspectionHistory(List<ExtinguisherInspection> list) {
        this.inspections = list.stream()
                .map(i -> new InspectionRow(
                        i.getInspectionDate(),
                        i.getInspectedByName(),
                        i.isFaulty(),
                        i.getFaultReason()))
                .collect(Collectors.toList());
    }

    @Getter
    public static class InspectionRow {
        private final LocalDate inspectionDate;
        private final String inspectorName;
        private final boolean isFaulty;
        private final String faultReason;

        public InspectionRow(LocalDate inspectionDate, String inspectorName,
                             boolean isFaulty, String faultReason) {
            this.inspectionDate = inspectionDate;
            this.inspectorName = inspectorName;
            this.isFaulty = isFaulty;
            this.faultReason = faultReason;
        }
    }
}
