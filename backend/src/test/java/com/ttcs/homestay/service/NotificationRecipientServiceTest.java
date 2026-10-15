package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class NotificationRecipientServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void selectsActiveOwnerAndReceptionistIncludingActor_andPreservesStableUniqueRepositoryResult() {
        User receptionist = mock(User.class);
        User owner = mock(User.class);
        when(receptionist.getId()).thenReturn(2L);
        when(owner.getId()).thenReturn(5L);
        List<User> expected = List.of(receptionist, owner);
        when(userRepository.findActiveRecipientsByRoleCodes(List.of("OWNER", "RECEPTIONIST")))
                .thenReturn(expected);

        List<User> recipients = new NotificationRecipientService(userRepository).findRecipients();

        assertThat(recipients).containsExactly(receptionist, owner);
        assertThat(recipients.get(0).getId()).isEqualTo(2L);
        assertThat(recipients.get(1).getId()).isEqualTo(5L);
        assertThat(recipients).doesNotHaveDuplicates();
        verify(userRepository).findActiveRecipientsByRoleCodes(List.of("OWNER", "RECEPTIONIST"));
    }

    @Test
    void forwardsOnlyRepositorySelectedUsersSoInactiveAndOtherRolesAreExcludedByQuery() {
        User activeOwner = mock(User.class);
        when(userRepository.findActiveRecipientsByRoleCodes(List.of("OWNER", "RECEPTIONIST")))
                .thenReturn(List.of(activeOwner));

        List<User> recipients = new NotificationRecipientService(userRepository).findRecipients();

        assertThat(recipients).containsExactly(activeOwner);
    }
}
