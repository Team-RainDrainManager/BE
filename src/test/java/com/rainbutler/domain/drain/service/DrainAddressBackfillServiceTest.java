package com.rainbutler.domain.drain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rainbutler.domain.drain.service.DrainAddressBackfillService.BackfillResult;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 주소 백필이 비어 있는 행만 채우고, 설정이 꺼져 있거나 카카오가 거절하면 멈추는지 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class DrainAddressBackfillServiceTest {

    @Mock
    private DrainAddressResolver drainAddressResolver;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private DrainAddressBackfillService service;

    @BeforeEach
    void setUp() {
        service = new DrainAddressBackfillService(drainAddressResolver, jdbcTemplate);
    }

    @Test
    void 주소가_비어_있는_행만_채운다() {
        given(drainAddressResolver.isEnabled()).willReturn(true);
        given(drainAddressResolver.resolveForBackfill(37.5, 126.8))
                .willReturn("경기도 부천시 원미구 지봉로 43");
        givenPages(page(row(1L, 37.5, 126.8)), List.<Object[]>of());

        BackfillResult result = service.backfill();

        assertThat(result.filled()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(jdbcTemplate).update("UPDATE drain SET address = ? WHERE id = ?",
                "경기도 부천시 원미구 지봉로 43", 1L);
    }

    @Test
    void 설정이_꺼져_있으면_조회도_하지_않는다() {
        given(drainAddressResolver.isEnabled()).willReturn(false);

        BackfillResult result = service.backfill();

        assertThat(result).isEqualTo(new BackfillResult(0, 0));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void 주소를_찾지_못한_행은_비워_두고_다음_시작_때_다시_한다() {
        given(drainAddressResolver.isEnabled()).willReturn(true);
        given(drainAddressResolver.resolveForBackfill(37.5, 126.8)).willReturn(null);
        givenPages(page(row(1L, 37.5, 126.8)), List.<Object[]>of());

        BackfillResult result = service.backfill();

        assertThat(result.filled()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(updateCount()).isZero();
    }

    @Test
    void 카카오가_403으로_거절하면_남은_행은_부르지_않고_멈춘다() {
        given(drainAddressResolver.isEnabled()).willReturn(true);
        given(drainAddressResolver.resolveForBackfill(37.5, 126.8))
                .willThrow(new DrainAddressResolver.AddressLookupForbiddenException());
        givenPages(page(row(1L, 37.5, 126.8), row(2L, 37.6, 126.9)));

        BackfillResult result = service.backfill();

        assertThat(result.filled()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        verify(drainAddressResolver).resolveForBackfill(37.5, 126.8);
        verify(drainAddressResolver, never()).resolveForBackfill(37.6, 126.9);
        assertThat(updateCount()).isZero();
    }

    /** 페이지를 차례로 돌려주도록 스텁합니다. 마지막 페이지는 빈 목록이어야 반복이 끝납니다. */
    @SafeVarargs
    private void givenPages(List<Object[]>... pages) {
        given(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<Object[]>>any(),
                any(Object[].class)))
                .willReturn(pages[0], Arrays.copyOfRange(pages, 1, pages.length));
    }

    private static List<Object[]> page(Object[]... rows) {
        return List.of(rows);
    }

    private static Object[] row(long id, double latitude, double longitude) {
        return new Object[] {id, latitude, longitude};
    }

    private long updateCount() {
        return mockingDetails(jdbcTemplate).getInvocations().stream()
                .filter(invocation -> "update".equals(invocation.getMethod().getName()))
                .count();
    }
}
