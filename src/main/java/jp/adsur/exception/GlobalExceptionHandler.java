package jp.adsur.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 核心业务异常处理
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneralException(Exception e) {
        // 过滤静态资源缺失的无关错误
        if (e instanceof NoResourceFoundException) {
            String message = e.getMessage();
            if (message.contains("static resource") || message.contains("robots933456.txt")) {
                return "静态资源未找到（非核心错误）";
            }
        }
        // 打印核心错误日志
        System.err.println("全局捕获未处理异常: " + e.getMessage());
        e.printStackTrace();
        return "服务器内部错误：" + e.getMessage();
    }
}