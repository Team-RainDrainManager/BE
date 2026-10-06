package com.rainbutler;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RainButlerApplication {

	public static void main(String[] args) {
		// 서버 위치와 관계없이 LocalDateTime.now()를 한국 시간으로 맞춘다 (06시·20시 알림 기준)
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(RainButlerApplication.class, args);
	}

}
