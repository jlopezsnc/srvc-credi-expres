package com.crediexpress.dto.repository;

import com.crediexpress.dto.entity.EstadoPrestamos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EstadoPrestamosRepository extends JpaRepository <EstadoPrestamos, Integer> {

    @Query("FROM EstadoPrestamos ep WHERE ep.idCliente.idCliente = ?1")
    List<EstadoPrestamos> findByIdCliente(Integer idCliente);
}
