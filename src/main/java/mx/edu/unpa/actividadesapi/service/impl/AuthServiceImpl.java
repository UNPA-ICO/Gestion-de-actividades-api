package mx.edu.unpa.actividadesapi.service.impl;

import lombok.RequiredArgsConstructor;
import mx.edu.unpa.actividadesapi.dto.request.*;
import mx.edu.unpa.actividadesapi.dto.response.LoginResponse;
import mx.edu.unpa.actividadesapi.dto.response.RecuperarContrasenaResponse;
import mx.edu.unpa.actividadesapi.enums.TipoUsuario;
import mx.edu.unpa.actividadesapi.exception.BusinessException;
import mx.edu.unpa.actividadesapi.exception.ResourceNotFoundException;
import mx.edu.unpa.actividadesapi.model.*;
import mx.edu.unpa.actividadesapi.repository.*;
import mx.edu.unpa.actividadesapi.security.JwtUtil;
import mx.edu.unpa.actividadesapi.service.AuthService;
import mx.edu.unpa.actividadesapi.service.correo.CorreoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UsuarioRepository          usuarioRepository;
    private final AlumnoRepository           alumnoRepository;
    private final TokenRecuperacionRepository tokenRepository;
    private final TokenBlacklistRepository   blacklistRepository;
    private final CorreoService              correoService;
    private final JwtUtil                    jwtUtil;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    private static final int HORAS_EXPIRACION_TOKEN = 1;

    @Value("${app.url-base:http://localhost:4200}")
    private String urlBase;

    // ====================================================================
    //  Login unificado
    // ====================================================================
    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String id = request.getIdentificador().trim();
        log.info("Intento de login para identificador={}", id);
        return id.contains("@") ? loginConCorreo(id, request.getContrasena())
                : loginConMatricula(id, request.getContrasena());
    }

    private LoginResponse loginConCorreo(String correo, String contrasena) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByCorreo(correo.toLowerCase());
        if (usuarioOpt.isPresent()) {
            Usuario u = usuarioOpt.get();
            if (!Boolean.TRUE.equals(u.getActivo()))
                throw new BusinessException("Tu cuenta está desactivada. Contacta al administrador.");
            if (!encoder.matches(contrasena, u.getContrasenaHash()))
                throw new BusinessException("Correo o contraseña incorrectos.");
            log.info("Login exitoso: usuario id={} rol={}", u.getIdUsuario(), u.getRol());
            return toLoginResponse(u);
        }

        Optional<Alumno> alumnoOpt = alumnoRepository.findByCorreo(correo.toLowerCase());
        if (alumnoOpt.isPresent()) return validarYResponderAlumno(alumnoOpt.get(), contrasena);

        throw new BusinessException("Correo o contraseña incorrectos.");
    }

    private LoginResponse loginConMatricula(String matricula, String contrasena) {
        Alumno alumno = alumnoRepository.findByMatricula(matricula)
                .orElseThrow(() -> new BusinessException("Matrícula o contraseña incorrectos."));
        return validarYResponderAlumno(alumno, contrasena);
    }

    private LoginResponse validarYResponderAlumno(Alumno alumno, String contrasena) {
        if (!Boolean.TRUE.equals(alumno.getActivo()))
            throw new BusinessException("Tu cuenta está desactivada. Contacta al administrador.");
        if (!encoder.matches(contrasena, alumno.getContrasenaHash()))
            throw new BusinessException("Matrícula o contraseña incorrectos.");
        log.info("Login exitoso: alumno id={}", alumno.getIdAlumno());
        return toLoginResponse(alumno);
    }

    // ====================================================================
    //  Logout — invalida el token en BD
    // ====================================================================
    @Override
    @Transactional
    public void logout(String tokenHeader) {
        if (tokenHeader == null || !tokenHeader.startsWith("Bearer ")) return;

        String token = tokenHeader.substring(7);
        if (!jwtUtil.esValido(token)) return;

        // Evitar duplicados
        if (blacklistRepository.existsByToken(token)) return;

        // Obtener fecha de expiración del token para limpieza futura
        Date exp = jwtUtil.parsear(token).getExpiration();
        LocalDateTime fechaExp = exp.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        TokenBlacklist entrada = new TokenBlacklist();
        entrada.setToken(token);
        entrada.setFechaExpiracion(fechaExp);
        blacklistRepository.save(entrada);

        log.info("Token agregado a blacklist — expira {}", fechaExp);
    }

    // ====================================================================
    //  Registro de alumnos
    // ====================================================================
    @Override
    @Transactional
    public LoginResponse registrarAlumno(RegistroAlumnoRequest request) {
        log.info("Registro de alumno matricula={}", request.getMatricula());

        if (!request.getContrasena().equals(request.getConfirmarContrasena()))
            throw new BusinessException("Las contraseñas no coinciden.");

        String matricula = request.getMatricula().trim();
        String correo    = request.getCorreo().trim().toLowerCase();

        if (alumnoRepository.existsByMatricula(matricula))
            throw new BusinessException("Ya existe una cuenta con esa matrícula.");
        if (alumnoRepository.existsByCorreo(correo) || usuarioRepository.existsByCorreo(correo))
            throw new BusinessException("Ya existe una cuenta con ese correo.");

        Alumno alumno = new Alumno();
        alumno.setMatricula(matricula);
        alumno.setNombre(request.getNombre().trim());
        alumno.setApellidos(request.getApellidos().trim());
        alumno.setCorreo(correo);
        alumno.setContrasenaHash(encoder.encode(request.getContrasena()));
        alumno.setActivo(true);

        Alumno guardado = alumnoRepository.save(alumno);
        log.info("Alumno registrado id={}", guardado.getIdAlumno());
        return toLoginResponse(guardado);
    }

    // ====================================================================
    //  Recuperación de contraseña
    // ====================================================================
    @Override
    @Transactional
    public RecuperarContrasenaResponse solicitarRecuperacion(RecuperarContrasenaRequest request) {
        String correo = request.getCorreo().trim().toLowerCase();

        Optional<Usuario> usuarioOpt = usuarioRepository.findByCorreo(correo);
        if (usuarioOpt.isPresent()) {
            Usuario u = usuarioOpt.get();
            tokenRepository.invalidarTokensDeUsuario(u.getIdUsuario());
            String tokenStr = generarYGuardarTokenUsuario(u);
            return correoService.enviarRecuperacion(correo, urlBase + "/auth/restablecer/" + tokenStr, tokenStr);
        }

        Optional<Alumno> alumnoOpt = alumnoRepository.findByCorreo(correo);
        if (alumnoOpt.isPresent()) {
            Alumno a = alumnoOpt.get();
            tokenRepository.invalidarTokensDeAlumno(a.getIdAlumno());
            String tokenStr = generarYGuardarTokenAlumno(a);
            return correoService.enviarRecuperacion(correo, urlBase + "/auth/restablecer/" + tokenStr, tokenStr);
        }

        RecuperarContrasenaResponse r = new RecuperarContrasenaResponse();
        r.setMensaje("Si el correo está registrado, recibirás un enlace en breve.");
        return r;
    }

    @Override
    @Transactional(readOnly = true)
    public void verificarToken(String token) {
        TokenRecuperacion tr = tokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException("El enlace de recuperación no es válido o ya fue utilizado."));
        if (!tr.esValido())
            throw new BusinessException("El enlace de recuperación expiró o ya fue utilizado.");
    }

    @Override
    @Transactional
    public void restablecerContrasena(RestablecerContrasenaRequest request) {
        if (!request.getNuevaContrasena().equals(request.getConfirmarContrasena()))
            throw new BusinessException("Las contraseñas no coinciden.");

        TokenRecuperacion tr = tokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BusinessException("El enlace de recuperación no es válido."));
        if (!tr.esValido())
            throw new BusinessException("El enlace de recuperación expiró o ya fue utilizado.");

        String nuevoHash = encoder.encode(request.getNuevaContrasena());
        if (tr.getUsuario() != null) {
            tr.getUsuario().setContrasenaHash(nuevoHash);
            usuarioRepository.save(tr.getUsuario());
        } else if (tr.getAlumno() != null) {
            tr.getAlumno().setContrasenaHash(nuevoHash);
            alumnoRepository.save(tr.getAlumno());
        }
        tr.setUsado(true);
        tokenRepository.save(tr);
    }

    // ====================================================================
    //  Helpers privados
    // ====================================================================

    private String generarYGuardarTokenUsuario(Usuario u) {
        TokenRecuperacion tr = new TokenRecuperacion();
        tr.setToken(UUID.randomUUID().toString());
        tr.setUsuario(u);
        tr.setFechaExpiracion(LocalDateTime.now().plusHours(HORAS_EXPIRACION_TOKEN));
        tokenRepository.save(tr);
        return tr.getToken();
    }

    private String generarYGuardarTokenAlumno(Alumno a) {
        TokenRecuperacion tr = new TokenRecuperacion();
        tr.setToken(UUID.randomUUID().toString());
        tr.setAlumno(a);
        tr.setFechaExpiracion(LocalDateTime.now().plusHours(HORAS_EXPIRACION_TOKEN));
        tokenRepository.save(tr);
        return tr.getToken();
    }

    private LoginResponse toLoginResponse(Usuario u) {
        LoginResponse dto = new LoginResponse();
        dto.setId(u.getIdUsuario());
        dto.setNombre(u.getNombre());
        dto.setApellidos(u.getApellidos());
        dto.setCorreo(u.getCorreo());
        dto.setMatricula(null);
        dto.setIniciales(calcularIniciales(u.getNombre(), u.getApellidos()));
        TipoUsuario tipo = TipoUsuario.valueOf(u.getRol().name());
        dto.setTipoUsuario(tipo);
        dto.setToken(jwtUtil.generar(u.getIdUsuario(), u.getNombre(), tipo));
        return dto;
    }

    private LoginResponse toLoginResponse(Alumno a) {
        LoginResponse dto = new LoginResponse();
        dto.setId(a.getIdAlumno());
        dto.setNombre(a.getNombre());
        dto.setApellidos(a.getApellidos());
        dto.setCorreo(a.getCorreo());
        dto.setMatricula(a.getMatricula());
        dto.setIniciales(calcularIniciales(a.getNombre(), a.getApellidos()));
        dto.setTipoUsuario(TipoUsuario.ALUMNO);
        dto.setToken(jwtUtil.generar(a.getIdAlumno(), a.getNombre(), TipoUsuario.ALUMNO));
        return dto;
    }

    private String calcularIniciales(String nombre, String apellidos) {
        String ini = "";
        if (nombre    != null && !nombre.isBlank())    ini += nombre.trim().charAt(0);
        if (apellidos != null && !apellidos.isBlank()) ini += apellidos.trim().charAt(0);
        return ini.toUpperCase();
    }
}