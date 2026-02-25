package com.company.module.fire.service;

import com.company.core.exception.BusinessException;
import com.company.core.exception.ResourceNotFoundException;
import com.company.module.fire.dto.FireHydrantResponse;
import com.company.module.fire.dto.FireHydrantSaveRequest;
import com.company.module.fire.entity.*;
import com.company.module.fire.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 소화전 관리 서비스
 * <p>
 * 기존 ASP.NET: Pages/FireHydrants/* 비즈니스 로직
 * - @Transactional은 이 Service 계층에서만 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FireHydrantService {

    private final FireHydrantRepository hydrantRepository;
    private final FireHydrantInspectionRepository inspectionRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;

    private static final int MAX_INSPECTION_HISTORY = 12;

    /**
     * 소화전 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<FireHydrantResponse> getHydrants(Long buildingId, Long floorId,
                                                   String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("building.buildingName").ascending()
                    .and(Sort.by("floor.floorName").ascending())
                    .and(Sort.by("serialNumber").ascending()));

        Long bId = (buildingId != null && buildingId > 0) ? buildingId : null;
        Long fId = (floorId != null && floorId > 0) ? floorId : null;
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        Page<FireHydrant> entityPage = hydrantRepository.searchHydrants(bId, fId, kw, pageable);
        return entityPage.map(h -> {
            FireHydrantResponse dto = new FireHydrantResponse(h);
            inspectionRepository
                    .findTopByHydrant_HydrantIdOrderByInspectionDateDescInspectionIdDesc(h.getHydrantId())
                    .ifPresent(dto::setLastInspection);
            return dto;
        });
    }

    /**
     * 소화전 상세 조회 (점검 이력 포함)
     */
    @Transactional(readOnly = true)
    public FireHydrantResponse getHydrantDetail(Long hydrantId) {
        FireHydrant h = hydrantRepository.findById(hydrantId)
                .orElseThrow(() -> new ResourceNotFoundException("소화전", hydrantId));

        FireHydrantResponse dto = new FireHydrantResponse(h);

        Pageable top12 = PageRequest.of(0, MAX_INSPECTION_HISTORY,
                Sort.by("inspectionDate").descending().and(Sort.by("inspectionId").descending()));
        List<FireHydrantInspection> history = inspectionRepository
                .findByHydrant_HydrantIdOrderByInspectionDateDescInspectionIdDesc(hydrantId, top12);

        if (!history.isEmpty()) {
            dto.setLastInspection(history.get(0));
        }
        dto.setInspectionHistory(history);
        return dto;
    }

    /**
     * 소화전 저장 (신규 / 수정)
     * <p>
     * 기존 ASP.NET: OnPostHydSaveAsync() 대응
     */
    @Transactional
    public FireHydrantResponse saveHydrant(FireHydrantSaveRequest req) {
        String hydrantType = (req.getHydrantType() != null) ? req.getHydrantType().trim() : "Indoor";
        String operationType = (req.getOperationType() != null) ? req.getOperationType().trim() : "Manual";

        if (!"Indoor".equals(hydrantType) && !"Outdoor".equals(hydrantType)) {
            throw new BusinessException("HydrantType은 Indoor 또는 Outdoor이어야 합니다.");
        }
        if (!"Auto".equals(operationType) && !"Manual".equals(operationType)) {
            throw new BusinessException("OperationType은 Auto 또는 Manual이어야 합니다.");
        }

        FireHydrant entity;

        if (req.getHydrantId() != null && req.getHydrantId() > 0) {
            // 수정 (타입은 변경 불가)
            entity = hydrantRepository.findById(req.getHydrantId())
                    .orElseThrow(() -> new ResourceNotFoundException("소화전", req.getHydrantId()));
            hydrantType = entity.getHydrantType();  // 기존 타입 유지
        } else {
            // 신규 등록
            String serialNumber = generateNextSerialNumber();
            entity = FireHydrant.builder()
                    .serialNumber(serialNumber)
                    .hydrantType(hydrantType)
                    .operationType(operationType)
                    .isActive(true)
                    .build();
            hydrantRepository.save(entity);
        }

        Building building;
        Floor floor;
        BigDecimal x, y;

        if ("Outdoor".equals(hydrantType)) {
            // 옥외: buildingId=99 (기존 규칙 유지), floorId=1
            building = buildingRepository.findById(99L)
                    .orElseGet(() -> buildingRepository.findById(1L)
                            .orElseThrow(() -> new BusinessException("옥외 건물(id=99) 정보를 설정하세요.")));
            floor = floorRepository.findById(1L)
                    .orElseThrow(() -> new BusinessException("기본 층(id=1) 정보를 설정하세요."));

            if (req.getX() == null || req.getY() == null) {
                throw new BusinessException("옥외 소화전은 좌표가 필요합니다.");
            }
            x = req.getX().setScale(2, java.math.RoundingMode.HALF_UP);
            y = req.getY().setScale(2, java.math.RoundingMode.HALF_UP);
        } else {
            if (req.getBuildingId() == null || req.getFloorId() == null) {
                throw new BusinessException("건물/층을 선택하세요.");
            }
            building = buildingRepository.findById(req.getBuildingId())
                    .orElseThrow(() -> new BusinessException("건물 정보를 찾을 수 없습니다."));
            floor = floorRepository.findById(req.getFloorId())
                    .orElseThrow(() -> new BusinessException("층 정보를 찾을 수 없습니다."));
            x = req.getX();
            y = req.getY();
        }

        entity.update(operationType, building, floor, x, y, req.getLocationDescription());

        log.info("FireHydrant saved: id={}, serial={}", entity.getHydrantId(), entity.getSerialNumber());
        return new FireHydrantResponse(entity);
    }

    /**
     * 소화전 점검 등록
     * <p>
     * 기존 ASP.NET: OnPostInspectAsync() 대응
     */
    @Transactional
    public void inspect(Long hydrantId, boolean isFaulty, String faultReason,
                        Long userId, String inspectorName) {
        if (isFaulty && (faultReason == null || faultReason.isBlank())) {
            throw new BusinessException("비정상인 경우 불량 사유가 필요합니다.");
        }

        FireHydrant hydrant = hydrantRepository.findById(hydrantId)
                .orElseThrow(() -> new ResourceNotFoundException("소화전", hydrantId));

        LocalDate today = LocalDate.now();

        FireHydrantInspection inspection = FireHydrantInspection.builder()
                .hydrant(hydrant)
                .inspectionDate(today)
                .isFaulty(isFaulty)
                .faultReason(faultReason)
                .inspectedByUserId(userId)
                .inspectedByName(inspectorName)
                .build();

        inspectionRepository.save(inspection);
        inspectionRepository.trimInspectionsKeepLatest12(hydrantId);

        log.info("FireHydrant inspected: hydrantId={}, isFaulty={}, by={}", hydrantId, isFaulty, inspectorName);
    }

    /**
     * 소화전 삭제
     */
    @Transactional
    public void deleteHydrant(Long hydrantId) {
        FireHydrant h = hydrantRepository.findById(hydrantId)
                .orElseThrow(() -> new ResourceNotFoundException("소화전", hydrantId));
        hydrantRepository.delete(h);
        log.info("FireHydrant deleted: id={}", hydrantId);
    }

    /**
     * 이미지 경로 업데이트
     */
    @Transactional
    public void updateImagePath(Long hydrantId, String imagePath) {
        FireHydrant h = hydrantRepository.findById(hydrantId)
                .orElseThrow(() -> new ResourceNotFoundException("소화전", hydrantId));
        h.updateImagePath(imagePath);
    }

    /**
     * 다음 일련번호 생성 (HYD-000001 형식)
     */
    private String generateNextSerialNumber() {
        List<String> serials = hydrantRepository.findAllSerialNumbers();
        int maxNum = 0;
        for (String s : serials) {
            try {
                int n = Integer.parseInt(s.substring(4));
                if (n > maxNum) maxNum = n;
            } catch (NumberFormatException ignored) { }
        }
        return String.format("HYD-%06d", maxNum + 1);
    }
}
