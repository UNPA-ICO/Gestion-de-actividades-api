package mx.edu.unpa.actividadesapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

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
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // CORS para la API
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:4200")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        // CORS para el corcho publico (US-20): cualquier origen, solo lectura
        // Va DESPUES de /api/** porque Spring evalua en orden y el ultimo gana
        // para el mismo path
        registry.addMapping("/api/corcho/**")
                .allowedOriginPatterns("*")          // patterns, no allowedOrigins, por compat con '*'
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)             // sin credenciales => '*' es valido
                .maxAge(3600);

        // para Inscripciones
        registry.addMapping("/api/inscripciones/**")
                .allowedOrigins("http://localhost:4200")
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        registry.addMapping("/api/inscripciones/externo/**")
                .allowedOrigins("http://localhost:4200")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        // CORS para las imágenes
        registry.addMapping("/uploads/**")
                .allowedOrigins("http://localhost:4200")
                .allowedMethods("GET");
    }
}