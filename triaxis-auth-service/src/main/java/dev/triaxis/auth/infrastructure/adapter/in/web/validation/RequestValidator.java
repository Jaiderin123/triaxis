package dev.triaxis.auth.infrastructure.adapter.in.web.validation;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Validator;

/** Runs bean validation for handlers (functional endpoints have no {@code @Valid}). */
@Component
public class RequestValidator {

	private final Validator validator;

	RequestValidator(Validator validator) {
		this.validator = validator;
	}

	public <T> T validate(T body) {
		BeanPropertyBindingResult errors = new BeanPropertyBindingResult(body, body.getClass().getSimpleName());
		validator.validate(body, errors);
		if (errors.hasErrors()) {
			List<String> fields = errors.getFieldErrors().stream().map(e -> e.getField()).distinct().sorted().toList();
			throw new RequestValidationException(fields);
		}
		return body;
	}

}
