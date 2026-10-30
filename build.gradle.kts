plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.rainbutler"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// Web
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.0")

	// Security
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

	// AWS S3 (SDK v2)
	implementation(platform("software.amazon.awssdk:bom:2.55.16"))
	implementation("software.amazon.awssdk:s3") {
		// 동기 S3Client(apache5-client)만 쓴다. 비동기 netty 클라이언트는 Boot의 netty 4.2와 버전이 어긋난다
		exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
	}

	// FCM
	implementation("com.google.firebase:firebase-admin:9.11.0") {
		// FCM(Messaging)만 쓴다. Firestore · Cloud Storage 클라이언트(gRPC · protobuf)는 빼서 이미지를 줄인다
		exclude(group = "com.google.cloud", module = "google-cloud-firestore")
		exclude(group = "com.google.cloud", module = "google-cloud-storage")
	}
	// 위에서 뺀 모듈이 가져오던 JSON 처리기. Firebase 초기화에 필요하다 (google-http-client와 같은 버전)
	implementation("com.google.http-client:google-http-client-jackson2:2.2.0")

	// 스케줄러 중복 실행 방지 (배포 중 컨테이너가 둘 떠도 배치는 한 번만). Spring 7용 7.x
	implementation("net.javacrumbs.shedlock:shedlock-spring:7.10.1")
	implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.10.1")

	// DB
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")

	// Lombok
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	developmentOnly("org.springframework.boot:spring-boot-devtools")

	// Test
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	// 운영(앱 시작 시 Asia/Seoul 고정 · Dockerfile TZ)과 같은 시간대로 테스트한다. CI 러너는 UTC라서 필요
	systemProperty("user.timezone", "Asia/Seoul")
}

// 실행 가능한 bootJar 하나만 만든다 (plain jar 비활성화 → build/libs/에 jar 1개)
tasks.named<Jar>("jar") {
	enabled = false
}
