package com.company.module.fire.repository;

import com.company.module.fire.entity.FireHydrant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 소화전 Repository
 */
public interface FireHydrantRepository extends JpaRepository<FireHydrant, Long> {

    Optional<FireHydrant> findBySerialNumber(String serialNumber);

    @Query("SELECT h FROM FireHydrant h " +
           "LEFT JOIN FETCH h.building b " +
           "LEFT JOIN FETCH h.floor f " +
           "WHERE h.isActive = true " +
           "AND (:buildingId IS NULL OR b.buildingId = :buildingId) " +
           "AND (:floorId IS NULL OR f.floorId = :floorId) " +
           "AND (:keyword IS NULL OR " +
           "     LOWER(h.serialNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(b.buildingName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(f.floorName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(h.hydrantType) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(h.locationDescription) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<FireHydrant> searchHydrants(
            @Param("buildingId") Long buildingId,
            @Param("floorId") Long floorId,
            @Param("keyword") String keyword,
            Pageable pageable);

    /** 도면용: 특정 건물/층의 활성 소화전 좌표 조회 */
    @Query("SELECT h FROM FireHydrant h " +
           "WHERE h.isActive = true " +
           "AND h.x IS NOT NULL AND h.y IS NOT NULL " +
           "AND h.hydrantType = :hydrantType " +
           "AND h.building.buildingId = :buildingId " +
           "AND h.floor.floorId = :floorId")
    List<FireHydrant> findForMap(
            @Param("hydrantType") String hydrantType,
            @Param("buildingId") Long buildingId,
            @Param("floorId") Long floorId);

    /** 다음 일련번호 계산용 */
    @Query("SELECT h.serialNumber FROM FireHydrant h WHERE h.serialNumber LIKE 'HYD-%'")
    List<String> findAllSerialNumbers();
}
