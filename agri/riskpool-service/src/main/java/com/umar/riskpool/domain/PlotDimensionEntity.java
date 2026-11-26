package com.umar.riskpool.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "pool_plot_dimensions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlotDimensionEntity {
    @Id @Column(name = "plot_id") private String plotId;
    @Column(name = "region_code", nullable = false) private String regionCode;
    private String geohash5;
    @Column(name = "area_ha", nullable = false) private double areaHa;
    @Column(name = "crop_code") private String cropCode;
}
