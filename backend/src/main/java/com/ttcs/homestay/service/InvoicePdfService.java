package com.ttcs.homestay.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.ttcs.homestay.dto.InvoiceData;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** Renders the already-assembled invoice snapshot without recalculating business amounts. */
@Service
public class InvoicePdfService {
    private static final Logger log = LoggerFactory.getLogger(InvoicePdfService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color DEEP_GREEN = new Color(29, 77, 57);
    private static final Color LEAF_GREEN = new Color(79, 133, 96);
    private static final Color PALE_GREEN = new Color(229, 239, 230);
    private static final Color SOFT_GREEN = new Color(245, 249, 245);
    private static final Color LINE_GREEN = new Color(194, 215, 198);
    private static final Pattern DESCRIBED_MONEY = Pattern.compile("(?<![\\p{L}\\d.,])-?\\d+(?:[.,]\\d+)?\\s*(?:VND|đ)(?![\\p{L}])|(?<![\\p{L}\\d.,])-\\d+(?:[.,]\\d+)?(?![\\p{L}\\d.,])", Pattern.CASE_INSENSITIVE);
    private final BaseFont baseFont;

    public InvoicePdfService() {
        try (InputStream fontStream = new ClassPathResource("NotoSans-Regular.ttf").getInputStream()) {
            byte[] bytes = fontStream.readAllBytes();
            baseFont = BaseFont.createFont("NotoSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
        } catch (IOException | DocumentException exception) {
            throw new IllegalStateException("Không thể tải font Unicode dùng cho hóa đơn PDF.", exception);
        }
    }

    public byte[] generate(InvoiceData data) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 40, 48);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new InvoicePageDecoration());
            document.open();
            addHeader(document, data);
            addBookingInfo(document, data);
            addItems(document, data);
            addSignatures(document);
            document.close();
            return output.toByteArray();
        } catch (DocumentException exception) {
            throw new IllegalStateException("Không thể tạo hóa đơn PDF.", exception);
        }
    }

    private void addHeader(Document document, InvoiceData data) throws DocumentException {
        InvoiceData.Homestay home = data.homestay();
        PdfPTable top = new PdfPTable(new float[] {1.25f, 5.75f});
        top.setWidthPercentage(100);
        top.setSpacingAfter(8);
        top.addCell(configuredLogoIsReadable(home.logoUrl()) ? logoCell(home.logoUrl()) : vectorMark());
        PdfPCell identity = noBorderCell();
        identity.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph name = paragraph(value(home.name()), 18, Font.BOLD);
        name.getFont().setColor(DEEP_GREEN);
        name.setSpacingAfter(4);
        identity.addElement(name);
        Paragraph contacts = paragraph("Địa chỉ: " + value(home.address()) + "\nSĐT: " + value(home.phone())
                + optional("  |  MST: ", home.taxCode()), 9, Font.NORMAL);
        contacts.getFont().setColor(Color.DARK_GRAY);
        identity.addElement(contacts);
        top.addCell(identity);
        document.add(top);

        Paragraph title = paragraph("HÓA ĐƠN THANH TOÁN", 18, Font.BOLD);
        title.getFont().setColor(DEEP_GREEN);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(10);
        title.setSpacingAfter(3);
        document.add(title);
        if (data.invoiceNumber() != null && data.issuedDate() != null) {
            Paragraph issuance = paragraph("Số hóa đơn: " + data.invoiceNumber() + "    Ngày xuất: "
                    + DATE.format(data.issuedDate()), 10, Font.BOLD);
            issuance.setAlignment(Element.ALIGN_CENTER);
            issuance.setSpacingAfter(4);
            document.add(issuance);
        }
        PdfPTable ornament = new PdfPTable(1);
        ornament.setWidthPercentage(100);
        ornament.setSpacingAfter(12);
        PdfPCell divider = noBorderCell();
        divider.setFixedHeight(12);
        divider.setCellEvent((cell, rect, canvases) -> {
            PdfContentByte c = canvases[PdfPTable.LINECANVAS];
            c.saveState(); c.setColorStroke(LEAF_GREEN); c.setLineWidth(1f);
            float mid = (rect.getLeft() + rect.getRight()) / 2;
            c.moveTo(mid - 44, rect.getBottom() + 6); c.lineTo(mid - 7, rect.getBottom() + 6);
            c.moveTo(mid + 7, rect.getBottom() + 6); c.lineTo(mid + 44, rect.getBottom() + 6); c.stroke();
            c.setColorFill(LEAF_GREEN); c.ellipse(mid - 5, rect.getBottom() + 3, mid + 5, rect.getBottom() + 10); c.fill();
            c.restoreState();
        });
        ornament.addCell(divider);
        document.add(ornament);
    }

    private PdfPCell vectorMark() {
        PdfPCell cell = noBorderCell();
        cell.setFixedHeight(72);
        cell.setCellEvent((c, rect, canvases) -> {
            PdfContentByte canvas = canvases[PdfPTable.BACKGROUNDCANVAS];
            float cx = (rect.getLeft() + rect.getRight()) / 2;
            float cy = rect.getBottom() + 34;
            canvas.saveState(); canvas.setColorStroke(DEEP_GREEN); canvas.setLineWidth(2.8f);
            canvas.moveTo(cx - 25, cy - 11); canvas.lineTo(cx - 25, cy + 13);
            canvas.lineTo(cx, cy + 34); canvas.lineTo(cx + 25, cy + 13); canvas.lineTo(cx + 25, cy - 11); canvas.stroke();
            canvas.setColorFill(PALE_GREEN); canvas.rectangle(cx - 19, cy - 9, 38, 19); canvas.fill();
            canvas.setColorStroke(DEEP_GREEN); canvas.setLineWidth(1.8f);
            canvas.moveTo(cx - 8, cy - 10); canvas.lineTo(cx - 8, cy + 5); canvas.lineTo(cx + 8, cy + 5); canvas.lineTo(cx + 8, cy - 10); canvas.stroke();
            canvas.setColorStroke(LEAF_GREEN); canvas.setLineWidth(2.2f);
            canvas.moveTo(cx, cy + 8); canvas.curveTo(cx - 2, cy + 17, cx + 3, cy + 24, cx + 1, cy + 31); canvas.stroke();
            canvas.setColorFill(LEAF_GREEN); canvas.ellipse(cx - 13, cy + 16, cx - 1, cy + 24); canvas.fill();
            canvas.ellipse(cx + 1, cy + 22, cx + 13, cy + 30); canvas.fill();
            canvas.restoreState();
        });
        return cell;
    }

    private boolean configuredLogoIsReadable(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) return false;
        try {
            Path path = Path.of(configuredPath);
            Path normalized = path.normalize();
            if (path.isAbsolute() || normalized.startsWith("..") || configuredPath.contains("://")
                    || !normalized.startsWith(Path.of("uploads"))) {
                log.warn("Skipping invoice logo: only files under uploads/ are allowed");
                return false;
            }
            Path root = Path.of("uploads").toAbsolutePath().normalize();
            Path resolved = root.resolve(normalized.subpath(1, normalized.getNameCount())).normalize();
            return resolved.startsWith(root) && Files.isRegularFile(resolved) && Files.isReadable(resolved);
        } catch (RuntimeException exception) {
            log.warn("Skipping invalid configured invoice logo path", exception);
            return false;
        }
    }

    private PdfPCell logoCell(String configuredPath) throws DocumentException {
        try {
            Path normalized = Path.of(configuredPath).normalize();
            Path resolved = Path.of("uploads").toAbsolutePath().normalize()
                    .resolve(normalized.subpath(1, normalized.getNameCount())).normalize();
            Image logo = Image.getInstance(Files.readAllBytes(resolved));
            logo.scaleToFit(100, 64);
            PdfPCell cell = new PdfPCell(logo, true);
            cell.setBorder(PdfPCell.NO_BORDER);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            return cell;
        } catch (IOException | RuntimeException exception) {
            log.warn("Skipping unreadable configured invoice logo", exception);
            return vectorMark();
        }
    }

    private void addBookingInfo(Document document, InvoiceData data) throws DocumentException {
        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        info.setSpacingAfter(11);
        info.setKeepTogether(true);
        info.addCell(infoCell("Mã booking", value(data.bookingCode())));
        info.addCell(infoCell("Khách hàng", value(data.guestName()) + optional("  |  ", data.guestPhone())));
        boolean company = data.companyName() != null && !data.companyName().isBlank()
                || data.companyTaxCode() != null && !data.companyTaxCode().isBlank();
        if (company) info.addCell(infoCell("Công ty / MST", value(data.companyName()) + optional("  |  ", data.companyTaxCode())));
        info.addCell(infoCell("Phòng", value(data.room().number()) + " (" + value(data.room().typeName()) + ")"));
        info.addCell(infoCell("Nhận phòng", DATE.format(data.checkInDate())));
        info.addCell(infoCell("Trả phòng", DATE.format(data.checkOutDate())));
        info.addCell(infoCell("Số đêm", data.nights() + " đêm"));
        if (company) info.addCell(blankInfoCell());
        document.add(info);
    }

    private PdfPCell blankInfoCell() {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(8);
        cell.setBorderColor(LINE_GREEN);
        cell.setBackgroundColor(SOFT_GREEN);
        return cell;
    }

    private PdfPCell infoCell(String label, String value) {
        PdfPCell cell = new PdfPCell(); cell.setPadding(8); cell.setBorderColor(LINE_GREEN); cell.setBackgroundColor(SOFT_GREEN);
        Paragraph p = paragraph(label + "\n", 8, Font.NORMAL); p.getFont().setColor(LEAF_GREEN);
        Phrase val = new Phrase(value, font(10, Font.BOLD)); val.getFont().setColor(DEEP_GREEN); p.add(val);
        cell.addElement(p); return cell;
    }

    private void addItems(Document document, InvoiceData data) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[] {0.65f, 4.15f, 1.5f});
        table.setWidthPercentage(100); table.setHeaderRows(1); table.setSplitRows(true); table.setSplitLate(false);
        addCell(table, "STT", true, Element.ALIGN_CENTER, 0);
        addCell(table, "Nội dung", true, Element.ALIGN_LEFT, 0);
        addCell(table, "Thành tiền (VND)", true, Element.ALIGN_RIGHT, 0);
        int index = 1;
        for (InvoiceData.LineItem item : data.lineItems()) {
            if ("TOTAL_PAID".equals(item.code()) || "BALANCE".equals(item.code())
                    || "PAYMENT_STATUS".equals(item.code())) continue;
            int stripe = index % 2;
            addCell(table, Integer.toString(index++), false, Element.ALIGN_CENTER, stripe);
            addCell(table, formatMoneyInDescription(item.description()), false, Element.ALIGN_LEFT, stripe);
            addMoneyCell(table, item.amount(), stripe);
        }
        document.add(table);
        PdfPTable conclusion = new PdfPTable(new float[] {0.65f, 4.15f, 1.5f});
        conclusion.setWidthPercentage(100);
        conclusion.setKeepTogether(true); conclusion.setSpacingBefore(10);
        boolean paidInFull = data.remainingAmount() != null && data.remainingAmount().signum() == 0
                && data.settlementType() != com.ttcs.homestay.entity.BookingSettlement.SettlementType.REFUND;
        InvoiceData.LineItem balanceItem = null;
        for (InvoiceData.LineItem item : data.lineItems()) {
            if ("TOTAL_PAID".equals(item.code())) {
                addCell(conclusion, "", false, Element.ALIGN_CENTER, 0);
                addCell(conclusion, item.description(), false, Element.ALIGN_LEFT, 0);
                addMoneyCell(conclusion, item.amount(), 0);
            } else if ("BALANCE".equals(item.code())) {
                balanceItem = item;
            }
        }
        for (InvoiceData.LineItem item : data.lineItems()) {
            if ("PAYMENT_STATUS".equals(item.code())) {
                if (paidInFull) {
                    PdfPCell status = new PdfPCell(new Phrase(item.description() + ": " + money(data.totalPaid()), font(9, Font.BOLD)));
                    status.setColspan(3); status.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    status.setPadding(5); status.setBorder(PdfPCell.NO_BORDER);
                    conclusion.addCell(status);
                }
            }
        }
        if (balanceItem != null && !(paidInFull && balanceItem.description().startsWith("Còn phải thu"))) {
            PdfPCell balance = new PdfPCell(new Phrase(
                    balanceLabel(data) + ": " + money(balanceItem.amount().abs()), font(12, Font.BOLD)));
            balance.setColspan(3); balance.setHorizontalAlignment(Element.ALIGN_RIGHT);
            balance.setPadding(9); balance.setBorderColor(LINE_GREEN);
            conclusion.addCell(balance);
        }
        PdfPTable balanceTable = new PdfPTable(1);
        balanceTable.setWidthPercentage(100); balanceTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        balanceTable.setKeepTogether(true);
        Paragraph totalInWords = paragraph("Bằng chữ: " + VietnameseCurrencyWords.format(data.finalTotal()), 9, Font.ITALIC);
        totalInWords.setAlignment(Element.ALIGN_RIGHT);
        totalInWords.setSpacingBefore(4);
        totalInWords.setKeepTogether(true);
        PdfPCell wordsCell = new PdfPCell();
        wordsCell.addElement(totalInWords);
        wordsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        wordsCell.setBorder(PdfPCell.NO_BORDER);
        wordsCell.setPadding(4);
        balanceTable.addCell(wordsCell);
        document.add(conclusion);
        if (balanceTable.size() > 0) document.add(balanceTable);
    }

    private void addSignatures(Document document) throws DocumentException {
        PdfPTable signatures = new PdfPTable(2); signatures.setWidthPercentage(100);
        signatures.setSpacingBefore(20); signatures.setKeepTogether(true);
        signatures.addCell(signatureCell("Khách hàng", "(Ký, ghi rõ họ tên)"));
        signatures.addCell(signatureCell("Đại diện homestay", "(Ký, ghi rõ họ tên)"));
        document.add(signatures);
    }

    private PdfPCell signatureCell(String title, String hint) {
        PdfPCell cell = noBorderCell(); cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_TOP); cell.setFixedHeight(100);
        Paragraph heading = paragraph(title, 10, Font.BOLD); heading.setAlignment(Element.ALIGN_CENTER); cell.addElement(heading);
        Paragraph space = paragraph("\n\n\n", 10, Font.NORMAL); space.setAlignment(Element.ALIGN_CENTER); cell.addElement(space);
        Paragraph line = paragraph("____________________________", 10, Font.NORMAL); line.setAlignment(Element.ALIGN_CENTER); cell.addElement(line);
        Paragraph note = paragraph(hint, 8, Font.ITALIC); note.setAlignment(Element.ALIGN_CENTER); cell.addElement(note);
        return cell;
    }

    private static class InvoicePageDecoration extends PdfPageEventHelper {
        @Override public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContentUnder(); canvas.saveState();
            float right = document.right(), top = document.top();
            canvas.setColorStroke(new Color(207, 225, 210)); canvas.setLineWidth(1.1f);
            canvas.moveTo(right - 34, top + 2); canvas.curveTo(right - 2, top + 28, right + 5, top + 57, right + 22, top + 75);
            canvas.moveTo(right - 22, top + 21); canvas.lineTo(right - 33, top + 32);
            canvas.moveTo(right - 11, top + 39); canvas.lineTo(right + 1, top + 48); canvas.stroke();
            canvas.setColorFill(new Color(221, 235, 224));
            canvas.ellipse(right - 47, top + 24, right - 27, top + 34); canvas.fill();
            canvas.ellipse(right - 15, top + 42, right + 5, top + 52); canvas.fill();
            if (writer.getPageNumber() == 1 && document.bottom() > 100) {
                float base = Math.max(28, document.bottom() - 18);
                canvas.setColorFill(new Color(232, 241, 234));
                canvas.moveTo(document.left(), base); canvas.lineTo(document.left() + 85, base + 34);
                canvas.lineTo(document.left() + 155, base + 4); canvas.lineTo(document.left() + 245, base + 39);
                canvas.lineTo(document.left() + 325, base + 7); canvas.lineTo(document.right(), base + 32);
                canvas.lineTo(document.right(), base); canvas.closePathFillStroke();
                canvas.setColorFill(new Color(239, 246, 240));
                canvas.moveTo(document.left(), base); canvas.lineTo(document.left(), base + 30);
                canvas.curveTo(180, base + 76, 295, base + 17, 390, base + 47);
                canvas.curveTo(470, base + 70, 530, base + 21, document.right(), base + 40);
                canvas.lineTo(document.right(), base); canvas.closePathFillStroke();
                canvas.setColorFill(new Color(226, 238, 228)); canvas.circle(document.left() + 37, base + 19, 8); canvas.fill();
                canvas.circle(document.left() + 50, base + 24, 10); canvas.fill();
                canvas.setColorStroke(new Color(177, 203, 182)); canvas.setLineWidth(1);
                float hx = document.left() + 86;
                canvas.moveTo(hx - 15, base + 3); canvas.lineTo(hx - 15, base + 20); canvas.lineTo(hx, base + 34);
                canvas.lineTo(hx + 15, base + 20); canvas.lineTo(hx + 15, base + 3); canvas.stroke();
                canvas.setColorFill(new Color(215, 231, 217)); canvas.rectangle(hx - 10, base + 3, 20, 12); canvas.fill();
                canvas.setColorStroke(new Color(125, 157, 132)); canvas.setLineWidth(1.3f);
                float houseX = document.right() - 82;
                canvas.moveTo(houseX - 14, base + 3); canvas.lineTo(houseX - 14, base + 19);
                canvas.lineTo(houseX, base + 31); canvas.lineTo(houseX + 14, base + 19);
                canvas.lineTo(houseX + 14, base + 3); canvas.stroke();
                canvas.setColorFill(new Color(210, 228, 213)); canvas.rectangle(houseX - 10, base + 3, 20, 12); canvas.fill();
                canvas.setColorFill(new Color(183, 207, 187));
                canvas.circle(document.right() - 36, base + 18, 7); canvas.fill();
                canvas.circle(document.right() - 45, base + 14, 6); canvas.fill();
            }
            canvas.restoreState();
        }
    }

    private static PdfPCell noBorderCell() { PdfPCell c = new PdfPCell(); c.setBorder(PdfPCell.NO_BORDER); return c; }
    void addMoneyCell(PdfPTable table, BigDecimal amount, int stripe) {
        addCell(table, money(amount), false, Element.ALIGN_RIGHT, stripe);
    }
    private void addCell(PdfPTable table, String text, boolean header, int alignment, int stripe) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font(header ? 9 : 8, header ? Font.BOLD : Font.NORMAL)));
        cell.setPadding(6); cell.setHorizontalAlignment(alignment); cell.setBorderColor(LINE_GREEN);
        cell.setNoWrap(false);
        cell.setBackgroundColor(header ? PALE_GREEN : stripe == 0 ? Color.WHITE : SOFT_GREEN);
        if (header) cell.getPhrase().getFont().setColor(DEEP_GREEN); table.addCell(cell);
    }
    private Paragraph paragraph(String text, int size, int style) { return new Paragraph(text == null ? "" : text, font(size, style)); }
    private Font font(int size, int style) { return new Font(baseFont, size, style); }
    private static String value(String value) { return value == null ? "" : value; }
    private static String optional(String label, String value) { return value == null || value.isBlank() ? "" : label + value; }
    private static String money(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        final java.math.BigInteger whole;
        try {
            whole = value.toBigIntegerExact();
        } catch (ArithmeticException fractionalVnd) {
            throw new IllegalArgumentException("VND amounts must be whole numbers for invoice display.", fractionalVnd);
        }
        java.text.DecimalFormatSymbols symbols = java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT);
        symbols.setGroupingSeparator('.');
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0", symbols);
        return format.format(whole) + " VND";
    }
    static String formatMoney(BigDecimal amount) { return money(amount); }
    private static String formatMoneyInDescription(String description) {
        if (description == null || description.isEmpty()) return description;
        Matcher matcher = DESCRIBED_MONEY.matcher(description);
        StringBuffer formatted = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group().replaceAll("(?i)\\s*(?:VND|đ)$", "").replace(',', '.');
            matcher.appendReplacement(formatted, Matcher.quoteReplacement(money(new BigDecimal(token))));
        }
        matcher.appendTail(formatted);
        return formatted.toString();
    }
    private static String balanceLabel(InvoiceData data) {
        return data.settlementType() == com.ttcs.homestay.entity.BookingSettlement.SettlementType.REFUND
                ? "Còn phải hoàn" : "Còn phải thu";
    }
}
