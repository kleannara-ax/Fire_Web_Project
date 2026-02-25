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
 */
public interface ExtinguisherRepository extends JpaRepository<Extinguisher, Long> {

    Optional<Extinguisher> findBySerialNumber(String serialNumber);

    Optional<Extinguisher> findByNoteKey(String noteKey);

    @Query("SELECT e FROM Extinguisher e " +
           "JOIN FETCH e.building b " +
           "JOIN FETCH e.floor f " +
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
