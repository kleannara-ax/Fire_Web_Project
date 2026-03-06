package com.company.module.fire.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 구역(Zone) 마스터 엔티티
 * <p>
 * 기존 ASP.NET: Zone (ZoneId, ZoneCode, ZoneName, BuildingId, FloorId, X, Y)
 * 테이블명: zone
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "zone")
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "zone_id")
    private Long zoneId;

    @Column(name = "zone_code", nullable = false, length = 50)
    private String zoneCode;

    @Column(name = "zone_name", length = 200)
    private String zoneName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id")
    private Floor floor;

    /** 도면 X 좌표 */
    @Column(name = "x", precision = 9, scale = 4)
    private BigDecimal x;

    /** 도면 Y 좌표 */
    @Column(name = "y", precision = 9, scale = 4)
    private BigDecimal y;

    @Builder
    public Zone(String zoneCode, String zoneName, Building building, Floor floor,
                BigDecimal x, BigDecimal y) {
        this.zoneCode = zoneCode;
        this.zoneName = zoneName;
        this.building = building;
        this.floor = floor;
        this.x = x;
        this.y = y;
    }
}
