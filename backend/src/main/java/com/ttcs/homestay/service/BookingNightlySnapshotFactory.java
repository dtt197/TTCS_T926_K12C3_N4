package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.pricing.NightlyPrice;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingNightlyPriceSnapshot;
import java.util.ArrayList;
import java.util.List;

public final class BookingNightlySnapshotFactory {
    private BookingNightlySnapshotFactory() {}

    public static void replace(Booking booking, List<NightlyPrice> prices,
            long surchargePerNight) {
        List<BookingNightlyPriceSnapshot> result = new ArrayList<>();
        for (NightlyPrice price : prices) {
            BookingNightlyPriceSnapshot line = new BookingNightlyPriceSnapshot();
            line.setBooking(booking);
            line.setNightDate(price.date());
            line.setPriceType(price.priceType().name());
            line.setPriceLabel(price.label());
            line.setRoomPrice(price.price() == null ? 0 : price.price());
            line.setSurchargeAmount(surchargePerNight);
            result.add(line);
        }
        booking.getNightlyPriceSnapshots().clear();
        booking.getNightlyPriceSnapshots().addAll(result);
    }
}
