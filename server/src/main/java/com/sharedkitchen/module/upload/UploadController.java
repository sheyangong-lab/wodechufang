package com.sharedkitchen.module.upload;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.common.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传（MVP：本地磁盘存储，URL 由 /files/** 静态映射提供）。
 * 生产切换 MinIO/OSS 时仅需替换本控制器与存储路径，客户端无感。
 */
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Path BASE = Paths.get("./data/uploads");

    @PostMapping
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择图片");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("仅支持 jpg/png/gif/webp 图片");
        }
        try {
            Files.createDirectories(BASE);
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = BASE.resolve(filename);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return ApiResponse.ok(Map.of("url", "/files/" + filename));
        } catch (IOException e) {
            throw new BusinessException("图片保存失败，请重试");
        }
    }
}
