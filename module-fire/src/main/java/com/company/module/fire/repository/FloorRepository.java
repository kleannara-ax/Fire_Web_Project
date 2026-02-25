package com.company.module.fire.repository;

import com.company.module.fire.entity.Floor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FloorRepository extends JpaRepository<Floor, Long> {
    List<Floor> findAllByOrderBySortOrderAsc();
}
