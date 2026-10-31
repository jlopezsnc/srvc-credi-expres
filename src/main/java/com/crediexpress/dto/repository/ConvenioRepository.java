package com.crediexpress.dto.repository;

import com.crediexpress.dto.entity.Convenio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConvenioRepository extends JpaRepository<Convenio, Integer> {

    Optional<Convenio> findByLlaveIdempotencia(String llaveIdempotencia);

    /** El convenio con sus préstamos de origen y relaciones en una sola consulta (detalle, sin N+1). */
    @EntityGraph(attributePaths = {"titular", "modalidad", "prestamoConvenio", "prestamos", "prestamos.prestamo",
            "prestamos.cliente"})
    @Query("SELECT c FROM Convenio c WHERE c.idConvenio = :idConvenio")
    Optional<Convenio> buscarDetalle(@Param("idConvenio") Integer idConvenio);

    /** Bloqueado ({@code FOR UPDATE}) hasta que termine la transacción: dos cancelaciones no se cruzan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Convenio c WHERE c.idConvenio = :idConvenio")
    Optional<Convenio> bloquear(@Param("idConvenio") Integer idConvenio);
}
