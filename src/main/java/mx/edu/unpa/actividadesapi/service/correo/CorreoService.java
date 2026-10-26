package mx.edu.unpa.actividadesapi.service.correo;

import mx.edu.unpa.actividadesapi.dto.response.RecuperarContrasenaResponse;

/**
 * Abstracción del envío de correos.
 * Implementación simulada por defecto; se cambia por SMTP real
 * ajustando application.properties y activando la implementación real.
 */
public interface CorreoService {

    /**
     * Envía (o simula) el correo de recuperación de contraseña.
     * Devuelve el DTO de respuesta que incluye el token simulado
     * cuando el SMTP no está configurado.
     *
     * @param destinatario correo del usuario
     * @param url          enlace completo con el token
     * @param token        token en texto plano (solo para simulación)
     */
    RecuperarContrasenaResponse enviarRecuperacion(String destinatario, String url, String token);

    /**
     * US-07: Envía (o simula) un correo de recordatorio de evento.
     *
     * @param destinatario correo del inscrito
     * @param nombreEvento nombre de la actividad
     * @param fecha        fecha formateada, ej. "lunes 9 de noviembre de 2026"
     * @param hora         rango horario, ej. "09:00 – 17:00"
     * @param lugar        nombre del lugar (null si no hay espacio asignado)
     * @param urlEvento    enlace público al detalle del evento
     */
    void enviarRecordatorio(String destinatario,
                            String nombreEvento,
                            String fecha,
                            String hora,
                            String lugar,
                            String urlEvento);
}
