package com.ttcs.homestay.exception;

/** S1-09 AC3: mốc huỷ chồng lấn / không giảm dần, hoặc giờ nhận – trả phòng không hợp lý. */
public class InvalidSettingsException extends RuntimeException {

    public InvalidSettingsException(String message) {
        super(message);
    }
}