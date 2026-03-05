package com.company.module.fire.controller;

import com.company.core.common.ApiResponse;
import com.company.module.fire.entity.*;
import com.company.module.fire.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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
    private static final String MSG_ERROR = "\uC694\uCCAD \uCC98\uB9AC \uC911 \uC624\uB958\uAC00 \uBC1C\uC0DD\uD588\uC2B5\uB2C8\uB2E4.";
    private static final String MSG_NOT_FOUND = "\uC694\uCCAD\uD55C \uB370\uC774\uD130\uB97C \uCC3E\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.";
    private static final String MSG_EMPTY_SERIAL = "QR \uCF54\uB4DC \uC77C\uB828\uBC88\uD638\uAC00 \uBE44\uC5B4 \uC788\uC2B5\uB2C8\uB2E4.";

    @GetMapping("/extinguishers/by-serial")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtBySerial(
            @RequestParam String serial) {

        Optional<Extinguisher> opt = extinguisherRepository.findBySerialNumber(serial.trim());

        Map<String, Object> result = new LinkedHashMap<>();
        if (opt.isEmpty()) {
            result.put("exists", false);
            result.put("serialNumber", serial.trim());
            result.put("buildings", getBuildingList());
            result.put("floors", getFloorList());
            result.put("mapOptions", getMappableBuildingFloorList());
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

    @GetMapping("/extinguishers/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtById(@PathVariable Long id) {
        Extinguisher e = extinguisherRepository.findById(id)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException(MSG_NOT_FOUND));

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

    @PostMapping("/extinguishers/register")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerExtinguisher(
            @RequestBody Map<String, Object> body) {

        String serial = getString(body, "serialNumber");
        if (serial == null || serial.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_EMPTY_SERIAL));
        }

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
        String inspectionDateStr = getString(body, "inspectionDate");
        String inspectorName = getString(body, "inspectorName");
        BigDecimal x = getBigDecimal(body, "x");
        BigDecimal y = getBigDecimal(body, "y");

        if (buildingId == null || buildingId <= 0)
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        if (floorId == null || floorId <= 0)
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        if (type == null || type.isBlank())
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));

        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException(MSG_NOT_FOUND));
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException(MSG_NOT_FOUND));

        String strictPlanPath = resolvePlanImagePathStrict(building.getBuildingName(), floor.getFloorName());
        if (strictPlanPath == null || strictPlanPath.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        }

        LocalDate installDate = (dateStr != null && !dateStr.isBlank())
                ? LocalDate.parse(dateStr) : LocalDate.now();
        LocalDate inspectionDate = null;
        if (inspectionDateStr != null && !inspectionDateStr.isBlank()) {
            try {
                inspectionDate = LocalDate.parse(inspectionDateStr);
            } catch (Exception ex) {
                return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
            }
        }

        int replacementYears = 10;

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
        if (inspectionDate != null) {
            String inspectedBy = (inspectorName == null || inspectorName.isBlank()) ? resolveInspectorName() : inspectorName.trim();
            ExtinguisherInspection inspection = ExtinguisherInspection.builder()
                    .extinguisher(entity)
                    .inspectionDate(inspectionDate)
                    .isFaulty(false)
                    .faultReason(null)
                    .inspectedByName(inspectedBy)
                    .build();
            extInspectionRepository.save(inspection);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("extinguisherId", entity.getExtinguisherId());
        res.put("alreadyExists", false);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    @PostMapping("/extinguishers/{id}/inspect")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> inspectExtinguisher(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        Extinguisher e = extinguisherRepository.findById(id)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException(MSG_NOT_FOUND));

        boolean isFaulty = Boolean.TRUE.equals(body.get("isFaulty"));
        String faultReason = getString(body, "faultReason");
        String inspectorName = getString(body, "inspectorName");
        if (inspectorName == null || inspectorName.isBlank()) inspectorName = resolveInspectorName();

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

    @PostMapping("/extinguishers/{id}/image")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadExtinguisherImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        }
        String ct = file.getContentType();
        if (ct == null || !ct.toLowerCase().startsWith("image/")) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        }

        Extinguisher e = extinguisherRepository.findById(id)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException(MSG_NOT_FOUND));

        try {
            Path dir = Paths.get("./uploads/extinguishers");
            Files.createDirectories(dir);

            String original = file.getOriginalFilename();
            String ext = "jpg";
            if (original != null) {
                int idx = original.lastIndexOf('.');
                if (idx > -1 && idx < original.length() - 1) {
                    ext = original.substring(idx + 1).replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                    if (ext.isBlank()) ext = "jpg";
                }
            }
            String filename = "mobile_ext_" + id + "_" + System.currentTimeMillis() + "." + ext;
            Path target = dir.resolve(filename).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String publicPath = "/fire-api/minspection/files/extinguishers/" + filename;
            e.updateImagePath(publicPath);
            extinguisherRepository.save(e);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("imagePath", publicPath);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.fail(MSG_ERROR));
        }
    }

    @GetMapping("/files/extinguishers/{filename:.+}")
    public ResponseEntity<Resource> getExtinguisherImageFile(@PathVariable String filename) {
        try {
            String clean = filename == null ? "" : filename.replace("\\", "/");
            if (clean.contains("..") || clean.contains("/")) {
                return ResponseEntity.badRequest().build();
            }
            Path base = Paths.get("./uploads/extinguishers").toAbsolutePath().normalize();
            Path file = base.resolve(clean).normalize();
            if (!file.startsWith(base) || !Files.exists(file)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new UrlResource(file.toUri());
            MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .contentType(mediaType)
                    .body(resource);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/extinguishers/mapdata")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtMapData(
            @RequestParam Long buildingId,
            @RequestParam Long floorId) {

        if (buildingId == null || buildingId <= 0 || floorId == null || floorId <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
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
            result.put("mapOptions", getMappableBuildingFloorList());
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

    @GetMapping("/hydrants/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHydById(@PathVariable Long id) {
        FireHydrant h = fireHydrantRepository.findById(id)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException(MSG_NOT_FOUND));

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

    @PostMapping("/hydrants/register")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerHydrant(
            @RequestBody Map<String, Object> body) {

        String serial = getString(body, "serialNumber");
        if (serial == null || serial.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_EMPTY_SERIAL));
        }

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
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        if (operationType == null || (!operationType.equals("Manual") && !operationType.equals("Auto")))
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));

        Long buildingId;
        Long floorId;

        if ("Outdoor".equals(hydrantType)) {
            buildingId = 99L;
            floorId = 1L;
        } else {
            buildingId = getLong(body, "buildingId");
            floorId = getLong(body, "floorId");
            if (buildingId == null || buildingId <= 0)
                return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
            if (floorId == null || floorId <= 0)
                return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        }

        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException(MSG_NOT_FOUND));
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException(MSG_NOT_FOUND));

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

    @PostMapping("/hydrants/{id}/inspect")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> inspectHydrant(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        FireHydrant h = fireHydrantRepository.findById(id)
                .orElseThrow(() -> new com.company.core.exception.ResourceNotFoundException(MSG_NOT_FOUND));

        boolean isFaulty = Boolean.TRUE.equals(body.get("isFaulty"));
        String faultReason = getString(body, "faultReason");
        String inspectorName = getString(body, "inspectorName");
        if (inspectorName == null || inspectorName.isBlank()) inspectorName = resolveInspectorName();

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

    @GetMapping("/hydrants/mapdata")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHydMapData(
            @RequestParam Long buildingId,
            @RequestParam Long floorId) {

        if (buildingId == null || buildingId <= 0 || floorId == null || floorId <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        }

        String buildingName = buildingRepository.findById(buildingId)
                .map(Building::getBuildingName).orElse("");
        String floorName = floorRepository.findById(floorId)
                .map(Floor::getFloorName).orElse("");

        String planImagePath = resolvePlanImagePath(buildingName, floorName);

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

    private List<Map<String, Object>> getMappableBuildingFloorList() {
        List<Building> buildings = new ArrayList<>(buildingRepository.findAll());
        buildings.sort(Comparator.comparing(b -> String.valueOf(b.getBuildingName())));
        List<Floor> floors = floorRepository.findAllByOrderBySortOrderAsc();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Building b : buildings) {
            String bName = b.getBuildingName();
            String bn = bName == null ? "" : bName.toLowerCase();
            if (bn.contains("\uC625\uC678") || bn.contains("outdoor")) continue;
            for (Floor f : floors) {
                String plan = resolvePlanImagePathStrict(bName, f.getFloorName());
                if (plan == null || plan.isBlank()) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("buildingId", b.getBuildingId());
                m.put("buildingName", b.getBuildingName());
                m.put("floorId", f.getFloorId());
                m.put("floorName", f.getFloorName());
                m.put("planImagePath", plan);
                result.add(m);
            }
        }
        return result;
    }

    private String resolvePlanImagePathStrict(String buildingName, String floorName) {
        String b = buildingName == null ? "" : buildingName.trim().toLowerCase();
        String f = floorName == null ? "" : floorName.trim().toLowerCase();
        String bn = b.replaceAll("[\\s,._-]", "");
        boolean b1 = isBasementFloor(f);
        int floorNo = parseFloorNumber(f);

        if (b.contains("\uBCF5\uC9C0\uAD00") || b.contains("bokji")) {
            if (b1) return "/images/bokji_B1.png";
            if (floorNo == 1) return "/images/bokji_1F.png";
            if (floorNo == 2) return "/images/bokji_2F.png";
            if (floorNo == 3) return "/images/bokji_3F.png";
            return null;
        }
        if (b.contains("\uAD00\uB9AC\uB3D9") || b.contains("gwanri")) {
            if (floorNo == 2) return "/images/gwanri_2F.PNG";
            if (floorNo == 1) return "/images/gwanri_1F.png";
            return null;
        }
        if (bn.contains("\uC81C\uC9C012\uD638\uAE30")
                || bn.contains("jeji12")
                || bn.contains("\uC81C\uC9C012")
                || bn.contains("\uC81C\uC9C02\uD638\uAE30")
                || bn.contains("jeji2")
                || (bn.contains("\uC81C\uC9C01\uD638\uAE30") && bn.contains("2\uD638\uAE30"))) {
            if (floorNo == 1) return "/images/jeji1,2_1F.PNG";
            if (floorNo == 2) return "/images/jeji1,2_2F.PNG";
            return null;
        }
        if (bn.contains("\uC81C\uC9C03\uD638\uAE30") || bn.contains("jeji3")) {
            if (floorNo == 1) return "/images/jeji3_1F.PNG";
            if (floorNo == 2) return "/images/jeji3_2F.PNG";
            return null;
        }
        if (bn.contains("\uC2EC\uBA74\uD384\uD37C")
                || bn.contains("\uC2EC\uBA74\uD384\uD504")
                || (bn.contains("\uC2EC\uBA74") && (bn.contains("\uD384\uD37C") || bn.contains("\uD384\uD504")))
                || bn.contains("palpa")
                || bn.contains("pulper")) {
            if (floorNo == 1) return "/images/palpa_1F.PNG";
            if (floorNo == 2) return "/images/palpa_2F.PNG";
            return null;
        }
        if (bn.contains("\uD328\uB4DC\uB3D9") || bn.contains("pad")) {
            if (floorNo == 1) return "/images/pad_1F.PNG";
            if (floorNo == 2) return "/images/pad_2F.PNG";
            return null;
        }
        if (bn.contains("\uD654\uC7A5\uC9C036\uD638\uAE30")
                || bn.contains("tissue36")) {
            if (floorNo == 1) return "/images/tissue1,3_1F.PNG";
            if (floorNo == 2) return "/images/tissue1,3_2F.PNG";
            return null;
        }
        if (bn.contains("\uD654\uC7A5\uC9C045\uD638\uAE30") || bn.contains("tissue45")) {
            if (b1) return "/images/tissue4,5_B1.PNG";
            if (floorNo == 1) return "/images/tissue4,5_1F.PNG";
            if (floorNo == 2) return "/images/tissue4,5_2F.PNG";
            if (floorNo == 3) return "/images/tissue4,5_3F.PNG";
            return null;
        }
        if (bn.contains("\uAE30\uC800\uADC0\uB3D9")
                || bn.contains("\uAE30\uC800\uADC0")
                || bn.contains("diaper")) {
            if (floorNo == 1) return "/images/diaper_1F.png";
            return null;
        }
        return null;
    }

    private boolean isBasementFloor(String floorName) {
        String f = floorName == null ? "" : floorName.toLowerCase().replaceAll("\\s+", "");
        return f.contains("\uC9C0\uD558") || f.contains("b1");
    }

    private int parseFloorNumber(String floorName) {
        if (isBasementFloor(floorName)) return -1;
        String f = floorName == null ? "" : floorName.toLowerCase().replaceAll("\\s+", "");
        if (f.contains("1\uCE35") || f.equals("1") || f.equals("1f") || f.equals("f1")) return 1;
        if (f.contains("2\uCE35") || f.equals("2") || f.equals("2f") || f.equals("f2")) return 2;
        if (f.contains("3\uCE35") || f.equals("3") || f.equals("3f") || f.equals("f3")) return 3;
        return -1;
    }

    private String resolvePlanImagePath(String buildingName, String floorName) {
        String b = buildingName == null ? "" : buildingName.trim().toLowerCase();
        String f = floorName == null ? "" : floorName.trim().toLowerCase();
        String bn = b.replaceAll("[\\s,._-]", "");

        if (b.contains("\uBCF5\uC9C0\uAD00") || b.contains("bokji")) {
            if (f.contains("\uC9C0\uD558") || f.contains("b1")) return "/images/bokji_B1.png";
            if (f.contains("2")) return "/images/bokji_2F.png";
            if (f.contains("1")) return "/images/bokji_1F.png";
            if (f.contains("3")) return "/images/bokji_3F.png";
        }
        if (b.contains("\uAD00\uB9AC\uB3D9") || b.contains("gwanri")) {
            if (f.contains("2")) return "/images/gwanri_2F.PNG";
            if (f.contains("1")) return "/images/gwanri_1F.png";
        }
        if (b.contains("\uC625\uC678") || b.contains("outdoor")) {
            return "/images/drone_photo.JPG";
        }
        if (bn.contains("\uC81C\uC9C012\uD638\uAE30")
                || bn.contains("jeji12")
                || bn.contains("\uC81C\uC9C012")
                || bn.contains("\uC81C\uC9C02\uD638\uAE30")
                || bn.contains("jeji2")
                || (bn.contains("\uC81C\uC9C01\uD638\uAE30") && bn.contains("2\uD638\uAE30"))) {
            if (f.contains("2")) return "/images/jeji1,2_2F.PNG";
            return "/images/jeji1,2_1F.PNG";
        }
        if (bn.contains("\uC81C\uC9C03\uD638\uAE30") || bn.contains("jeji3")) {
            if (f.contains("2")) return "/images/jeji3_2F.PNG";
            return "/images/jeji3_1F.PNG";
        }
        if (bn.contains("\uC2EC\uBA74\uD384\uD37C")
                || bn.contains("\uC2EC\uBA74\uD384\uD504")
                || (bn.contains("\uC2EC\uBA74") && (bn.contains("\uD384\uD37C") || bn.contains("\uD384\uD504")))
                || bn.contains("palpa")
                || bn.contains("pulper")) {
            if (f.contains("2")) return "/images/palpa_2F.PNG";
            return "/images/palpa_1F.PNG";
        }
        if (bn.contains("\uD328\uB4DC\uB3D9") || bn.contains("pad")) {
            if (f.contains("2")) return "/images/pad_2F.PNG";
            return "/images/pad_1F.PNG";
        }
        if (bn.contains("\uD654\uC7A5\uC9C036\uD638\uAE30") || bn.contains("tissue36")) {
            if (f.contains("2")) return "/images/tissue1,3_2F.PNG";
            return "/images/tissue1,3_1F.PNG";
        }
        if (bn.contains("\uD654\uC7A5\uC9C045\uD638\uAE30") || bn.contains("tissue45")) {
            if (f.contains("\uC9C0\uD558") || f.contains("b1")) return "/images/tissue4,5_B1.PNG";
            if (f.contains("3")) return "/images/tissue4,5_3F.PNG";
            if (f.contains("2")) return "/images/tissue4,5_2F.PNG";
            return "/images/tissue4,5_1F.PNG";
        }
        if (bn.contains("\uAE30\uC800\uADC0\uB3D9")
                || bn.contains("\uAE30\uC800\uADC0")
                || bn.contains("diaper")) {
            return "/images/diaper_1F.png";
        }
        return "/images/bokji_1F.png";
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

    private String resolveInspectorName() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String name = auth.getName();
                if (name != null && !name.isBlank() && !"anonymousUser".equalsIgnoreCase(name)) {
                    return name;
                }
            }
        } catch (Exception ignored) {
        }
        return "\uBAA8\uBC14\uC77CQR";
    }
}
