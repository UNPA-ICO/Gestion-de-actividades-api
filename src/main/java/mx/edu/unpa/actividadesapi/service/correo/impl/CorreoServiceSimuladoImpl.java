package mx.edu.unpa.actividadesapi.service.correo.impl;

import mx.edu.unpa.actividadesapi.dto.response.RecuperarContrasenaResponse;
import mx.edu.unpa.actividadesapi.service.correo.CorreoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Implementación simulada del servicio de correo.
 * Activa cuando correo.smtp.habilitado=false (valor por defecto).
 * Imprime el token en el log y lo devuelve en la respuesta
 * para que el equipo pueda probar el flujo sin servidor SMTP.
 *
 * Para activar SMTP real: correo.smtp.habilitado=true en application.properties
 */
@Service
@ConditionalOnProperty(name = "correo.smtp.habilitado", havingValue = "false", matchIfMissing = true)
public class CorreoServiceSimuladoImpl implements CorreoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoServiceSimuladoImpl.class);

    @Override
    public RecuperarContrasenaResponse enviarRecuperacion(String destinatario, String url, String token) {
        log.info("==========================================================");
        log.info("[CORREO SIMULADO] Destinatario : {}", destinatario);
        log.info("[CORREO SIMULADO] URL de reset : {}", url);
        log.info("[CORREO SIMULADO] Token        : {}", token);
        log.info("==========================================================");

        RecuperarContrasenaResponse respuesta = new RecuperarContrasenaResponse();
        respuesta.setMensaje("Si el correo está registrado, recibirás un enlace en breve.");
        respuesta.setTokenSimulado(token); // visible en la respuesta JSON para pruebas
        return respuesta;
    }

    @Override
    public void enviarRecordatorio(String destinatario, String nombreEvento,
                                   String fecha, String hora, String lugar, String urlEvento) {
        log.info("==========================================================");
        log.info("[RECORDATORIO SIMULADO] Para    : {}", destinatario);
        log.info("[RECORDATORIO SIMULADO] Evento  : {}", nombreEvento);
        log.info("[RECORDATORIO SIMULADO] Fecha   : {} | Hora: {}", fecha, hora);
        log.info("[RECORDATORIO SIMULADO] Lugar   : {}", lugar != null ? lugar : "Sin espacio asignado");
        log.info("[RECORDATORIO SIMULADO] Enlace  : {}", urlEvento);
        log.info("==========================================================");
    }
}
