package com.company.module.fire.service;

import com.company.core.exception.BusinessException;
import com.company.core.exception.ResourceNotFoundException;
import com.company.module.fire.dto.ExtinguisherInspectRequest;
import com.company.module.fire.dto.ExtinguisherResponse;
import com.company.module.fire.dto.ExtinguisherSaveRequest;
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

import java.time.LocalDate;
import java.util.List;

/**
 * 소화기 관리 서비스
 * <p>
 * 기존 ASP.NET: Pages/Extinguishers/* 비즈니스 로직
 * - @Transactional은 이 Service 계층에서만 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtinguisherService {

    private final ExtinguisherRepository extinguisherRepository;
    private final ExtinguisherInspectionRepository inspectionRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;

    /** 최근 점검 이력 최대 보관 건수 */
    private static final int MAX_INSPECTION_HISTORY = 12;

    /**
     * 소화기 목록 조회 (페이지네이션)
     * <p>
     * 기존 ASP.NET: IndexModel.OnGetAsync() 대응
     */
    @Transactional(readOnly = true)
    public Page<ExtinguisherResponse> getExtinguishers(Long buildingId, Long floorId,
                                                        String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("extinguisherId").ascending());

        Long bId = (buildingId != null && buildingId > 0) ? buildingId : null;
        Long fId = (floorId != null && floorId > 0) ? floorId : null;
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        Page<Extinguisher> entityPage = extinguisherRepository.searchExtinguishers(bId, fId, kw, pageable);
        return entityPage.map(e -> {
            ExtinguisherResponse dto = new ExtinguisherResponse(e);
            inspectionRepository
                    .findTopByExtinguisher_ExtinguisherIdOrderByInspectionDateDescInspectionIdDesc(e.getExtinguisherId())
                    .ifPresent(dto::setLastInspection);
            return dto;
        });
    }

    /**
     * 소화기 상세 조회 (점검 이력 포함)
     */
    @Transactional(readOnly = true)
    public ExtinguisherResponse getExtinguisherDetail(Long extinguisherId) {
        Extinguisher e = extinguisherRepository.findById(extinguisherId)
                .orElseThrow(() -> new ResourceNotFoundException("소화기", extinguisherId));

        ExtinguisherResponse dto = new ExtinguisherResponse(e);

        Pageable top12 = PageRequest.of(0, MAX_INSPECTION_HISTORY,
                Sort.by("inspectionDate").descending().and(Sort.by("inspectionId").descending()));
        List<ExtinguisherInspection> history = inspectionRepository
                .findByExtinguisher_ExtinguisherIdOrderByInspectionDateDescInspectionIdDesc(extinguisherId, top12);

        if (!history.isEmpty()) {
            dto.setLastInspection(history.get(0));
        }
        dto.setInspectionHistory(history);
        return dto;
    }

    /**
     * 소화기 저장 (신규 등록 / 수정)
     * <p>
     * 기존 ASP.NET: OnPostExtSaveAsync() 대응
     */
    @Transactional
    public ExtinguisherResponse saveExtinguisher(ExtinguisherSaveRequest req) {
        Building building = buildingRepository.findById(req.getBuildingId())
                .orElseThrow(() -> new BusinessException("건물 정보를 찾을 수 없습니다."));
        Floor floor = floorRepository.findById(req.getFloorId())
                .orElseThrow(() -> new BusinessException("층 정보를 찾을 수 없습니다."));

        Extinguisher entity;

        if (req.getExtinguisherId() != null && req.getExtinguisherId() > 0) {
            // 수정
            entity = extinguisherRepository.findById(req.getExtinguisherId())
                    .orElseThrow(() -> new ResourceNotFoundException("소화기", req.getExtinguisherId()));

            entity.update(building, floor, null,
                    req.getExtinguisherType(), req.getInstallDate(),
                    req.getReplacementCycleYears(), req.getQuantity(),
                    req.getX(), req.getY(), req.getNote());
        } else {
            // 신규 등록 - 일련번호 생성
            String serialNumber = generateNextSerialNumber();
            entity = Extinguisher.builder()
                    .serialNumber(serialNumber)
                    .building(building)
                    .floor(floor)
                    .extinguisherType(req.getExtinguisherType())
                    .installDate(req.getInstallDate())
                    .replacementCycleYears(req.getReplacementCycleYears())
                    .quantity(req.getQuantity())
                    .x(req.getX())
                    .y(req.getY())
                    .note(req.getNote())
                    .build();
            extinguisherRepository.save(entity);
        }

        log.info("Extinguisher saved: id={}, serial={}", entity.getExtinguisherId(), entity.getSerialNumber());
        return new ExtinguisherResponse(entity);
    }

    /**
     * 소화기 점검 등록
     * <p>
     * 기존 ASP.NET: OnPostInspectAsync() 대응
     * - 오늘 이미 점검한 경우 재점검 불가
     * - 점검 이력 최근 12건 유지
     */
    @Transactional
    public void inspect(ExtinguisherInspectRequest req, Long userId, String inspectorName) {
        Long extId = req.getExtinguisherId();

        if (req.isFaulty() && (req.getFaultReason() == null || req.getFaultReason().isBlank())) {
            throw new BusinessException("비정상인 경우 불량 사유가 필요합니다.");
        }

        Extinguisher extinguisher = extinguisherRepository.findById(extId)
                .orElseThrow(() -> new ResourceNotFoundException("소화기", extId));

        LocalDate inspectionDate = req.getInspectionDate() != null
                ? req.getInspectionDate()
                : LocalDate.now();

        if (inspectionRepository.existsByExtinguisher_ExtinguisherIdAndInspectionDate(extId, inspectionDate)) {
            throw new BusinessException("오늘 이미 점검을 완료했습니다.");
        }

        ExtinguisherInspection inspection = ExtinguisherInspection.builder()
                .extinguisher(extinguisher)
                .inspectionDate(inspectionDate)
                .isFaulty(req.isFaulty())
                .faultReason(req.getFaultReason())
                .inspectedByUserId(userId)
                .inspectedByName(inspectorName)
                .build();

        inspectionRepository.save(inspection);

        // 최근 12건 초과분 삭제
        inspectionRepository.trimInspectionsKeepLatest12(extId);

        log.info("Extinguisher inspected: extId={}, isFaulty={}, by={}", extId, req.isFaulty(), inspectorName);
    }

    /**
     * 소화기 삭제 (점검 이력 Cascade Delete)
     */
    @Transactional
    public void deleteExtinguisher(Long extinguisherId) {
        Extinguisher e = extinguisherRepository.findById(extinguisherId)
                .orElseThrow(() -> new ResourceNotFoundException("소화기", extinguisherId));
        extinguisherRepository.delete(e);
        log.info("Extinguisher deleted: id={}", extinguisherId);
    }

    /**
     * 이미지 경로 업데이트
     */
    @Transactional
    public void updateImagePath(Long extinguisherId, String imagePath) {
        Extinguisher e = extinguisherRepository.findById(extinguisherId)
                .orElseThrow(() -> new ResourceNotFoundException("소화기", extinguisherId));
        e.updateImagePath(imagePath);
    }

    /**
     * 다음 일련번호 생성 (EXT-000001 형식)
     */
    private String generateNextSerialNumber() {
        List<String> allSerials = extinguisherRepository.findAll().stream()
                .map(Extinguisher::getSerialNumber)
                .filter(s -> s != null && s.startsWith("EXT-"))
                .toList();

        int maxNum = 0;
        for (String serial : allSerials) {
            try {
                int num = Integer.parseInt(serial.substring(4));
                if (num > maxNum) maxNum = num;
            } catch (NumberFormatException ignored) { }
        }
        return String.format("EXT-%06d", maxNum + 1);
    }
}
