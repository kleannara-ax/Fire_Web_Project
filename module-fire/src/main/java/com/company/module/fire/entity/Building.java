package com.company.module.fire.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 건물 마스터 엔티티
 * <p>
 * 기존 ASP.NET: Building (BuildingId, BuildingName, IsActive)
 * 테이블명: building
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "building")
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "building_id")
    private Long buildingId;

    @Column(name = "building_name", nullable = false, length = 200)
    private String buildingName;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Builder
    public Building(String buildingName, boolean isActive) {
        this.buildingName = buildingName;
        this.isActive = isActive;
    }
}
