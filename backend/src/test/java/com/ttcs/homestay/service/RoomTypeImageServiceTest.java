package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.roomtype.RoomTypeImageResponse;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.entity.RoomTypeImage;
import com.ttcs.homestay.exception.ImageSizeExceededException;
import com.ttcs.homestay.exception.InvalidImageException;
import com.ttcs.homestay.exception.MaxImageCountExceededException;
import com.ttcs.homestay.exception.RoomTypeNotFoundException;
import com.ttcs.homestay.repository.RoomTypeImageRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class RoomTypeImageServiceTest {

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private RoomTypeImageRepository roomTypeImageRepository;

    private ImageStorageService imageStorageService;
    private RoomTypeImageService roomTypeImageService;

    @TempDir
    java.nio.file.Path tempDir;

    private RoomType sampleRoomType;

    @BeforeEach
    void setUp() {
        imageStorageService = new ImageStorageService(tempDir.toString());
        roomTypeImageService = new RoomTypeImageService(
                roomTypeRepository,
                roomTypeImageRepository,
                imageStorageService
        );

        sampleRoomType = new RoomType();
        sampleRoomType.setId(1L);
        sampleRoomType.setCode("VIP");
        sampleRoomType.setName("Phòng VIP");
    }

    private byte[] createTestImageBytes(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    @Test
    void uploadImage_dungDinhDangVaKichThuoc_thanhCongVaTuDongNhanDienAnhDauTienLamDaiDien() throws Exception {
        byte[] imageBytes = createTestImageBytes(800, 600, "JPEG");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "room.jpg",
                "image/jpeg",
                imageBytes
        );

        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(sampleRoomType));
        when(roomTypeImageRepository.countByRoomTypeId(1L)).thenReturn(0L);
        when(roomTypeImageRepository.save(any(RoomTypeImage.class))).thenAnswer(invocation -> {
            RoomTypeImage saved = invocation.getArgument(0);
            saved.setId(101L);
            return saved;
        });

        RoomTypeImageResponse response = roomTypeImageService.uploadImage(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.roomTypeId()).isEqualTo(1L);
        assertThat(response.isPrimary()).isTrue(); // Tự động nhận diện ảnh đầu tiên làm ảnh đại diện
        assertThat(response.displayOrder()).isZero();
        assertThat(response.imageUrl()).contains("/uploads/room-types/1/");
        assertThat(response.thumbnailUrl()).contains("_thumb.jpg");
    }

    @Test
    void uploadImage_anhThuHaiTroDi_khongLamAnhDaiDien() throws Exception {
        byte[] imageBytes = createTestImageBytes(800, 600, "PNG");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "room2.png",
                "image/png",
                imageBytes
        );

        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(sampleRoomType));
        when(roomTypeImageRepository.countByRoomTypeId(1L)).thenReturn(1L); // Đã có 1 ảnh
        when(roomTypeImageRepository.save(any(RoomTypeImage.class))).thenAnswer(invocation -> {
            RoomTypeImage saved = invocation.getArgument(0);
            saved.setId(102L);
            return saved;
        });

        RoomTypeImageResponse response = roomTypeImageService.uploadImage(1L, file);

        assertThat(response.isPrimary()).isFalse(); // Chỉ ảnh đầu tiên mới là ảnh đại diện
        assertThat(response.displayOrder()).isEqualTo(1);
    }

    @Test
    void uploadImage_saiDinhDang_biChan() {
        MockMultipartFile txtFile = new MockMultipartFile(
                "file",
                "document.txt",
                "text/plain",
                "Hello world".getBytes()
        );

        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(sampleRoomType));
        when(roomTypeImageRepository.countByRoomTypeId(1L)).thenReturn(0L);

        assertThatThrownBy(() -> roomTypeImageService.uploadImage(1L, txtFile))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("Chỉ chấp nhận tệp JPG hoặc PNG");

        verify(roomTypeImageRepository, never()).save(any());
    }

    @Test
    void uploadImage_vuotQua5MB_biChan() {
        // Tạo file giả lập vượt quá 5MB
        byte[] largeBytes = new byte[5 * 1024 * 1024 + 10];
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large.jpg",
                "image/jpeg",
                largeBytes
        );

        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(sampleRoomType));
        when(roomTypeImageRepository.countByRoomTypeId(1L)).thenReturn(0L);

        assertThatThrownBy(() -> roomTypeImageService.uploadImage(1L, largeFile))
                .isInstanceOf(ImageSizeExceededException.class)
                .hasMessageContaining("vượt quá giới hạn tối đa 5MB");

        verify(roomTypeImageRepository, never()).save(any());
    }

    @Test
    void uploadImage_vuotQuaGioiHan8Anh_biChan() throws Exception {
        byte[] imageBytes = createTestImageBytes(400, 300, "JPEG");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "room9.jpg",
                "image/jpeg",
                imageBytes
        );

        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(sampleRoomType));
        when(roomTypeImageRepository.countByRoomTypeId(1L)).thenReturn(8L); // Đã có đủ 8 ảnh

        assertThatThrownBy(() -> roomTypeImageService.uploadImage(1L, file))
                .isInstanceOf(MaxImageCountExceededException.class)
                .hasMessageContaining("tối đa 8 ảnh");

        verify(roomTypeImageRepository, never()).save(any());
    }

    @Test
    void uploadImage_anhLonHon1600px_tuDongThuNhoVe1600pxVaSinhThumbnail() throws Exception {
        // Tạo ảnh 2400x1800 (> 1600px)
        byte[] imageBytes = createTestImageBytes(2400, 1800, "JPEG");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "wide.jpg",
                "image/jpeg",
                imageBytes
        );

        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(sampleRoomType));
        when(roomTypeImageRepository.countByRoomTypeId(1L)).thenReturn(0L);
        when(roomTypeImageRepository.save(any(RoomTypeImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomTypeImageResponse response = roomTypeImageService.uploadImage(1L, file);

        assertThat(response).isNotNull();
        // Kiểm tra tệp đã lưu trong tempDir
        java.nio.file.Path savedDir = tempDir.resolve("room-types").resolve("1");
        assertThat(java.nio.file.Files.exists(savedDir)).isTrue();

        // Đọc ảnh đã lưu để kiểm tra chiều rộng không vượt quá 1600px
        java.io.File[] files = savedDir.toFile().listFiles((d, name) -> !name.contains("_thumb"));
        assertThat(files).isNotNull().hasSize(1);
        BufferedImage savedOriginal = ImageIO.read(files[0]);
        assertThat(savedOriginal.getWidth()).isEqualTo(1600);
        assertThat(savedOriginal.getHeight()).isEqualTo(1200);

        // Đọc ảnh thumbnail để kiểm tra chiều rộng <= 400px
        java.io.File[] thumbFiles = savedDir.toFile().listFiles((d, name) -> name.contains("_thumb"));
        assertThat(thumbFiles).isNotNull().hasSize(1);
        BufferedImage savedThumb = ImageIO.read(thumbFiles[0]);
        assertThat(savedThumb.getWidth()).isEqualTo(400);
        assertThat(savedThumb.getHeight()).isEqualTo(300);
    }

    @Test
    void getImages_loaiPhongKhongTonTai_nemNgoaiLe() {
        when(roomTypeRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> roomTypeImageService.getImages(999L))
                .isInstanceOf(RoomTypeNotFoundException.class);
    }

    @Test
    void getImages_traVeDanhSachSapXepTheoThuTu() {
        when(roomTypeRepository.existsById(1L)).thenReturn(true);

        RoomTypeImage img1 = new RoomTypeImage(sampleRoomType, "/uploads/1/a.jpg", "/uploads/1/a_thumb.jpg", 0, true);
        img1.setId(10L);
        RoomTypeImage img2 = new RoomTypeImage(sampleRoomType, "/uploads/1/b.jpg", "/uploads/1/b_thumb.jpg", 1, false);
        img2.setId(11L);

        when(roomTypeImageRepository.findByRoomTypeIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(img1, img2));

        List<RoomTypeImageResponse> results = roomTypeImageService.getImages(1L);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isEqualTo(10L);
        assertThat(results.get(0).isPrimary()).isTrue();
        assertThat(results.get(1).id()).isEqualTo(11L);
        assertThat(results.get(1).isPrimary()).isFalse();
    }

    @Test
    void reorderImages_keoThaThayDoiViTri_luuDungThuTuVaCapNhatAnhDauTienLamDaiDien() {
        when(roomTypeRepository.existsById(1L)).thenReturn(true);

        RoomTypeImage img1 = new RoomTypeImage(sampleRoomType, "/uploads/1/a.jpg", "/uploads/1/a_thumb.jpg", 0, true);
        img1.setId(101L);
        RoomTypeImage img2 = new RoomTypeImage(sampleRoomType, "/uploads/1/b.jpg", "/uploads/1/b_thumb.jpg", 1, false);
        img2.setId(102L);
        RoomTypeImage img3 = new RoomTypeImage(sampleRoomType, "/uploads/1/c.jpg", "/uploads/1/c_thumb.jpg", 2, false);
        img3.setId(103L);

        when(roomTypeImageRepository.findByRoomTypeIdOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(img1, img2, img3));
        when(roomTypeImageRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Kéo ảnh 103 lên đầu danh sách: thứ tự mới là [103, 101, 102]
        List<RoomTypeImageResponse> updated =
                roomTypeImageService.reorderImages(1L, List.of(103L, 101L, 102L));

        assertThat(updated).hasSize(3);
        // Ảnh 103 lên đầu: thứ tự 0, tự động trở thành ảnh đại diện
        assertThat(updated.get(0).id()).isEqualTo(103L);
        assertThat(updated.get(0).displayOrder()).isZero();
        assertThat(updated.get(0).isPrimary()).isTrue();

        // Ảnh 101 xuống thứ 2: thứ tự 1, không còn là ảnh đại diện
        assertThat(updated.get(1).id()).isEqualTo(101L);
        assertThat(updated.get(1).displayOrder()).isEqualTo(1);
        assertThat(updated.get(1).isPrimary()).isFalse();

        // Ảnh 102 xuống thứ 3: thứ tự 2
        assertThat(updated.get(2).id()).isEqualTo(102L);
        assertThat(updated.get(2).displayOrder()).isEqualTo(2);
        assertThat(updated.get(2).isPrimary()).isFalse();
    }

    @Test
    void reorderImages_soLuongIdKhongKhop_biChan() {
        when(roomTypeRepository.existsById(1L)).thenReturn(true);

        RoomTypeImage img1 = new RoomTypeImage(sampleRoomType, "/uploads/1/a.jpg", "/uploads/1/a_thumb.jpg", 0, true);
        img1.setId(101L);
        RoomTypeImage img2 = new RoomTypeImage(sampleRoomType, "/uploads/1/b.jpg", "/uploads/1/b_thumb.jpg", 1, false);
        img2.setId(102L);

        when(roomTypeImageRepository.findByRoomTypeIdOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(img1, img2));

        assertThatThrownBy(() -> roomTypeImageService.reorderImages(1L, List.of(101L)))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("không khớp");
    }

    @Test
    void reorderImages_chuaIdKhongThuocLoaiPhong_biChan() {
        when(roomTypeRepository.existsById(1L)).thenReturn(true);

        RoomTypeImage img1 = new RoomTypeImage(sampleRoomType, "/uploads/1/a.jpg", "/uploads/1/a_thumb.jpg", 0, true);
        img1.setId(101L);

        when(roomTypeImageRepository.findByRoomTypeIdOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(img1));

        assertThatThrownBy(() -> roomTypeImageService.reorderImages(1L, List.of(999L)))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("không thuộc về loại phòng này");
    }

    @Test
    void reorderImages_loaiPhongKhongTonTai_nemNgoaiLe() {
        when(roomTypeRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> roomTypeImageService.reorderImages(999L, List.of(1L, 2L)))
                .isInstanceOf(RoomTypeNotFoundException.class);
    }
}
