package com.ttcs.homestay.exception;

/** S1-06 AC2: sức chứa tối đa không được nhỏ hơn sức chứa tiêu chuẩn. */
public class InvalidRoomTypeCapacityException extends RuntimeException {

    public InvalidRoomTypeCapacityException(int standardCapacity, int maxCapacity) {
        super("Sức chứa tối đa (" + maxCapacity + ") không được nhỏ hơn sức chứa tiêu chuẩn ("
                + standardCapacity + ")");
    }
}