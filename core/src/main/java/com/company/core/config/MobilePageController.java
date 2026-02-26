package com.company.core.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 모바일 점검 및 QR 페이지 컨트롤러
 * <p>
 * ViewControllerRegistry 대신 직접 HTML 파일을 서빙
 * 이렇게 하면 Spring Security 인증 없이 접근 가능
 */
@Controller
public class MobilePageController {

    /**
     * 소화기 점검 모바일 페이지
     * GET /minspection/extinguishers/{serial}
     */
    @GetMapping("/minspection/extinguishers/{serial}")
    @ResponseBody
    public ResponseEntity<String> extinguisherInspectionPage(
            @PathVariable String serial) throws IOException {
        return serveHtml("static/minspection/extinguishers/index.html");
    }

    /**
     * 소화전 점검 모바일 페이지
     * GET /minspection/hydrants/{serial}
     */
    @GetMapping("/minspection/hydrants/{serial}")
    @ResponseBody
    public ResponseEntity<String> hydrantInspectionPage(
            @PathVariable String serial) throws IOException {
        return serveHtml("static/minspection/hydrants/index.html");
    }

    /**
     * 점검 완료 페이지
     * GET /minspection/complete
     */
    @GetMapping("/minspection/complete")
    @ResponseBody
    public ResponseEntity<String> completePage() throws IOException {
        return serveHtml("static/minspection/complete.html");
    }

    /**
     * QR 코드 페이지
     * GET /qr or /qr/
     */
    @GetMapping({"/qr", "/qr/"})
    @ResponseBody
    public ResponseEntity<String> qrPage() throws IOException {
        return serveHtml("static/qr/index.html");
    }

    private ResponseEntity<String> serveHtml(String resourcePath) throws IOException {
        Resource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String html = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(html);
    }
}
