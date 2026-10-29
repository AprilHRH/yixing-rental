package com.yixing.web;

import com.yixing.system.SystemService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static com.yixing.common.BusinessException.require;

@RestController
public class FileController {
    private final SystemService system;
    public FileController(SystemService system) { this.system = system; }
    @PostMapping("/api/uploads") public Map<String, Object> upload(@RequestParam MultipartFile file) throws IOException {
        require(file.getSize() > 0 && file.getSize() <= 5 * 1024 * 1024, "图片不得为空或超过 5MB");
        String type = file.getContentType(); require(Set.of("image/png", "image/jpeg").contains(type == null ? "" : type), "仅支持 PNG、JPG 图片");
        var image = ImageIO.read(file.getInputStream()); require(image != null && image.getWidth() <= 6000 && image.getHeight() <= 6000, "图片无效或尺寸超出限制");
        String extension = "image/png".equals(type) ? "png" : "jpg", name = UUID.randomUUID() + "." + extension;
        Path directory = Path.of("uploads"); Files.createDirectories(directory);
        require(ImageIO.write(image, extension, directory.resolve(name).toFile()), "图片处理失败");
        return Map.of("url", "/uploads/" + name);
    }
    @GetMapping("/uploads/{name}") public ResponseEntity<Resource> image(@PathVariable String name, HttpServletRequest request) throws IOException {
        if (request.getSession(false) == null || request.getSession(false).getAttribute("userId") == null || !Long.valueOf(com.yixing.auth.SessionFilter.epoch()).equals(request.getSession(false).getAttribute("epoch"))) return ResponseEntity.status(401).build();
        require(name.matches("[a-f0-9-]+\\.(png|jpg)"), "图片名称不正确"); Path file = Path.of("uploads").resolve(name);
        if (!Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(name.endsWith("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG).header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(file));
    }
    @GetMapping("/api/system/backup") public ResponseEntity<byte[]> backup() throws IOException {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=yixing-backup.zip").contentType(MediaType.APPLICATION_OCTET_STREAM).body(system.backup());
    }
    @PostMapping("/api/system/restore") public Map<String, Object> restore(@RequestParam MultipartFile file, @RequestParam String confirmation, HttpServletRequest request) throws IOException {
        system.restore(file, confirmation); request.getSession().invalidate(); return Map.of("success", true);
    }
}
