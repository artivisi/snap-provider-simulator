package com.artivisi.snapsimulator.controller.snap;

import com.artivisi.snapsimulator.exception.SnapException;
import com.artivisi.snapsimulator.snap.SnapService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** SNAP endpoints always answer in SNAP format. */
@RestControllerAdvice(basePackageClasses = SnapExceptionHandler.class)
public class SnapExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SnapExceptionHandler.class);

    @ExceptionHandler(SnapException.class)
    ResponseEntity<SnapErrorBody> snap(SnapException e) {
        return ResponseEntity.status(e.responseCode().httpStatus()).body(SnapErrorBody.of(e));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<SnapErrorBody> unreadable(HttpServletRequest request) {
        return snap(new SnapException(service(request).badRequest()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<SnapErrorBody> unexpected(HttpServletRequest request, Exception e) {
        log.error("Unhandled error on SNAP endpoint {}", request.getRequestURI(), e);
        return snap(new SnapException(service(request).generalError()));
    }

    private static SnapService service(HttpServletRequest request) {
        return (SnapService) request.getAttribute(SnapInboundFilter.SERVICE);
    }
}
