package mx.edu.unpa.actividadesapi.service.correo.impl;

import mx.edu.unpa.actividadesapi.dto.response.RecuperarContrasenaResponse;
import mx.edu.unpa.actividadesapi.service.correo.CorreoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Implementación real del servicio de correo con JavaMailSender.
 * Activa cuando correo.smtp.habilitado=true en application.properties.
 *
 * Configuración necesaria en application.properties:
 *   correo.smtp.habilitado=true
 *   spring.mail.host=smtp.gmail.com
 *   spring.mail.port=587
 *   spring.mail.username=tu-correo@gmail.com
 *   spring.mail.password=tu-app-password
 *   spring.mail.properties.mail.smtp.auth=true
 *   spring.mail.properties.mail.smtp.starttls.enable=true
 *   correo.remitente=no-reply@unpa.edu.mx
 */
@Service
@ConditionalOnProperty(name = "correo.smtp.habilitado", havingValue = "true")
public class CorreoServiceSmtpImpl implements CorreoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoServiceSmtpImpl.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${correo.remitente:no-reply@unpa.edu.mx}")
    private String remitente;

    @Override
    public RecuperarContrasenaResponse enviarRecuperacion(String destinatario, String url, String token) {
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(remitente);
            mensaje.setTo(destinatario);
            mensaje.setSubject("Recuperación de contraseña — UNPA Eventos");
            mensaje.setText(construirCuerpoRecuperacion(url));

            mailSender.send(mensaje);
            log.info("Correo de recuperación enviado a {}", destinatario);
        } catch (Exception e) {
            log.error("Error al enviar correo de recuperación a {}: {}", destinatario, e.getMessage());
            // No lanzamos excepción al cliente: la respuesta genérica es igual
            // si el correo existe o no (seguridad anti-enumeración de cuentas).
        }

        RecuperarContrasenaResponse respuesta = new RecuperarContrasenaResponse();
        respuesta.setMensaje("Si el correo está registrado, recibirás un enlace en breve.");
        respuesta.setTokenSimulado(null); // en producción no se devuelve el token
        return respuesta;
    }

    @Override
    public void enviarRecordatorio(String destinatario, String nombreEvento,
                                   String fecha, String hora, String lugar, String urlEvento) {
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(remitente);
            mensaje.setTo(destinatario);
            mensaje.setSubject("Recordatorio: " + nombreEvento + " — UNPA Eventos");
            mensaje.setText(construirCuerpoRecordatorio(nombreEvento, fecha, hora, lugar, urlEvento));
            mailSender.send(mensaje);
            log.info("[RECORDATORIO] Enviado a {}", destinatario);
        } catch (Exception e) {
            log.error("[RECORDATORIO] Error al enviar a {}: {}", destinatario, e.getMessage());
        }
    }

    private String construirCuerpoRecuperacion(String url) {
        return """
                Hola,

                Recibimos una solicitud para restablecer la contraseña de tu cuenta en UNPA Eventos.

                Haz clic en el siguiente enlace para crear una nueva contraseña:
                %s

                Este enlace es válido por 1 hora. Si no solicitaste este cambio, ignora este mensaje.

                UNPA — Gestión de Eventos Universitarios
                """.formatted(url);
    }
    private String construirCuerpoRecordatorio(String nombreEvento, String fecha,
                                               String hora, String lugar, String urlEvento) {
        String lugarTexto = (lugar != null && !lugar.isBlank())
                ? "Lugar  : " + lugar
                : "Lugar  : Por confirmar";

        return """
                Hola,
 
                Te recordamos que tienes una inscripción confirmada en el siguiente evento:
 
                Evento : %s
                Fecha  : %s
                Hora   : %s
                %s
 
                Puedes consultar los detalles completos aquí:
                %s
 
                ¡Te esperamos!
 
                UNPA — Gestión de Eventos Universitarios
                """.formatted(nombreEvento, fecha, hora, lugarTexto, urlEvento);
    }
}
