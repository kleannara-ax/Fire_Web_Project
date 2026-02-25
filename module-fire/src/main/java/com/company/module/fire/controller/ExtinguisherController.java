package com.company.module.fire.controller;

import com.company.core.common.ApiResponse;
import com.company.module.fire.dto.*;
import com.company.module.fire.service.ExtinguisherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * 소화기 관리 API Controller
 * <p>
 * 기존 ASP.NET: Pages/Extinguishers/* 대응
 * URL Prefix: /fire-api/extinguishers/**
 */
@RestController
@RequestMapping("/fire-api/extinguishers")
@RequiredArgsConstructor
public class ExtinguisherController {

    private final ExtinguisherService extinguisherService;

    /**
     * GET /fire-api/extinguishers
     * 소화기 목록 조회 (페이지네이션, 검색, 필터)
     * <p>
     * 기존 ASP.NET: GET /Extinguishers
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ExtinguisherResponse>>> getList(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<ExtinguisherResponse> result = extinguisherService.getExtinguishers(
                buildingId, floorId, q, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * GET /fire-api/extinguishers/{id}
     * 소화기 상세 조회 (점검 이력 포함)
     * <p>
     * 기존 ASP.NET: GET /Extinguishers/details?id={id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExtinguisherResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(extinguisherService.getExtinguisherDetail(id)));
    }

    /**
     * POST /fire-api/extinguishers
     * 소화기 등록/수정 (Admin 전용)
     * <p>
     * 기존 ASP.NET: POST /Extinguishers?handler=ExtSave
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ExtinguisherResponse>> save(
            @Valid @RequestBody ExtinguisherSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(extinguisherService.saveExtinguisher(request)));
    }

    /**
     * POST /fire-api/extinguishers/inspect
     * 소화기 점검 등록
     * <p>
     * 기존 ASP.NET: POST /Extinguishers?handler=Inspect
     */
    @PostMapping("/inspect")
    public ResponseEntity<ApiResponse<Void>> inspect(
            @Valid @RequestBody ExtinguisherInspectRequest request,
            Principal principal) {
        // TODO: Principal에서 userId/displayName 조회 (UserService 연동)
        extinguisherService.inspect(request, null, principal.getName());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * DELETE /fire-api/extinguishers/{id}
     * 소화기 삭제 (Admin 전용)
     * <p>
     * 기존 ASP.NET: POST /Extinguishers/Delete?handler=Delete
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        extinguisherService.deleteExtinguisher(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
