package com.endel.demobox.exception;

import com.endel.demobox.model.dto.CustomErrorDTO;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    @ExceptionHandler(InvalidKitCompositionException.class)
    public ResponseEntity<CustomErrorDTO> handleInvalidKitCompositionException(InvalidKitCompositionException ex){
        return new ResponseEntity<>(
                new CustomErrorDTO("invalid_request", ex.getMessage()),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<CustomErrorDTO> handleBadRequestException(BadRequestException ex){
        return new ResponseEntity<>(
                new CustomErrorDTO("invalid_request", ex.getMessage()),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidOrderTransitionException.class)
    public ResponseEntity<CustomErrorDTO> handleInvalidOrderTransitionException(InvalidOrderTransitionException ex){
        return new ResponseEntity<>(
                new CustomErrorDTO("conflict", ex.getMessage()),
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<CustomErrorDTO> handleConflictException(ConflictException ex){
        return new ResponseEntity<>(
                new CustomErrorDTO("conflict", ex.getMessage()),
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<CustomErrorDTO> handleInvalidCredentialsException(InvalidCredentialsException ex){
        return new ResponseEntity<>(
                new CustomErrorDTO("unauthorized", ex.getMessage()),
                HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<CustomErrorDTO> handleTooManyRequestsException(TooManyRequestsException ex){
        long seconds = Math.max(1, (ex.getRetryAfter().toMillis() + 999) / 1000); // rounded up
        long minutes = Math.max(1, (seconds + 59) / 60);
        String wait = minutes == 1 ? "1 minuto" : minutes + " minutos";
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(new CustomErrorDTO("too_many_requests", ex.getMessage() + " Intenta de nuevo en " + wait + "."));
    }

    @ExceptionHandler(StorageUnavailableException.class)
    public ResponseEntity<CustomErrorDTO> handleStorageUnavailableException(StorageUnavailableException ex){
        return new ResponseEntity<>(
                new CustomErrorDTO("storage_unavailable", ex.getMessage()),
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CustomErrorDTO> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex){
        return new ResponseEntity<>(
                new CustomErrorDTO("invalid_request", "La foto pesa más de 10 MB; usa una más ligera."),
                HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(InvalidApplianceTemplateException.class)
    public ResponseEntity<CustomErrorDTO> handleInvalidApplianceTemplateException(InvalidApplianceTemplateException ex){
        return new ResponseEntity<>(
                new CustomErrorDTO("invalid_request", ex.getMessage()),
                HttpStatus.BAD_REQUEST);
    }

}
