package org.metrolink.bas.edge;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class RestExceptionAdvice {
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<String> unsupported(UnsupportedOperationException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Operation not implemented";
        return ResponseEntity.status(501).body(msg);
    }
}
