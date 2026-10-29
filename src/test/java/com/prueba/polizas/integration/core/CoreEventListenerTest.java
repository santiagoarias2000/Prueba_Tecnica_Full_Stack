package com.prueba.polizas.integration.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.prueba.polizas.model.enums.OperacionCore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Listener AFTER_COMMIT y construccion del payload enviado al CORE (02e, RN-15). */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("CoreEventListener y CoreEventoPayload (02e, RN-15)")
class CoreEventListenerTest {

    private final CoreEdicionPort puerto = mock(CoreEdicionPort.class);
    private final CoreEventListener listener = new CoreEventListener(puerto);

    @Test
    @DisplayName("Traduce el CambioEstadoEvent a payload ACTUALIZACION y notifica al puerto")
    void debeNotificarPayloadDerivadoDelEvento() {
        listener.alConfirmarCambio(new CambioEstadoEvent(2L, 100L, OperacionCore.AGREGAR_RIESGO));

        verify(puerto).notificar(new CoreEventoPayload("ACTUALIZACION", 2L, OperacionCore.AGREGAR_RIESGO, 100L));
    }

    @Test
    @DisplayName("RN-15: si el puerto lanza RuntimeException, el listener la registra en [CORE-LISTENER] y no la propaga")
    void noDebePropagarFallosDelPuerto(CapturedOutput salida) {
        doThrow(new IllegalStateException("CORE caido")).when(puerto).notificar(any());

        assertThatCode(() -> listener.alConfirmarCambio(
                new CambioEstadoEvent(1L, null, OperacionCore.RENOVAR_POLIZA))).doesNotThrowAnyException();

        assertThat(salida.getOut())
                .contains("[CORE-LISTENER]")
                .contains("polizaId=1")
                .contains("operacion=RENOVAR_POLIZA")
                .contains("CORE caido");
    }

    @Test
    @DisplayName("CoreEventoPayload.desde: evento fijo ACTUALIZACION y copia polizaId, operacion y riesgoId")
    void debeConstruirPayloadDesdeEvento() {
        CoreEventoPayload deRiesgo = CoreEventoPayload.desde(
                new CambioEstadoEvent(4L, 21L, OperacionCore.CANCELAR_RIESGO));
        CoreEventoPayload dePoliza = CoreEventoPayload.desde(
                new CambioEstadoEvent(2L, null, OperacionCore.CANCELAR_POLIZA));

        assertThat(deRiesgo).isEqualTo(new CoreEventoPayload("ACTUALIZACION", 4L, OperacionCore.CANCELAR_RIESGO, 21L));
        assertThat(dePoliza.evento()).isEqualTo(CoreEventoPayload.EVENTO_ACTUALIZACION);
        assertThat(dePoliza.polizaId()).isEqualTo(2L);
        assertThat(dePoliza.operacion()).isEqualTo(OperacionCore.CANCELAR_POLIZA);
        assertThat(dePoliza.riesgoId()).isNull();
    }

    @Test
    @DisplayName("CambioEstadoEvent exige operacion no nula")
    void debeExigirOperacion() {
        assertThatNullPointerException().isThrownBy(() -> new CambioEstadoEvent(1L, null, null))
                .withMessageContaining("operacion");
    }
}
