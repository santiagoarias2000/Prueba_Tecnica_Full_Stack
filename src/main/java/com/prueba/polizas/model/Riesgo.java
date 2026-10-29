package com.prueba.polizas.model;

import com.prueba.polizas.exception.CodigoError;
import com.prueba.polizas.exception.EstadoInvalidoException;
import com.prueba.polizas.model.enums.EstadoRiesgo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Contrato de arrendamiento asegurado (inmueble + arrendatario + arrendador) dentro de una poliza.
 * Sus transiciones de estado solo se ejecutan a traves del agregado {@link Poliza}.
 */
@Entity
@Table(name = "riesgo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Riesgo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poliza_id", nullable = false)
    private Poliza poliza;

    @Column(name = "descripcion", nullable = false, length = 200)
    private String descripcion;

    @Column(name = "arrendatario", nullable = false, length = 200)
    private String arrendatario;

    @Column(name = "arrendador", nullable = false, length = 200)
    private String arrendador;

    @Column(name = "valor_canon", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorCanon;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoRiesgo estado;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_cancelacion")
    private LocalDate fechaCancelacion;

    @Column(name = "motivo_cancelacion", length = 300)
    private String motivoCancelacion;

    private Riesgo(Poliza poliza, DatosRiesgo datos, LocalDate fechaInicio) {
        Objects.requireNonNull(datos, "datos del riesgo");
        this.poliza = Objects.requireNonNull(poliza, "poliza");
        this.descripcion = Objects.requireNonNull(datos.descripcion(), "descripcion");
        this.arrendatario = Objects.requireNonNull(datos.arrendatario(), "arrendatario");
        this.arrendador = Objects.requireNonNull(datos.arrendador(), "arrendador");
        this.valorCanon = Dinero.redondear(Objects.requireNonNull(datos.valorCanon(), "valorCanon"));
        if (this.valorCanon.signum() <= 0) {
            throw new IllegalArgumentException("El canon del riesgo debe ser mayor que cero");
        }
        this.fechaInicio = Objects.requireNonNull(fechaInicio, "fechaInicio");
        this.estado = EstadoRiesgo.ACTIVO;
    }

    static Riesgo nuevo(Poliza poliza, DatosRiesgo datos, LocalDate fechaInicio) {
        return new Riesgo(poliza, datos, fechaInicio);
    }

    /** Indica si el riesgo esta {@code ACTIVO}. */
    public boolean estaActivo() {
        return estado == EstadoRiesgo.ACTIVO;
    }

    /**
     * Cancela el riesgo con fecha de hoy (RN-09: un riesgo ya cancelado no se vuelve a cancelar).
     */
    void cancelar(String motivo) {
        if (!estaActivo()) {
            throw new EstadoInvalidoException(CodigoError.RIESGO_YA_CANCELADO,
                    "El riesgo " + id + " ya se encuentra cancelado");
        }
        this.estado = EstadoRiesgo.CANCELADO;
        this.fechaCancelacion = LocalDate.now();
        this.motivoCancelacion = motivo;
    }

    /** Ajusta el canon por IPC: {@code canon x (1 + ipc)}, escala 2 HALF_UP (RN-05, S9). */
    void ajustarCanon(BigDecimal ipc) {
        this.valorCanon = Dinero.ajustarPorIpc(valorCanon, ipc);
    }
}
