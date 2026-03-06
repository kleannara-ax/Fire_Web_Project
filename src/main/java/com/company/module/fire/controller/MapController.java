package com.company.module.fire.controller;

import com.company.core.common.ApiResponse;
import com.company.module.fire.entity.Building;
import com.company.module.fire.entity.Extinguisher;
import com.company.module.fire.entity.FireHydrant;
import com.company.module.fire.entity.Floor;
import com.company.module.fire.repository.BuildingRepository;
import com.company.module.fire.repository.ExtinguisherRepository;
import com.company.module.fire.repository.FireHydrantRepository;
import com.company.module.fire.repository.FloorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/fire-api/maps")
@RequiredArgsConstructor
public class MapController {

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final ExtinguisherRepository extinguisherRepository;
    private final FireHydrantRepository fireHydrantRepository;

    @GetMapping("/floor-data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFloorData(
            @RequestParam Long buildingId,
            @RequestParam Long floorId) {

        if (buildingId == null || buildingId <= 0 || floorId == null || floorId <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Invalid buildingId/floorId"));
        }

        Building building = buildingRepository.findById(buildingId).orElse(null);
        Floor floor = floorRepository.findById(floorId).orElse(null);
        if (building == null || floor == null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Building/Floor not found"));
        }

        String buildingName = building.getBuildingName() == null ? "" : building.getBuildingName();
        String floorName = floor.getFloorName() == null ? "" : floor.getFloorName();

        String planImagePath = resolvePlanImagePath(buildingName, floorName);

        List<Map<String, Object>> extinguishers = new ArrayList<>();
        for (Extinguisher e : extinguisherRepository.findForMap(buildingId, floorId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("extinguisherId", e.getExtinguisherId());
            row.put("x", e.getX());
            row.put("y", e.getY());
            extinguishers.add(row);
        }

        List<Map<String, Object>> hydrants = new ArrayList<>();
        for (FireHydrant h : fireHydrantRepository.findForMap("Indoor", buildingId, floorId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("hydrantId", h.getHydrantId());
            row.put("hydrantType", h.getHydrantType());
            row.put("x", h.getX());
            row.put("y", h.getY());
            hydrants.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buildingId", buildingId);
        result.put("buildingName", buildingName);
        result.put("floorId", floorId);
        result.put("floorName", floorName);
        result.put("planImagePath", planImagePath);
        result.put("extinguishers", extinguishers);
        result.put("hydrants", hydrants);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private String resolvePlanImagePath(String buildingName, String floorName) {
        String b = buildingName == null ? "" : buildingName.trim().toLowerCase();
        String f = floorName == null ? "" : floorName.trim().toLowerCase();
        String bn = b.replaceAll("[\\s,._-]", "");

        if (b.contains("복지관") || b.contains("bokji")) {
            if (f.contains("지하") || f.contains("b1")) return "/images/bokji_B1.png";
            if (f.contains("2")) return "/images/bokji_2F.png";
            if (f.contains("1")) return "/images/bokji_1F.png";
            if (f.contains("3")) return "/images/bokji_3F.png";
        }
        if (b.contains("관리동") || b.contains("gwanri")) {
            if (f.contains("2")) return "/images/gwanri_2F.PNG";
            if (f.contains("1")) return "/images/gwanri_1F.png";
        }
        if (b.contains("옥외") || b.contains("outdoor")) {
            return "/images/drone_photo.JPG";
        }
        if (bn.contains("제지12호기")
                || bn.contains("jeji12")
                || bn.contains("제지12")
                || bn.contains("제지2호기")
                || bn.contains("jeji2")
                || (bn.contains("제지1호기") && bn.contains("2호기"))) {
            if (f.contains("2")) return "/images/jeji1,2_2F.PNG";
            return "/images/jeji1,2_1F.PNG";
        }
        if (bn.contains("제지3호기") || bn.contains("jeji3")) {
            if (f.contains("2")) return "/images/jeji3_2F.PNG";
            return "/images/jeji3_1F.PNG";
        }
        if (bn.contains("심면펄퍼")
                || bn.contains("심면펄프")
                || (bn.contains("심면") && (bn.contains("펄퍼") || bn.contains("펄프")))
                || bn.contains("palpa")
                || bn.contains("pulper")) {
            if (f.contains("2")) return "/images/palpa_2F.PNG";
            return "/images/palpa_1F.PNG";
        }
        if (bn.contains("패드동") || bn.contains("pad")) {
            if (f.contains("2")) return "/images/pad_2F.PNG";
            return "/images/pad_1F.PNG";
        }
        if (bn.contains("화장지36호기") || bn.contains("tissue36")) {
            if (f.contains("2")) return "/images/tissue1,3_2F.PNG";
            return "/images/tissue1,3_1F.PNG";
        }
        if (bn.contains("화장지45호기") || bn.contains("tissue45")) {
            if (f.contains("지하") || f.contains("b1")) return "/images/tissue4,5_B1.PNG";
            if (f.contains("3")) return "/images/tissue4,5_3F.PNG";
            if (f.contains("2")) return "/images/tissue4,5_2F.PNG";
            return "/images/tissue4,5_1F.PNG";
        }
        if (bn.contains("기저귀동")
                || bn.contains("기저귀")
                || bn.contains("diaper")) {
            return "/images/diaper_1F.png";
        }
        return "";
    }
}