package com.ttcs.homestay.service;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Converts whole VND amounts to Vietnamese words without rounding the source amount. */
final class VietnameseCurrencyWords {
    private static final String[] DIGITS = {
        "không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"
    };
    private static final String[] SCALES = {
        "", "nghìn", "triệu", "tỷ", "nghìn tỷ", "triệu tỷ", "tỷ tỷ"
    };

    private VietnameseCurrencyWords() {}

    static String format(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("Số tiền không được để trống.");
        final BigInteger whole;
        try {
            whole = amount.toBigIntegerExact();
        } catch (ArithmeticException fractionalVnd) {
            throw new IllegalArgumentException("Số tiền VND phải là số nguyên để ghi bằng chữ.", fractionalVnd);
        }
        return formatWhole(whole);
    }

    private static String formatWhole(BigInteger whole) {
        if (whole.signum() == 0) return "Không đồng";
        if (whole.signum() < 0) return "Âm " + formatWhole(whole.negate());

        String digits = whole.toString();
        int groupCount = (digits.length() + 2) / 3;
        if (groupCount > 10) throw new IllegalArgumentException("Số tiền vượt phạm vi có thể ghi bằng chữ.");
        int padding = groupCount * 3 - digits.length();
        digits = "0".repeat(padding) + digits;

        StringBuilder words = new StringBuilder();
        boolean hasPreviousGroup = false;
        for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
            int offset = groupIndex * 3;
            int group = (digits.charAt(offset) - '0') * 100
                    + (digits.charAt(offset + 1) - '0') * 10
                    + digits.charAt(offset + 2) - '0';
            if (group == 0) continue;
            if (hasPreviousGroup && group < 100) words.append(" không trăm");
            words.append(' ').append(readGroup(group, hasPreviousGroup));
            String scale = SCALES[groupCount - groupIndex - 1];
            if (!scale.isEmpty()) words.append(' ').append(scale);
            hasPreviousGroup = true;
        }
        return capitalize(words.toString().trim()) + " đồng";
    }

    private static String readGroup(int number, boolean hasPreviousGroup) {
        int hundreds = number / 100;
        int tens = (number / 10) % 10;
        int units = number % 10;
        StringBuilder words = new StringBuilder();
        if (hundreds > 0) words.append(DIGITS[hundreds]).append(" trăm");
        if (tens > 1) {
            words.append(' ').append(DIGITS[tens]).append(" mươi");
            if (units == 1) words.append(" mốt");
            else if (units == 4) words.append(" tư");
            else if (units == 5) words.append(" lăm");
            else if (units > 0) words.append(' ').append(DIGITS[units]);
        } else if (tens == 1) {
            words.append(" mười");
            if (units == 5) words.append(" lăm");
            else if (units > 0) words.append(' ').append(DIGITS[units]);
        } else if (units > 0) {
            if (hundreds > 0 || hasPreviousGroup) words.append(" lẻ");
            words.append(' ').append(DIGITS[units]);
        }
        return words.toString().trim();
    }

    private static String capitalize(String words) {
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
