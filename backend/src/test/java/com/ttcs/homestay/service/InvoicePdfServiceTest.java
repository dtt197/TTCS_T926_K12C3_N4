package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lowagie.text.Element;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfReader;
import com.ttcs.homestay.dto.InvoiceData;
import com.ttcs.homestay.entity.BookingSettlement;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoicePdfServiceTest {
    private final InvoicePdfService service = new InvoicePdfService();

    @Test
    void generatesEmbeddedUnicodeA4PortraitInvoiceAndRepeatsTableHeaderAcrossPages() throws IOException {
        byte[] bytes = service.generate(sampleData(20));
        Path samplePdf = Path.of("target", "invoice-20-lines-test.pdf");
        Files.createDirectories(samplePdf.getParent());
        Files.write(samplePdf, bytes);
        PdfReader reader = new PdfReader(bytes);
        try {
            assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                assertThat(reader.getPageSize(page).getWidth()).isEqualTo(595f);
                assertThat(reader.getPageSize(page).getHeight()).isEqualTo(842f);
            }
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(page));
            }
            String extracted = text.toString();
            String secondPage = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(2);
            assertThat(extracted).contains("BK-44", "305", "Standard");
            assertThat(extracted).contains("10/10/2026", "12/10/2026", "2 đêm", "HÓA ĐƠN THANH TOÁN");
            assertThat(extracted).doesNotContain("Lưu ý:");
            assertThat(extracted).contains("Còn phải thu: 0 VND").doesNotContain("Đã thanh toán đủ:");
            assertThat(countOccurrences(extracted, "Nội dung")).isGreaterThan(1);
            assertThat(secondPage).contains("STT", "Nội dung", "Thành tiền (VND)");
            for (int i = 1; i <= 20; i++) {
                String lineId = String.format("%02d", i);
                assertThat(countOccurrences(extracted, "Mã dòng " + lineId))
                        .as("line %s appears exactly once", lineId).isEqualTo(1);
            }
            assertThat(extracted).contains("Còn phải thu", "Khách hàng", "Đại diện homestay", "Ký, ghi rõ họ tên");
            String pdfObjects = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
            assertThat(pdfObjects).contains("/FontFile2", "/FontFile", "NotoSans");
            assertThat(pdfObjects).doesNotContain("Arial");
            assertThat(extracted).contains("Nguyễn An", "Hà Nội", "phụ thu tiếng Việt");
        } finally {
            reader.close();
        }
    }

    @Test
    void formatsVndWithExplicitVietnameseSeparatorsRegardlessOfDefaultLocale() {
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.US);
            assertThat(InvoicePdfService.formatMoney(new BigDecimal("105000"))).isEqualTo("105.000 VND");
            assertThat(InvoicePdfService.formatMoney(new BigDecimal("1005000"))).isEqualTo("1.005.000 VND");
            assertThat(InvoicePdfService.formatMoney(new BigDecimal("1250000"))).isEqualTo("1.250.000 VND");
            assertThat(InvoicePdfService.formatMoney(new BigDecimal("1350000.0000"))).isEqualTo("1.350.000 VND");
            assertThatThrownBy(() -> InvoicePdfService.formatMoney(new BigDecimal("1234567.5")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("VND amounts must be whole numbers for invoice display.");
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    private static int countOccurrences(String text, String token) {
        return (text.length() - text.replace(token, "").length()) / token.length();
    }

    @Test
    void alignsMoneyCellsToTheRight() throws IOException {
        PdfPTable table = new PdfPTable(1);
        service.addMoneyCell(table, new BigDecimal("1250000"), 0);

        assertThat(table.getRow(0).getCells()[0].getHorizontalAlignment()).isEqualTo(Element.ALIGN_RIGHT);
        assertThat(table.getRow(0).getCells()[0].getPhrase().getContent()).isEqualTo("1.250.000 VND");
    }

    private static String extractAllPages(PdfReader reader) throws IOException {
        StringBuilder text = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            assertThat(reader.getPageSize(page).getWidth()).isEqualTo(595f);
            assertThat(reader.getPageSize(page).getHeight()).isEqualTo(842f);
            text.append(new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(page));
        }
        return text.toString();
    }

    @Test
    void rendersGroupedVndAmountsAndShowsSettlementMatchingInvoiceData() throws IOException {
        List<InvoiceData.LineItem> lines = List.of(
                new InvoiceData.LineItem("EXTRA_PERSON", "\u0054h\u00eam ng\u01b0\u1eddi (T\u1ef1 \u0111\u1ed9ng \u0111\u00e3 ch\u1ed1t: 1 kh\u00e1ch)", new BigDecimal("200000")),
                new InvoiceData.LineItem("ROOM", "Tiền phòng", new BigDecimal("1350000.0000")),
                new InvoiceData.LineItem("EXTRA", "Phụ thu đơn giá đã sửa: 200000 VND; hoàn tiền -300000", new BigDecimal("200000")),
                new InvoiceData.LineItem("ZERO", "Không phụ thu", BigDecimal.ZERO),
                new InvoiceData.LineItem("REFUND", "Điều chỉnh", new BigDecimal("-300000")),
                new InvoiceData.LineItem("CHECKOUT_PAYMENT", "Thu thêm khi trả phòng", new BigDecimal("950000")),
                new InvoiceData.LineItem("TOTAL_PAID", "Tổng đã thanh toán", new BigDecimal("1250000")),
                new InvoiceData.LineItem("BALANCE", "Còn phải thu", BigDecimal.ZERO),
                new InvoiceData.LineItem("PAYMENT_STATUS", "Đã thanh toán đủ", BigDecimal.ZERO));
        InvoiceData data = new InvoiceData(44L, "BK-44", "Nguyễn An", "0900000000", null, null,
                new InvoiceData.Homestay("Nhà nghỉ", "123 Đường A", "0901234567", null, null),
                new InvoiceData.Room(12L, "305", "Standard"), LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 12), 2, lines, new BigDecimal("1250000"), new BigDecimal("300000"),
                BookingSettlement.SettlementType.COLLECT, new BigDecimal("950000"), BigDecimal.ZERO, null, true,
                new BigDecimal("950000"), new BigDecimal("1250000"), null, null);
        byte[] bytes = service.generate(data);
        PdfReader reader = new PdfReader(bytes);
        try {
            String extracted = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(extracted).contains("1.350.000 VND", "200.000 VND", "0 VND", "-300.000 VND", "950.000 VND");
            assertThat(extracted).contains("Th\u00eam ng\u01b0\u1eddi", "T\u1ef1 \u0111\u1ed9ng \u0111\u00e3 ch\u1ed1t");
            assertThat(extracted).doesNotContain("1350000.0000", "1350000 VND");
            assertThat(extracted).contains("B\u1eb1ng ch\u1eef:", "M\u1ed9t tri\u1ec7u hai tr\u0103m n\u0103m m\u01b0\u01a1i ngh\u00ecn \u0111\u1ed3ng");
            assertThat(extracted).doesNotContain("B\u1eb1ng ch\u1eef: Ch\u00edn tr\u0103m n\u0103m m\u01b0\u01a1i ngh\u00ecn \u0111\u1ed3ng");
            assertThat(extracted).contains("200.000 VND; hoàn tiền -300.000 VND");
            assertThat(countOccurrences(extracted, "Đã thanh toán đủ")).isEqualTo(1);
            assertThat(extracted).contains("Đã thanh toán đủ: 1.250.000 VND").doesNotContain("Còn phải thu: 0 VND");
        } finally {
            reader.close();
        }
    }

    @Test
    void displaysCheckoutCollectionPaidTotalAndZeroBalanceSeparately() throws IOException {
        InvoiceData data = new InvoiceData(44L, "BK-44", "Nguyễn An", "0900000000", null, null,
                new InvoiceData.Homestay("Nhà nghỉ", "123 Đường A", "0901234567", null, null),
                new InvoiceData.Room(12L, "305", "Standard"), LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 12), 2, List.of(
                        new InvoiceData.LineItem("TOTAL_BEFORE_DEPOSIT", "Tổng tiền trước cọc", new BigDecimal("800000")),
                        new InvoiceData.LineItem("DEPOSIT", "Tiền cọc đã nhận", new BigDecimal("-600000")),
                        new InvoiceData.LineItem("CHECKOUT_PAYMENT", "Thu thêm khi trả phòng", new BigDecimal("200000")),
                        new InvoiceData.LineItem("TOTAL_PAID", "Tổng đã thanh toán", new BigDecimal("800000")),
                        new InvoiceData.LineItem("BALANCE", "Còn phải thu", BigDecimal.ZERO),
                        new InvoiceData.LineItem("PAYMENT_STATUS", "Đã thanh toán đủ", BigDecimal.ZERO)),
                new BigDecimal("800000"), new BigDecimal("600000"), BookingSettlement.SettlementType.COLLECT,
                new BigDecimal("200000"), BigDecimal.ZERO, null, false, new BigDecimal("200000"),
                new BigDecimal("800000"), null, null);

        PdfReader reader = new PdfReader(service.generate(data));
        try {
            String extracted = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(extracted).contains("Tổng tiền trước cọc", "800.000 VND", "Tiền cọc đã nhận", "-600.000 VND",
                    "Thu thêm khi trả phòng", "200.000 VND", "Tổng đã thanh toán", "Đã thanh toán đủ",
                    "Đã thanh toán đủ: 800.000 VND", "Tám trăm nghìn đồng");
            assertThat(extracted).doesNotContain("Đã thanh toán đủ: 0 VND", "Còn phải thu: 0 VND");
        } finally {
            reader.close();
        }
    }

    @Test
    void wrapsLongDescriptionsAndPreservesSettlementBalance() throws IOException {
        String longDescription = "Thêm người (Phụ thu tự động đã chốt: 1 khách × 100.000 VND/khách/đêm × 2 đêm; "
                + "Phụ thu phát sinh: ".concat("ghi chú dài với nhiều nội dung tiếng Việt ".repeat(30)) + ")";
        InvoiceData data = new InvoiceData(44L, "BK-44", "Nguyễn An", "0900000000", null, null,
                new InvoiceData.Homestay("Nhà nghỉ", "123 Đường A", "0901234567", null, null),
                new InvoiceData.Room(12L, "305", "Standard"), LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 12), 2, List.of(
                        new InvoiceData.LineItem("ROOM", "Tiền phòng cơ bản (chưa gồm phụ thu)", new BigDecimal("1000000")),
                        new InvoiceData.LineItem("EXTRA_PERSON", longDescription, new BigDecimal("200000")),
                        new InvoiceData.LineItem("TOTAL_PAID", "Tổng đã thanh toán", new BigDecimal("1200000")),
                        new InvoiceData.LineItem("BALANCE", "Còn phải thu", BigDecimal.ZERO),
                        new InvoiceData.LineItem("PAYMENT_STATUS", "Đã thanh toán đủ", BigDecimal.ZERO)),
                new BigDecimal("1200000"), new BigDecimal("500000"),
                BookingSettlement.SettlementType.COLLECT, new BigDecimal("700000"), BigDecimal.ZERO, null, false,
                new BigDecimal("700000"), new BigDecimal("1200000"), null, null);

        byte[] pdf = service.generate(data);
        PdfReader reader = new PdfReader(pdf);
        try {
            String extracted = extractAllPages(reader);
            assertThat(extracted).contains("1.000.000 VND", "200.000 VND", "Đã thanh toán đủ: 1.200.000 VND").doesNotContain("Còn phải thu: 0 VND");
            assertThat(extracted).contains("B\u1eb1ng ch\u1eef:", "M\u1ed9t tri\u1ec7u hai tr\u0103m ngh\u00ecn \u0111\u1ed3ng");
            assertThat(extracted).contains("1.000.000 VND", "200.000 VND", "Đã thanh toán đủ: 1.200.000 VND").doesNotContain("Còn phải thu: 0 VND");
            assertThat(extracted).contains("ghi chú dài");
        } finally {
            reader.close();
        }
    }

    private static InvoiceData sampleData(int lineCount) {
        List<InvoiceData.LineItem> lines = new ArrayList<>();
        for (int i = 1; i <= lineCount; i++) {
            String lineId = String.format("%02d", i);
            lines.add(new InvoiceData.LineItem("ITEM_" + lineId, "Mã dòng " + lineId + " – phụ thu tiếng Việt. "
                    .repeat(7), BigDecimal.valueOf(i * 1000L)));
        }
        lines.add(new InvoiceData.LineItem("TOTAL_PAID", "Tổng đã thanh toán", BigDecimal.ZERO));
        lines.add(new InvoiceData.LineItem("BALANCE", "Còn phải thu", BigDecimal.ZERO));
        return new InvoiceData(44L, "BK-44", "Nguyễn An", "0900000000", "Công ty Mẫu", "0101234567",
                new InvoiceData.Homestay("Nhà nghỉ", "123 Đường A, Hà Nội", "0901234567", null, "0109999999"),
                new InvoiceData.Room(12L, "305", "Standard"), LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 12), 2, lines, BigDecimal.valueOf(1_300_000), BigDecimal.valueOf(300_000),
                BookingSettlement.SettlementType.COLLECT, BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(1_000_000), null,
                true);
    }
}
