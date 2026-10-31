package com.crediexpress.dto.repository;

import com.crediexpress.dto.entity.EstadoCuota;
import com.crediexpress.dto.entity.EstadoPrestamos;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EstadoPrestamosRepository extends JpaRepository<EstadoPrestamos, Integer>,
        JpaSpecificationExecutor<EstadoPrestamos> {

    /** Saldo pendiente de un grupo de cuotas: cuántas son y cuánto deben en total. */
    interface TotalCuotas {
        Long getCuotas();

        /** Suma de dobles que devuelve MySQL: normalizar con {@code Dinero.de(Number)}. */
        Number getImporte();
    }

    // Saldo = importe_cuota (redondeado a centavos) − pagos VIGENTE de la cuota.
    String SALDO_CUOTAS = """
            SELECT COUNT(*) AS cuotas,
                   SUM(ROUND(c.importe_cuota, 2) - COALESCE((SELECT SUM(p.importe) FROM pago p
                        WHERE p.id_cuota = c.id AND p.estatus = 'VIGENTE'), 0)) AS importe
            FROM estado_prestamos c
            WHERE (c.estado_pago IS NULL OR c.estado_pago IN (1, 2))
            """;

    @Query("FROM EstadoPrestamos ep WHERE ep.idCliente.idCliente = ?1")
    List<EstadoPrestamos> findByIdCliente(Integer idCliente);

    /** Lista de cobranza: trae cliente, préstamo y modalidad en la misma consulta. */
    @Override
    @EntityGraph(attributePaths = {"idCliente", "idCalculadoraPrestamo", "idCalculadoraPrestamo.idModalidad"})
    Page<EstadoPrestamos> findAll(Specification<EstadoPrestamos> especificacion, Pageable pagina);

    /** Reportes: todos los del filtro, con sus relaciones en la misma consulta (sin N+1). */
    @Override
    @EntityGraph(attributePaths = {"idCliente", "idCalculadoraPrestamo"})
    List<EstadoPrestamos> findAll(Specification<EstadoPrestamos> especificacion, Sort orden);

    @Query("SELECT c.idCalculadoraPrestamo.idCalculadoraPrestamo FROM EstadoPrestamos c WHERE c.id = :idCuota")
    Optional<Integer> findIdPrestamo(@Param("idCuota") Integer idCuota);

    @Query("""
            SELECT c FROM EstadoPrestamos c
            WHERE c.idCalculadoraPrestamo.idCalculadoraPrestamo = :idPrestamo
              AND (c.estadoPago IS NULL OR c.estadoPago IN :conSaldo)
            ORDER BY c.numeroCuota""")
    List<EstadoPrestamos> findConSaldo(@Param("idPrestamo") Integer idPrestamo,
                                       @Param("conSaldo") Collection<Integer> conSaldo);

    /**
     * Las cuotas con saldo del préstamo, bloqueadas ({@code SELECT … FOR UPDATE}) hasta que termine
     * la transacción: dos cobros al mismo préstamo se hacen uno después del otro. Siempre en el
     * mismo orden para no provocar bloqueos cruzados.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM EstadoPrestamos c
            WHERE c.idCalculadoraPrestamo.idCalculadoraPrestamo = :idPrestamo
              AND (c.estadoPago IS NULL OR c.estadoPago IN :conSaldo)
            ORDER BY c.numeroCuota""")
    List<EstadoPrestamos> bloquearConSaldo(@Param("idPrestamo") Integer idPrestamo,
                                           @Param("conSaldo") Collection<Integer> conSaldo);

    @Query("""
            SELECT c FROM EstadoPrestamos c
            WHERE c.idCalculadoraPrestamo.idCalculadoraPrestamo = :idPrestamo
            ORDER BY c.numeroCuota""")
    List<EstadoPrestamos> cuotasDelPrestamo(@Param("idPrestamo") Integer idPrestamo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM EstadoPrestamos c WHERE c.id IN :ids ORDER BY c.id")
    List<EstadoPrestamos> bloquear(@Param("ids") Collection<Integer> ids);

    @Query("""
            SELECT COUNT(c) > 0 FROM EstadoPrestamos c
            WHERE c.idCliente.idCliente = :idCliente
              AND (c.estadoPago IS NULL OR c.estadoPago IN :conSaldo)""")
    boolean existsConSaldoDelCliente(@Param("idCliente") Integer idCliente,
                                     @Param("conSaldo") Collection<Integer> conSaldo);

    @Query("""
            SELECT COUNT(c) > 0 FROM EstadoPrestamos c
            WHERE c.idCalculadoraPrestamo.idCalculadoraPrestamo = :idPrestamo AND c.estadoPago = :estado""")
    boolean existsDelPrestamoConEstado(@Param("idPrestamo") Integer idPrestamo, @Param("estado") Integer estado);

    @NativeQuery(SALDO_CUOTAS + "  AND c.fecha_vencimiento = :fecha")
    TotalCuotas totalQueVence(@Param("fecha") LocalDate fecha);

    @NativeQuery(SALDO_CUOTAS + "  AND c.fecha_vencimiento < :fecha")
    TotalCuotas totalVencidoAntesDe(@Param("fecha") LocalDate fecha);

    @NativeQuery(SALDO_CUOTAS + "  AND c.fecha_vencimiento BETWEEN :desde AND :hasta")
    TotalCuotas totalQueVenceEntre(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    default List<EstadoPrestamos> findConSaldoDelPrestamo(Integer idPrestamo) {
        return findConSaldo(idPrestamo, EstadoCuota.codigosConSaldo());
    }

    default List<EstadoPrestamos> bloquearConSaldoDelPrestamo(Integer idPrestamo) {
        return bloquearConSaldo(idPrestamo, EstadoCuota.codigosConSaldo());
    }

    default boolean prestamoEnConvenio(Integer idPrestamo) {
        return existsDelPrestamoConEstado(idPrestamo, EstadoCuota.EN_CONVENIO.codigo());
    }

    default boolean prestamoCancelado(Integer idPrestamo) {
        return existsDelPrestamoConEstado(idPrestamo, EstadoCuota.CANCELADA.codigo());
    }

    /** Todas las cuotas del préstamo, bloqueadas, en orden. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM EstadoPrestamos c
            WHERE c.idCalculadoraPrestamo.idCalculadoraPrestamo = :idPrestamo
            ORDER BY c.numeroCuota""")
    List<EstadoPrestamos> bloquearDelPrestamo(@Param("idPrestamo") Integer idPrestamo);

    /** Las cuotas del préstamo con ese {@code estado_pago}, bloqueadas, en orden. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM EstadoPrestamos c
            WHERE c.idCalculadoraPrestamo.idCalculadoraPrestamo = :idPrestamo AND c.estadoPago = :estado
            ORDER BY c.numeroCuota""")
    List<EstadoPrestamos> bloquearDelPrestamoConEstado(@Param("idPrestamo") Integer idPrestamo,
                                                      @Param("estado") Integer estado);

    default boolean clienteTieneSaldo(Integer idCliente) {
        return existsConSaldoDelCliente(idCliente, EstadoCuota.codigosConSaldo());
    }
}
