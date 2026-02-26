package com.company.module.fire.controller;

import com.company.core.common.ApiResponse;
import com.company.module.fire.entity.*;
import com.company.module.fire.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 도면(Floor Map) API Controller
 * <p>
 * 기존 ASP.NET: Pages/Maps/Floor.cshtml 대응
 *
 * URL 패턴 (모두 인증 필요):
 *   GET  /fire-api/floor/data           → 도면 데이터 (소화기 목록 + 소화전 목록 + 이동 대상 목록)
 *   GET  /fire-api/floor/jump-targets   → 도면이 등록된 건물/층 목록
 *   POST /fire-api/floor/ext/save       → 소화기 좌표 저장 (그룹 자동 처리)
 *   POST /fire-api/floor/ext/clear      → 소화기 좌표 삭제
 *   POST /fire-api/floor/ext/create     → 소화기 추가 (도면 특정 위치)
 *   POST /fire-api/floor/ext/delete     → 소화기 삭제 (점검 이력 포함)
 *   POST /fire-api/floor/ext/inspect    → 소화기 점검
 *   POST /fire-api/floor/hyd/save       → 소화전 좌표 저장
 *   POST /fire-api/floor/hyd/clear      → 소화전 좌표 삭제
 *   POST /fire-api/floor/hyd/create     → 소화전 추가 (도면 특정 위치)
 *   POST /fire-api/floor/hyd/delete     → 소화전 삭제 (점검 이력 포함)
 *   POST /fire-api/floor/hyd/inspect    → 소화전 점검
 */
@RestController
@RequestMapping("/fire-api/floor")
@RequiredArgsConstructor
public class FloorController {

    private final ExtinguisherRepository extinguisherRepository;
    private final ExtinguisherGroupRepository extinguisherGroupRepository;
    private final ExtinguisherInspectionRepository extInspectionRepository;
    private final FireHydrantRepository fireHydrantRepository;
    private final FireHydrantInspectionRepository hydInspectionRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;

    // ─────────────────────────────────────────────
    // GET: 도면 전체 데이터
    // ─────────────────────────────────────────────

    /**
     * 도면 데이터 조회
     * GET /fire-api/floor/data?buildingId=1&floorId=1
     */
    @GetMapping("/data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFloorData(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) String buildingName,
            @RequestParam(required = false) String floorName) {

        // buildingId/floorId 미전달 시 buildingName/floorName으로 조회
        if (buildingId == null && buildingName != null) {
            buildingId = buildingRepository.findAllByOrderByBuildingNameAsc().stream()
                    .filter(b -> b.getBuildingName().equals(buildingName))
                    .findFirst().map(Building::getBuildingId).orElse(null);
        }
        if (floorId == null && floorName != null) {
            floorId = floorRepository.findAllByOrderBySortOrderAsc().stream()
                    .filter(f -> f.getFloorName().equals(floorName))
                    .findFirst().map(Floor::getFloorId).orElse(null);
        }
        // 기본값: 복지관 1층
        if (buildingId == null) {
            buildingId = buildingRepository.findAllByOrderByBuildingNameAsc().stream()
                    .filter(b -> b.getBuildingName().contains("복지관"))
                    .findFirst().map(Building::getBuildingId).orElse(1L);
        }
        if (floorId == null) {
            floorId = floorRepository.findAllByOrderBySortOrderAsc().stream()
                    .filter(f -> f.getFloorName().contains("1층"))
                    .findFirst().map(Floor::getFloorId).orElse(1L);
        }

        final Long finalBuildingId = buildingId;
        final Long finalFloorId = floorId;

        Building building = buildingRepository.findById(finalBuildingId).orElse(null);
        Floor floor = floorRepository.findById(finalFloorId).orElse(null);

        String bName = building != null ? building.getBuildingName() : "";
        String fName = floor != null ? floor.getFloorName() : "";
        String planImagePath = resolvePlanImagePath(bName, fName);

        // 소화기 목록 (해당 건물/층)
        List<Map<String, Object>> extItems = buildExtItems(finalBuildingId, finalFloorId);

        // 소화전 목록 (Indoor, 해당 건물/층)
        List<Map<String, Object>> hydItems = buildHydItems(finalBuildingId, finalFloorId);

        // 이동 대상 목록 (jumpTargets)
        List<Map<String, Object>> jumpTargets = buildJumpTargets();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buildingId", finalBuildingId);
        result.put("floorId", finalFloorId);
        result.put("buildingName", bName);
        result.put("floorName", fName);
        result.put("planImagePath", planImagePath);
        result.put("items", extItems);
        result.put("hydrants", hydItems);
        result.put("jumpTargets", jumpTargets);
        result.put("isAdmin", false); // 프론트에서 JWT 기반으로 판단

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ─────────────────────────────────────────────
    // 소화기: 좌표 저장
    // ─────────────────────────────────────────────

    /**
     * 소화기 좌표 저장 (그룹 자동 처리)
     * POST /fire-api/floor/ext/save
     */
    @PostMapping("/ext/save")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveExtCoord(
            @RequestBody Map<String, Object> body) {

        Long extId = getLong(body, "extinguisherId");
        Long groupId = getLong(body, "groupId");
        Long buildingId = getLong(body, "buildingId");
        Long floorId = getLong(body, "floorId");
        BigDecimal x = bd2(getBigDecimal(body, "x"));
        BigDecimal y = bd2(getBigDecimal(body, "y"));

        if (extId == null) return badRequest("extinguisherId가 필요합니다.");
        if (buildingId == null) return badRequest("buildingId가 필요합니다.");
        if (floorId == null) return badRequest("floorId가 필요합니다.");
        if (x == null || y == null) return badRequest("x/y 좌표가 필요합니다.");

        Extinguisher ext = extinguisherRepository.findById(extId)
                .filter(e -> e.getBuilding().getBuildingId().equals(buildingId)
                          && e.getFloor().getFloorId().equals(floorId))
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("소화기를 찾을 수 없습니다."));

        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("건물을 찾을 수 없습니다."));
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("층을 찾을 수 없습니다."));

        // 그룹이 있으면 그룹 전체 이동
        Long effectiveGroupId = groupId != null ? groupId : (ext.getGroup() != null ? ext.getGroup().getGroupId() : null);

        if (effectiveGroupId != null) {
            ExtinguisherGroup group = extinguisherGroupRepository.findById(effectiveGroupId)
                    .orElse(null);
            if (group != null) {
                group.updateCoordinates(x, y);
                // 같은 그룹의 소화기 전체 좌표 갱신
                List<Extinguisher> sameGroup = extinguisherRepository.findAll().stream()
                        .filter(e -> e.getGroup() != null
                                  && e.getGroup().getGroupId().equals(effectiveGroupId)
                                  && e.getBuilding().getBuildingId().equals(buildingId)
                                  && e.getFloor().getFloorId().equals(floorId))
                        .collect(Collectors.toList());
                for (Extinguisher e : sameGroup) {
                    e.update(e.getBuilding(), e.getFloor(), group,
                             e.getExtinguisherType(), e.getInstallDate(),
                             e.getReplacementCycleYears(), e.getQuantity(),
                             x, y, e.getNote());
                }
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("groupId", effectiveGroupId);
                res.put("x", x);
                res.put("y", y);
                res.put("moved", sameGroup.size());
                return ResponseEntity.ok(ApiResponse.success(res));
            }
        }

        // 그룹 없음: 동일 좌표에 기존 그룹이 있으면 합류, 없으면 신규 그룹 생성
        Optional<ExtinguisherGroup> existingGroupOpt =
                extinguisherGroupRepository.findByCoordinates(buildingId, floorId, x, y);

        ExtinguisherGroup group;
        if (existingGroupOpt.isPresent()) {
            group = existingGroupOpt.get();
        } else {
            group = ExtinguisherGroup.builder()
                    .building(building).floor(floor).x(x).y(y).build();
            extinguisherGroupRepository.save(group);
        }

        // 같은 좌표에 GroupId 없는 기존 소화기들도 동일 그룹으로 묶기
        List<Extinguisher> sameCoordNoGroup = extinguisherRepository.findAll().stream()
                .filter(e -> e.getGroup() == null
                          && e.getBuilding().getBuildingId().equals(buildingId)
                          && e.getFloor().getFloorId().equals(floorId)
                          && x.equals(bd2(e.getX()))
                          && y.equals(bd2(e.getY())))
                .collect(Collectors.toList());

        final ExtinguisherGroup finalGroup = group;
        for (Extinguisher e : sameCoordNoGroup) {
            e.update(e.getBuilding(), e.getFloor(), finalGroup,
                     e.getExtinguisherType(), e.getInstallDate(),
                     e.getReplacementCycleYears(), e.getQuantity(),
                     x, y, e.getNote());
        }

        ext.update(building, floor, group, ext.getExtinguisherType(), ext.getInstallDate(),
                   ext.getReplacementCycleYears(), ext.getQuantity(), x, y, ext.getNote());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("extinguisherId", ext.getExtinguisherId());
        res.put("groupId", group.getGroupId());
        res.put("x", x);
        res.put("y", y);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    // ─────────────────────────────────────────────
    // 소화기: 좌표 삭제
    // ─────────────────────────────────────────────

    /**
     * POST /fire-api/floor/ext/clear
     */
    @PostMapping("/ext/clear")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> clearExtCoord(
            @RequestBody Map<String, Object> body) {

        Long extId = getLong(body, "extinguisherId");
        Long buildingId = getLong(body, "buildingId");
        Long floorId = getLong(body, "floorId");
        if (extId == null) return badRequest("extinguisherId가 필요합니다.");
        if (buildingId == null) return badRequest("buildingId가 필요합니다.");
        if (floorId == null) return badRequest("floorId가 필요합니다.");

        Extinguisher ext = extinguisherRepository.findById(extId)
                .filter(e -> e.getBuilding().getBuildingId().equals(buildingId)
                          && e.getFloor().getFloorId().equals(floorId))
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("소화기를 찾을 수 없습니다."));

        ext.update(ext.getBuilding(), ext.getFloor(), ext.getGroup(),
                   ext.getExtinguisherType(), ext.getInstallDate(),
                   ext.getReplacementCycleYears(), ext.getQuantity(),
                   null, null, ext.getNote());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("extinguisherId", extId);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    // ─────────────────────────────────────────────
    // 소화기: 추가
    // ─────────────────────────────────────────────

    /**
     * POST /fire-api/floor/ext/create
     */
    @PostMapping("/ext/create")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> createExt(
            @RequestBody Map<String, Object> body) {

        Long buildingId = getLong(body, "buildingId");
        Long floorId    = getLong(body, "floorId");
        String type     = getString(body, "extinguisherType");
        String dateStr  = getString(body, "installDate");
        int qty         = getInt(body, "quantity", 1);
        String note     = getString(body, "note");
        Long groupId    = getLong(body, "groupId");
        BigDecimal x    = bd2(getBigDecimal(body, "x"));
        BigDecimal y    = bd2(getBigDecimal(body, "y"));

        if (buildingId == null) return badRequest("buildingId가 필요합니다.");
        if (floorId == null)    return badRequest("floorId가 필요합니다.");
        if (type == null || type.isBlank()) return badRequest("소화기 종류를 입력하세요.");
        if (x == null || y == null) return badRequest("x/y 좌표가 필요합니다.");

        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("건물을 찾을 수 없습니다."));
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("층을 찾을 수 없습니다."));

        LocalDate installDate = (dateStr != null && !dateStr.isBlank())
                ? LocalDate.parse(dateStr) : LocalDate.now();

        // 그룹 처리
        ExtinguisherGroup group = null;
        if (groupId != null) {
            group = extinguisherGroupRepository.findById(groupId).orElse(null);
        }
        if (group == null) {
            // 동일 좌표 기존 그룹 찾거나 새로 생성
            group = extinguisherGroupRepository.findByCoordinates(buildingId, floorId, x, y)
                    .orElse(null);
            if (group == null) {
                group = ExtinguisherGroup.builder()
                        .building(building).floor(floor).x(x).y(y).build();
                extinguisherGroupRepository.save(group);
            }
        }

        // 다음 시리얼 번호 생성
        String serial = generateNextExtSerial();
        int replacementYears = "이산화탄소소화기".equals(type) ? 99 : 10;

        Extinguisher ext = Extinguisher.builder()
                .serialNumber(serial)
                .building(building)
                .floor(floor)
                .group(group)
                .extinguisherType(type.trim())
                .installDate(installDate)
                .replacementCycleYears(replacementYears)
                .quantity(qty > 0 ? qty : 1)
                .x(x)
                .y(y)
                .note(note)
                .build();

        extinguisherRepository.save(ext);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("extinguisherId", ext.getExtinguisherId());
        res.put("serialNumber", ext.getSerialNumber());
        res.put("groupId", group.getGroupId());
        res.put("x", x);
        res.put("y", y);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    // ─────────────────────────────────────────────
    // 소화기: 삭제
    // ─────────────────────────────────────────────

    /**
     * POST /fire-api/floor/ext/delete
     */
    @PostMapping("/ext/delete")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteExt(
            @RequestBody Map<String, Object> body) {

        Long extId = getLong(body, "extinguisherId");
        Long buildingId = getLong(body, "buildingId");
        Long floorId    = getLong(body, "floorId");
        if (extId == null) return badRequest("extinguisherId가 필요합니다.");

        Extinguisher ext = extinguisherRepository.findById(extId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("소화기를 찾을 수 없습니다."));

        // 점검 이력 삭제 (CascadeType.REMOVE로 자동 삭제됨)
        extinguisherRepository.delete(ext);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("extinguisherId", extId);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    // ─────────────────────────────────────────────
    // 소화기: 점검
    // ─────────────────────────────────────────────

    /**
     * POST /fire-api/floor/ext/inspect
     */
    @PostMapping("/ext/inspect")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> inspectExt(
            @RequestBody Map<String, Object> body) {

        Long extId = getLong(body, "extinguisherId");
        if (extId == null) return badRequest2("extinguisherId가 필요합니다.");

        Extinguisher ext = extinguisherRepository.findById(extId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("소화기를 찾을 수 없습니다."));

        boolean isFaulty = Boolean.TRUE.equals(body.get("isFaulty"));
        String faultReason = getString(body, "faultReason");
        String inspectorName = getString(body, "inspectorName");
        if (inspectorName == null || inspectorName.isBlank()) inspectorName = "점검자";

        ExtinguisherInspection inspection = ExtinguisherInspection.builder()
                .extinguisher(ext)
                .inspectionDate(LocalDate.now())
                .isFaulty(isFaulty)
                .faultReason(isFaulty ? faultReason : null)
                .inspectedByName(inspectorName)
                .build();

        extInspectionRepository.save(inspection);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // ─────────────────────────────────────────────
    // 소화전: 좌표 저장
    // ─────────────────────────────────────────────

    /**
     * POST /fire-api/floor/hyd/save
     */
    @PostMapping("/hyd/save")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveHydCoord(
            @RequestBody Map<String, Object> body) {

        Long hydId = getLong(body, "hydrantId");
        BigDecimal x = bd2(getBigDecimal(body, "x"));
        BigDecimal y = bd2(getBigDecimal(body, "y"));

        if (hydId == null) return badRequest("hydrantId가 필요합니다.");

        FireHydrant h = fireHydrantRepository.findById(hydId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("소화전을 찾을 수 없습니다."));

        updateHydrantCoords(h, x, y);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("hydrantId", hydId);
        res.put("x", x);
        res.put("y", y);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    // ─────────────────────────────────────────────
    // 소화전: 좌표 삭제
    // ─────────────────────────────────────────────

    /**
     * POST /fire-api/floor/hyd/clear
     */
    @PostMapping("/hyd/clear")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> clearHydCoord(
            @RequestBody Map<String, Object> body) {

        Long hydId = getLong(body, "hydrantId");
        if (hydId == null) return badRequest("hydrantId가 필요합니다.");

        FireHydrant h = fireHydrantRepository.findById(hydId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("소화전을 찾을 수 없습니다."));

        updateHydrantCoords(h, null, null);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("hydrantId", hydId);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    // ─────────────────────────────────────────────
    // 소화전: 추가 (도면 위치에 Indoor 소화전 생성)
    // ─────────────────────────────────────────────

    /**
     * POST /fire-api/floor/hyd/create
     */
    @PostMapping("/hyd/create")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> createHydrant(
            @RequestBody Map<String, Object> body) {

        Long buildingId = getLong(body, "buildingId");
        Long floorId    = getLong(body, "floorId");
        String opType   = getString(body, "operationType");
        BigDecimal x    = bd2(getBigDecimal(body, "x"));
        BigDecimal y    = bd2(getBigDecimal(body, "y"));

        if (buildingId == null) return badRequest("buildingId가 필요합니다.");
        if (floorId == null)    return badRequest("floorId가 필요합니다.");
        if (x == null || y == null) return badRequest("x/y 좌표가 필요합니다.");

        String op = (opType != null && (opType.equals("Auto") || opType.equals("Manual"))) ? opType : "Manual";

        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("건물을 찾을 수 없습니다."));
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("층을 찾을 수 없습니다."));

        String serial = generateNextHydrantSerial();

        FireHydrant hydrant = FireHydrant.builder()
                .serialNumber(serial)
                .hydrantType("Indoor")
                .operationType(op)
                .building(building)
                .floor(floor)
                .x(x)
                .y(y)
                .isActive(true)
                .build();

        fireHydrantRepository.save(hydrant);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("hydrantId", hydrant.getHydrantId());
        res.put("serialNumber", hydrant.getSerialNumber());
        res.put("x", x);
        res.put("y", y);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    // ─────────────────────────────────────────────
    // 소화전: 삭제
    // ─────────────────────────────────────────────

    /**
     * POST /fire-api/floor/hyd/delete
     */
    @PostMapping("/hyd/delete")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteHydrant(
            @RequestBody Map<String, Object> body) {

        Long hydId = getLong(body, "hydrantId");
        if (hydId == null) return badRequest("hydrantId가 필요합니다.");

        FireHydrant h = fireHydrantRepository.findById(hydId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("소화전을 찾을 수 없습니다."));

        fireHydrantRepository.delete(h);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("hydrantId", hydId);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    // ─────────────────────────────────────────────
    // 소화전: 점검
    // ─────────────────────────────────────────────

    /**
     * POST /fire-api/floor/hyd/inspect
     */
    @PostMapping("/hyd/inspect")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> inspectHydrant(
            @RequestBody Map<String, Object> body) {

        Long hydId = getLong(body, "hydrantId");
        if (hydId == null) return badRequest2("hydrantId가 필요합니다.");

        FireHydrant h = fireHydrantRepository.findById(hydId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException("소화전을 찾을 수 없습니다."));

        boolean isFaulty = Boolean.TRUE.equals(body.get("isFaulty"));
        String faultReason = getString(body, "faultReason");
        String inspectorName = getString(body, "inspectorName");
        if (inspectorName == null || inspectorName.isBlank()) inspectorName = "점검자";

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

    // ─────────────────────────────────────────────
    // private: 데이터 빌더
    // ─────────────────────────────────────────────

    private List<Map<String, Object>> buildExtItems(Long buildingId, Long floorId) {
        List<Extinguisher> exts = extinguisherRepository.findAll().stream()
                .filter(e -> e.getBuilding() != null && e.getBuilding().getBuildingId().equals(buildingId)
                          && e.getFloor() != null && e.getFloor().getFloorId().equals(floorId))
                .sorted(Comparator.comparingLong(Extinguisher::getExtinguisherId))
                .collect(Collectors.toList());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Extinguisher e : exts) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("extinguisherId", e.getExtinguisherId());
            m.put("serialNumber", e.getSerialNumber());
            m.put("extinguisherType", e.getExtinguisherType());
            m.put("installDate", e.getInstallDate() != null ? e.getInstallDate().toString() : "");
            m.put("quantity", e.getQuantity());
            m.put("note", e.getNote());
            m.put("groupId", e.getGroup() != null ? e.getGroup().getGroupId() : null);
            m.put("x", e.getX());
            m.put("y", e.getY());

            // 최종 점검 정보
            extInspectionRepository
                    .findTopByExtinguisher_ExtinguisherIdOrderByInspectionDateDescInspectionIdDesc(e.getExtinguisherId())
                    .ifPresent(ins -> {
                        m.put("lastInspectionDate", ins.getInspectionDate() != null ? ins.getInspectionDate().toString() : "");
                        m.put("lastInspectorName", ins.getInspectedByName());
                        m.put("lastIsFaulty", ins.isFaulty());
                        m.put("lastFaultReason", ins.getFaultReason());
                    });

            result.add(m);
        }
        return result;
    }

    private List<Map<String, Object>> buildHydItems(Long buildingId, Long floorId) {
        List<FireHydrant> hyds = fireHydrantRepository.findAll().stream()
                .filter(h -> h.isActive()
                          && "Indoor".equals(h.getHydrantType())
                          && h.getBuilding() != null && h.getBuilding().getBuildingId().equals(buildingId)
                          && h.getFloor() != null && h.getFloor().getFloorId().equals(floorId))
                .sorted(Comparator.comparingLong(FireHydrant::getHydrantId))
                .collect(Collectors.toList());

        List<Map<String, Object>> result = new ArrayList<>();
        for (FireHydrant h : hyds) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hydrantId", h.getHydrantId());
            m.put("serialNumber", h.getSerialNumber());
            m.put("hydrantType", h.getHydrantType());
            m.put("operationType", h.getOperationType());
            m.put("locationDescription", h.getLocationDescription());
            m.put("x", h.getX());
            m.put("y", h.getY());

            hydInspectionRepository
                    .findTopByHydrant_HydrantIdOrderByInspectionDateDescInspectionIdDesc(h.getHydrantId())
                    .ifPresent(ins -> {
                        m.put("lastInspectionDate", ins.getInspectionDate() != null ? ins.getInspectionDate().toString() : "");
                        m.put("lastInspectorName", ins.getInspectedByName());
                        m.put("lastIsFaulty", ins.isFaulty());
                        m.put("lastFaultReason", ins.getFaultReason());
                    });

            result.add(m);
        }
        return result;
    }

    private List<Map<String, Object>> buildJumpTargets() {
        // 소화기가 있는 건물/층 + 소화전이 있는 건물/층 조합으로 이동 목록 생성
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> targets = new ArrayList<>();

        // 소화기 건물/층
        extinguisherRepository.findAll().stream()
                .filter(e -> e.getBuilding() != null && e.getFloor() != null)
                .forEach(e -> {
                    String key = e.getBuilding().getBuildingId() + "|" + e.getFloor().getFloorId();
                    if (seen.add(key)) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("buildingId", e.getBuilding().getBuildingId());
                        m.put("floorId", e.getFloor().getFloorId());
                        m.put("buildingName", e.getBuilding().getBuildingName());
                        m.put("floorName", e.getFloor().getFloorName());
                        m.put("label", e.getBuilding().getBuildingName() + " " + e.getFloor().getFloorName());
                        targets.add(m);
                    }
                });

        // 소화전 건물/층 추가
        fireHydrantRepository.findAll().stream()
                .filter(h -> h.isActive() && "Indoor".equals(h.getHydrantType())
                          && h.getBuilding() != null && h.getFloor() != null)
                .forEach(h -> {
                    String key = h.getBuilding().getBuildingId() + "|" + h.getFloor().getFloorId();
                    if (seen.add(key)) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("buildingId", h.getBuilding().getBuildingId());
                        m.put("floorId", h.getFloor().getFloorId());
                        m.put("buildingName", h.getBuilding().getBuildingName());
                        m.put("floorName", h.getFloor().getFloorName());
                        m.put("label", h.getBuilding().getBuildingName() + " " + h.getFloor().getFloorName());
                        targets.add(m);
                    }
                });

        targets.sort(Comparator
                .comparing((Map<String, Object> m) -> (String) m.get("buildingName"))
                .thenComparing(m -> (String) m.get("floorName")));

        return targets;
    }

    // ─────────────────────────────────────────────
    // private: 유틸
    // ─────────────────────────────────────────────

    private String resolvePlanImagePath(String buildingName, String floorName) {
        String b = buildingName == null ? "" : buildingName.trim();
        String f = floorName == null ? "" : floorName.trim();

        if (b.contains("복지관")) {
            if (f.contains("지하") || f.contains("B1")) return "/images/bokji_B1.PNG";
            if (f.contains("2")) return "/images/bokji_2F.PNG";
            if (f.contains("3")) return "/images/bokji_3F.PNG";
            if (f.contains("1")) return "/images/bokji_1F.PNG";
        }
        if (b.contains("관리")) {
            if (f.contains("1")) return "/images/gwanri_1F.PNG";
        }
        return "/images/bokji_1F.PNG";
    }

    private String generateNextExtSerial() {
        List<String> serials = extinguisherRepository.findAll().stream()
                .map(Extinguisher::getSerialNumber)
                .filter(s -> s != null && s.startsWith("EXT-"))
                .collect(Collectors.toList());
        int max = serials.stream()
                .mapToInt(s -> {
                    try { return Integer.parseInt(s.substring(4)); } catch (Exception e) { return 0; }
                }).max().orElse(0);
        return String.format("EXT-%06d", max + 1);
    }

    private String generateNextHydrantSerial() {
        List<String> serials = fireHydrantRepository.findAllSerialNumbers();
        int max = serials.stream()
                .mapToInt(s -> {
                    try { return Integer.parseInt(s.substring(4)); } catch (Exception e) { return 0; }
                }).max().orElse(0);
        return String.format("HYD-%06d", max + 1);
    }

    /** FireHydrant 좌표 업데이트 */
    private void updateHydrantCoords(FireHydrant h, BigDecimal x, BigDecimal y) {
        h.updateCoordinates(x, y);
    }

    private BigDecimal bd2(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private String getString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString().trim();
    }

    private Long getLong(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private int getInt(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return def; }
    }

    private BigDecimal getBigDecimal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return null; }
    }

    private <T> ResponseEntity<ApiResponse<T>> badRequest(String msg) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(msg));
    }

    private ResponseEntity<ApiResponse<Void>> badRequest2(String msg) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(msg));
    }
}
