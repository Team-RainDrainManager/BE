package com.rainbutler.domain.user.certificate;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * 활동 확인서 PDF를 그립니다.
 *
 * <p>A4 한 장에 맞춰 그리고, 한글이 깨지지 않도록 나눔고딕을 PDF에 임베드합니다. 저장은 하지 않고 매번
 * 바이트로 만들어 바로 내려줍니다.
 */
@Component
public class CertificatePdfRenderer {

    private static final String REGULAR_FONT = "/fonts/NanumGothic-Regular.ttf";
    private static final String BOLD_FONT = "/fonts/NanumGothic-Bold.ttf";

    private static final Color NAVY = new Color(18, 50, 79);
    private static final Color INK = new Color(29, 47, 69);
    private static final Color SLATE = new Color(91, 113, 137);
    private static final Color MUTED = new Color(139, 160, 184);
    private static final Color LIGHT = new Color(163, 178, 194);
    private static final Color LINE = new Color(219, 228, 238);
    private static final Color BOX = new Color(245, 248, 252);
    private static final Color BLUE = new Color(57, 129, 247);
    private static final Color STAMP = new Color(198, 60, 60);

    private static final DateTimeFormatter DOT_DATE = DateTimeFormatter.ofPattern("yyyy. MM. dd.");

    private final BaseFont regularBase;
    private final BaseFont boldBase;

    public CertificatePdfRenderer() {
        this.regularBase = loadFont(REGULAR_FONT);
        this.boldBase = loadFont(BOLD_FONT);
    }

    /**
     * 확인서 PDF를 만듭니다.
     *
     * @param data 확인서 값
     * @return PDF 바이트
     */
    public byte[] render(CertificateData data) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 46, 46, 40, 36);
        try {
            PdfWriter.getInstance(document, output);
            document.open();
            addHeader(document, data);
            addRules(document);
            addTitle(document);
            addLead(document);
            addInfoTable(document, data);
            addNotice(document);
            addSignature(document, data);
            addFooter(document);
        } catch (DocumentException e) {
            throw new IllegalStateException("활동 확인서 PDF를 만들지 못했습니다.", e);
        } finally {
            document.close();
        }
        return output.toByteArray();
    }

    private void addHeader(Document document, CertificateData data) throws DocumentException {
        PdfPTable header = new PdfPTable(new float[]{60, 40});
        header.setWidthPercentage(100);
        header.setSpacingAfter(0);

        Phrase brand = new Phrase();
        brand.add(new Chunk("또랑", font(boldBase, 18, NAVY)));
        brand.add(Chunk.NEWLINE);
        brand.add(spaced(new Chunk("RAIN BUTLER", font(regularBase, 6.5f, MUTED)), 2.2f));
        PdfPCell left = borderless(new Phrase(brand));
        left.setPadding(0);
        left.setPaddingBottom(3);
        header.addCell(left);

        Phrase meta = new Phrase();
        meta.add(new Chunk("발급일 ", font(regularBase, 8, MUTED)));
        meta.add(new Chunk(DOT_DATE.format(data.issuedAt()), font(boldBase, 8, SLATE)));
        meta.add(Chunk.NEWLINE);
        meta.add(new Chunk("발급번호 없음 · 활동 기록 기준", font(regularBase, 7, LIGHT)));
        PdfPCell right = borderless(meta);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setPadding(0);
        right.setPaddingTop(2);
        header.addCell(right);

        document.add(header);
    }

    private void addRules(Document document) throws DocumentException {
        addRule(document, NAVY, 1.6f, 12);
        addRule(document, LINE, 0.6f, 2);
    }

    private void addRule(Document document, Color color, float height, float before)
            throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(before);
        PdfPCell cell = new PdfPCell(new Phrase(" ", font(regularBase, 0.1f, Color.WHITE)));
        cell.setFixedHeight(height);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(color);
        cell.setPadding(0);
        table.addCell(cell);
        document.add(table);
    }

    private void addTitle(Document document) throws DocumentException {
        Paragraph title = new Paragraph();
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(48);
        title.setSpacingAfter(0);
        title.add(spaced(new Chunk("활동 확인서", font(boldBase, 32, NAVY)), 7f));
        document.add(title);

        Paragraph subtitle = new Paragraph();
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingBefore(12);
        subtitle.add(spaced(new Chunk("CERTIFICATE OF ACTIVITY",
                font(regularBase, 8, MUTED)), 2.8f));
        document.add(subtitle);
    }

    private void addLead(Document document) throws DocumentException {
        Paragraph lead = new Paragraph();
        lead.setAlignment(Element.ALIGN_CENTER);
        lead.setSpacingBefore(30);
        lead.setLeading(20);
        lead.add(new Chunk("아래 사람은 ", font(regularBase, 11, SLATE)));
        lead.add(new Chunk("또랑", font(boldBase, 11, NAVY)));
        lead.add(new Chunk("에서 우리 동네 빗물받이를 돌보는 활동에 참여하였음을 확인합니다.",
                font(regularBase, 11, SLATE)));
        document.add(lead);
    }

    private void addInfoTable(Document document, CertificateData data) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1.05f, 3.95f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(34);

        addInfoRow(table, "성명", simple(data.realName(), 12, INK));
        addInfoRow(table, "활동 기간", periodCell(data));
        addInfoRow(table, "인정 점검", inspectionCell(data));
        addInfoRow(table, "봉사 시간", volunteerCell(data));
        document.add(table);
    }

    private Phrase periodCell(CertificateData data) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(DOT_DATE.format(data.periodStart()) + " ~ "
                + DOT_DATE.format(data.issuedAt()), font(boldBase, 11, INK)));
        phrase.add(Chunk.NEWLINE);
        phrase.add(new Chunk("첫 인정 활동일부터 발급일까지", font(regularBase, 8, MUTED)));
        return phrase;
    }

    private Phrase inspectionCell(CertificateData data) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(String.valueOf(data.inspectionCount()), font(boldBase, 20, NAVY)));
        phrase.add(new Chunk("회", font(regularBase, 9.5f, SLATE)));
        phrase.add(Chunk.NEWLINE);
        phrase.add(new Chunk("정기 " + data.regularCount() + "회 · 비 예보 "
                + data.forecastCount() + "회 · 대신 " + data.substituteCount() + "회",
                font(regularBase, 8, MUTED)));
        return phrase;
    }

    private Phrase volunteerCell(CertificateData data) {
        long minutes = data.volunteerMinutes();
        long hours = minutes / 60;
        long rest = minutes % 60;

        Phrase phrase = new Phrase();
        phrase.add(new Chunk(String.valueOf(hours), font(boldBase, 20, NAVY)));
        phrase.add(new Chunk("시간", font(regularBase, 9.5f, SLATE)));
        if (rest > 0) {
            phrase.add(new Chunk(" " + rest + "분", font(regularBase, 9.5f, SLATE)));
        }
        phrase.add(Chunk.NEWLINE);
        phrase.add(new Chunk("활동 점수 100점당 1시간 · 총 " + minutes + "분",
                font(regularBase, 8, MUTED)));
        return phrase;
    }

    private Phrase simple(String text, float size, Color color) {
        return new Phrase(new Chunk(text, font(boldBase, size, color)));
    }

    private void addInfoRow(PdfPTable table, String label, Phrase value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font(regularBase, 9, SLATE)));
        labelCell.setBackgroundColor(BOX);
        styleInfoCell(labelCell);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(value);
        styleInfoCell(valueCell);
        table.addCell(valueCell);
    }

    private void styleInfoCell(PdfPCell cell) {
        cell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        cell.setBorderColor(LINE);
        cell.setBorderWidth(0.7f);
        cell.setPaddingTop(12);
        cell.setPaddingBottom(12);
        cell.setPaddingLeft(12);
        cell.setPaddingRight(10);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
    }

    private void addNotice(Document document) throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(26);

        Phrase phrase = new Phrase();
        phrase.setLeading(14);
        phrase.add(new Chunk("안내. ", font(boldBase, 8.5f, INK)));
        phrase.add(new Chunk("본 확인서는 또랑 앱의 점검 · 청소 기록 중 인정된 활동을 기준으로 "
                + "발급됩니다. 1365 자원봉사포털 등록 전에는 공식 봉사 실적으로 인정되지 않으며, "
                + "발급번호 없이 활동 기록으로 재발급됩니다.", font(regularBase, 8.5f, SLATE)));

        PdfPCell cell = new PdfPCell(phrase);
        cell.setBackgroundColor(BOX);
        cell.setBorder(Rectangle.LEFT);
        cell.setBorderColor(BLUE);
        cell.setBorderWidth(2.5f);
        cell.setPadding(13);
        cell.setPaddingLeft(14);
        table.addCell(cell);
        document.add(table);
    }

    private void addSignature(Document document, CertificateData data) throws DocumentException {
        Paragraph date = new Paragraph(
                new Chunk(koreanDate(data.issuedAt()), font(regularBase, 10, SLATE)));
        date.setAlignment(Element.ALIGN_CENTER);
        date.setSpacingBefore(44);
        document.add(date);

        Paragraph issuer = new Paragraph();
        issuer.setAlignment(Element.ALIGN_CENTER);
        issuer.setSpacingBefore(18);
        issuer.add(new Chunk("또랑", font(boldBase, 15, NAVY)));
        issuer.add(new Chunk("   Rain Butler", font(regularBase, 8, MUTED)));
        document.add(issuer);

        PdfPTable stampTable = new PdfPTable(1);
        stampTable.setWidthPercentage(14);
        stampTable.setHorizontalAlignment(Element.ALIGN_CENTER);
        stampTable.setSpacingBefore(14);

        Phrase stampText = new Phrase();
        stampText.setLeading(12);
        stampText.add(new Chunk("또랑", font(boldBase, 9, STAMP)));
        stampText.add(Chunk.NEWLINE);
        stampText.add(new Chunk("직인", font(boldBase, 9, STAMP)));

        PdfPCell stamp = new PdfPCell(stampText);
        stamp.setFixedHeight(54);
        stamp.setBorder(Rectangle.NO_BORDER);
        stamp.setHorizontalAlignment(Element.ALIGN_CENTER);
        stamp.setVerticalAlignment(Element.ALIGN_MIDDLE);
        stamp.setCellEvent((cell, position, canvases) -> {
            PdfContentByte canvas = canvases[PdfPTable.TEXTCANVAS];
            float centerX = (position.getLeft() + position.getRight()) / 2;
            float centerY = (position.getBottom() + position.getTop()) / 2;
            canvas.saveState();
            canvas.setColorStroke(STAMP);
            canvas.setLineWidth(1.8f);
            canvas.circle(centerX, centerY, 25f);
            canvas.stroke();
            canvas.restoreState();
        });
        stampTable.addCell(stamp);
        document.add(stampTable);
    }

    private void addFooter(Document document) throws DocumentException {
        addRule(document, new Color(229, 236, 243), 0.6f, 26);

        Paragraph footer = new Paragraph();
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setLeading(13);
        footer.add(new Chunk("본 확인서는 사용자가 직접 발급한 문서이며, 직인은 앱 내 발급 표시입니다.",
                font(regularBase, 7, LIGHT)));
        footer.add(Chunk.NEWLINE);
        footer.add(new Chunk("문의 · 재발급: 또랑 앱 → 프로필 → 활동 확인서",
                font(regularBase, 7, LIGHT)));
        document.add(footer);
    }

    private String koreanDate(LocalDate date) {
        return date.getYear() + "년 " + date.getMonthValue() + "월 " + date.getDayOfMonth() + "일";
    }

    private Font font(BaseFont base, float size, Color color) {
        return new Font(base, size, Font.NORMAL, color);
    }

    private Chunk spaced(Chunk chunk, float spacing) {
        chunk.setCharacterSpacing(spacing);
        return chunk;
    }

    private PdfPCell borderless(Phrase phrase) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private BaseFont loadFont(String path) {
        try (InputStream input = CertificatePdfRenderer.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("확인서 폰트를 찾을 수 없습니다: " + path);
            }
            return BaseFont.createFont(path.substring(path.lastIndexOf('/') + 1),
                    BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, input.readAllBytes(), null);
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("확인서 폰트를 읽지 못했습니다: " + path, e);
        }
    }
}
