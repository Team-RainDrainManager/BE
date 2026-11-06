package com.rainbutler.domain.user.certificate;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 활동 확인서 PDF가 실제 PDF로 만들어지는지 확인합니다.
 */
class CertificatePdfRendererTest {

    private final CertificatePdfRenderer renderer = new CertificatePdfRenderer();

    @Test
    void PDF_매직바이트로_시작하고_A4_한_장이다() throws Exception {
        CertificateData data = new CertificateData(
                "홍길동",
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 11, 5),
                24, 18, 4, 2,
                360);

        byte[] pdf = renderer.render(data);
        Files.write(Path.of("build/certificate-preview.pdf"), pdf);

        assertThat(pdf.length).isGreaterThan(3_000);
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(new PdfReader(pdf).getNumberOfPages()).isEqualTo(1);
    }

    @Test
    void 봉사_시간이_1시간_미만이어도_만들어진다() {
        CertificateData data = new CertificateData(
                "김또랑",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 5),
                1, 1, 0, 0,
                45);

        byte[] pdf = renderer.render(data);

        assertThat(pdf.length).isGreaterThan(3_000);
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }
}
