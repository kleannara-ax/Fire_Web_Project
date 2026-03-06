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
 * ?뚰솕湲?愿由??쒕퉬?? * <p>
 * 湲곗〈 ASP.NET: Pages/Extinguishers/* 鍮꾩쫰?덉뒪 濡쒖쭅
 * - @Transactional? ??Service 怨꾩링?먯꽌留??ъ슜
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtinguisherService {

    private final ExtinguisherRepository extinguisherRepository;
    private final ExtinguisherInspectionRepository inspectionRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;

    /** 理쒓렐 ?먭? ?대젰 理쒕? 蹂닿? 嫄댁닔 */
    private static final int MAX_INSPECTION_HISTORY = 12;

    /**
     * ?뚰솕湲?紐⑸줉 議고쉶 (?섏씠吏?ㅼ씠??
     * <p>
     * 湲곗〈 ASP.NET: IndexModel.OnGetAsync() ???     */
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
     * ?뚰솕湲??곸꽭 議고쉶 (?먭? ?대젰 ?ы븿)
     */
    @Transactional(readOnly = true)
    public ExtinguisherResponse getExtinguisherDetail(Long extinguisherId) {
        Extinguisher e = extinguisherRepository.findById(extinguisherId)
                .orElseThrow(() -> new ResourceNotFoundException("Extinguisher", extinguisherId));

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
     * ?뚰솕湲????(?좉퇋 ?깅줉 / ?섏젙)
     * <p>
     * 湲곗〈 ASP.NET: OnPostExtSaveAsync() ???     */
    @Transactional
    public ExtinguisherResponse saveExtinguisher(ExtinguisherSaveRequest req) {
        Building building = buildingRepository.findById(req.getBuildingId())
                .orElseThrow(() -> new BusinessException("嫄대Ъ ?뺣낫瑜?李얠쓣 ???놁뒿?덈떎."));
        Floor floor = floorRepository.findById(req.getFloorId())
                .orElseThrow(() -> new BusinessException("痢??뺣낫瑜?李얠쓣 ???놁뒿?덈떎."));

        Extinguisher entity;

        if (req.getExtinguisherId() != null && req.getExtinguisherId() > 0) {
            // ?섏젙
            entity = extinguisherRepository.findById(req.getExtinguisherId())
                    .orElseThrow(() -> new ResourceNotFoundException("Extinguisher", req.getExtinguisherId()));

            entity.update(building, floor, null,
                    req.getExtinguisherType(), req.getInstallDate(),
                    req.getReplacementCycleYears(), req.getQuantity(),
                    req.getX(), req.getY(), req.getNote());
        } else {
            // ?좉퇋 ?깅줉 - ?쇰젴踰덊샇 ?앹꽦
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
     * ?뚰솕湲??먭? ?깅줉
     * <p>
     * 湲곗〈 ASP.NET: OnPostInspectAsync() ???     * - ?ㅻ뒛 ?대? ?먭???寃쎌슦 ?ъ젏寃 遺덇?
     * - ?먭? ?대젰 理쒓렐 12嫄??좎?
     */
    @Transactional
    public void inspect(ExtinguisherInspectRequest req, Long userId, String inspectorName) {
        Long extId = req.getExtinguisherId();

        if (req.isFaulty() && (req.getFaultReason() == null || req.getFaultReason().isBlank())) {
            throw new BusinessException("鍮꾩젙?곸씤 寃쎌슦 遺덈웾 ?ъ쑀媛 ?꾩슂?⑸땲??");
        }

        Extinguisher extinguisher = extinguisherRepository.findById(extId)
                .orElseThrow(() -> new ResourceNotFoundException("Extinguisher", extId));

        LocalDate inspectionDate = req.getInspectionDate() != null
                ? req.getInspectionDate()
                : LocalDate.now();

        if (inspectionRepository.existsByExtinguisher_ExtinguisherIdAndInspectionDate(extId, inspectionDate)) {
            throw new BusinessException("?ㅻ뒛 ?대? ?먭????꾨즺?덉뒿?덈떎.");
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

        // 理쒓렐 12嫄?珥덇낵遺???젣
        inspectionRepository.trimInspectionsKeepLatest12(extId);

        log.info("Extinguisher inspected: extId={}, isFaulty={}, by={}", extId, req.isFaulty(), inspectorName);
    }

    @Transactional
    public void addInspection(Long extinguisherId, LocalDate inspectionDate, boolean isFaulty,
                              String faultReason, String inspectorName, Long userId) {
        if (inspectionDate == null) {
            throw new BusinessException("점검일은 필수입니다.");
        }
        if (isFaulty && (faultReason == null || faultReason.isBlank())) {
            throw new BusinessException("비정상인 경우 고장 사유가 필요합니다.");
        }
        Extinguisher extinguisher = extinguisherRepository.findById(extinguisherId)
                .orElseThrow(() -> new ResourceNotFoundException("Extinguisher", extinguisherId));

        if (inspectionRepository.existsByExtinguisher_ExtinguisherIdAndInspectionDate(extinguisherId, inspectionDate)) {
            throw new BusinessException("해당 날짜의 점검 이력이 이미 존재합니다.");
        }

        ExtinguisherInspection inspection = ExtinguisherInspection.builder()
                .extinguisher(extinguisher)
                .inspectionDate(inspectionDate)
                .isFaulty(isFaulty)
                .faultReason(faultReason)
                .inspectedByUserId(userId)
                .inspectedByName((inspectorName == null || inspectorName.isBlank()) ? "관리자" : inspectorName.trim())
                .build();
        inspectionRepository.save(inspection);
        inspectionRepository.trimInspectionsKeepLatest12(extinguisherId);
    }

    @Transactional
    public void updateInspectionDate(Long extinguisherId, Long inspectionId, LocalDate inspectionDate,
                                     boolean isFaulty, String faultReason, String inspectorName) {
        if (inspectionDate == null) {
            throw new BusinessException("점검일은 필수입니다.");
        }
        if (isFaulty && (faultReason == null || faultReason.isBlank())) {
            throw new BusinessException("비정상인 경우 고장 사유가 필요합니다.");
        }

        ExtinguisherInspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new ResourceNotFoundException("점검 이력", inspectionId));

        if (inspection.getExtinguisher() == null
                || !extinguisherId.equals(inspection.getExtinguisher().getExtinguisherId())) {
            throw new BusinessException("소화기와 점검 이력이 일치하지 않습니다.");
        }

        boolean duplicated = inspectionRepository
                .existsByExtinguisher_ExtinguisherIdAndInspectionDateAndInspectionIdNot(
                        extinguisherId, inspectionDate, inspectionId);
        if (duplicated) {
            throw new BusinessException("해당 날짜의 점검 이력이 이미 존재합니다.");
        }

        inspection.updateInspection(inspectionDate, isFaulty, faultReason, inspectorName);
    }

    /**
     * ?뚰솕湲???젣 (?먭? ?대젰 Cascade Delete)
     */
    @Transactional
    public void deleteExtinguisher(Long extinguisherId) {
        Extinguisher e = extinguisherRepository.findById(extinguisherId)
                .orElseThrow(() -> new ResourceNotFoundException("Extinguisher", extinguisherId));
        extinguisherRepository.delete(e);
        log.info("Extinguisher deleted: id={}", extinguisherId);
    }

    /**
     * ?대?吏 寃쎈줈 ?낅뜲?댄듃
     */
    @Transactional
    public void updateImagePath(Long extinguisherId, String imagePath) {
        Extinguisher e = extinguisherRepository.findById(extinguisherId)
                .orElseThrow(() -> new ResourceNotFoundException("Extinguisher", extinguisherId));
        e.updateImagePath(imagePath);
    }

    /**
     * ?ㅼ쓬 ?쇰젴踰덊샇 ?앹꽦 (EXT-000001 ?뺤떇)
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
