//package jp.adsur.common;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.*;
//import org.springframework.web.bind.annotation.*;
//
//@Slf4j
//@RestControllerAdvice
//public class GlobalExceptionHandler {
//
//    @ExceptionHandler(Exception.class)
//    public ResponseEntity<String> handle(Exception e) {
//        log.error("Unhandled exception", e);
//        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body("Internal Server Error");
//    }
//}
