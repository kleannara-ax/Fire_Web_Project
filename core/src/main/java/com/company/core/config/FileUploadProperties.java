package com.company.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 파일 업로드 공통 설정
 * application.yml: fireweb.upload.*
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fireweb.upload")
public class FileUploadProperties {

    /** 업로드 기본 경로 */
    private String basePath = "./uploads";

    /** 소화기 이미지 경로 */
    private String extinguishers = "./uploads/extinguishers";

    /** 소화전 이미지 경로 */
    private String hydrants = "./uploads/hydrants";

    /** 점검 이미지 경로 */
    private String inspections = "./uploads/inspections";
}
