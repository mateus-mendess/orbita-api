package com.orbita.api.common.config;

import com.orbita.api.common.model.Category;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToCategoryConverter implements Converter<String, Category> {

	@Override
	public Category convert(String source) {
		return Category.fromValue(source);
	}

}
