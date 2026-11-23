package com.orbita.api.auth.internal.repository;

import com.orbita.api.auth.internal.model.VerificationPurpose;
import com.orbita.api.auth.internal.model.VerificationRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class VerificationCodeRepository {

	private static final Duration CODE_TTL = Duration.ofMinutes(10);
	private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
	private static final String FIELD_HASH = "codeHash";
	private static final String FIELD_ATTEMPTS = "attempts";

	private static final DefaultRedisScript<Long> SAVE_HASH_WITH_TTL_SCRIPT = new DefaultRedisScript<>(
			"""
			redis.call('HSET', KEYS[1], 'codeHash', ARGV[1], 'attempts', '0')
			redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
			return 1
			""",
			Long.class
	);

	private static final DefaultRedisScript<Long> INCREMENT_ATTEMPTS_IF_EXISTS_SCRIPT = new DefaultRedisScript<>(
			"""
			if redis.call('EXISTS', KEYS[1]) == 1 then
				return redis.call('HINCRBY', KEYS[1], 'attempts', 1)
			end
			return -1
			""",
			Long.class
	);

	private final StringRedisTemplate redisTemplate;

	public boolean acquireCooldown(VerificationPurpose purpose, String identifier) {
		Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
				cooldownKey(purpose, identifier),
				"1",
				COOLDOWN_TTL
		);
		return Boolean.TRUE.equals(acquired);
	}

	public void saveCode(VerificationPurpose purpose, String identifier, String codeHash) {
		String key = verificationKey(purpose, identifier);
		redisTemplate.execute(
				SAVE_HASH_WITH_TTL_SCRIPT,
				List.of(key),
				codeHash,
				String.valueOf(CODE_TTL.toSeconds())
		);
	}

	public Optional<VerificationRecord> findCode(VerificationPurpose purpose, String identifier) {
		String key = verificationKey(purpose, identifier);
		Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
		if (entries.isEmpty()) {
			return Optional.empty();
		}
		String codeHash = (String) entries.get(FIELD_HASH);
		String attemptsStr = (String) entries.get(FIELD_ATTEMPTS);
		if (codeHash == null) {
			return Optional.empty();
		}
		int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;
		return Optional.of(new VerificationRecord(codeHash, attempts));
	}

	public long incrementAttempts(VerificationPurpose purpose, String identifier) {
		String key = verificationKey(purpose, identifier);
		Long updated = redisTemplate.execute(
				INCREMENT_ATTEMPTS_IF_EXISTS_SCRIPT,
				List.of(key)
		);
		return updated != null ? updated : -1L;
	}

	public void deleteCode(VerificationPurpose purpose, String identifier) {
		redisTemplate.delete(verificationKey(purpose, identifier));
	}

	private String verificationKey(VerificationPurpose purpose, String identifier) {
		return "verification:" + purpose.name() + ":" + identifier;
	}

	private String cooldownKey(VerificationPurpose purpose, String identifier) {
		return "cooldown:" + purpose.name() + ":" + identifier;
	}

}
