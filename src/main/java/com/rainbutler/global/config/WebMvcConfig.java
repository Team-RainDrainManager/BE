package com.rainbutler.global.config;

import com.rainbutler.global.security.LoginUserIdArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 설정입니다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * {@code @LoginUserId} 파라미터 리졸버를 등록합니다.
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginUserIdArgumentResolver());
    }
}
