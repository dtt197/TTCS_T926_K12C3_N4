package com.ttcs.homestay.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.UserRepository;

@Service
public class NotificationRecipientService {

    private static final List<String> RECIPIENT_ROLE_CODES = List.of("OWNER", "RECEPTIONIST");

    private final UserRepository userRepository;

    public NotificationRecipientService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<User> findRecipients() {
        return userRepository.findActiveRecipientsByRoleCodes(RECIPIENT_ROLE_CODES);
    }
}
