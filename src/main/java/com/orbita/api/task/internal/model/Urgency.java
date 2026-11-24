package com.orbita.api.task.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum Urgency {
	NOT_URGENT("nao_urgente"),
	LOW_URGENT("pouco_urgente"),
	URGENT("urgente"),
	VERY_URGENT("muito_urgente");

	private final String value;

	Urgency(String value) {
		this.value = value;
	}

	@JsonValue
	public String getValue() {
		return value;
	}

	@JsonCreator
	public static Urgency fromValue(String value) {
		return Arrays.stream(values())
				.filter(urgency -> urgency.value.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown urgency: " + value));
	}

}
