package com.ttcs.homestay.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.ttcs.homestay.entity.User;

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
}