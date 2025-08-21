package com.crediexpress.dto.repository;

import com.crediexpress.dto.entity.CalculadoraPrestamos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.stereotype.Repository;

@Repository
public interface CalculadoraPrestamosRepository extends JpaRepository<CalculadoraPrestamos, Integer> {
}
