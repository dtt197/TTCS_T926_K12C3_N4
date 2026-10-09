package com.ttcs.homestay.controller.booking;
import com.ttcs.homestay.dto.booking.BookingCancelRequest;
import com.ttcs.homestay.dto.booking.BookingConfirmRequest;
import com.ttcs.homestay.dto.booking.BookingConfirmResponse;
import com.ttcs.homestay.dto.booking.BookingDepositAdjustmentRequest;
import com.ttcs.homestay.dto.booking.BookingDepositAdjustmentResponse;
import com.ttcs.homestay.dto.booking.BookingListItemResponse;
import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.dto.booking.BookingUpdateRequest;
import com.ttcs.homestay.dto.booking.PageResponse;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.dto.booking.RoomShortageAlertResponse;
import com.ttcs.homestay.service.BookingService;
import com.ttcs.homestay.service.RoomShortageAlertService;
import com.ttcs.homestay.service.BookingDepositAdjustmentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.ttcs.homestay.dto.booking.BookingCreateRequest;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final RoomShortageAlertService roomShortageAlertService;
    private final BookingDepositAdjustmentService bookingDepositAdjustmentService;

    public BookingController(
            BookingService bookingService,
            RoomShortageAlertService roomShortageAlertService,
            BookingDepositAdjustmentService bookingDepositAdjustmentService) {
        this.bookingService = bookingService;
        this.roomShortageAlertService = roomShortageAlertService;
        this.bookingDepositAdjustmentService = bookingDepositAdjustmentService;
    }

    /**
     * S3-08: Danh sách cảnh báo các cặp (ngày, loại phòng) có số booking còn hiệu lực vượt số phòng khả dụng.
     */
    @GetMapping("/shortage-alerts")
    public List<RoomShortageAlertResponse> getShortageAlerts() {
        return roomShortageAlertService.getShortageAlerts();
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(request));
    }

    @PutMapping("/{id}/confirm")
    public BookingConfirmResponse confirmBooking(
            @PathVariable Long id,
            @Valid @RequestBody BookingConfirmRequest request) {
        return bookingService.confirmBooking(id, request);
    }

    @GetMapping("/{id}/available-rooms")
    public List<com.ttcs.homestay.dto.RoomResponse> getAvailableRooms(@PathVariable Long id) {
        return bookingService.getAvailableRoomsForBooking(id);
    }

    @PutMapping("/{id}/room")
    public BookingResponse assignRoom(@PathVariable Long id, @RequestBody AssignRoomRequest request) {
        if (request == null || request.roomId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId không được để trống");
        }
        return bookingService.assignRoom(id, request.roomId());
    }

    public record AssignRoomRequest(Long roomId) {}

    @PutMapping("/{id}/room-change")
    public BookingResponse changeRoom(@PathVariable Long id, @RequestBody ChangeRoomRequest request) {
        if (request == null || request.roomId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId không được để trống");
        }
        return bookingService.changeConfirmedRoom(id, request.roomId(), request.reason());
    }

    public record ChangeRoomRequest(Long roomId, String reason) {}

    @GetMapping("/{id}/room-change-history")
    public List<com.ttcs.homestay.dto.booking.BookingRoomChangeHistoryResponse> getRoomChangeHistory(
            @PathVariable Long id) {
        return bookingService.getRoomChangeHistory(id);
    }
    
    /** S3-02 Lát 3: lễ tân huỷ booking kèm lý do. */
    @PutMapping("/{id}/cancel")
    public BookingResponse cancelBooking(
            @PathVariable Long id,
            @Valid @RequestBody BookingCancelRequest request) {
        return bookingService.cancelBooking(id, request);
    }

    @PostMapping("/{id}/deposit-adjustments")
    public ResponseEntity<BookingDepositAdjustmentResponse> createDepositAdjustment(
            @PathVariable Long id,
            @Valid @RequestBody BookingDepositAdjustmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingDepositAdjustmentService.createAdjustment(id, request));
    }

    @PutMapping("/{id}")
    public BookingResponse updateBooking(
            @PathVariable Long id,
            @Valid @RequestBody BookingUpdateRequest request) {
        return bookingService.updateBooking(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteBooking(@PathVariable Long id) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                "Không hỗ trợ xóa booking trực tiếp");
    }

    @GetMapping("/{id}/history")
    public List<com.ttcs.homestay.dto.booking.BookingAuditLogResponse> getBookingHistory(
            @PathVariable Long id) {
        return bookingService.getBookingHistory(id);
    }

    @PostMapping("/{id}/preview")
    public com.ttcs.homestay.dto.booking.BookingChangePreviewResponse previewChange(
            @PathVariable Long id,
            @Valid @RequestBody BookingUpdateRequest request) {
        return bookingService.previewBookingChange(id, request);
    }

    @GetMapping("/latest")
    public List<BookingListItemResponse> getBookings() {
        return bookingService.getLatestBookings();
    }

    @GetMapping
    public PageResponse<BookingResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate checkInFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate checkInTo,
            @RequestParam(required = false) String keyword) {

        if (checkInFrom != null
                && checkInTo != null
                && checkInTo.isBefore(checkInFrom)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu");
        }

        return PageResponse.from(
                bookingService.findPage(
                        page,
                        status,
                        checkInFrom,
                        checkInTo,
                        keyword
                ).map(BookingResponse::from)
        );
    }
}
