package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VietnameseCurrencyWordsTest {
    @Test
    void formatsZeroAndVietnameseTensAndHundreds() {
        assertThat(VietnameseCurrencyWords.format(new BigDecimal("0"))).isEqualTo("Không đồng");
        assertThat(VietnameseCurrencyWords.format(new BigDecimal("101"))).isEqualTo("Một trăm lẻ một đồng");
        assertThat(VietnameseCurrencyWords.format(new BigDecimal("1005"))).isEqualTo("Một nghìn không trăm lẻ năm đồng");
        assertThat(VietnameseCurrencyWords.format(new BigDecimal("21001"))).isEqualTo("Hai mươi mốt nghìn không trăm lẻ một đồng");
        assertThat(VietnameseCurrencyWords.format(new BigDecimal("120045"))).isEqualTo("Một trăm hai mươi nghìn không trăm bốn mươi lăm đồng");
    }

    @Test
    void formatsLargeAmountsAndNegativeAdjustmentsExactly() {
        assertThat(VietnameseCurrencyWords.format(new BigDecimal("1000000000")))
                .isEqualTo("Một tỷ đồng");
        assertThat(VietnameseCurrencyWords.format(new BigDecimal("1234567890123")))
                .isEqualTo("Một nghìn tỷ hai trăm ba mươi tư tỷ năm trăm sáu mươi bảy triệu tám trăm chín mươi nghìn một trăm hai mươi ba đồng");
        assertThat(VietnameseCurrencyWords.format(new BigDecimal("-2500")))
                .isEqualTo("Âm Hai nghìn năm trăm đồng");
    }

    @Test
    void rejectsFractionalVndInsteadOfSilentlyRounding() {
        assertThatThrownBy(() -> VietnameseCurrencyWords.format(new BigDecimal("12.5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Số tiền VND phải là số nguyên để ghi bằng chữ.");
    }
}
