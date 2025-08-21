package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.CalculadoraPrestamos;
import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.entity.EstadoPrestamos;
import com.crediexpress.dto.entity.Modalidad;
import com.crediexpress.dto.repository.CalculadoraPrestamosRepository;
import com.crediexpress.dto.repository.ClienteRepository;
import com.crediexpress.dto.repository.EstadoPrestamosRepository;
import com.crediexpress.dto.repository.ModalidadRepository;
import com.crediexpress.dto.view.request.CalculadoraPagosRequest;
import com.crediexpress.dto.view.response.ClienteResponse;
import com.crediexpress.dto.view.response.EstadoPrestamosResponse;
import com.crediexpress.dto.view.response.ModalidadResponse;
import com.crediexpress.service.EstadoPrestamosService;
import com.crediexpress.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@Service
@Slf4j
public class EstadoPrestamosServiceImpl implements EstadoPrestamosService {

    @Autowired
    private EstadoPrestamosRepository estadoPrestamosRepository;

    @Autowired
    private ModalidadRepository modalidadRepository;

    @Autowired
    private CalculadoraPrestamosRepository calculadoraPrestamosRepository;

    @Autowired
    private ClienteRepository clienteRepository;


    @Override
    public List<EstadoPrestamosResponse> getEstadosPrestamos() {
        List<EstadoPrestamosResponse> estadoPrestamosResponses = new ArrayList<>();
        estadoPrestamosRepository.findAll().forEach(e ->
                estadoPrestamosResponses.add(getResponse(e)));
        return estadoPrestamosResponses;
    }

    @Override
    public EstadoPrestamosResponse getEstadoPrestamosById(Integer id) {
        return estadoPrestamosRepository.findById(id).isPresent() ?
                getResponse(estadoPrestamosRepository.findById(id).get()) :
                null;
    }

    @Override
    public List<EstadoPrestamosResponse> getEstadoPrestamosByIdCliente(Integer id) {
        List<EstadoPrestamosResponse> estadoPrestamosResponses = new ArrayList<>();
        estadoPrestamosRepository.findByIdCliente(id).forEach(
                e -> {
                    estadoPrestamosResponses.add(getResponse(e));
                }
        );
        return estadoPrestamosResponses;
    }

    @Override
    public String guardarEstadoPrestamos(CalculadoraPagosRequest calculadoraPagosRequest) {
        log.info("Iniciando proceso de calculadora de pagos.");

        CalculadoraPrestamos calculadoraPrestamos = getSave(calculadoraPagosRequest);

        log.info("Se van a crear " + calculadoraPrestamos.getNumeroCuotas() + " registros de estado prestamo.");
        Integer diasIncremento = modalidadRepository.getById(calculadoraPagosRequest.getIdModalidad()).getDiasActivos();

        java.util.Date ultimaFechaCalculada = null;
        java.util.Date utilFechaSolicitud = calculadoraPrestamos.getFechaInicioCredito();
        for (int i = 1; i <= Utils.BigDecimalToInt(calculadoraPagosRequest.getNumeroCuota()); i++) {
            //Se calcularán los pagos de acuerdo a la fecha que se envia desde el front
            //incrementar en dias a partir del segundo registro
            if (i != 1) {
                utilFechaSolicitud = agregarDias(ultimaFechaCalculada, diasIncremento);
                log.info("Incrementando fecha en {} dias", diasIncremento + ". Fecha calculada {} " + utilFechaSolicitud);
            }
            calculadoraPagosRequest.setIdCalculadoraPrestamo(calculadoraPrestamos.getIdCalculadoraPrestamo());
            EstadoPrestamos estadoPrestamos = getEstadoPrestamos(calculadoraPagosRequest, utilFechaSolicitud, i);
            estadoPrestamosRepository.save(estadoPrestamos);
            ultimaFechaCalculada = estadoPrestamos.getFechaVencimiento();
        }

        log.info("Estado prestamo agregado correctamente.");
        return "Prestamo generado correctamente";
    }

    private CalculadoraPrestamos getSave(CalculadoraPagosRequest calculadoraPagosRequest) {
        return calculadoraPrestamosRepository.save(voToDto(calculadoraPagosRequest));
    }


    private static EstadoPrestamos getEstadoPrestamos(CalculadoraPagosRequest calculadoraPagosRequest, java.util.Date utilFechaSolicitud, int i) {
        return EstadoPrestamos.builder()
                .idCalculadoraPrestamo(CalculadoraPrestamos.builder()
                        .idCalculadoraPrestamo(calculadoraPagosRequest.getIdCalculadoraPrestamo())
                        .build())
                //.estadoPago(calculadoraPagosRequest.getEstadoPago())
                .fechaVencimiento(new Date(utilFechaSolicitud.getTime()))
                //.diasMoratorios("")
                .alerta("VIGENTE")
                .idCliente(Cliente.builder().idCliente(calculadoraPagosRequest.getIdCliente()).build())
                .numeroCuota(new BigDecimal(i))
                .importeCuota(calculadoraPagosRequest.getImporteCuota())
                .build();
    }

    private static EstadoPrestamosResponse getResponse(EstadoPrestamos estadoPrestamos) {
        return EstadoPrestamosResponse.builder()
                .estadoPago(estadoPrestamos.getEstadoPago())
                .id(estadoPrestamos.getId())
                .modalidadResponse(ModalidadResponse.builder()
                        .idModalidad(estadoPrestamos.getIdCalculadoraPrestamo().getIdModalidad().getIdModalidad())
                        .descripcion(estadoPrestamos.getIdCalculadoraPrestamo().getIdModalidad().getDescripcion())
                        .build())
                .clienteResponse(ClienteResponse.builder().idCliente(estadoPrestamos.getIdCliente().getIdCliente())
                        .nombreCompleto(estadoPrestamos.getIdCliente().getNombreCompleto()).build())
                .idCalculadoraPrestamo(estadoPrestamos.getIdCalculadoraPrestamo().getIdCalculadoraPrestamo())
                .diasMoratorios(estadoPrestamos.getDiasMoratorios())
                .estadoPago(estadoPrestamos.getEstadoPago())
                .numeroCuota(estadoPrestamos.getNumeroCuota())
                .alerta(estadoPrestamos.getAlerta())
                .fechaPago(estadoPrestamos.getFechaPago())
                .fechaVencimiento(estadoPrestamos.getFechaVencimiento())
                .importeCuota(estadoPrestamos.getImporteCuota())
                .build();
    }

    private EstadoPrestamos guardarEstadoPrestamo(CalculadoraPagosRequest estadoPrestamosRequest) {
        EstadoPrestamos estadoPrestamos = EstadoPrestamos.builder()
                .estadoPago(estadoPrestamosRequest.getEstadoPago())
                .alerta(estadoPrestamosRequest.getAlerta())
                .diasMoratorios(estadoPrestamosRequest.getDiasMoratorios())
                .idCalculadoraPrestamo(CalculadoraPrestamos.builder()
                        .idModalidad(Modalidad.builder().idModalidad(estadoPrestamosRequest.getIdModalidad()).build())
                        .build())
                .fechaPago(new Date(estadoPrestamosRequest.getFechaPago().getTime()))
                .idCalculadoraPrestamo(CalculadoraPrestamos.builder()
                        .idCalculadoraPrestamo(estadoPrestamosRequest.getId()).build())
                .estadoPago(estadoPrestamosRequest.getIdCalculadoraPrestamo())
                .fechaVencimiento(new Date(estadoPrestamosRequest.getFechaVencimiento().getTime()))
                .idCliente(Cliente.builder().idCliente(estadoPrestamosRequest.getIdCliente()).build())
                .importeCuota(estadoPrestamosRequest.getImporteCuota())
                .numeroCuota(estadoPrestamosRequest.getNumeroCuota())
                .build();
        return estadoPrestamosRepository.save(estadoPrestamos);
    }

    private CalculadoraPagosRequest dtoToVo(CalculadoraPrestamos calculadoraPrestamos) {
    return null;
    }

    private CalculadoraPrestamos voToDto(CalculadoraPagosRequest calculadoraPagosRequest) {
        return CalculadoraPrestamos.builder()
                .fechaInicioCredito(new Date(calculadoraPagosRequest.getFechaInicioCredito().getTime()))
                .fechaSolicitud(new Date(calculadoraPagosRequest.getFechaGeneracionOperacion().getTime()))
                .interesGenerado(BigDecimal.valueOf(calculadoraPagosRequest.getInteresGenerado()))
                .totalPagar(BigDecimal.valueOf(calculadoraPagosRequest.getTotalPagar()))
                .importeCuota(calculadoraPagosRequest.getImporteCuota())
                .numeroCuotas(Integer.valueOf(calculadoraPagosRequest.getNumeroCuota().toString()))
                .tasaInteres(BigDecimal.valueOf(calculadoraPagosRequest.getTasaInteres()))
                .idModalidad(Modalidad.builder().idModalidad(calculadoraPagosRequest.getIdModalidad()).build())
                .importeCredito(BigDecimal.valueOf(calculadoraPagosRequest.getImporteCredito()))
                .idCliente(Cliente.builder().idCliente(calculadoraPagosRequest.getIdCliente()).build())
                .build();
    }

    private java.util.Date agregarDias(java.util.Date fecha, int diasAgregar) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(fecha);
        calendar.add(Calendar.DATE, diasAgregar);
        return calendar.getTime();
    }
}