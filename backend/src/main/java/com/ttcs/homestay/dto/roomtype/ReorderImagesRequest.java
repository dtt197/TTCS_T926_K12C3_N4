package com.ttcs.homestay.dto.roomtype;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReorderImagesRequest(
        @NotEmpty(message = "Danh sách thứ tự ảnh không được để trống")
        List<Long> imageIds
) {}
