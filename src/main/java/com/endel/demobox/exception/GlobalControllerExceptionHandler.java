package com.endel.demobox.exception;

import com.endel.demobox.model.dto.CustomErrorDTO;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

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

    @ExceptionHandler(InvalidApplianceTemplateException.class)
    public ResponseEntity<CustomErrorDTO> handleInvalidApplianceTemplateException(InvalidApplianceTemplateException ex){
        return new ResponseEntity<>(
                new CustomErrorDTO("invalid_request", ex.getMessage()),
                HttpStatus.BAD_REQUEST);
    }

}
