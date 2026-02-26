package com.company.module.fire.controller;

import com.company.core.common.ApiResponse;
import com.company.module.fire.entity.*;
import com.company.module.fire.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 모바일 점검 (minspection) API Controller
 * <p>
 * 기존 ASP.NET: Pages/minspection/* 대응
 * 인증 없이 접근 가능 (QR 스캔 후 바로 접근)
 *
 * URL 패턴:
 *   GET  /fire-api/minspection/extinguishers/{serial}       → 소화기 조회 (등록 여부 포함)
 *   POST /fire-api/minspection/extinguishers/register       → 미등록 소화기 등록
 *   GET  /fire-api/minspection/extinguishers/mapdata        → 도면 데이터
 *   GET  /fire-api/minspection/hydrants/{serial}            → 소화전 조회
 *   POST /fire-api/minspection/hydrants/register            → 미등록 소화전 등록
 *   GET  /fire-api/minspection/hydrants/mapdata             → 도면 데이터
 *   POST /fire-api/minspection/extinguishers/{id}/inspect   → 소화기 점검
 *   POST /fire-api/minspection/hydrants/{id}/inspect        → 소화전 점검
 */
@RestController
@RequestMapping("/fire-api/minspection")
@RequiredArgsConstructor
public class MobileInspectionController {

    private final ExtinguisherRepository extinguisherRepository;
    private final FireHydrantRepository fireHydrantRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final ExtinguisherInspectionRepository extInspectionRepository;
    private final FireHydrantInspectionRepository hydInspectionRepository;

    // ==================== 소화기 ====================

    /**
     * 소화기 시리얼로 조회 (등록 여부 + 기본 정보)
     * GET /fire-api/minspection/extinguishers/by-serial?serial=EXT-000001
     */
    @GetMapping("/extinguishers/by-serial")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtBySerial(
            @RequestParam String serial) {

        Optional<Extinguisher> opt = extinguisherRepository.findBySerialNumber(serial.trim());

        Map<String, Object> result = new LinkedHashMap<>();
        if (opt.isEmpty()) {
            result.put("exists", false);
            result.put("serialNumber", serial.trim());
            // 건물/층 목록도 함께 반환 (등록 폼에 사용)
            result.put("buildings", getBuildingList());
            result.put("floors", getFloorList());
        } else {
            Extinguisher e = opt.get();
            result.put("exists", true);
            result.put("extinguisherId", e.getExtinguisherId());
            result.put("serialNumber", e.getSerialNumber());
            result.put("buildingName", e.getBuilding() != null ? e.getBuilding().getBuildingName() : "-");
            result.put("floorName", e.getFloor() != null ? e.getFloor().getFloorName() : "-");
            result.put("extinguisherType", e.getExtinguisherType());
            result.put("installDate", e.getInstallDate());
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 소화기 ID로 조회
     * GET /fire-api/minspection/extinguishers/{id}
     */
    @GetMapping("/extinguishers/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtById(@PathVariable Long id) {
        Extinguisher e = extinguisherRepository.findById(id)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("소화기를 찾을 수 없습니다."));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("extinguisherId", e.getExtinguisherId());
        result.put("serialNumber", e.getSerialNumber());
        result.put("buildingName", e.getBuilding() != null ? e.getBuilding().getBuildingName() : "-");
        result.put("floorName", e.getFloor() != null ? e.getFloor().getFloorName() : "-");
        result.put("extinguisherType", e.getExtinguisherType());
        result.put("installDate", e.getInstallDate());
        result.put("imagePath", e.getImagePath());
        result.put("quantity", e.getQuantity());
        result.put("note", e.getNote());

        // 최종 점검 정보
        extInspectionRepository
                .findTopByExtinguisher_ExtinguisherIdOrderByInspectionDateDescInspectionIdDesc(e.getExtinguisherId())
                .ifPresent(ins -> {
                    result.put("lastInspectionDate", ins.getInspectionDate());
                    result.put("lastInspectorName", ins.getInspectedByName());
                    result.put("lastIsFaulty", ins.isFaulty());
                    result.put("lastFaultReason", ins.getFaultReason());
                });

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 미등록 소화기 등록
     * POST /fire-api/minspection/extinguishers/register
     */
    @PostMapping("/extinguishers/register")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerExtinguisher(
            @RequestBody Map<String, Object> body) {

        String serial = getString(body, "serialNumber");
        if (serial == null || serial.isBlank())
            return ResponseEntity.badRequest().body(ApiResponse.fail("QR 코드가 비어 있습니다."));

        // 이미 등록된 경우 → 기존 ID 반환
        Optional<Extinguisher> existing = extinguisherRepository.findBySerialNumber(serial.trim());
        if (existing.isPresent()) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("extinguisherId", existing.get().getExtinguisherId());
            res.put("alreadyExists", true);
            return ResponseEntity.ok(ApiResponse.success(res));
        }

        Long buildingId = getLong(body, "buildingId");
        Long floorId = getLong(body, "floorId");
        String type = getString(body, "extinguisherType");
        String dateStr = getString(body, "installDate");
        BigDecimal x = getBigDecimal(body, "x");
        BigDecimal y = getBigDecimal(body, "y");

        if (buildingId == null || buildingId <= 0)
            return ResponseEntity.badRequest().body(ApiResponse.fail("건물을 선택하세요."));
        if (floorId == null || floorId <= 0)
            return ResponseEntity.badRequest().body(ApiResponse.fail("층을 선택하세요."));
        if (type == null || type.isBlank())
            return ResponseEntity.badRequest().body(ApiResponse.fail("소화기 종류를 선택하세요."));

        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("건물을 찾을 수 없습니다."));
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("층을 찾을 수 없습니다."));

        LocalDate installDate = (dateStr != null && !dateStr.isBlank())
                ? LocalDate.parse(dateStr) : LocalDate.now();

        // 이산화탄소: 교체 주기 없음(99), 그 외: 10년
        int replacementYears = "이산화탄소소화기".equals(type) ? 99 : 10;

        Extinguisher entity = Extinguisher.builder()
                .serialNumber(serial.trim())
                .building(building)
                .floor(floor)
                .extinguisherType(type.trim())
                .installDate(installDate)
                .replacementCycleYears(replacementYears)
                .quantity(1)
                .x(x)
                .y(y)
                .build();

        extinguisherRepository.save(entity);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("extinguisherId", entity.getExtinguisherId());
        res.put("alreadyExists", false);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    /**
     * 소화기 점검 등록 (모바일)
     * POST /fire-api/minspection/extinguishers/{id}/inspect
     */
    @PostMapping("/extinguishers/{id}/inspect")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> inspectExtinguisher(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        Extinguisher e = extinguisherRepository.findById(id)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("소화기를 찾을 수 없습니다."));

        boolean isFaulty = Boolean.TRUE.equals(body.get("isFaulty"));
        String faultReason = getString(body, "faultReason");
        String inspectorName = getString(body, "inspectorName");
        if (inspectorName == null || inspectorName.isBlank()) inspectorName = "모바일점검";

        ExtinguisherInspection inspection = ExtinguisherInspection.builder()
                .extinguisher(e)
                .inspectionDate(LocalDate.now())
                .isFaulty(isFaulty)
                .faultReason(isFaulty ? faultReason : null)
                .inspectedByName(inspectorName)
                .build();

        extInspectionRepository.save(inspection);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 소화기 도면 데이터 조회
     * GET /fire-api/minspection/extinguishers/mapdata?buildingId=1&floorId=2
     */
    @GetMapping("/extinguishers/mapdata")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtMapData(
            @RequestParam Long buildingId,
            @RequestParam Long floorId) {

        if (buildingId == null || buildingId <= 0 || floorId == null || floorId <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("buildingId/floorId가 올바르지 않습니다."));
        }

        String buildingName = buildingRepository.findById(buildingId)
                .map(Building::getBuildingName).orElse("");
        String floorName = floorRepository.findById(floorId)
                .map(Floor::getFloorName).orElse("");

        String planImagePath = resolvePlanImagePath(buildingName, floorName);

        List<Map<String, Object>> items = extinguisherRepository.findForMap(buildingId, floorId)
                .stream().map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("extinguisherId", e.getExtinguisherId());
                    m.put("x", e.getX());
                    m.put("y", e.getY());
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("planImagePath", planImagePath);
        result.put("items", items);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ==================== 소화전 ====================

    /**
     * 소화전 시리얼로 조회
     * GET /fire-api/minspection/hydrants/by-serial?serial=HYD-000001
     */
    @GetMapping("/hydrants/by-serial")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHydBySerial(
            @RequestParam String serial) {

        Optional<FireHydrant> opt = fireHydrantRepository.findBySerialNumber(serial.trim());

        Map<String, Object> result = new LinkedHashMap<>();
        if (opt.isEmpty()) {
            result.put("exists", false);
            result.put("serialNumber", serial.trim());
            result.put("buildings", getBuildingList());
            result.put("floors", getFloorList());
        } else {
            FireHydrant h = opt.get();
            result.put("exists", true);
            result.put("hydrantId", h.getHydrantId());
            result.put("serialNumber", h.getSerialNumber());
            result.put("buildingName", h.getBuilding() != null ? h.getBuilding().getBuildingName() : "-");
            result.put("floorName", h.getFloor() != null ? h.getFloor().getFloorName() : "-");
            result.put("hydrantType", h.getHydrantType());
            result.put("operationType", h.getOperationType());
            result.put("locationDescription", h.getLocationDescription());
            result.put("imagePath", h.getImagePath());
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 소화전 ID로 조회
     * GET /fire-api/minspection/hydrants/{id}
     */
    @GetMapping("/hydrants/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHydById(@PathVariable Long id) {
        FireHydrant h = fireHydrantRepository.findById(id)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("소화전을 찾을 수 없습니다."));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hydrantId", h.getHydrantId());
        result.put("serialNumber", h.getSerialNumber());
        result.put("buildingName", h.getBuilding() != null ? h.getBuilding().getBuildingName() : "-");
        result.put("floorName", h.getFloor() != null ? h.getFloor().getFloorName() : "-");
        result.put("hydrantType", h.getHydrantType());
        result.put("operationType", h.getOperationType());
        result.put("locationDescription", h.getLocationDescription());
        result.put("imagePath", h.getImagePath());
        result.put("isActive", h.isActive());

        // 최종 점검 정보
        hydInspectionRepository
                .findTopByHydrant_HydrantIdOrderByInspectionDateDescInspectionIdDesc(h.getHydrantId())
                .ifPresent(ins -> {
                    result.put("lastInspectionDate", ins.getInspectionDate());
                    result.put("lastInspectorName", ins.getInspectedByName());
                    result.put("lastIsFaulty", ins.isFaulty());
                    result.put("lastFaultReason", ins.getFaultReason());
                });

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 미등록 소화전 등록
     * POST /fire-api/minspection/hydrants/register
     */
    @PostMapping("/hydrants/register")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerHydrant(
            @RequestBody Map<String, Object> body) {

        String serial = getString(body, "serialNumber");
        if (serial == null || serial.isBlank())
            return ResponseEntity.badRequest().body(ApiResponse.fail("QR 코드가 비어 있습니다."));

        Optional<FireHydrant> existing = fireHydrantRepository.findBySerialNumber(serial.trim());
        if (existing.isPresent()) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("hydrantId", existing.get().getHydrantId());
            res.put("alreadyExists", true);
            return ResponseEntity.ok(ApiResponse.success(res));
        }

        String hydrantType = getString(body, "hydrantType");
        String operationType = getString(body, "operationType");
        String locationDescription = getString(body, "locationDescription");
        BigDecimal x = getBigDecimal(body, "x");
        BigDecimal y = getBigDecimal(body, "y");

        if (hydrantType == null || (!hydrantType.equals("Indoor") && !hydrantType.equals("Outdoor")))
            return ResponseEntity.badRequest().body(ApiResponse.fail("구분을 선택하세요."));
        if (operationType == null || (!operationType.equals("Manual") && !operationType.equals("Auto")))
            return ResponseEntity.badRequest().body(ApiResponse.fail("작동 방식을 선택하세요."));

        Long buildingId;
        Long floorId;

        if ("Outdoor".equals(hydrantType)) {
            buildingId = 99L;
            floorId = 1L;
        } else {
            buildingId = getLong(body, "buildingId");
            floorId = getLong(body, "floorId");
            if (buildingId == null || buildingId <= 0)
                return ResponseEntity.badRequest().body(ApiResponse.fail("건물을 선택하세요."));
            if (floorId == null || floorId <= 0)
                return ResponseEntity.badRequest().body(ApiResponse.fail("층을 선택하세요."));
        }

        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("건물을 찾을 수 없습니다."));
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("층을 찾을 수 없습니다."));

        FireHydrant entity = FireHydrant.builder()
                .serialNumber(serial.trim())
                .hydrantType(hydrantType)
                .operationType(operationType)
                .building(building)
                .floor(floor)
                .x(x)
                .y(y)
                .locationDescription(locationDescription)
                .isActive(true)
                .build();

        fireHydrantRepository.save(entity);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("hydrantId", entity.getHydrantId());
        res.put("alreadyExists", false);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    /**
     * 소화전 점검 등록 (모바일)
     * POST /fire-api/minspection/hydrants/{id}/inspect
     */
    @PostMapping("/hydrants/{id}/inspect")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> inspectHydrant(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        FireHydrant h = fireHydrantRepository.findById(id)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("소화전을 찾을 수 없습니다."));

        boolean isFaulty = Boolean.TRUE.equals(body.get("isFaulty"));
        String faultReason = getString(body, "faultReason");
        String inspectorName = getString(body, "inspectorName");
        if (inspectorName == null || inspectorName.isBlank()) inspectorName = "모바일점검";

        FireHydrantInspection inspection = FireHydrantInspection.builder()
                .hydrant(h)
                .inspectionDate(LocalDate.now())
                .isFaulty(isFaulty)
                .faultReason(isFaulty ? faultReason : null)
                .inspectedByName(inspectorName)
                .build();

        hydInspectionRepository.save(inspection);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 소화전 도면 데이터 조회
     * GET /fire-api/minspection/hydrants/mapdata?buildingId=1&floorId=2
     */
    @GetMapping("/hydrants/mapdata")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHydMapData(
            @RequestParam Long buildingId,
            @RequestParam Long floorId) {

        if (buildingId == null || buildingId <= 0 || floorId == null || floorId <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("buildingId/floorId가 올바르지 않습니다."));
        }

        String buildingName = buildingRepository.findById(buildingId)
                .map(Building::getBuildingName).orElse("");
        String floorName = floorRepository.findById(floorId)
                .map(Floor::getFloorName).orElse("");

        String planImagePath = resolvePlanImagePath(buildingName, floorName);

        // 소화전은 Indoor 타입만 도면에 표시
        List<Map<String, Object>> items = fireHydrantRepository
                .findForMap("Indoor", buildingId, floorId)
                .stream().map(h -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("hydrantId", h.getHydrantId());
                    m.put("x", h.getX());
                    m.put("y", h.getY());
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("planImagePath", planImagePath);
        result.put("items", items);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ==================== Helper ====================

    private List<Map<String, Object>> getBuildingList() {
        return buildingRepository.findByActiveTrueOrderByBuildingName().stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("buildingId", b.getBuildingId());
            m.put("buildingName", b.getBuildingName());
            return m;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> getFloorList() {
        return floorRepository.findAllByOrderBySortOrderAsc().stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("floorId", f.getFloorId());
            m.put("floorName", f.getFloorName());
            return m;
        }).collect(Collectors.toList());
    }

    private String resolvePlanImagePath(String buildingName, String floorName) {
        String b = buildingName == null ? "" : buildingName.trim();
        String f = floorName == null ? "" : floorName.trim();

        if (b.contains("복지관")) {
            if (f.contains("지하") || f.contains("B1")) return "/images/bokji_B1.PNG";
            if (f.contains("2")) return "/images/bokji_2F.PNG";
            if (f.contains("1")) return "/images/bokji_1F.PNG";
            if (f.contains("3")) return "/images/bokji_3F.PNG";
        }
        if (b.contains("관리")) {
            if (f.contains("1")) return "/images/gwanri_1F.PNG";
        }
        return "/images/bokji_1F.PNG";
    }

    private String getString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString().trim();
    }

    private Long getLong(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private BigDecimal getBigDecimal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
