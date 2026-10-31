# polizas-api

API REST para gestionar pólizas de arrendamiento (individuales y colectivas) y sus riesgos. Permite consultar, renovar por IPC, cancelar y agregar o cancelar riesgos. Cada cambio de estado se notifica al CORE a través de un mock que simula el servicio de edición de la capa media WebLogic.

Es la solución del Módulo 2 de la prueba técnica para Desarrollador TI Sénior.

## Tecnologías

- Java 17 y Spring Boot 3.5 (Web, Data JPA, Validation, Actuator)
- H2 en memoria, con el esquema en `schema.sql` y datos de ejemplo en `data.sql`
- Swagger UI con springdoc-openapi
- JUnit 5, Mockito, MockMvc y JaCoCo para las pruebas
- Maven Wrapper, así que no hace falta tener Maven instalado

## Cómo ejecutarla

Solo se necesita el JDK 17.

```bash
./mvnw spring-boot:run        # en Windows: mvnw.cmd spring-boot:run
```

La API queda en `http://localhost:8080`. También se puede generar el jar y ejecutarlo:

```bash
./mvnw clean package
java -jar target/polizas-api-0.0.1-SNAPSHOT.jar
```

O con Docker:

```bash
docker compose up --build
```

| Qué | Dónde |
|---|---|
| Swagger | http://localhost:8080/swagger-ui.html |
| Consola H2 | http://localhost:8080/h2-console (JDBC `jdbc:h2:mem:polizasdb`, usuario `sa`, sin clave) |
| Health | http://localhost:8080/actuator/health |

La base es en memoria: cada vez que se reinicia la aplicación, los datos vuelven a su estado inicial.

## Seguridad

Todos los endpoints piden el header `x-api-key: 123456`. Si falta o no coincide, la API responde 401. En Swagger se configura una sola vez con el botón **Authorize**.

Swagger, la consola H2 y el health check no piden la clave. El valor se puede cambiar con la variable de entorno `POLIZAS_API_KEY`.

## Endpoints

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/polizas?tipo=&estado=` | Lista las pólizas. Los dos filtros son opcionales y no distinguen mayúsculas |
| GET | `/polizas/{id}` | Detalle de una póliza (lo agregué para el front de pólizas individuales) |
| GET | `/polizas/{id}/riesgos` | Riesgos de una póliza |
| POST | `/polizas/{id}/renovar` | Renueva la póliza aplicando el IPC. Body opcional: `{"ipc": 0.06}` |
| POST | `/polizas/{id}/cancelar` | Cancela la póliza y todos sus riesgos. Body opcional: `{"motivo": "..."}` |
| POST | `/polizas/{id}/riesgos` | Agrega un riesgo. Solo para pólizas colectivas |
| POST | `/riesgos/{id}/cancelar` | Cancela un riesgo de una póliza colectiva |
| POST | `/core-mock/evento` | Mock del CORE. Solo deja en el log el intento de envío |

Los errores se devuelven en formato `ProblemDetail` (RFC 7807), con un campo `codigo` que el front puede usar, por ejemplo `POLIZA_CANCELADA_NO_RENOVABLE`.

## Datos de ejemplo

| Id | Número | Tipo | Estado | Canon | Riesgos |
|---|---|---|---|---|---|
| 1 | IND-2026-0001 | Individual | Activa | 1.500.000 | 1 |
| 2 | COL-2026-0001 | Colectiva | Activa | 3.200.000 | 10 y 11 |
| 3 | IND-2026-0002 | Individual | Cancelada | 950.000 | 3 (cancelado) |
| 4 | COL-2026-0002 | Colectiva | Renovada | 3.150.000 | 20, 21 y 22 (el 22 cancelado) |
| 5 | IND-2026-0003 | Individual | Activa | 2.300.000 | 5 |

Los riesgos nuevos se crean desde el id 100.

## Ejemplos

Con la aplicación recién arrancada:

```bash
# Listar las colectivas activas
curl -H 'x-api-key: 123456' 'http://localhost:8080/polizas?tipo=COLECTIVA&estado=ACTIVA'

# Renovar la póliza 1: el canon pasa de 1.500.000 a 1.576.500 (IPC 5,10 %)
curl -X POST -H 'x-api-key: 123456' http://localhost:8080/polizas/1/renovar

# Agregar un riesgo a la póliza 2 -> 201
curl -X POST http://localhost:8080/polizas/2/riesgos \
  -H 'x-api-key: 123456' -H 'Content-Type: application/json' \
  -d '{"descripcion":"Apto 502 - Calle 100 # 10-20","arrendatario":"Luis Martínez","arrendador":"María Rodríguez","valorCanon":1800000}'

# Cancelar la póliza 2 junto con sus riesgos
curl -X POST -H 'x-api-key: 123456' http://localhost:8080/polizas/2/cancelar

# Mock del CORE -> 202
curl -X POST http://localhost:8080/core-mock/evento \
  -H 'x-api-key: 123456' -H 'Content-Type: application/json' \
  -d '{"evento":"ACTUALIZACION","polizaId":555}'
```

Casos de error:

```bash
# Renovar una póliza cancelada -> 409
curl -X POST -H 'x-api-key: 123456' http://localhost:8080/polizas/3/renovar

# Agregar un riesgo a una individual -> 422
curl -X POST http://localhost:8080/polizas/1/riesgos \
  -H 'x-api-key: 123456' -H 'Content-Type: application/json' \
  -d '{"descripcion":"Casa 2","arrendatario":"Ana","arrendador":"Pedro","valorCanon":900000}'

# Sin API key -> 401
curl -i http://localhost:8080/polizas
```

En `requests.http` están todas las peticiones, incluidos más casos de error, listas para ejecutarlas desde IntelliJ o desde VS Code con la extensión REST Client.

## Reglas de negocio

Las reglas están en las entidades `Poliza` y `Riesgo`, no en los controllers ni en los servicios:

- Una póliza individual tiene un solo riesgo. Si se intenta agregar otro, responde 422.
- Una póliza cancelada no se puede renovar (409).
- Al cancelar una póliza se cancelan todos sus riesgos activos.
- Solo se agregan riesgos a pólizas colectivas que no estén canceladas.
- Al renovar, el canon de cada riesgo activo se multiplica por `(1 + IPC)`, la prima se recalcula como canon por meses de vigencia y la nueva vigencia empieza el día siguiente al fin de la anterior.
- En una colectiva, el canon de la póliza es la suma de sus riesgos activos.

Para el dinero uso `BigDecimal` con dos decimales y redondeo `HALF_UP`. El IPC por defecto es 5,10 % (dato del DANE para 2025) y se puede cambiar en `application.yml`.

## Integración con el CORE

Cuando una operación cambia el estado de una póliza o de un riesgo, el servicio publica un evento. Después del commit, un listener lo envía por HTTP a `/core-mock/evento` a través de la interfaz `CoreEdicionPort`. El mock solo escribe en el log:

```
[CORE-MOCK] Intento de envío al CORE -> evento=ACTUALIZACION, polizaId=1, operacion=RENOVAR_POLIZA ...
```

Lo hice así por dos razones. La primera es que el servicio no depende de cómo se llame al CORE: para usar el servicio real de WebLogic basta con definir `POLIZAS_CORE_URL`. La segunda es que, al notificar después del commit, nunca se avisa al CORE de un cambio que luego se revierte.

Si el CORE falla, la operación local se mantiene y el error queda en el log.

## Supuestos

El enunciado deja algunos puntos abiertos. Estas son las decisiones que tomé:

- Se pueden renovar pólizas activas o ya renovadas.
- Cancelar algo que ya está cancelado responde 409.
- El riesgo de una póliza individual no se cancela por separado; se cancela junto con la póliza (422).
- No se agregan riesgos a una póliza cancelada (409).
- Si se cancela el último riesgo de una colectiva, la póliza no se cancela sola. Me parece una decisión de negocio, así que solo dejo una advertencia en el log.
- Al cancelar una póliza, el canon y la prima se conservan como histórico.

## Pruebas

```bash
./mvnw clean verify
```

Ejecuta las 171 pruebas (dominio, servicios, controllers, seguridad e integración de punta a punta) y revisa con JaCoCo que la cobertura de `service` y `model` no baje del 80 %; hoy está en 100 %. El reporte queda en `target/site/jacoco/index.html`.

## Estructura

```
src/main/java/com/prueba/polizas/
├── controller/     endpoints REST y mock del CORE
├── service/        casos de uso
├── repository/     acceso a datos con Spring Data JPA
├── model/          entidades Poliza y Riesgo, con las reglas de negocio
├── dto/            objetos de entrada y salida
├── mapper/         conversión entre entidades y DTOs
├── integration/    envío de eventos al CORE
├── security/       filtro de la API key
├── config/         configuración, Swagger y correlation id
└── exception/      manejo de errores
```

## Qué cambiaría para producción

- Reemplazar la notificación directa por una tabla outbox y un broker (Kafka), con reintentos, para no perder eventos si el CORE está caído.
- Usar Oracle o PostgreSQL con migraciones en Flyway.
- Mover la autenticación a OAuth2/JWT en el API Gateway.
- Agregar paginación a los listados y versionar las rutas (`/v1`).
- Un job que renueve automáticamente las pólizas próximas a vencer y envíe las notificaciones.

## Autor

Santiago Arias
