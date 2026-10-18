package com.campusplus.backend.exception;

import com.campusplus.backend.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("Cache-Control", "no-store")
                .body(new ErrorResponse(401, "Giriş başarısız", "E-posta veya parola hatalı."));
    }

    @ExceptionHandler({UserNotFoundException.class, ResourceNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            RuntimeException exception) {

        ErrorResponse error = new ErrorResponse(
                404,
                "Bulunamadı",
                exception.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(error);
    }

    @ExceptionHandler({EmailAlreadyExistsException.class, ResourceConflictException.class})
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            RuntimeException exception) {

        ErrorResponse error = new ErrorResponse(
                409,
                "Çakışma",
                exception.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error);
    }


    // A concurrent request can pass the pre-check before the unique constraint rejects it.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                409, "Çakışma", "İşlem mevcut kayıtlarla çakışıyor. Gönderdiğiniz bilgileri kontrol ediniz."
        ));
    }

    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class,
            org.springframework.dao.PessimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleConcurrentChange(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                409, "Çakışma", "Kayıt değişti veya işlem şu anda kilitli; güncel veriyi alıp tekrar deneyiniz."));
    }

    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleInvalidInput(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(400, "Geçersiz istek", "İstek alanlarını ve sınırlarını kontrol ediniz."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(
            MethodArgumentNotValidException exception) {

        Map<String, String> errors = new HashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(error.getField(), error.getDefaultMessage())
                );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errors);
    }



}