package com.company.module.fire.repository;

import com.company.module.fire.entity.ExtinguisherGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 소화기 그룹 Repository
 * <p>
 * 도면 위치 단위로 소화기를 묶는 그룹 관리
 */
public interface ExtinguisherGroupRepository extends JpaRepository<ExtinguisherGroup, Long> {

    /** 건물+층의 모든 그룹 조회 */
    List<ExtinguisherGroup> findByBuilding_BuildingIdAndFloor_FloorId(Long buildingId, Long floorId);

    /** 동일 좌표의 기존 그룹 찾기 */
    Optional<ExtinguisherGroup> findByBuilding_BuildingIdAndFloor_FloorIdAndXAndY(
            Long buildingId, Long floorId, BigDecimal x, BigDecimal y);

    /** 건물+층+좌표 범위로 그룹 조회 (도면 클릭 시 중복 방지) */
    @Query("SELECT g FROM ExtinguisherGroup g " +
           "WHERE g.building.buildingId = :buildingId " +
           "AND g.floor.floorId = :floorId " +
           "AND g.x = :x AND g.y = :y")
    Optional<ExtinguisherGroup> findByCoordinates(
            @Param("buildingId") Long buildingId,
            @Param("floorId") Long floorId,
            @Param("x") BigDecimal x,
            @Param("y") BigDecimal y);
}
