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

@Service
public class MailService {

	private static final Logger log = LoggerFactory.getLogger(MailService.class);

	private final JavaMailSender mailSender;
	private final String from;
	private final String frontendUrl;

	public MailService(JavaMailSender mailSender,
			@Value("${app.mail.from}") String from,
			@Value("${app.frontend-url}") String frontendUrl) {
		this.mailSender = mailSender;
		this.from = from;
		this.frontendUrl = frontendUrl;
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
				+ "Đăng nhập tại: " + frontendUrl + "\n"
				+ "Bạn sẽ được yêu cầu đổi mật khẩu ở lần đăng nhập đầu tiên.");
		try {
			mailSender.send(message);
		} catch (MailException e) {
			log.warn("Không gửi được email mật khẩu tạm tới {}: {}", user.getEmail(), e.getMessage());
		}
	}
	public void sendPasswordResetLink(User user, String rawToken) {
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
		message.setTo(booking.getGuestEmail());
		message.setSubject(reminderSubject(booking));
		message.setText(reminderBody(booking));
		mailSender.send(message);
	}
}