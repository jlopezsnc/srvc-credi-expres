package com.crediexpress.dto.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.sql.Date;
import java.time.Instant;

@Entity
@Table(name = "cliente")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Integer idCliente;

    @Column(name = "nombre_completo")
    private String nombreCompleto;

    @Column(name = "curp")
    private String curp;

    @Column(name = "domicilio_calle")
    private String domicilioCalle;

    @Column(name = "domicilio_colonia")
    private String domicilioColonia;

    @Column(name = "domicilio_ciudad")
    private String domicilioCiudad;

    @Column(name = "telefono")
    private String telefono;

    @Column(name = "correo_electronico")
    private String correoElectronico;

    @Column(name = "fecha_alta")
    private Date fechaAlta;

    /** Auditoría (V7): correo o uid del token. NULL en los clientes anteriores a la auditoría. */
    @Column(name = "registrado_por", updatable = false)
    private String registradoPor;

    @Column(name = "actualizado_por")
    private String actualizadoPor;

    /** En UTC. */
    @Column(name = "fecha_actualizacion")
    private Instant fechaActualizacion;

    /** V8: los clientes no se borran, se dan de baja. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estatus_cliente", nullable = false)
    @Builder.Default
    private EstatusCliente estatusCliente = EstatusCliente.ACTIVO;
}
