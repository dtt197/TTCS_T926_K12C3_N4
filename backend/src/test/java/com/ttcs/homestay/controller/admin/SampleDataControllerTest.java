package com.ttcs.homestay.controller.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.service.SampleDataService;
import com.ttcs.homestay.service.SampleDataService.SampleDataSummary;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** S4-09: chỉ nạp lại dữ liệu mẫu khi bật chế độ demo (staging), máy cá nhân và hệ thống bàn giao bị từ chối. */
class SampleDataControllerTest {

    @Test
    void tatCheDoDemoThiTuChoiVaKhongXoaDuLieu() {
        SampleDataService service = mock(SampleDataService.class);
        SampleDataController controller = new SampleDataController(service, false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, controller::reload);

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(service, never()).reload();
    }

    @Test
    void batCheDoDemoThiNapLai() {
        SampleDataService service = mock(SampleDataService.class);
        SampleDataSummary summary = mock(SampleDataSummary.class);
        when(service.reload()).thenReturn(summary);
        SampleDataController controller = new SampleDataController(service, true);

        assertEquals(summary, controller.reload().getBody());
        verify(service).reload();
    }
}