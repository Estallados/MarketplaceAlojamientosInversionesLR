package co.edu.unbosque.marketplaceInversionesLR.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas, por ejemplo la limpieza de los códigos de
 * verificación vencidos ({@code CodigoVerificacionRepository#limpiar}).
 */
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
