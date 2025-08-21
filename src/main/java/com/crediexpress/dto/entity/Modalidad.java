package com.crediexpress.dto.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "modalidad")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Modalidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_modalidad")
    private Integer idModalidad;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "informacion_adicional")
    private String informacionAdicional;

    @Column(name = "dias_activos")
    private Integer diasActivos;
}