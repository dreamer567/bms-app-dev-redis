package jp.adsur.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice // 全局异常处理
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 捕获所有运行时异常
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException e) {
        // 记录ERROR级别日志，包含完整堆栈
        log.error("全局捕获运行时异常", e);
        // 返回500状态码和友好提示
        return new ResponseEntity<>("服务器内部错误：" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // 捕获所有Exception
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        log.error("全局捕获未处理异常", e);
        return new ResponseEntity<>("服务器异常：" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}