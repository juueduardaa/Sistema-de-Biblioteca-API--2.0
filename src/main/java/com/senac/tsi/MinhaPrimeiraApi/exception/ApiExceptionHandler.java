package com.senac.tsi.MinhaPrimeiraApi.exception;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Centraliza as exceções dos controllers e devolve um formato JSON padronizado. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessRuleException ex) {
        return build(ex.getStatus(), ex.getMessage());
    }

    /** Erros de validação dos corpos JSON marcados com @Valid. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "Dados inválidos: " + details);
    }

    /** Erros de validação em parâmetros, IDs e valores individuais. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        String details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "Parâmetros inválidos: " + details);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBind(BindException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "Parâmetros inválidos: " + details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST,
                "Corpo da requisição inválido. Confira o JSON, os formatos de data (AAAA-MM-DD) e os valores permitidos para enum.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Valor inválido para o parâmetro '" + ex.getName() + "'.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST, "Parâmetro obrigatório ausente: '" + ex.getParameterName() + "'.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT,
                "A operação viola uma regra de integridade do banco: verifique valores duplicados ou registros relacionados.");
    }

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ApiError> handleInvalidQuery(InvalidDataAccessApiUsageException ex) {
        return build(HttpStatus.BAD_REQUEST,
                "Parâmetros de consulta inválidos. Confira os nomes de campos usados em 'sort'.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        HttpHeaders headers = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }
        ApiError body = error(HttpStatus.METHOD_NOT_ALLOWED,
                "Método HTTP não permitido neste endereço. Métodos aceitos: " + ex.getSupportedHttpMethods());
        return new ResponseEntity<>(body, headers, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Tipo de conteúdo não suportado. Envie o corpo da requisição como application/json.");
    }

    /** Evita expor detalhes internos ou rastros de código ao consumidor da API. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro interno ao processar a solicitação. Tente novamente ou contate o responsável pela API.");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(error(status, message));
    }

    private ApiError error(HttpStatus status, String message) {
        return new ApiError(Instant.now().toString(), status.value(), status.getReasonPhrase(), message);
    }
}
