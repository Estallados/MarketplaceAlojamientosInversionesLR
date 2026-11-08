package co.edu.unbosque.marketplaceInversionesLR.configuration;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra el {@link ModelMapper} que copia los datos de las entidades a los
 * DTO.
 */
@Configuration
public class ModelMapperConfig {

	/**
	 * Copiador de entidades a DTO.
	 *
	 * @return instancia de ModelMapper
	 */
	@Bean
	public ModelMapper modelMapper() {
		return new ModelMapper();
	}
}
