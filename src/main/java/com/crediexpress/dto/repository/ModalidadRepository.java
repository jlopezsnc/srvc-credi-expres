package com.crediexpress.dto.repository;

import com.crediexpress.dto.entity.Modalidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModalidadRepository extends JpaRepository <Modalidad, Integer> {
}
