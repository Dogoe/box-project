package com.endel.demobox.exception;

import com.endel.demobox.model.dto.CustomErrorDTO;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@ControllerAdvice
@Log4j2
public class GlobalControllerExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<CustomErrorDTO> handleRuntimeException(RuntimeException ex){
        log.error("Internal server error", ex);
        return new ResponseEntity<>(
                new CustomErrorDTO("fatal", "internal server error"),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(PostCustomException.class)
    public ResponseEntity<CustomErrorDTO> handlePostCustomException(PostCustomException ex){
        log.error("post custom error", ex);
        return new ResponseEntity<>(
                new CustomErrorDTO("fatal", "internal server error"),
                HttpStatus.resolve(ex.getStatus()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<CustomErrorDTO> handleResourceNotFoundException(ResourceNotFoundException ex){
        return new ResponseEntity<>(
                new CustomErrorDTO("not_found", ex.getMessage()),
                HttpStatus.NOT_FOUND);
    }

    // Handled here rather than by Spring's default resolver, which forwards to /error and
    // surfaces the field's own message (e.g. "La URL de referencia debe empezar con...").
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CustomErrorDTO> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex){
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Datos inválidos");
        return new ResponseEntity<>(
                new CustomErrorDTO("invalid_request", message),
                HttpStatus.BAD_REQUEST);
    }

    // Raised for constraint violations on list elements (e.g. List<@Valid KitItemDto>); it's a
    // RuntimeException, so without this it would fall through to the 500 handler above.
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<CustomErrorDTO> handleHandlerMethodValidationException(HandlerMethodValidationException ex){
        return new ResponseEntity<>(
                new CustomErrorDTO("invalid_request", "Datos inválidos"),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidApplianceTemplateException.class)
    public ResponseEntity<CustomErrorDTO> handleInvalidApplianceTemplateException(InvalidApplianceTemplateException ex){
        return new ResponseEntity<>(
                new CustomErrorDTO("invalid_request", ex.getMessage()),
                HttpStatus.BAD_REQUEST);
    }

}
