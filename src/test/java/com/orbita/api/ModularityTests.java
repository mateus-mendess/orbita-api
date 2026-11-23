package com.orbita.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

	@Test
	@DisplayName("Should verify Spring Modulith module boundaries and structure")
	void verifyModularStructure() {
		ApplicationModules.of(OrbitaApiApplication.class).verify();
	}

}
