package com.company.module.fire.controller;

import com.company.core.common.ApiResponse;
import com.company.module.fire.dto.QrExtItem;
import com.company.module.fire.dto.QrHydItem;
import com.company.module.fire.entity.Building;
import com.company.module.fire.entity.Extinguisher;
import com.company.module.fire.entity.FireHydrant;
import com.company.module.fire.entity.Floor;
import com.company.module.fire.repository.BuildingRepository;
import com.company.module.fire.repository.ExtinguisherRepository;
import com.company.module.fire.repository.FireHydrantRepository;
import com.company.module.fire.repository.FloorRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * QR 코드 관련 API
 * <p>
 * 기존 ASP.NET: Pages/Qr/Index 대응
 * - GET /fire-api/qr/image?type=ext&id=EXT-000001  → QR PNG 이미지 반환 (인증 불필요)
 * - GET /fire-api/qr/list                           → 소화기/소화전 목록 (인증 필요)
 * - GET /fire-api/qr/buildings                      → 건물 목록 (인증 필요)
 * - GET /fire-api/qr/floors                         → 층 목록 (인증 필요)
 * - GET /fire-api/qr/unregistered-serials           → 미등록 시리얼 생성 (인증 필요)
 */
@RestController
@RequestMapping("/fire-api/qr")
@RequiredArgsConstructor
public class QrController {

    private final ExtinguisherRepository extinguisherRepository;
    private final FireHydrantRepository fireHydrantRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;

    /**
     * QR 이미지 생성 (PNG)
     * GET /fire-api/qr/image?type=ext&id=EXT-000001
     * 인증 없이 접근 가능 (SecurityConfig에서 permitAll 설정 필요)
     */
    @GetMapping("/image")
    public ResponseEntity<byte[]> getQrImage(
            @RequestParam String type,
            @RequestParam String id,
            HttpServletRequest request) throws WriterException, IOException {

        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() != 80 && request.getServerPort() != 443
                        ? ":" + request.getServerPort() : "");

        String url;
        if ("hyd".equalsIgnoreCase(type)) {
            url = baseUrl + "/minspection/hydrants/" + id;
        } else {
            url = baseUrl + "/minspection/extinguishers/" + id;
        }

        byte[] png = generateQrPng(url, 240);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(png);
    }

    /**
     * 소화기 + 소화전 목록 조회
     * GET /fire-api/qr/list?buildingId=1&floorId=2
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getList(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId) {

        Long bId = (buildingId != null && buildingId > 0) ? buildingId : null;
        Long fId = (floorId != null && floorId > 0) ? floorId : null;

        List<Extinguisher> extList;
        List<FireHydrant> hydList;

        if (bId == null && fId == null) {
            extList = extinguisherRepository.findAll();
            hydList = fireHydrantRepository.findAll();
        } else if (bId != null && fId != null) {
            extList = extinguisherRepository.findByBuilding_BuildingIdAndFloor_FloorId(bId, fId);
            hydList = fireHydrantRepository.findByBuilding_BuildingIdAndFloor_FloorId(bId, fId);
        } else if (bId != null) {
            extList = extinguisherRepository.findByBuilding_BuildingId(bId);
            hydList = fireHydrantRepository.findByBuilding_BuildingId(bId);
        } else {
            extList = extinguisherRepository.findByFloor_FloorId(fId);
            hydList = fireHydrantRepository.findByFloor_FloorId(fId);
        }

        List<QrExtItem> extItems = extList.stream()
                .sorted(Comparator.comparingLong(Extinguisher::getExtinguisherId).reversed())
                .map(e -> new QrExtItem(
                        e.getExtinguisherId(),
                        e.getSerialNumber(),
                        e.getBuilding() != null ? e.getBuilding().getBuildingName() : "-",
                        e.getFloor() != null ? e.getFloor().getFloorName() : "-",
                        e.getExtinguisherType(),
                        e.getInstallDate() != null ? e.getInstallDate().toString() : "-"))
                .collect(Collectors.toList());

        List<QrHydItem> hydItems = hydList.stream()
                .sorted(Comparator.comparingLong(FireHydrant::getHydrantId).reversed())
                .map(h -> new QrHydItem(
                        h.getHydrantId(),
                        h.getSerialNumber(),
                        h.getBuilding() != null ? h.getBuilding().getBuildingName() : "-",
                        h.getFloor() != null ? h.getFloor().getFloorName() : "-",
                        h.getHydrantType(),
                        h.getOperationType(),
                        h.getLocationDescription()))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("extinguishers", extItems);
        result.put("hydrants", hydItems);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 건물 목록
     * GET /fire-api/qr/buildings
     */
    @GetMapping("/buildings")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBuildings() {
        List<Building> buildings = buildingRepository.findAllByOrderByBuildingNameAsc();
        List<Map<String, Object>> list = buildings.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("buildingId", b.getBuildingId());
            m.put("buildingName", b.getBuildingName());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    /**
     * 층 목록
     * GET /fire-api/qr/floors
     */
    @GetMapping("/floors")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFloors() {
        List<Floor> floors = floorRepository.findAllByOrderBySortOrderAscFloorNameAsc();
        List<Map<String, Object>> list = floors.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("floorId", f.getFloorId());
            m.put("floorName", f.getFloorName());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    /**
     * 미등록 시리얼 목록 생성
     * GET /fire-api/qr/unregistered-serials?extCount=5&hydCount=3
     */
    @GetMapping("/unregistered-serials")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUnregisteredSerials(
            @RequestParam(defaultValue = "0") int extCount,
            @RequestParam(defaultValue = "0") int hydCount) {

        extCount = Math.max(0, Math.min(extCount, 500));
        hydCount = Math.max(0, Math.min(hydCount, 500));

        List<String> extSerials = Collections.emptyList();
        List<String> hydSerials = Collections.emptyList();

        if (extCount > 0) {
            List<String> existing = extinguisherRepository.findAll().stream()
                    .map(Extinguisher::getSerialNumber).collect(Collectors.toList());
            extSerials = generateSerials(existing, "EXT-", extCount);
        }
        if (hydCount > 0) {
            List<String> existing = fireHydrantRepository.findAll().stream()
                    .map(FireHydrant::getSerialNumber).collect(Collectors.toList());
            hydSerials = generateSerials(existing, "HYD-", hydCount);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unregisteredExtSerials", extSerials);
        result.put("unregisteredHydSerials", hydSerials);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ===== Helper Methods =====

    private List<String> generateSerials(List<String> existing, String prefix, int count) {
        int max = 0;
        for (String s : existing) {
            if (s == null || !s.startsWith(prefix)) continue;
            try {
                int n = Integer.parseInt(s.substring(prefix.length()));
                if (n > max) max = n;
            } catch (NumberFormatException ignored) {}
        }
        List<String> result = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            result.add(String.format("%s%06d", prefix, max + i));
        }
        return result;
    }

    private byte[] generateQrPng(String payload, int size) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q);
        hints.put(EncodeHintType.MARGIN, 1);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(payload, BarcodeFormat.QR_CODE, size, size, hints);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        return out.toByteArray();
    }
}
