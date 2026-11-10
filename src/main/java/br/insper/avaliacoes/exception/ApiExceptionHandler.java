package br.insper.avaliacoes.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record Erro(int status, String mensagem, Map<String, String> campos) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Erro> validation(MethodArgumentNotValidException exception) {
        Map<String, String> campos = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new Erro(400, "Revise os campos da avaliação.", campos));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Erro> invalidJson() {
        return ResponseEntity.badRequest().body(new Erro(400, "Envie um JSON válido com os campos da avaliação.", Map.of()));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Erro> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(new Erro(exception.getStatusCode().value(), exception.getReason(), Map.of()));
    }
}
