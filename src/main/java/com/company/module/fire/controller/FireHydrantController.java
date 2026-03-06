package com.company.module.fire.controller;

import com.company.core.common.ApiResponse;
import com.company.module.fire.dto.*;
import com.company.module.fire.service.FireHydrantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * 소화전 관리 API Controller
 * <p>
 * 기존 ASP.NET: Pages/FireHydrants/* 대응
 * URL Prefix: /fire-api/hydrants/**
 */
@RestController
@RequestMapping("/fire-api/hydrants")
@RequiredArgsConstructor
public class FireHydrantController {

    private final FireHydrantService fireHydrantService;

    /**
     * GET /fire-api/hydrants
     * 소화전 목록 조회
     * <p>
     * 기존 ASP.NET: GET /FireHydrants
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<FireHydrantResponse>>> getList(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<FireHydrantResponse> result = fireHydrantService.getHydrants(
                buildingId, floorId, q, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * GET /fire-api/hydrants/{id}
     * 소화전 상세 조회 (점검 이력 포함)
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FireHydrantResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(fireHydrantService.getHydrantDetail(id)));
    }

    /**
     * POST /fire-api/hydrants
     * 소화전 등록/수정 (Admin 전용)
     * <p>
     * 기존 ASP.NET: POST /FireHydrants?handler=HydSave
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FireHydrantResponse>> save(
            @Valid @RequestBody FireHydrantSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(fireHydrantService.saveHydrant(request)));
    }

    /**
     * POST /fire-api/hydrants/{id}/inspect
     * 소화전 점검 등록
     * <p>
     * 기존 ASP.NET: POST /FireHydrants?handler=Inspect
     */
    @PostMapping("/{id}/inspect")
    public ResponseEntity<ApiResponse<Void>> inspect(
            @PathVariable Long id,
            @RequestParam boolean isFaulty,
            @RequestParam(required = false) String faultReason,
            Principal principal) {
        fireHydrantService.inspect(id, isFaulty, faultReason, null, principal.getName());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/{id}/inspections/{inspectionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateInspection(
            @PathVariable("id") Long hydrantId,
            @PathVariable Long inspectionId,
            @Valid @RequestBody FireHydrantInspectionUpdateRequest request) {
        fireHydrantService.updateInspection(
                hydrantId,
                inspectionId,
                request.getInspectionDate(),
                Boolean.TRUE.equals(request.getIsFaulty()),
                request.getFaultReason(),
                request.getInspectorName());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{id}/inspections")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> addInspection(
            @PathVariable("id") Long hydrantId,
            @Valid @RequestBody FireHydrantInspectionUpdateRequest request,
            Principal principal) {
        fireHydrantService.addInspection(
                hydrantId,
                request.getInspectionDate(),
                Boolean.TRUE.equals(request.getIsFaulty()),
                request.getFaultReason(),
                request.getInspectorName() != null ? request.getInspectorName() : principal.getName(),
                null);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * DELETE /fire-api/hydrants/{id}
     * 소화전 삭제 (Admin 전용)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        fireHydrantService.deleteHydrant(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
