package com.prueba.polizas.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce excepciones a {@link ProblemDetail} ({@code application/problem+json}) con el formato de 02c.
 * Extiende {@link ResponseEntityExceptionHandler} para cubrir de forma uniforme las excepciones estandar
 * de Spring MVC (405, 415, ...), sobreescribiendo las que tienen codigo propio.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<Object> manejarNegocio(NegocioException ex, HttpServletRequest request) {
        log.warn("Error de negocio {}: {}", ex.getCodigo(), ex.getMessage());
        return respuesta(ProblemDetailFactory.crear(ex.getCodigo(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Object> manejarConcurrencia(OptimisticLockingFailureException ex,
                                                      HttpServletRequest request) {
        log.warn("Conflicto de concurrencia en {}: {}", request.getRequestURI(), ex.getMessage());
        return respuesta(ProblemDetailFactory.crear(CodigoError.CONFLICTO_CONCURRENCIA,
                "El recurso fue modificado por otra operacion; consulte de nuevo y reintente",
                request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> manejarNoControlada(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respuesta(ProblemDetailFactory.crear(CodigoError.ERROR_INTERNO,
                "Ocurrio un error interno; reporte el correlationId al equipo de soporte",
                request.getRequestURI()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<ErrorCampo> errores = Stream.concat(
                        ex.getBindingResult().getFieldErrors().stream()
                                .map(e -> new ErrorCampo(e.getField(), e.getDefaultMessage())),
                        ex.getBindingResult().getGlobalErrors().stream()
                                .map(e -> new ErrorCampo(e.getObjectName(), e.getDefaultMessage())))
                .sorted(Comparator.comparing(ErrorCampo::campo).thenComparing(ErrorCampo::mensaje))
                .toList();
        ProblemDetail problem = ProblemDetailFactory.crear(CodigoError.VALIDACION,
                "La solicitud tiene " + errores.size() + " error(es) de validacion", uri(request));
        problem.setProperty("errores", errores);
        return respuesta(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        log.debug("Body ilegible: {}", ex.getMessage());
        return respuesta(ProblemDetailFactory.crear(CodigoError.VALIDACION,
                "El cuerpo de la solicitud no es un JSON valido o tiene tipos incorrectos", uri(request)));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String nombre = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        StringBuilder detail = new StringBuilder("El parametro '").append(nombre)
                .append("' tiene un valor invalido: '").append(ex.getValue()).append("'");
        Class<?> tipo = ex.getRequiredType();
        if (tipo != null && tipo.isEnum()) {
            detail.append(". Valores permitidos: ").append(Arrays.stream(tipo.getEnumConstants())
                    .map(Object::toString).collect(Collectors.joining(", ")));
        }
        return respuesta(ProblemDetailFactory.crear(CodigoError.PARAMETRO_INVALIDO, detail.toString(),
                uri(request)));
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex,
                                                                    HttpHeaders headers, HttpStatusCode status,
                                                                    WebRequest request) {
        return respuesta(ProblemDetailFactory.crear(CodigoError.RECURSO_NO_ENCONTRADO,
                "No existe la ruta " + uri(request), uri(request)));
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                         HttpHeaders headers,
                                                                         HttpStatusCode status,
                                                                         WebRequest request) {
        ProblemDetail problem = ProblemDetailFactory.crear(CodigoError.METODO_NO_PERMITIDO,
                "El metodo " + ex.getMethod() + " no esta soportado en " + uri(request), uri(request));
        return ResponseEntity.status(status).headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                     HttpHeaders headers, HttpStatusCode status,
                                                                     WebRequest request) {
        return respuesta(ProblemDetailFactory.crear(CodigoError.TIPO_CONTENIDO_NO_SOPORTADO,
                "Content-Type no soportado; use application/json", uri(request)));
    }

    /** Resto de excepciones estandar de Spring MVC: se normalizan al formato de 02c. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        String detail = body instanceof ProblemDetail pd && pd.getDetail() != null ? pd.getDetail() : ex.getMessage();
        HttpStatus resuelto = HttpStatus.resolve(statusCode.value());
        String codigo = statusCode.value() == 400 ? CodigoError.PARAMETRO_INVALIDO.name()
                : resuelto != null ? resuelto.name() : CodigoError.ERROR_INTERNO.name();
        ProblemDetail problem = ProblemDetailFactory.crear(statusCode, codigo, detail, uri(request));
        return ResponseEntity.status(statusCode).headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static ResponseEntity<Object> respuesta(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static String uri(WebRequest request) {
        if (request instanceof NativeWebRequest nativo) {
            HttpServletRequest servlet = nativo.getNativeRequest(HttpServletRequest.class);
            if (servlet != null) {
                return servlet.getRequestURI();
            }
        }
        return null;
    }
}
