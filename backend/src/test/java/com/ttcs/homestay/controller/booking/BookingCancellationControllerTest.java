package com.ttcs.homestay.controller.booking;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.service.BookingCancellationService;
import com.ttcs.homestay.service.CancellationEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class BookingCancellationControllerTest {

    private BookingCancellationService cancellationService;
    private CancellationEmailService emailService;
    private BookingCancellationController controller;

    @BeforeEach
    void setUp() {
        cancellationService = mock(BookingCancellationService.class);
        emailService = mock(CancellationEmailService.class);
        controller = new BookingCancellationController(cancellationService, emailService);
    }

    @Test
    void sendsEmailAfterSuccessfulCancel() {
        controller.cancel("BK1", null);

        verify(emailService).sendCancellationEmail("BK1");
    }

    @Test
    void cancellingAgainDoesNotSendSecondEmail() {
        when(cancellationService.cancel(eq("BK1"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Booking đã được huỷ trước đó"));

        assertThrows(ResponseStatusException.class, () -> controller.cancel("BK1", null));

        verify(emailService, never()).sendCancellationEmail(any());
    }
}