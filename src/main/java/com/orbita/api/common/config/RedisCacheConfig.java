package com.orbita.api.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisCacheConfig {

	private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(10);

	@Bean
	public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
		GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.create(
				builder -> builder.enableSpringCacheNullValueSupport().enableUnsafeDefaultTyping()
		);

		RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(DEFAULT_CACHE_TTL)
				.disableCachingNullValues()
				.serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
				.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

		return RedisCacheManager.builder(RedisCacheWriter.create(
						connectionFactory,
						RedisCacheWriter.RedisCacheWriterConfigurer::immediateWrites
				))
				.cacheDefaults(config)
				.build();
	}

}
