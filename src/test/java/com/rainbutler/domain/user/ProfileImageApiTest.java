package com.rainbutler.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * 마이페이지 프로필 사진 등록 · 교체 · 삭제 API를 검증합니다.
 *
 * <p>S3는 목으로 두고, key 형식 · 소유자 검증만 실제 규칙과 같게 흉내 냅니다.
 */
@AutoConfigureMockMvc
class ProfileImageApiTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/users/me/profile-image";

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

    private User user;
    private String token;
    private String photoKey;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}irrelevant", "사진집사"));
        token = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        photoKey = "test/profile/" + user.getId() + "/" + UUID.randomUUID() + ".jpg";

        given(storageService.verifyUploaded(anyLong(), anyString(), any(UploadPurpose.class)))
                .willAnswer(invocation -> {
                    Long userId = invocation.getArgument(0);
                    String key = invocation.getArgument(1);
                    if (key == null || !key.startsWith("test/profile/")) {
                        throw new BusinessException(ErrorCode.INVALID_PHOTO_KEY);
                    }
                    if (!key.startsWith("test/profile/" + userId + "/")) {
                        throw new BusinessException(ErrorCode.PHOTO_FORBIDDEN);
                    }
                    return key;
                });
        given(storageService.toReadUrl(anyString()))
                .willAnswer(invocation -> "http://" + invocation.getArgument(0));
    }

    @Test
    void 프로필_사진을_등록하면_DB에는_key를_저장하고_응답에는_조회_URL을_준다() throws Exception {
        update(photoKey)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.profileImage").value("http://" + photoKey))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT profile_image FROM users WHERE id = ?", String.class, user.getId()))
                .isEqualTo(photoKey);

        me().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImage").value("http://" + photoKey));
    }

    @Test
    void 기존_사진을_다른_사진으로_교체한다() throws Exception {
        String first = photoKey;
        String second = "test/profile/" + user.getId() + "/" + UUID.randomUUID() + ".png";

        update(first).andExpect(status().isOk());
        update(second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImage").value("http://" + second));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT profile_image FROM users WHERE id = ?", String.class, user.getId()))
                .isEqualTo(second);
    }

    @Test
    void 사진_key가_비어_있으면_400_INVALID_INPUT() throws Exception {
        update("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 프로필_용도가_아닌_key면_400_INVALID_PHOTO_KEY() throws Exception {
        update("test/report/" + user.getId() + "/" + UUID.randomUUID() + ".jpg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PHOTO_KEY"));
    }

    @Test
    void 다른_사용자가_올린_사진이면_403_PHOTO_FORBIDDEN() throws Exception {
        update("test/profile/" + (user.getId() + 1) + "/" + UUID.randomUUID() + ".jpg")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PHOTO_FORBIDDEN"));
    }

    @Test
    void 프로필_사진을_삭제하면_기본_프로필로_돌아간다() throws Exception {
        update(photoKey).andExpect(status().isOk());

        remove()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.profileImage").doesNotExist())
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT profile_image FROM users WHERE id = ?", String.class, user.getId()))
                .isNull();

        me().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImage").doesNotExist());
    }

    @Test
    void 사진이_없어도_삭제는_성공한다() throws Exception {
        remove()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImage").doesNotExist());
    }

    @Test
    void 로그인하지_않으면_등록과_삭제_모두_401() throws Exception {
        mockMvc.perform(patch(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoKey\":\"%s\"}".formatted(photoKey)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(delete(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private ResultActions update(String key) throws Exception {
        return mockMvc.perform(patch(PATH)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"photoKey\":\"%s\"}".formatted(key)));
    }

    private ResultActions remove() throws Exception {
        return mockMvc.perform(delete(PATH)
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions me() throws Exception {
        return mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token));
    }
}
