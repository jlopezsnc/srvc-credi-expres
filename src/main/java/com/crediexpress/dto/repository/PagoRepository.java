package com.crediexpress.dto.repository;

import com.crediexpress.dto.entity.EstatusPago;
import com.crediexpress.dto.entity.Pago;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Integer>, JpaSpecificationExecutor<Pago> {

    interface AbonoCuota {
        Integer getIdCuota();

        BigDecimal getAbonado();
    }

    interface TotalCobrado {
        Long getPagos();

        Number getImporte();
    }

    @Override
    @EntityGraph(attributePaths = {"cliente", "prestamo", "cuota"})
    Page<Pago> findAll(Specification<Pago> especificacion, Pageable pagina);

    /** Reportes: todos los del filtro, con sus relaciones en la misma consulta (sin N+1). */
    @Override
    @EntityGraph(attributePaths = {"cliente", "prestamo", "cuota"})
    List<Pago> findAll(Specification<Pago> especificacion, Sort orden);

    /** Los renglones que ya se registraron con esta llave (más de uno si fue una liquidación). */
    @EntityGraph(attributePaths = {"cliente", "prestamo", "cuota"})
    List<Pago> findByLlaveIdempotenciaOrderByIdPago(String llaveIdempotencia);

    /** Solo el folio (sin cargar la entidad), para después bloquear la operación completa. */
    @Query("SELECT p.folio FROM Pago p WHERE p.idPago = :idPago")
    Optional<String> findFolio(@Param("idPago") Integer idPago);

    /** Todos los renglones de una operación, bloqueados para cancelarlos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pago p WHERE p.folio = :folio ORDER BY p.idPago")
    List<Pago> bloquearPorFolio(@Param("folio") String folio);

    @Query("""
            SELECT p.cuota.id AS idCuota, SUM(p.importe) AS abonado FROM Pago p
            WHERE p.cuota.id IN :idsCuota AND p.estatus = :estatus
            GROUP BY p.cuota.id""")
    List<AbonoCuota> sumarPorCuota(@Param("idsCuota") Collection<Integer> idsCuota,
                                   @Param("estatus") EstatusPago estatus);

    /** Operaciones (folios distintos) y dinero que entró en caja (importe + recargo) en el periodo. */
    @Query("""
            SELECT COUNT(DISTINCT p.folio) AS pagos, SUM(p.importe + p.recargo) AS importe FROM Pago p
            WHERE p.estatus = :estatus AND p.fechaRegistro >= :desde AND p.fechaRegistro < :hasta""")
    TotalCobrado totalRegistrado(@Param("estatus") EstatusPago estatus,
                                 @Param("desde") Instant desde,
                                 @Param("hasta") Instant hasta);

    default List<AbonoCuota> sumarVigentesPorCuota(Collection<Integer> idsCuota) {
        return sumarPorCuota(idsCuota, EstatusPago.VIGENTE);
    }

    default TotalCobrado totalCobrado(Instant desde, Instant hasta) {
        return totalRegistrado(EstatusPago.VIGENTE, desde, hasta);
    }
}
