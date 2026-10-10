package com.ttcs.homestay.dto;

import java.time.LocalDate;
import java.util.List;

public record RoomCalendarResponse(LocalDate startDate, LocalDate endDateExclusive,
        List<LocalDate> dates, List<Row> rooms) {
    public enum CellStatus { AVAILABLE, BOOKED, MAINTENANCE }
    public record Cell(LocalDate date, CellStatus status, String guestName, String bookingCode) {
        public Cell(LocalDate date, CellStatus status) { this(date, status, null, null); }
    }
    public record Row(Long roomId, String roomNumber, String roomType, List<Cell> cells) {}
}
