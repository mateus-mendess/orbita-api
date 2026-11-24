package com.orbita.api.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum Category {
	PERSONAL("pessoal"),
	WORK("trabalho"),
	STUDY("estudo");

	private final String value;

	Category(String value) {
		this.value = value;
	}

	@JsonValue
	public String getValue() {
		return value;
	}

	@JsonCreator
	public static Category fromValue(String value) {
		return Arrays.stream(values())
				.filter(category -> category.value.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown category: " + value));
	}

}
