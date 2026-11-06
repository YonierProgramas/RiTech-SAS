package co.edu.ucc.ritech.web;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,Object> uploadTooLarge(MaxUploadSizeExceededException ex) {
        return Map.of("timestamp", Instant.now().toString(), "status", 400, "message", "El archivo supera el tamaño máximo permitido");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,Object> badRequest(IllegalArgumentException ex) {
        return Map.of("timestamp", Instant.now().toString(), "status", 400, "message", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,Object> validation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().map(e -> e.getField() + ": " + e.getDefaultMessage()).orElse("Datos inválidos");
        return Map.of("timestamp", Instant.now().toString(), "status", 400, "message", msg);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String,Object> conflict(DataIntegrityViolationException ex) {
        return Map.of("timestamp", Instant.now().toString(), "status", 409, "message", "La operación entra en conflicto con datos existentes");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String,Object> notFound(NoHandlerFoundException ex) {
        return Map.of("timestamp", Instant.now().toString(), "status", 404, "message", "Recurso no encontrado");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String,Object> forbidden(AccessDeniedException ex) {
        return Map.of("timestamp", Instant.now().toString(), "status", 403, "message", "No tienes permisos para esta operación");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String,Object> serverError(Exception ex) {
        return Map.of("timestamp", Instant.now().toString(), "status", 500, "message", "Error interno del servidor");
    }
}
