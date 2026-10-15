package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.maintenance.MaintenanceResponse;
import com.ttcs.homestay.dto.maintenance.MaintenanceNoteRequest;
import com.ttcs.homestay.dto.maintenance.VendorRequest;
import com.ttcs.homestay.entity.MaintenanceRequestEntity;
import com.ttcs.homestay.entity.MaintenanceRequestHistory;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomStatusHistory;
import com.ttcs.homestay.repository.MaintenanceRequestHistoryRepository;
import com.ttcs.homestay.repository.MaintenanceRequestRepository;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomStatusHistoryRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class MaintenanceWorkflowService {
    private final MaintenanceRequestRepository requests;
    private final MaintenanceRequestHistoryRepository history;
    private final UserRepository users;
    private final AuditLogService audit;
    private final RoomRepository rooms;
    private final RoomStatusHistoryRepository roomHistory;
    private final NotificationWriterService notifications;

    public MaintenanceWorkflowService(MaintenanceRequestRepository requests,
            MaintenanceRequestHistoryRepository history, UserRepository users, AuditLogService audit,
            RoomRepository rooms, RoomStatusHistoryRepository roomHistory, NotificationWriterService notifications) {
        this.requests = requests;
        this.history = history;
        this.users = users;
        this.audit = audit;
        this.rooms = rooms;
        this.roomHistory = roomHistory;
        this.notifications = notifications;
    }

    public record RequestPage(List<MaintenanceResponse> content, int page, int totalPages, long totalElements) {}
    public record Detail(MaintenanceResponse request, List<MaintenanceRequestHistory> history) {}

    public RequestPage list(Jwt jwt, int page, int size) {
        if (jwt != null && "HOUSEKEEPING".equals(jwt.getClaimAsString("role"))) authorizeHousekeeping(jwt);
        else authorize(jwt, false);
        var pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)),
                Sort.by(Sort.Order.desc("reportedAt"), Sort.Order.desc("id")));
        var result = jwt != null && "HOUSEKEEPING".equals(jwt.getClaimAsString("role"))
                ? requests.findByStatus("CHO_VE_SINH", pageable) : requests.findAll(pageable);
        return new RequestPage(result.map(MaintenanceResponse::from).getContent(), result.getNumber(),
                result.getTotalPages(), result.getTotalElements());
    }

    public List<Long> cleaningRoomIds(Jwt jwt) {
        if (jwt == null || !"HOUSEKEEPING".equals(jwt.getClaimAsString("role")))
            throw new AccessDeniedException("Chi HOUSEKEEPING duoc xem phong cho ve sinh");
        return requests.findRoomIdsAwaitingCleaning();
    }

    public Detail detail(Jwt jwt, Long id) {
        if (jwt != null && "HOUSEKEEPING".equals(jwt.getClaimAsString("role"))) authorizeHousekeeping(jwt);
        else authorize(jwt, false);
        var request = requests.findById(id).orElseThrow(MaintenanceWorkflowService::notFound);
        if ("HOUSEKEEPING".equals(jwt.getClaimAsString("role")) && !"CHO_VE_SINH".equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Khong tim thay yeu cau cho ve sinh");
        }
        return new Detail(MaintenanceResponse.from(request),
                history.findByMaintenanceRequestIdOrderByOccurredAtAscIdAsc(id));
    }

    @Transactional
    public MaintenanceResponse cleaningCompleted(Jwt jwt, Long id, MaintenanceNoteRequest body, String ip) {
        User actor = authorizeHousekeeping(jwt);
        var request = locked(id, "CHO_VE_SINH");
        String notes = requiredNotes(body);
        var roomId = request.getRoom().getId();
        if (requests.existsOtherByRoomIdAndStatusNot(roomId, "HOAN_TAT", request.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phong co yeu cau bao tri khac dang mo");
        }
        var room = rooms.findByIdForUpdate(roomId)
                .orElseThrow(MaintenanceWorkflowService::notFound);
        if (room.getStatus() != RoomStatus.BAO_TRI && room.getStatus() != RoomStatus.TRONG_BAN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phong hoac yeu cau bao tri da thay doi");
        }
        RoomStatus previousStatus = room.getStatus();
        OffsetDateTime now = OffsetDateTime.now();
        request.setCleanedBy(actor);
        request.setCleanedAt(now);
        request.setCleaningNotes(notes);
        request.setCompletedAt(now);
        var statusHistory = new RoomStatusHistory();
        statusHistory.setRoom(room);
        statusHistory.setPreviousStatus(previousStatus);
        statusHistory.setNewStatus(RoomStatus.TRONG_SACH);
        statusHistory.setChangedBy(actor.getFullName());
        statusHistory.setChangedAt(now);
        statusHistory.setMaintenanceReason(null);
        statusHistory.setMaintenanceStartDate(null);
        statusHistory.setMaintenanceEndDate(null);
        room.setStatus(RoomStatus.TRONG_SACH);
        room.setMaintenanceReason(null);
        room.setMaintenanceStartDate(null);
        room.setMaintenanceEndDate(null);
        roomHistory.save(statusHistory);
        record(request, actor, "CLEANING_COMPLETED", "HOAN_TAT", notes, ip);
        notifications.writeMaintenanceCleaned(request);
        return MaintenanceResponse.from(request);
    }

    @Transactional
    public MaintenanceResponse accept(Jwt jwt, Long id, String ip) {
        User actor = authorize(jwt, true);
        var request = locked(id, "CHO_TIEP_NHAN");
        request.setAcceptedBy(actor);
        request.setAcceptedAt(OffsetDateTime.now());
        record(request, actor, "ACCEPTED", "DA_TIEP_NHAN", ip);
        return MaintenanceResponse.from(request);
    }

    @Transactional
    public MaintenanceResponse updateVendor(Jwt jwt, Long id, VendorRequest vendor, String ip) {
        User actor = authorize(jwt, true);
        var request = locked(id, "DA_TIEP_NHAN");
        setVendor(request, vendor);
        record(request, actor, "VENDOR_UPDATED", "DA_TIEP_NHAN", ip);
        return MaintenanceResponse.from(request);
    }

    @Transactional
    public MaintenanceResponse start(Jwt jwt, Long id, VendorRequest vendor, String ip) {
        User actor = authorize(jwt, true);
        var request = locked(id, "DA_TIEP_NHAN");
        setVendor(request, vendor);
        request.setProcessingStartedAt(OffsetDateTime.now());
        record(request, actor, "PROCESSING_STARTED", "DANG_XU_LY", ip);
        return MaintenanceResponse.from(request);
    }

    @Transactional
    public MaintenanceResponse repairCompleted(Jwt jwt, Long id, MaintenanceNoteRequest body, String ip) {
        User actor = authorize(jwt, true);
        var request = locked(id, "DANG_XU_LY");
        String notes = requiredNotes(body);
        request.setRepairCompletedBy(actor);
        request.setRepairCompletedAt(OffsetDateTime.now());
        request.setRepairNotes(notes);
        record(request, actor, "REPAIR_COMPLETED", "CHO_XAC_NHAN", notes, ip);
        return MaintenanceResponse.from(request);
    }

    @Transactional
    public MaintenanceResponse verify(Jwt jwt, Long id, boolean passed, MaintenanceNoteRequest body, String ip) {
        User actor = authorize(jwt, true);
        var request = locked(id, "CHO_XAC_NHAN");
        String notes = requiredNotes(body);
        var room = rooms.findByIdForUpdate(request.getRoom().getId())
                    .orElseThrow(MaintenanceWorkflowService::notFound);
        if (passed) {
            if (room.getStatus() != RoomStatus.BAO_TRI) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Phong khong con o trang thai bao tri");
            }
            room.setStatus(RoomStatus.TRONG_BAN);
            room.setMaintenanceReason(null);
            room.setMaintenanceStartDate(null);
            room.setMaintenanceEndDate(null);
            var roomStatusHistory = new RoomStatusHistory();
            roomStatusHistory.setRoom(room);
            roomStatusHistory.setPreviousStatus(RoomStatus.BAO_TRI);
            roomStatusHistory.setNewStatus(RoomStatus.TRONG_BAN);
            roomStatusHistory.setChangedBy(actor.getFullName());
            roomStatusHistory.setChangedAt(OffsetDateTime.now());
            roomHistory.save(roomStatusHistory);
        } else if (room.getStatus() != RoomStatus.BAO_TRI) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phong khong con o trang thai bao tri");
        }
        request.setVerifiedBy(actor);
        request.setVerifiedAt(OffsetDateTime.now());
        request.setVerificationNotes(notes);
        record(request, actor, passed ? "VERIFIED" : "REPAIR_REJECTED",
                passed ? "CHO_VE_SINH" : "DANG_XU_LY", notes, ip);
        return MaintenanceResponse.from(request);
    }

    private User authorize(Jwt jwt, boolean write) {
        if (jwt == null || !("OWNER".equals(jwt.getClaimAsString("role"))
                || (!write && ("RECEPTIONIST".equals(jwt.getClaimAsString("role"))
                    || "HOUSEKEEPING".equals(jwt.getClaimAsString("role")))))) {
            throw new AccessDeniedException("Không có quyền xử lý yêu cầu bảo trì");
        }
        Long id;
        try { id = Long.valueOf(jwt.getSubject()); }
        catch (RuntimeException e) { throw new AccessDeniedException("Tài khoản không hợp lệ", e); }
        User actor = users.findWithRoleById(id)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không hợp lệ"));
        if (!actor.isActive() || !actor.getRole().getCode().equals(jwt.getClaimAsString("role"))) {
            throw new AccessDeniedException("Tài khoản không có quyền truy cập");
        }
        return actor;
    }

    private User authorizeHousekeeping(Jwt jwt) {
        if (jwt == null || !"HOUSEKEEPING".equals(jwt.getClaimAsString("role")))
            throw new AccessDeniedException("Chi HOUSEKEEPING duoc hoan tat ve sinh");
        Long id;
        try { id = Long.valueOf(jwt.getSubject()); }
        catch (RuntimeException e) { throw new AccessDeniedException("Tai khoan khong hop le", e); }
        User actor = users.findWithRoleById(id).orElseThrow(() -> new AccessDeniedException("Tai khoan khong hop le"));
        if (!actor.isActive() || !"HOUSEKEEPING".equals(actor.getRole().getCode()))
            throw new AccessDeniedException("Tai khoan khong co quyen truy cap");
        return actor;
    }

    private MaintenanceRequestEntity locked(Long id, String required) {
        var request = requests.findByIdForUpdate(id).orElseThrow(MaintenanceWorkflowService::notFound);
        if (!required.equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Yêu cầu đã đổi trạng thái. Vui lòng tải lại chi tiết.");
        }
        return request;
    }

    private void setVendor(MaintenanceRequestEntity request, VendorRequest vendor) {
        if (vendor == null || vendor.name() == null || vendor.name().isBlank() || vendor.name().length() > 200
                || vendor.phone() == null || !vendor.phone().trim().matches("[+0-9() .-]{6,30}")
                || !vendor.phone().matches(".*[0-9].*")
                || vendor.phone().length() > 30 || (vendor.notes() != null && vendor.notes().length() > 2000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin đơn vị sửa chữa không hợp lệ");
        }
        request.setVendorName(vendor.name().trim());
        request.setVendorPhone(vendor.phone().trim());
        request.setVendorNotes(vendor.notes() == null ? null : vendor.notes().trim());
    }

    private String requiredNotes(MaintenanceNoteRequest body) {
        if (body == null || body.notes() == null || body.notes().isBlank() || body.notes().length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ghi chú không được để trống và tối đa 2000 ký tự");
        }
        return body.notes().trim();
    }

    private void record(MaintenanceRequestEntity request, User actor, String action, String next, String ip) {
        record(request, actor, action, next, request.getVendorNotes(), ip);
    }

    private void record(MaintenanceRequestEntity request, User actor, String action, String next, String notes, String ip) {
        var entry = new MaintenanceRequestHistory();
        entry.setMaintenanceRequestId(request.getId());
        entry.setAction(action);
        entry.setPreviousStatus(request.getStatus());
        entry.setNewStatus(next);
        entry.setActorUserId(actor.getId());
        entry.setActorName(actor.getFullName());
        entry.setOccurredAt(OffsetDateTime.now());
        entry.setVendorName(request.getVendorName());
        entry.setVendorPhone(request.getVendorPhone());
        entry.setNotes(notes);
        request.setStatus(next);
        history.save(entry);
        if ("CLEANING_COMPLETED".equals(action)) request.setCompletedAt(OffsetDateTime.now());
        audit.recordSensitiveActionInCurrentTransaction(actor.getId(), actor.getEmail(), null,
                "Maintenance request #" + request.getId(), "MAINTENANCE_" + action, ip);
        // Managed entity dirty checking commits with history + audit. No room mutation or request creation.
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu bảo trì");
    }
}
