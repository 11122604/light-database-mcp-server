package org.turnright.mysqlmcpserver.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.InputStream;

/**
 * 管理页面入口：/admin 与 /admin.html。
 * 直接以 text/html;charset=UTF-8 输出页面资源，避免无 charset 时浏览器按错误编码渲染中文。
 */
@Controller
@ConditionalOnProperty(name = "mcp.transport", havingValue = "http")
public class AdminPageController {

    @GetMapping(value = {"/admin", "/admin.html"}, produces = "text/html;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<byte[]> adminPage() throws Exception {
        // 优先读源码目录（开发时改 admin.html 立即生效），jar 部署时回退 classpath
        byte[] body = null;
        java.nio.file.Path dev = java.nio.file.Paths.get("src/main/resources/static/admin.html");
        if (java.nio.file.Files.exists(dev)) {
            body = java.nio.file.Files.readAllBytes(dev);
        } else {
            ClassPathResource res = new ClassPathResource("static/admin.html");
            try (InputStream in = res.getInputStream()) {
                body = StreamUtils.copyToByteArray(in);
            }
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/html;charset=UTF-8"));
        // 管理页禁止缓存，避免刷新仍看到旧版本
        headers.setCacheControl("no-store");
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}
