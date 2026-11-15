//package com.umar.exception;
//
//
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.ExceptionHandler;
//import org.springframework.web.bind.annotation.RestControllerAdvice;
//
//import java.time.Instant;
//
//@RestControllerAdvice
//public class VaultExceptionHandler {
//
//    @ExceptionHandler(VaultPolicyDeniedException.class)
//    public ResponseEntity<ErrorResponse> handlePolicyDenied(
//            VaultPolicyDeniedException exception) {
//
//        return ResponseEntity
//                .status(HttpStatus.FORBIDDEN)
//                .body(new ErrorResponse(
//                        "POLICY_DENIED",
//                        exception.getMessage(),
//                        Instant.now()
//                ));
//    }
//
//    @ExceptionHandler(IllegalArgumentException.class)
//    public ResponseEntity<ErrorResponse> handleBadRequest(
//            IllegalArgumentException exception) {
//
//        return ResponseEntity
//                .badRequest()
//                .body(new ErrorResponse(
//                        "VALIDATION_ERROR",
//                        exception.getMessage(),
//                        Instant.now()
//                ));
//    }
//
//    @ExceptionHandler(Exception.class)
//    public ResponseEntity<ErrorResponse> handleInternalError(
//            Exception exception) {
//
//        // Log the exception safely using a correlation ID.
//        // Never log request values, tokens, or secrets.
//
//        return ResponseEntity
//                .status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body(new ErrorResponse(
//                        "INTERNAL_ERROR",
//                        "An internal Vault Service error occurred",
//                        Instant.now()
//                ));
//    }
//
//    public record ErrorResponse(
//            String code,
//            String message,
//            Instant timestamp
//    ) {
//    }
//}
