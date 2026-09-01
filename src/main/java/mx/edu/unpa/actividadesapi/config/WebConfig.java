package mx.edu.unpa.actividadesapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Origen(es) permitidos para CORS, separados por coma si hay más de uno
     * (ej. dominio con y sin www, o staging + producción).
     * Se define en application.properties vía app.cors.allowed-origins,
     * que a su vez lee la variable de entorno CORS_ALLOWED_ORIGIN.
     */
    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    /**
     * Mapea las URLs /uploads/** a la carpeta física del proyecto.
     * Permite servir las imágenes subidas como archivos estáticos.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
        String uploadPath = uploadDir.toUri().toString();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath)
                .setCachePeriod(3600);
    }

    /**
     * Configuración CORS unificada:
     * - /api/**     → todos los métodos (frontend Angular consume la API)
     * - /uploads/** → solo GET (Angular muestra imágenes con <img src=...>)
     *
     * Nota: /api/inscripciones/** y /api/inscripciones/externo/** ya no tienen
     * mapping propio porque /api/** los cubre y es igual o más permisivo;
     * los controllers de inscripciones solo exponen GET/POST/DELETE, así que
     * no hay diferencia real de comportamiento.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // CORS para la API
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        // CORS para el corcho publico (US-20): cualquier origen, solo lectura.
        // Va DESPUES de /api/** porque Spring evalua el pattern mas especifico
        // y este es intencionalmente publico (no usa allowedOrigins parametrizado).
        registry.addMapping("/api/corcho/**")
                .allowedOriginPatterns("*")          // patterns, no allowedOrigins, por compat con '*'
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)             // sin credenciales => '*' es valido
                .maxAge(3600);

        // CORS para las imágenes
        registry.addMapping("/uploads/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET");
    }
}