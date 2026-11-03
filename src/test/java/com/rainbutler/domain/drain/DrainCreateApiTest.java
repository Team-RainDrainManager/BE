package com.rainbutler.domain.drain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.UploadPurpose;
import com.rainbutler.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 빗물받이 등록 API를 검증합니다.
 *
 * <p>등록은 검증 · AI 판정 뒤 {@code DrainWriter}의 짧은 트랜잭션에서 저장합니다. 읽기 전용
 * 트랜잭션에 합류해 INSERT가 막히지 않는지 함께 확인합니다.
 */
@AutoConfigureMockMvc
class DrainCreateApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StorageService storageService;

    private Long userId;
    private String token;
    private String photoKey;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "등록집사"));
        userId = user.getId();
        token = jwtTokenProvider.createAccessToken(userId, user.getRole());
        photoKey = "test/drain/" + userId + "/" + UUID.randomUUID() + ".jpg";

        given(storageService.verifyUploaded(anyLong(), anyString(), any(UploadPurpose.class)))
                .willAnswer(invocation -> invocation.getArgument(1));
        given(storageService.toReadUrl(anyString()))
                .willAnswer(invocation -> "http://" + invocation.getArgument(0));
    }

    @Test
    void 빗물받이를_등록한다() throws Exception {
        mockMvc.perform(post("/api/v1/drain")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "latitude": 37.5,
                                  "longitude": 126.7,
                                  "photoKey": "%s",
                                  "isRisk": false
                                }
                                """.formatted(photoKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.adoptionStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.photoUrl").value("http://" + photoKey));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM drain WHERE registered_by = ? AND photo_url = ?",
                Integer.class, userId, photoKey)).isEqualTo(1);
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(post("/api/v1/drain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "latitude": 37.5,
                                  "longitude": 126.7,
                                  "photoKey": "%s"
                                }
                                """.formatted(photoKey)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
