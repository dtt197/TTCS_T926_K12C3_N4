package com.ttcs.homestay.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.entity.Booking;
import java.time.format.DateTimeFormatter; 
import java.time.ZoneId;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;

@Service
public class MailService {

	private static final Logger log = LoggerFactory.getLogger(MailService.class);

	private final JavaMailSender mailSender;
	private final String from;
	private final String frontendUrl;
	private final boolean smtpConfigured;

	public MailService(JavaMailSender mailSender,
			@Value("${app.mail.from}") String from,
			@Value("${app.frontend-url}") String frontendUrl,
			@Value("${spring.mail.host:}") String mailHost,
			@Value("${spring.mail.username:}") String mailUsername,
			@Value("${spring.mail.password:}") String mailPassword,
			@Value("${spring.mail.properties.mail.smtp.auth:false}") boolean smtpAuth) {
		this.mailSender = mailSender;
		this.from = from;
		this.frontendUrl = frontendUrl;
		this.smtpConfigured = mailHost != null && !mailHost.isBlank()
				&& (!smtpAuth || (mailUsername != null && !mailUsername.isBlank()
						&& mailPassword != null && !mailPassword.isBlank()));
	}

	/** Gửi lỗi chỉ ghi log, không làm hỏng việc tạo tài khoản. */
	public void sendTemporaryPassword(User user, String temporaryPassword) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(user.getEmail());
		message.setSubject("Tài khoản hệ thống HomeStay của bạn");
		message.setText("Xin chào " + user.getFullName() + ",\n\n"
				+ "Quản trị viên vừa tạo tài khoản cho bạn trên hệ thống HomeStay.\n"
				+ "Email đăng nhập: " + user.getEmail() + "\n"
				+ "Mật khẩu tạm: " + temporaryPassword + "\n\n"
				+ "Đăng nhập tại: " + frontendUrl + "/dang-nhap\n"
				+ "Bạn sẽ được yêu cầu đổi mật khẩu ở lần đăng nhập đầu tiên.");
		try {
			mailSender.send(message);
		} catch (MailException e) {
			log.warn("Không gửi được email mật khẩu tạm tới {}: {}", user.getEmail(), e.getMessage());
		}
	}
	public void sendPasswordResetLink(User user, String rawToken) {
		if (!smtpConfigured) {
			throw new IllegalStateException("SMTP chưa được cấu hình. Hãy đặt MAIL_HOST; nếu máy chủ yêu cầu xác thực, cần đặt thêm MAIL_AUTH=true, MAIL_USERNAME và MAIL_PASSWORD.");
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(user.getEmail());
		message.setSubject("Yêu cầu đặt lại mật khẩu - HomeStay");
		message.setText("Xin chào " + user.getFullName() + ",\n\n"
				+ "Nhấn vào liên kết sau để đặt mật khẩu mới (hết hạn sau 30 phút):\n"
				+ frontendUrl + "/reset-password?token=" + rawToken + "\n\n"
				+ "Nếu bạn không yêu cầu điều này, hãy bỏ qua email này.");
		try {
			mailSender.send(message);
		} catch (MailException e) {
			log.warn("Không gửi được email đặt lại mật khẩu tới {}: {}", user.getEmail(), e.getMessage());
		}
	}

	private static final DateTimeFormatter DATE_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	/** Tiêu đề email nhắc nhận phòng (tách riêng để test). */
	public static String reminderSubject(Booking booking) {
		return "Nhắc lịch nhận phòng ngày " + booking.getCheckInDate().format(DATE_VN)
				+ " - Mã booking " + booking.getBookingCode();
	}

	/** Nội dung email nhắc nhận phòng (tách riêng để test). */
	public static String reminderBody(Booking booking) {
		return "Xin chào " + booking.getGuestName() + ",\n\n"
				+ "HomeStay xin nhắc bạn sắp đến ngày nhận phòng.\n"
				+ "Mã booking: " + booking.getBookingCode() + "\n"
				+ "Loại phòng: " + booking.getRoomTypeNameSnapshot() + "\n"
				+ "Ngày nhận phòng: " + booking.getCheckInDate().format(DATE_VN) + "\n"
				+ "Ngày trả phòng: " + booking.getCheckOutDate().format(DATE_VN) + "\n\n"
				+ "Hẹn gặp bạn tại HomeStay.";
	}

	/**
	 * Gửi email nhắc nhận phòng. KHÁC các phương thức trên: nếu gửi lỗi thì ném MailException
	 * để nơi gọi ghi nhận thất bại vào nhật ký.
	 */
	public void sendCheckInReminder(Booking booking) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(booking.getGuestEmail() != null ? booking.getGuestEmail().trim() : "");
		message.setSubject(reminderSubject(booking));
		message.setText(reminderBody(booking));
		mailSender.send(message);
	}

	private static final ZoneId HOMESTAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final DateTimeFormatter DATE_TIME_VN = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

	public static String bookingCreatedSubject(Booking booking) {
		return "Tiếp nhận yêu cầu đặt phòng " + booking.getBookingCode() + " - HomeStay";
	}

	public static String bookingCreatedBody(Booking booking, String frontendUrl) {
		StringBuilder sb = new StringBuilder();
		sb.append("Xin chào ").append(booking.getGuestName()).append(",\n\n")
				.append("HomeStay đã tiếp nhận yêu cầu đặt phòng trực tuyến của bạn.\n\n")
				.append("Mã booking: ").append(booking.getBookingCode()).append("\n")
				.append("Loại phòng: ").append(booking.getRoomTypeNameSnapshot()).append("\n")
				.append("Ngày nhận phòng: ").append(booking.getCheckInDate().format(DATE_VN)).append(" (từ 14:00)\n")
				.append("Ngày trả phòng: ").append(booking.getCheckOutDate().format(DATE_VN)).append(" (trước 12:00)\n")
				.append("Số khách: ").append(booking.getGuestCount()).append(" người\n")
				.append("Tổng tiền tạm tính: ").append(formatMoneyVnd(booking.getTotalAmount())).append("\n\n");
		if (booking.getHoldExpiresAt() != null) {
			String expiryStr = booking.getHoldExpiresAt().atZoneSameInstant(HOMESTAY_ZONE).format(DATE_TIME_VN);
			sb.append("Thời hạn giữ chỗ đến: ").append(expiryStr).append(" (trong vòng 24 giờ).\n")
					.append("Vui lòng hoàn tất chuyển khoản đặt cọc trước thời hạn trên để lễ tân xác nhận giữ phòng.\n\n");
		}
		if (frontendUrl != null && !frontendUrl.isBlank()) {
			sb.append("Tra cứu thông tin đặt phòng tại: ").append(frontendUrl).append("/tra-cuu\n\n");
		}
		sb.append("Cảm ơn bạn đã lựa chọn HomeStay!");
		return sb.toString();
	}

	public void sendBookingCreatedEmail(Booking booking) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(booking.getGuestEmail() != null ? booking.getGuestEmail().trim() : "");
		message.setSubject(bookingCreatedSubject(booking));
		message.setText(bookingCreatedBody(booking, frontendUrl));
		mailSender.send(message);
	}

	public static String confirmationSubject(Booking booking) {
		return "Xác nhận đặt phòng thành công - Mã booking " + booking.getBookingCode();
	}

	public static String confirmationBody(Booking booking, Long depositAmount, String frontendUrl) {
		long deposit = depositAmount == null ? 0L : depositAmount;
		long remaining = Math.max(0L, booking.getTotalAmount() - deposit);
		StringBuilder sb = new StringBuilder();
		sb.append("Xin chào ").append(booking.getGuestName()).append(",\n\n")
				.append("HomeStay xin thông báo booking của bạn đã được XÁC NHẬN THÀNH CÔNG.\n\n")
				.append("Mã booking: ").append(booking.getBookingCode()).append("\n")
				.append("Loại phòng: ").append(booking.getRoomTypeNameSnapshot()).append("\n");
		if (booking.getRoom() != null && booking.getRoom().getRoomNumber() != null) {
			sb.append("Số phòng: ").append(booking.getRoom().getRoomNumber()).append("\n");
		}
		sb.append("Ngày nhận phòng: ").append(booking.getCheckInDate().format(DATE_VN)).append(" (từ 14:00)\n")
				.append("Ngày trả phòng: ").append(booking.getCheckOutDate().format(DATE_VN)).append(" (trước 12:00)\n")
				.append("Số khách: ").append(booking.getGuestCount()).append(" người\n")
				.append("Tổng tiền phòng: ").append(formatMoneyVnd(booking.getTotalAmount())).append("\n")
				.append("Tiền cọc đã ghi nhận: ").append(formatMoneyVnd(deposit)).append("\n")
				.append("Số tiền còn lại thanh toán khi nhận/trả phòng: ").append(formatMoneyVnd(remaining)).append("\n\n");
		if (frontendUrl != null && !frontendUrl.isBlank()) {
			sb.append("Tra cứu chi tiết booking tại: ").append(frontendUrl).append("/tra-cuu\n\n");
		}
		sb.append("Chúng tôi rất hân hạnh được đón tiếp bạn tại HomeStay!\n")
				.append("Nếu cần hỗ trợ thêm, vui lòng liên hệ lễ tân HomeStay.");
		return sb.toString();
	}

	public void sendBookingConfirmation(Booking booking, Long depositAmount) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(booking.getGuestEmail() != null ? booking.getGuestEmail().trim() : "");
		message.setSubject(confirmationSubject(booking));
		message.setText(confirmationBody(booking, depositAmount, frontendUrl));
		mailSender.send(message);
	}

	private static final Map<String, String> CANCEL_REASON_LABELS = Map.of(
			"KHACH_DOI_KE_HOACH", "Khách đổi kế hoạch",
			"KHACH_KHONG_LIEN_LAC", "Không liên lạc được với khách",
			"TRUNG_BOOKING", "Booking bị trùng",
			"LY_DO_KHAC", "Lý do khác");

	/** Lý do hiển thị cho khách; lý do chữ tự do cũ (S3-02) giữ nguyên văn. */
	public static String cancelReasonLabel(String reason) {
		if (reason == null || reason.isBlank()) {
			return "Không có";
		}
		return CANCEL_REASON_LABELS.getOrDefault(reason, reason);
	}

	/** Số nguyên VND, dạng 1.500.000 đ (không phụ thuộc cấu hình máy chủ). */
	public static String formatMoneyVnd(long amount) {
		DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
		symbols.setGroupingSeparator('.');
		return new DecimalFormat("#,##0", symbols).format(amount) + " đ";
	}

	public static String cancellationSubject(Booking booking) {
		return "Thông báo huỷ booking " + booking.getBookingCode();
	}

	public static String cancellationBody(Booking booking) {
		long deposit = booking.getCancelDepositAmount() == null ? 0L : booking.getCancelDepositAmount();
		int percent = booking.getCancelRefundPercent() == null ? 0 : booking.getCancelRefundPercent();
		long refund = booking.getCancelRefundAmount() == null ? 0L : booking.getCancelRefundAmount();
		String reason = booking.getCancelReason();
		String note = booking.getCancelNote();

		StringBuilder sb = new StringBuilder();
		sb.append("Xin chào ").append(booking.getGuestName()).append(",\n\n")
				.append("Booking ").append(booking.getBookingCode())
				.append(" (").append(booking.getRoomTypeNameSnapshot())
				.append(", nhận phòng ngày ").append(booking.getCheckInDate().format(DATE_VN))
				.append(") đã được huỷ.\n\n")
				.append("Lý do huỷ: ").append(cancelReasonLabel(reason)).append("\n");
		if ("LY_DO_KHAC".equals(reason) && note != null && !note.isBlank()) {
			sb.append("Chi tiết: ").append(note.trim()).append("\n");
		}
		sb.append("Tiền cọc đã ghi nhận: ").append(formatMoneyVnd(deposit)).append("\n")
				.append("Tỷ lệ hoàn cọc áp dụng: ").append(percent).append("%\n");
		if (refund > 0) {
			sb.append("Số tiền được hoàn: ").append(formatMoneyVnd(refund)).append("\n");
		} else if (deposit == 0) {
			sb.append("Số tiền được hoàn: 0 đ (booking chưa ghi nhận tiền cọc)\n");
		} else {
			sb.append("Số tiền được hoàn: Không hoàn cọc (0 đ)\n");
		}
		sb.append("\nXin lỗi vì sự bất tiện này. Vui lòng liên hệ HomeStay nếu bạn cần hỗ trợ thêm.");
		return sb.toString();
	}

	/** Như sendCheckInReminder: gửi lỗi thì ném MailException để nơi gọi ghi nhật ký. */
	public void sendCancellationEmail(Booking booking) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(booking.getGuestEmail());
		message.setSubject(cancellationSubject(booking));
		message.setText(cancellationBody(booking));
		mailSender.send(message);
	}
}
