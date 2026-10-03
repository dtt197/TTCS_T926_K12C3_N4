package com.ttcs.homestay.service;

import com.ttcs.homestay.exception.ImageSizeExceededException;
import com.ttcs.homestay.exception.InvalidImageException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageStorageService {

    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    public static final int MAX_WIDTH = 1600;
    public static final int THUMBNAIL_WIDTH = 400;

    private final Path uploadRoot;

    public ImageStorageService(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public record StoredImageResult(String imageUrl, String thumbnailUrl) {}

    public StoredImageResult storeRoomTypeImage(Long roomTypeId, MultipartFile file) {
        validateFile(file);

        try {
            BufferedImage originalImage = ImageIO.read(file.getInputStream());
            if (originalImage == null) {
                throw new InvalidImageException("Tệp ảnh không hợp lệ hoặc bị hỏng.");
            }

            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
            boolean isPng = originalFilename.toLowerCase(Locale.ROOT).endsWith(".png")
                    || "image/png".equalsIgnoreCase(file.getContentType());
            String ext = isPng ? "png" : "jpg";
            String formatName = isPng ? "PNG" : "JPEG";

            int origW = originalImage.getWidth();
            int origH = originalImage.getHeight();

            // 1. Tự động điều chỉnh chiều rộng tối đa còn 1600px
            int targetW = origW;
            int targetH = origH;
            if (origW > MAX_WIDTH) {
                targetW = MAX_WIDTH;
                targetH = (int) Math.round((double) origH * MAX_WIDTH / origW);
            }
            BufferedImage scaledOriginal = resizeImage(originalImage, targetW, targetH, isPng);

            // 2. Sinh bản thu nhỏ dùng cho danh sách (chiều rộng tối đa 400px)
            int thumbW = origW;
            int thumbH = origH;
            if (origW > THUMBNAIL_WIDTH) {
                thumbW = THUMBNAIL_WIDTH;
                thumbH = (int) Math.round((double) origH * THUMBNAIL_WIDTH / origW);
            }
            BufferedImage thumbnail = resizeImage(originalImage, thumbW, thumbH, isPng);

            // 3. Lưu vào thư mục hệ thống
            Path roomTypeDir = uploadRoot.resolve("room-types").resolve(String.valueOf(roomTypeId));
            Files.createDirectories(roomTypeDir);

            String fileId = UUID.randomUUID().toString();
            String originalFileName = fileId + "." + ext;
            String thumbFileName = fileId + "_thumb." + ext;

            Path originalFilePath = roomTypeDir.resolve(originalFileName);
            Path thumbFilePath = roomTypeDir.resolve(thumbFileName);

            ImageIO.write(scaledOriginal, formatName, originalFilePath.toFile());
            ImageIO.write(thumbnail, formatName, thumbFilePath.toFile());

            String imageUrl = "/uploads/room-types/" + roomTypeId + "/" + originalFileName;
            String thumbnailUrl = "/uploads/room-types/" + roomTypeId + "/" + thumbFileName;

            return new StoredImageResult(imageUrl, thumbnailUrl);
        } catch (IOException e) {
            throw new RuntimeException("Không thể lưu trữ tệp ảnh: " + e.getMessage(), e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("Vui lòng chọn tệp ảnh để tải lên.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ImageSizeExceededException("Kích thước tệp vượt quá giới hạn tối đa 5MB.");
        }

        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";

        boolean isJpg = "image/jpeg".equalsIgnoreCase(contentType)
                || originalFilename.endsWith(".jpg")
                || originalFilename.endsWith(".jpeg");
        boolean isPng = "image/png".equalsIgnoreCase(contentType)
                || originalFilename.endsWith(".png");

        if (!isJpg && !isPng) {
            throw new InvalidImageException("Định dạng tệp không hợp lệ. Chỉ chấp nhận tệp JPG hoặc PNG.");
        }
    }

    private BufferedImage resizeImage(BufferedImage src, int targetWidth, int targetHeight, boolean isPng) {
        int imageType = isPng ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, imageType);
        Graphics2D g2d = result.createGraphics();

        if (!isPng) {
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, targetWidth, targetHeight);
        }

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(src, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();

        return result;
    }
}
