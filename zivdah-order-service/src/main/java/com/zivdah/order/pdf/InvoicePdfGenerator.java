package com.zivdah.order.pdf;

import com.zivdah.order.config.InvoiceCompanyProperties;
import com.zivdah.order.exception.InvoicePdfGenerationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// Renders a professional-looking, single/multi-page invoice PDF straight from the resolved
// InvoicePdfData — no HTML/template engine dependency, just Apache PDFBox content-stream
// drawing (Apache-2.0 licensed, unlike iText's AGPL/commercial dual license).
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoicePdfGenerator {

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 50f;
    private static final float BOTTOM_LIMIT = 80f;
    private static final float CONTENT_RIGHT = PAGE_WIDTH - MARGIN;

    private static final PDFont BOLD = PDType1Font.HELVETICA_BOLD;
    private static final PDFont REGULAR = PDType1Font.HELVETICA;
    private static final PDFont ITALIC = PDType1Font.HELVETICA_OBLIQUE;

    private static final Color HEADER_GRAY = new Color(245, 245, 245);
    private static final Color BORDER_GRAY = new Color(200, 200, 200);
    private static final Color MUTED = new Color(110, 110, 110);
    private static final Color PAID_GREEN = new Color(34, 139, 34);
    private static final Color WARN_ORANGE = new Color(200, 120, 0);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    // Product | Qty | Unit Price | Discount | Total
    private static final float COL_PRODUCT_X = MARGIN;
    private static final float COL_QTY_RIGHT = MARGIN + 260;
    private static final float COL_UNIT_RIGHT = MARGIN + 340;
    private static final float COL_DISCOUNT_RIGHT = MARGIN + 420;
    private static final float COL_TOTAL_RIGHT = CONTENT_RIGHT;
    private static final float TABLE_ROW_HEIGHT = 20f;

    private final InvoiceCompanyProperties company;

    public byte[] generate(InvoicePdfData data) {
        try (PDDocument document = new PDDocument()) {
            RenderState state = new RenderState(document);
            renderHeader(state, data);
            renderPartiesAndOrderInfo(state, data);
            renderItemsTable(state, data);
            renderTotalsAndPayment(state, data);
            renderFooter(state, data);
            state.closeStream();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            log.error("Failed to generate invoice PDF for {}: {}", data.getInvoiceNumber(), e.getMessage(), e);
            throw new InvoicePdfGenerationException("Failed to generate invoice PDF: " + e.getMessage(), e);
        }
    }

    // --- sections ------------------------------------------------------------------------

    private void renderHeader(RenderState s, InvoicePdfData data) throws IOException {
        float topY = s.y;

        // Left: company block
        s.text(BOLD, 16, MARGIN, s.y, company.getName());
        s.y -= 18;
        for (String line : List.of(company.getAddressLine1(), company.getAddressLine2())) {
            if (line != null && !line.isBlank()) {
                s.text(REGULAR, 9, MARGIN, s.y, line, MUTED);
                s.y -= 12;
            }
        }
        String contactLine = joinNonBlank(" | ", company.getEmail(), company.getPhone(), company.getWebsite());
        if (!contactLine.isBlank()) {
            s.text(REGULAR, 9, MARGIN, s.y, contactLine, MUTED);
            s.y -= 12;
        }
        if (company.getTaxId() != null && !company.getTaxId().isBlank()) {
            s.text(REGULAR, 9, MARGIN, s.y, "Tax/GSTIN: " + company.getTaxId(), MUTED);
            s.y -= 12;
        }
        float leftBottom = s.y;

        // Right: "INVOICE" title + number/date
        float ry = topY;
        s.textRight(BOLD, 22, CONTENT_RIGHT, ry, "INVOICE");
        ry -= 22;
        s.textRight(REGULAR, 10, CONTENT_RIGHT, ry, "Invoice #: " + data.getInvoiceNumber());
        ry -= 14;
        s.textRight(REGULAR, 10, CONTENT_RIGHT, ry, "Date: " + DATE_FMT.format(data.getInvoiceDate()));
        ry -= 14;

        s.y = Math.min(leftBottom, ry) - 8;
        s.hr();
        s.y -= 16;
    }

    private void renderPartiesAndOrderInfo(RenderState s, InvoicePdfData data) throws IOException {
        float startY = s.y;
        float rightColX = MARGIN + 300;

        s.text(BOLD, 10, MARGIN, s.y, "Bill To");
        float leftY = s.y - 14;
        leftY = s.textWrapped(REGULAR, 9, MARGIN, leftY, 240, data.getCustomerName());
        if (data.getCustomerEmail() != null && !data.getCustomerEmail().isBlank()) {
            leftY = s.textWrapped(REGULAR, 9, MARGIN, leftY, 240, data.getCustomerEmail());
        }
        if (data.getCustomerMobile() != null && !data.getCustomerMobile().isBlank()) {
            leftY = s.textWrapped(REGULAR, 9, MARGIN, leftY, 240, data.getCustomerMobile());
        }
        String address = joinNonBlank(", ",
                data.getAddressLine1(), data.getAddressLine2(), data.getCity(),
                data.getState(), data.getPinCode(), data.getCountry());
        if (!address.isBlank()) {
            leftY = s.textWrapped(REGULAR, 9, MARGIN, leftY, 240, "Shipping: " + address);
        }

        s.text(BOLD, 10, rightColX, startY, "Order Info");
        float rightY = startY - 14;
        rightY = s.textWrapped(REGULAR, 9, rightColX, rightY, 195, "Order #: " + data.getOrderNumber());
        rightY = s.textWrapped(REGULAR, 9, rightColX, rightY, 195, "Order ID: " + data.getOrderId());

        s.y = Math.min(leftY, rightY) - 10;
        s.hr();
        s.y -= 14;
    }

    private void renderItemsTable(RenderState s, InvoicePdfData data) throws IOException {
        drawTableHeader(s);
        for (InvoicePdfData.LineItem item : data.getItems()) {
            if (s.ensureSpace(TABLE_ROW_HEIGHT)) {
                drawTableHeader(s);
            }
            float rowTop = s.y;
            s.text(REGULAR, 9, COL_PRODUCT_X, s.y - 13, truncate(item.getProductName(), 40));
            s.textRight(REGULAR, 9, COL_QTY_RIGHT, s.y - 13, String.valueOf(item.getQuantity()));
            s.textRight(REGULAR, 9, COL_UNIT_RIGHT, s.y - 13, money(item.getUnitPrice(), data.getCurrency()));
            s.textRight(REGULAR, 9, COL_DISCOUNT_RIGHT, s.y - 13, money(item.getDiscount(), data.getCurrency()));
            s.textRight(REGULAR, 9, COL_TOTAL_RIGHT, s.y - 13, money(item.getItemTotal(), data.getCurrency()));
            s.y = rowTop - TABLE_ROW_HEIGHT;
            s.lineAt(s.y, BORDER_GRAY);
        }
        s.y -= 10;
    }

    private void drawTableHeader(RenderState s) throws IOException {
        s.fillRect(MARGIN, s.y - TABLE_ROW_HEIGHT, CONTENT_RIGHT - MARGIN, TABLE_ROW_HEIGHT, HEADER_GRAY);
        float ty = s.y - 13;
        s.text(BOLD, 9, COL_PRODUCT_X, ty, "Product");
        s.textRight(BOLD, 9, COL_QTY_RIGHT, ty, "Qty");
        s.textRight(BOLD, 9, COL_UNIT_RIGHT, ty, "Unit Price");
        s.textRight(BOLD, 9, COL_DISCOUNT_RIGHT, ty, "Discount");
        s.textRight(BOLD, 9, COL_TOTAL_RIGHT, ty, "Total");
        s.y -= TABLE_ROW_HEIGHT;
    }

    private void renderTotalsAndPayment(RenderState s, InvoicePdfData data) throws IOException {
        s.ensureSpace(140);
        float blockTop = s.y;

        // Left: payment info
        s.text(BOLD, 10, MARGIN, s.y, "Payment Information");
        float py = s.y - 16;
        boolean paid = "PAID".equalsIgnoreCase(data.getPaymentStatus());
        s.text(REGULAR, 9, MARGIN, py, "Status: ", MUTED);
        s.text(BOLD, 9, MARGIN + 40, py, nullToDash(data.getPaymentStatus()), paid ? PAID_GREEN : WARN_ORANGE);
        py -= 14;
        s.text(REGULAR, 9, MARGIN, py, "Method: " + nullToDash(data.getPaymentMethod()), MUTED);
        py -= 14;
        if (data.getTransactionId() != null && !data.getTransactionId().isBlank()) {
            s.text(REGULAR, 9, MARGIN, py, "Transaction ID: " + data.getTransactionId(), MUTED);
            py -= 14;
        }

        // Right: totals
        float labelRight = CONTENT_RIGHT - 110;
        float ty = blockTop;
        ty = totalsRow(s, labelRight, ty, "Subtotal", data.getSubtotal(), data.getCurrency(), false);
        if (data.getDiscount() != null && data.getDiscount().compareTo(BigDecimal.ZERO) > 0) {
            ty = totalsRow(s, labelRight, ty, "Discount", data.getDiscount().negate(), data.getCurrency(), false);
        }
        ty = totalsRow(s, labelRight, ty, "Delivery Fee", data.getDeliveryFee(), data.getCurrency(), false);
        ty = totalsRow(s, labelRight, ty, "Tax", data.getTax(), data.getCurrency(), false);
        s.lineAt(ty + 4, BORDER_GRAY, labelRight - 20, CONTENT_RIGHT);
        ty -= 4;
        ty = totalsRow(s, labelRight, ty, "Total Amount", data.getTotalAmount(), data.getCurrency(), true);

        s.y = Math.min(py, ty) - 16;
    }

    private float totalsRow(RenderState s, float labelRight, float y, String label, BigDecimal amount, String currency, boolean emphasize) throws IOException {
        PDFont font = emphasize ? BOLD : REGULAR;
        int size = emphasize ? 11 : 9;
        s.textRight(font, size, labelRight, y, label + ":");
        s.textRight(font, size, CONTENT_RIGHT, y, money(amount, currency));
        return y - (emphasize ? 18 : 14);
    }

    private void renderFooter(RenderState s, InvoicePdfData data) throws IOException {
        s.ensureSpace(40);
        s.hr();
        s.y -= 14;
        s.text(ITALIC, 9, MARGIN, s.y, "Thank you for shopping with " + company.getName() + "!", MUTED);
        s.y -= 12;
        s.text(REGULAR, 8, MARGIN, s.y, "This is a system-generated invoice and does not require a signature.", MUTED);
    }

    // --- formatting helpers ----------------------------------------------------------------

    private static String money(BigDecimal amount, String currency) {
        BigDecimal safe = amount != null ? amount : BigDecimal.ZERO;
        String symbol = currency == null || currency.isBlank() ? "" : currency + " ";
        return symbol + String.format(Locale.US, "%,.2f", safe);
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private static String joinNonBlank(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (sb.length() > 0) sb.append(sep);
                sb.append(p);
            }
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // --- low-level page/content-stream management -------------------------------------------

    // Wraps the current page + content stream + drawing cursor, and starts a fresh page (with
    // the same top margin) whenever ensureSpace() finds there isn't enough room left — this is
    // what makes an invoice with many line items paginate instead of drawing off the page.
    private final class RenderState {
        private final PDDocument document;
        private PDPageContentStream cs;
        private float y;

        RenderState(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void newPage() throws IOException {
            if (cs != null) {
                cs.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            cs = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN;
        }

        /** Starts a new page if {@code needed} points don't fit above the bottom limit. Returns true if a new page was started. */
        boolean ensureSpace(float needed) throws IOException {
            if (y - needed < BOTTOM_LIMIT) {
                newPage();
                return true;
            }
            return false;
        }

        void closeStream() throws IOException {
            cs.close();
        }

        void text(PDFont font, float size, float x, float yPos, String value) throws IOException {
            text(font, size, x, yPos, value, Color.BLACK);
        }

        void text(PDFont font, float size, float x, float yPos, String value, Color color) throws IOException {
            if (value == null) value = "";
            cs.beginText();
            cs.setFont(font, size);
            cs.setNonStrokingColor(color);
            cs.newLineAtOffset(x, yPos);
            cs.showText(value);
            cs.endText();
        }

        void textRight(PDFont font, float size, float rightX, float yPos, String value) throws IOException {
            textRight(font, size, rightX, yPos, value, Color.BLACK);
        }

        void textRight(PDFont font, float size, float rightX, float yPos, String value, Color color) throws IOException {
            if (value == null) value = "";
            float width = font.getStringWidth(value) / 1000f * size;
            text(font, size, rightX - width, yPos, value, color);
        }

        /** Draws (possibly wraps) a line of text within maxWidth, returning the new y cursor. */
        float textWrapped(PDFont font, float size, float x, float yPos, float maxWidth, String value) throws IOException {
            if (value == null || value.isBlank()) return yPos;
            String line = value;
            float width = font.getStringWidth(line) / 1000f * size;
            if (width <= maxWidth) {
                text(font, size, x, yPos, line);
                return yPos - 12;
            }
            // Simple char-count-based wrap for the rare overly-long line (address, long name).
            int approxChars = Math.max(1, (int) (maxWidth / (size * 0.5f)));
            String first = line.substring(0, Math.min(approxChars, line.length()));
            text(font, size, x, yPos, first);
            String rest = line.length() > first.length() ? line.substring(first.length()) : null;
            float next = yPos - 12;
            return rest != null ? textWrapped(font, size, x, next, maxWidth, rest.trim()) : next;
        }

        void hr() throws IOException {
            lineAt(y, BORDER_GRAY);
        }

        void lineAt(float yPos, Color color) throws IOException {
            lineAt(yPos, color, MARGIN, CONTENT_RIGHT);
        }

        void lineAt(float yPos, Color color, float fromX, float toX) throws IOException {
            cs.setStrokingColor(color);
            cs.setLineWidth(0.75f);
            cs.moveTo(fromX, yPos);
            cs.lineTo(toX, yPos);
            cs.stroke();
        }

        void fillRect(float x, float yBottom, float width, float height, Color color) throws IOException {
            cs.setNonStrokingColor(color);
            cs.addRect(x, yBottom, width, height);
            cs.fill();
        }
    }
}
