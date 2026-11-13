package com.rainbutler.domain.drain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.domain.drain.service.DrainRegionResolver.RegionDocument;
import com.rainbutler.domain.drain.service.DrainRegionResolver.RegionResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카카오 좌표 → 행정구역 응답에서 행정동 이름을 꺼내는지 검증합니다.
 */
class DrainRegionResolverTest {

    @Test
    void 법정동_B가_아닌_행정동_H의_3단계_이름을_쓴다() {
        RegionResponse response = JsonMapper.builder().build().readValue("""
                {"meta":{"total_count":2},"documents":[
                  {"region_type":"B","region_1depth_name":"경기도","region_2depth_name":"부천시 소사구",
                   "region_3depth_name":"역곡동","code":"4119410600"},
                  {"region_type":"H","region_1depth_name":"경기도","region_2depth_name":"부천시 소사구",
                   "region_3depth_name":"역곡2동","code":"4119460500"}]}
                """, RegionResponse.class);

        assertThat(DrainRegionResolver.administrativeDong(response)).isEqualTo("역곡2동");
    }

    @Test
    void 행정동이_없거나_응답이_비면_null() {
        assertThat(DrainRegionResolver.administrativeDong(null)).isNull();
        assertThat(DrainRegionResolver.administrativeDong(new RegionResponse(List.of(
                new RegionDocument("B", "역곡동"))))).isNull();
        assertThat(DrainRegionResolver.administrativeDong(new RegionResponse(List.of(
                new RegionDocument("H", " "))))).isNull();
    }
}
