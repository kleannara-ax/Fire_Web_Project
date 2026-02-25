package com.company.module.fire.repository;

import com.company.module.fire.entity.Extinguisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 소화기 Repository
 * <p>
 * NOTE: JOIN FETCH를 사용하는 Pageable 쿼리는 반드시 countQuery를 분리 선언해야 한다.
 *       분리하지 않으면 HibernateJpaDialect가 COUNT(*) 에 JOIN FETCH를 포함시켜
 *       HHH90003004 경고 또는 QueryException 이 발생한다.
 */
public interface ExtinguisherRepository extends JpaRepository<Extinguisher, Long> {

    Optional<Extinguisher> findBySerialNumber(String serialNumber);

    Optional<Extinguisher> findByNoteKey(String noteKey);

    /**
     * 소화기 목록 검색 (페이징)
     * - countQuery 분리: JOIN FETCH + Pageable 사용 시 필수
     */
    @Query(value =
           "SELECT e FROM Extinguisher e " +
           "JOIN FETCH e.building b " +
           "JOIN FETCH e.floor f " +
           "WHERE (:buildingId IS NULL OR b.buildingId = :buildingId) " +
           "AND (:floorId IS NULL OR f.floorId = :floorId) " +
           "AND (:keyword IS NULL OR " +
           "     LOWER(b.buildingName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(f.floorName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(e.extinguisherType) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(e.note) LIKE LOWER(CONCAT('%', :keyword, '%')))",
           countQuery =
           "SELECT COUNT(e) FROM Extinguisher e " +
           "JOIN e.building b " +
           "JOIN e.floor f " +
           "WHERE (:buildingId IS NULL OR b.buildingId = :buildingId) " +
           "AND (:floorId IS NULL OR f.floorId = :floorId) " +
           "AND (:keyword IS NULL OR " +
           "     LOWER(b.buildingName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(f.floorName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(e.extinguisherType) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(e.note) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Extinguisher> searchExtinguishers(
            @Param("buildingId") Long buildingId,
            @Param("floorId") Long floorId,
            @Param("keyword") String keyword,
            Pageable pageable);

    boolean existsBySerialNumber(String serialNumber);
}
