package com.orbita.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

@SpringBootTest
public abstract class AbstractIntegrationTest {

	@ServiceConnection
	protected static final MongoDBContainer MONGODB_CONTAINER = new MongoDBContainer(
			DockerImageName.parse("mongo:7.0")
	);

	@SuppressWarnings("resource")
	protected static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(
			DockerImageName.parse("redis:7-alpine")
	).withExposedPorts(6379);

	private static final String TEST_PRIVATE_KEY_BASE64;
	private static final String TEST_PUBLIC_KEY_BASE64;

	static {
		MONGODB_CONTAINER.start();
		REDIS_CONTAINER.start();

		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			KeyPair keyPair = generator.generateKeyPair();

			String privatePem = "-----BEGIN PRIVATE KEY-----\n"
					+ Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
					+ "\n-----END PRIVATE KEY-----";
			String publicPem = "-----BEGIN PUBLIC KEY-----\n"
					+ Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
					+ "\n-----END PUBLIC KEY-----";

			TEST_PRIVATE_KEY_BASE64 = Base64.getEncoder().encodeToString(privatePem.getBytes(StandardCharsets.UTF_8));
			TEST_PUBLIC_KEY_BASE64 = Base64.getEncoder().encodeToString(publicPem.getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	@MockitoBean
	protected JavaMailSender javaMailSender;

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.mongodb.uri", MONGODB_CONTAINER::getReplicaSetUrl);
		registry.add("spring.mongodb.uri", MONGODB_CONTAINER::getReplicaSetUrl);
		registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
		registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
		registry.add("security.jwt.private-key-base64", () -> TEST_PRIVATE_KEY_BASE64);
		registry.add("security.jwt.public-key-base64", () -> TEST_PUBLIC_KEY_BASE64);
		registry.add("security.jwt.expiration-seconds", () -> "604800");
		registry.add("spring.mail.host", () -> "localhost");
		registry.add("spring.mail.port", () -> "2525");
		registry.add("spring.mail.username", () -> "test");
		registry.add("spring.mail.password", () -> "test");
		registry.add("app.mail.from", () -> "noreply@orbita.app");
	}

}
