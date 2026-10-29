package com.prueba.polizas.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadatos OpenAPI y esquema de seguridad {@code x-api-key} aplicado globalmente (boton Authorize). */
@Configuration
public class OpenApiConfig {

    static final String ESQUEMA = "ApiKeyAuth";

    @Bean
    public OpenAPI polizasOpenApi(PolizasProperties properties) {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Gestión de Pólizas de Arrendamiento")
                        .version("1.0.0")
                        .description("Prueba técnica Desarrollador TI Sénior. Gestión de pólizas individuales y "
                                + "colectivas, riesgos, renovación por IPC e integración (mock) con el CORE."))
                .components(new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(properties.seguridad().header())))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }
}
