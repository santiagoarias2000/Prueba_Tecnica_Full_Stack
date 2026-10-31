package com.prueba.polizas.config;

import com.prueba.polizas.model.enums.EstadoPoliza;
import com.prueba.polizas.model.enums.TipoPoliza;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Filtros {@code tipo}/{@code estado} case-insensitive. Un valor desconocido lanza
 * {@code IllegalArgumentException}, que Spring envuelve en {@code MethodArgumentTypeMismatchException}
 * y el handler traduce a 400 {@code PARAMETRO_INVALIDO}.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, TipoPoliza.class, ignorandoMayusculas(TipoPoliza::valueOf));
        registry.addConverter(String.class, EstadoPoliza.class, ignorandoMayusculas(EstadoPoliza::valueOf));
    }

    private static <E extends Enum<E>> Converter<String, E> ignorandoMayusculas(Function<String, E> valueOf) {
        return fuente -> {
            String valor = fuente.trim();
            return valor.isEmpty() ? null : valueOf.apply(valor.toUpperCase(Locale.ROOT));
        };
    }
}
