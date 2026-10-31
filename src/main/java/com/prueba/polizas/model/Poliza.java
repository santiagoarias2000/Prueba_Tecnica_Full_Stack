package com.prueba.polizas.model;

import com.prueba.polizas.exception.CodigoError;
import com.prueba.polizas.exception.EstadoInvalidoException;
import com.prueba.polizas.exception.RecursoNoEncontradoException;
import com.prueba.polizas.exception.ReglaNegocioException;
import com.prueba.polizas.model.enums.EstadoPoliza;
import com.prueba.polizas.model.enums.TipoPoliza;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Agregado raiz: poliza de arrendamiento (Individual o Colectiva) con sus riesgos.
 * Aqui viven las reglas de negocio: tipo de poliza, renovacion, cancelacion y recalculo de valores.
 */
@Entity
@Table(name = "poliza")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Poliza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_poliza", nullable = false, unique = true, length = 30)
    private String numeroPoliza;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoPoliza tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoPoliza estado;

    @Column(name = "tomador", nullable = false, length = 200)
    private String tomador;

    @Column(name = "fecha_inicio_vigencia", nullable = false)
    private LocalDate fechaInicioVigencia;

    @Column(name = "fecha_fin_vigencia", nullable = false)
    private LocalDate fechaFinVigencia;

    @Column(name = "meses_vigencia", nullable = false)
    private Integer mesesVigencia;

    @Column(name = "valor_canon", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorCanon;

    @Column(name = "valor_prima", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorPrima;

    @OneToMany(mappedBy = "poliza", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("id ASC")
    private List<Riesgo> riesgos = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en")
    private Instant actualizadoEn;

    private Poliza(String numeroPoliza, TipoPoliza tipo, String tomador, LocalDate fechaInicioVigencia,
                   int mesesVigencia) {
        if (mesesVigencia <= 0) {
            throw new IllegalArgumentException("Los meses de vigencia deben ser mayores que cero");
        }
        this.numeroPoliza = Objects.requireNonNull(numeroPoliza, "numeroPoliza");
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.tomador = Objects.requireNonNull(tomador, "tomador");
        this.mesesVigencia = mesesVigencia;
        this.estado = EstadoPoliza.ACTIVA;
        asignarVigencia(Objects.requireNonNull(fechaInicioVigencia, "fechaInicioVigencia"));
    }

    /**
     * Crea una poliza ACTIVA con sus riesgos iniciales. Si es individual debe venir con un solo riesgo.
     */
    public static Poliza nueva(String numeroPoliza, TipoPoliza tipo, String tomador, LocalDate fechaInicioVigencia,
                               int mesesVigencia, List<DatosRiesgo> riesgos) {
        Objects.requireNonNull(riesgos, "riesgos");
        if (tipo == TipoPoliza.INDIVIDUAL && riesgos.size() != 1) {
            throw new ReglaNegocioException(CodigoError.POLIZA_INDIVIDUAL_UN_RIESGO,
                    "Una poliza individual solo puede tener un riesgo");
        }
        Poliza poliza = new Poliza(numeroPoliza, tipo, tomador, fechaInicioVigencia, mesesVigencia);
        riesgos.forEach(datos -> poliza.riesgos.add(Riesgo.nuevo(poliza, datos, fechaInicioVigencia)));
        poliza.recalcularValores();
        return poliza;
    }

    /** Crea una poliza INDIVIDUAL con su unico riesgo. */
    public static Poliza individual(String numeroPoliza, String tomador, LocalDate fechaInicioVigencia,
                                    int mesesVigencia, DatosRiesgo riesgo) {
        return nueva(numeroPoliza, TipoPoliza.INDIVIDUAL, tomador, fechaInicioVigencia, mesesVigencia,
                List.of(riesgo));
    }

    /** Crea una poliza COLECTIVA con sus riesgos iniciales. */
    public static Poliza colectiva(String numeroPoliza, String tomador, LocalDate fechaInicioVigencia,
                                   int mesesVigencia, List<DatosRiesgo> riesgos) {
        return nueva(numeroPoliza, TipoPoliza.COLECTIVA, tomador, fechaInicioVigencia, mesesVigencia, riesgos);
    }

    /** Vista inmodificable de los riesgos (ordenados por id). */
    public List<Riesgo> getRiesgos() {
        return Collections.unmodifiableList(riesgos);
    }

    /**
     * Agrega un riesgo y recalcula canon y prima. Solo aplica a colectivas que no esten canceladas.
     * El riesgo empieza hoy, o en el inicio de vigencia si todavia no ha llegado.
     */
    public Riesgo agregarRiesgo(String descripcion, String arrendatario, String arrendador, BigDecimal canon) {
        if (tipo != TipoPoliza.COLECTIVA) {
            throw new ReglaNegocioException(CodigoError.TIPO_POLIZA_NO_PERMITE_RIESGOS,
                    "Solo las polizas colectivas permiten agregar riesgos");
        }
        if (estado == EstadoPoliza.CANCELADA) {
            throw new EstadoInvalidoException(CodigoError.POLIZA_CANCELADA,
                    "No se puede agregar un riesgo a una poliza cancelada");
        }
        LocalDate hoy = LocalDate.now();
        LocalDate fechaInicio = fechaInicioVigencia.isAfter(hoy) ? fechaInicioVigencia : hoy;
        Riesgo riesgo = Riesgo.nuevo(this, new DatosRiesgo(descripcion, arrendatario, arrendador, canon), fechaInicio);
        riesgos.add(riesgo);
        recalcularValores();
        return riesgo;
    }

    /**
     * Renueva por el mismo periodo: canon de cada riesgo activo x (1 + IPC), prima = canon x meses,
     * nueva vigencia desde el dia siguiente a la fecha fin y estado RENOVADA. Una cancelada no se renueva.
     */
    public void renovar(BigDecimal ipc) {
        Objects.requireNonNull(ipc, "ipc");
        if (estado == EstadoPoliza.CANCELADA) {
            throw new EstadoInvalidoException(CodigoError.POLIZA_CANCELADA_NO_RENOVABLE,
                    "La poliza " + numeroPoliza + " esta cancelada y no se puede renovar");
        }
        riesgos.stream().filter(Riesgo::estaActivo).forEach(r -> r.ajustarCanon(ipc));
        recalcularValores();
        asignarVigencia(fechaFinVigencia.plusDays(1));
        this.estado = EstadoPoliza.RENOVADA;
    }

    /**
     * Cancela la poliza y todos sus riesgos activos. Una poliza ya cancelada no se vuelve a cancelar.
     * Canon y prima se conservan como registro historico.
     *
     * @return numero de riesgos cancelados
     */
    public int cancelar(String motivo) {
        if (estado == EstadoPoliza.CANCELADA) {
            throw new EstadoInvalidoException(CodigoError.POLIZA_YA_CANCELADA,
                    "La poliza " + numeroPoliza + " ya se encuentra cancelada");
        }
        int cancelados = 0;
        for (Riesgo riesgo : riesgos) {
            if (riesgo.estaActivo()) {
                riesgo.cancelar(motivo);
                cancelados++;
            }
        }
        this.estado = EstadoPoliza.CANCELADA;
        return cancelados;
    }

    /**
     * Cancela un riesgo de esta poliza y recalcula canon y prima. En una individual el riesgo
     * no se cancela por separado: hay que cancelar la poliza.
     */
    public void cancelarRiesgo(Riesgo riesgo, String motivo) {
        if (riesgo == null || !riesgos.contains(riesgo)) {
            throw new RecursoNoEncontradoException(CodigoError.RIESGO_NO_ENCONTRADO,
                    "El riesgo no pertenece a la poliza " + numeroPoliza);
        }
        if (estado == EstadoPoliza.CANCELADA) {
            throw new EstadoInvalidoException(CodigoError.RIESGO_YA_CANCELADO,
                    "El riesgo ya fue cancelado junto con la poliza " + numeroPoliza);
        }
        if (tipo == TipoPoliza.INDIVIDUAL) {
            throw new ReglaNegocioException(CodigoError.RIESGO_INDIVIDUAL_NO_CANCELABLE,
                    "El riesgo de una poliza individual se cancela cancelando la poliza");
        }
        riesgo.cancelar(motivo);
        recalcularValores();
    }

    /**
     * COLECTIVA → canon = suma de canones de riesgos ACTIVOS (cada uno ya redondeado);
     * INDIVIDUAL → canon = canon de su riesgo. Prima = canon x meses de vigencia.
     */
    public void recalcularValores() {
        BigDecimal canon;
        if (tipo == TipoPoliza.INDIVIDUAL) {
            canon = riesgos.isEmpty() ? Dinero.CERO : riesgos.get(0).getValorCanon();
        } else {
            canon = riesgos.stream()
                    .filter(Riesgo::estaActivo)
                    .map(Riesgo::getValorCanon)
                    .reduce(Dinero.CERO, BigDecimal::add);
        }
        this.valorCanon = Dinero.redondear(canon);
        this.valorPrima = Dinero.multiplicar(valorCanon, mesesVigencia);
    }

    /** Numero de riesgos en estado ACTIVO. */
    public long cantidadRiesgosActivos() {
        return riesgos.stream().filter(Riesgo::estaActivo).count();
    }

    private void asignarVigencia(LocalDate inicio) {
        this.fechaInicioVigencia = inicio;
        this.fechaFinVigencia = inicio.plusMonths(mesesVigencia).minusDays(1);
    }

    @PrePersist
    void alCrear() {
        Instant ahora = Instant.now();
        this.creadoEn = ahora;
        this.actualizadoEn = ahora;
    }

    @PreUpdate
    void alActualizar() {
        this.actualizadoEn = Instant.now();
    }
}
