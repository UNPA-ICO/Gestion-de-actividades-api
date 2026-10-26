package mx.edu.unpa.actividadesapi.service.impl;

import lombok.RequiredArgsConstructor;
import mx.edu.unpa.actividadesapi.dto.response.RecordatorioProgresoResponse;
import mx.edu.unpa.actividadesapi.exception.BusinessException;
import mx.edu.unpa.actividadesapi.exception.ResourceNotFoundException;
import mx.edu.unpa.actividadesapi.model.*;
import mx.edu.unpa.actividadesapi.repository.*;
import mx.edu.unpa.actividadesapi.service.RecordatorioService;
import mx.edu.unpa.actividadesapi.service.correo.CorreoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * US-07: Implementación del servicio de recordatorios.
 *
 * Nota de diseño: se eliminó @Async para evitar dos problemas de Spring Security 6:
 *   1. LazyInitializationException — el hilo async no hereda la sesión de Hibernate.
 *   2. Access Denied — Spring Security 6 limpia el SecurityContext al final del request,
 *      por lo que el hilo async queda sin autenticación en el dispatch del SSE.
 *
 * El envío es síncrono pero el SSE sigue funcionando porque el controlador
 * devuelve el SseEmitter antes de que el método retorne al cliente.
 * Para eventos con muchos inscritos se puede migrar a un TaskExecutor dedicado
 * con DelegatingSecurityContextRunnable cuando sea necesario.
 */
@Service
@RequiredArgsConstructor
public class RecordatorioServiceImpl implements RecordatorioService {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioServiceImpl.class);
    private static final long HORAS_ESPERA = 24;

    private final ActividadRepository        actividadRepository;
    private final UsuarioRepository          usuarioRepository;
    private final ActividadRecursoRepository actividadRecursoRepository;
    private final RecursoEspacioRepository   recursoEspacioRepository;
    private final LogRecordatorioRepository  logRepository;
    private final CorreoService              correoService;

    // Bean auxiliar separado para resolver el problema de self-invocation con @Transactional
    private final RecordatorioCorreosHelper  correosHelper;

    @Value("${app.url-base:http://localhost:4200}")
    private String urlBase;

    @Override
    @Transactional
    public void enviarRecordatorios(Integer idActividad, Integer idUsuario, SseEmitter emitter) {
        try {
            // 1. Validar entidades
            Actividad actividad = actividadRepository.findById(idActividad)
                    .orElseThrow(() -> new ResourceNotFoundException("Actividad no encontrada: " + idActividad));

            Usuario usuario = usuarioRepository.findById(idUsuario)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + idUsuario));

            // 2. Restricción: máx. 1 recordatorio cada 24 h
            LocalDateTime hace24h = LocalDateTime.now().minusHours(HORAS_ESPERA);
            boolean yaEnvioReciente = logRepository
                    .findTopByActividad_IdActividadAndFechaEnvioAfterOrderByFechaEnvioDesc(idActividad, hace24h)
                    .isPresent();

            if (yaEnvioReciente) {
                enviarError(emitter, "Ya se envió un recordatorio para este evento en las últimas 24 horas.");
                return;
            }

            // 3. Recolectar correos — llamada a bean externo para que @Transactional funcione
            List<String> correos = correosHelper.recolectarCorreos(idActividad);
            int total = correos.size();

            if (total == 0) {
                enviarError(emitter, "Esta actividad no tiene inscritos con correo registrado.");
                return;
            }

            // 4. Datos del evento
            String nombreEvento = actividad.getNombre();
            String fecha = actividad.getFechaActividad()
                    .format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", new Locale("es")));
            String hora  = actividad.getHoraInicio() + " – " + actividad.getHoraFin();
            String lugar = obtenerNombreLugar(idActividad);
            String url   = urlBase + "/calendario/" + idActividad;

            // 5. Enviar correos y emitir progreso vía SSE
            int enviados = 0;
            for (String correo : correos) {
                correoService.enviarRecordatorio(correo, nombreEvento, fecha, hora, lugar, url);
                enviados++;
                emitir(emitter, new RecordatorioProgresoResponse(
                        enviados, total, "EN_PROGRESO",
                        "Enviando " + enviados + " de " + total + "..."));
            }

            // 6. Guardar log de auditoría
            LogRecordatorio registro = new LogRecordatorio();
            registro.setActividad(actividad);
            registro.setUsuario(usuario);
            registro.setTotalDestinatarios(total);
            logRepository.save(registro);

            log.info("US-07: Recordatorios enviados actividad={} usuario={} total={}", idActividad, idUsuario, total);

            // 7. Evento final
            emitir(emitter, new RecordatorioProgresoResponse(
                    total, total, "COMPLETADO",
                    "✅ Recordatorio enviado a " + total + " inscrito(s)."));

            emitter.complete();

        } catch (BusinessException | ResourceNotFoundException ex) {
            enviarError(emitter, ex.getMessage());
        } catch (Exception ex) {
            log.error("US-07: Error inesperado en recordatorios actividad={}", idActividad, ex);
            enviarError(emitter, "Error inesperado al enviar recordatorios.");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String obtenerNombreLugar(Integer idActividad) {
        return actividadRecursoRepository.findByActividadIdActividad(idActividad).stream()
                .map(ar -> ar.getRecurso().getIdRecurso())
                .flatMap(id -> recursoEspacioRepository.findById(id).stream())
                .map(RecursoEspacio::getNombre)
                .findFirst()
                .orElse(null);
    }

    private void emitir(SseEmitter emitter, RecordatorioProgresoResponse datos) {
        try {
            emitter.send(SseEmitter.event().name("progreso").data(datos));
        } catch (IOException e) {
            log.warn("US-07: Cliente SSE desconectado.");
            emitter.completeWithError(e);
        }
    }

    private void enviarError(SseEmitter emitter, String mensaje) {
        try {
            emitter.send(SseEmitter.event().name("progreso")
                    .data(new RecordatorioProgresoResponse(0, 0, "ERROR", mensaje)));
            emitter.complete();
        } catch (IOException ignored) {
            emitter.completeWithError(ignored);
        }
    }
}