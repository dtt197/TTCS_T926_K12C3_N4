package com.ttcs.homestay.dto.booking;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GuestBookingRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private GuestBookingRequest request(
            String phone,
            String email,
            Boolean acceptedPolicy) {

        return new GuestBookingRequest(
                1L,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(7),
                "Nguyễn Văn A",
                phone,
                email,
                2,
                null,
                acceptedPolicy
        );
    }

    @Test
    void soDienThoai10SoHopLe() {
        var violations = validator.validate(
                request("0912345678", "quang@gmail.com", true)
        );

        assertThat(violations)
                .noneMatch(v ->
                        v.getPropertyPath().toString().equals("phone"));
    }

    @Test
    void soDienThoaiThieuSo() {
        var violations = validator.validate(
                request("091234567", "quang@gmail.com", true)
        );

        assertThat(violations)
                .anyMatch(v ->
                        v.getPropertyPath().toString().equals("phone"));
    }

    @Test
    void soDienThoaiThuaSo() {
        var violations = validator.validate(
                request("09123456789", "quang@gmail.com", true)
        );

        assertThat(violations)
                .anyMatch(v ->
                        v.getPropertyPath().toString().equals("phone"));
    }

    @Test
    void emailHopLe() {
        var violations = validator.validate(
                request("0912345678", "quang@gmail.com", true)
        );

        assertThat(violations)
                .noneMatch(v ->
                        v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void emailSaiDinhDang() {
        var violations = validator.validate(
                request("0912345678", "quang@", true)
        );

        assertThat(violations)
                .anyMatch(v ->
                        v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void chuaXacNhanChinhSach() {
        var violations = validator.validate(
                request("0912345678", "quang@gmail.com", false)
        );

        assertThat(violations)
                .anyMatch(v ->
                        v.getPropertyPath().toString()
                                .equals("acceptedCancellationPolicy"));
    }

    @Test
    void nhieuTruongSaiCungLuc() {
        var violations = validator.validate(
                request("091234567", "quang@", false)
        );

        assertThat(violations)
                .anyMatch(v ->
                        v.getPropertyPath().toString().equals("phone"));

        assertThat(violations)
                .anyMatch(v ->
                        v.getPropertyPath().toString().equals("email"));

        assertThat(violations)
                .anyMatch(v ->
                        v.getPropertyPath().toString()
                                .equals("acceptedCancellationPolicy"));
    }
}