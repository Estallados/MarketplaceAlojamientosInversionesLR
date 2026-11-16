package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import lombok.Getter;
import lombok.Setter;

/**
 * Página de resultados con el formato del contrato: contenido, número de
 * página, tamaño y totales.
 *
 * @param <T> tipo de los elementos
 */
@Getter
@Setter
public class PaginaDTO<T> {

	/** Elementos de la página. */
	private List<T> contenido;

	/** Número de página, desde 0. */
	private int pagina;

	/** Elementos por página. */
	private int tamanio;

	/** Total de elementos en todas las páginas. */
	private long totalElementos;

	/** Total de páginas. */
	private int totalPaginas;

	/**
	 * Crea el DTO vacío; Jackson y ModelMapper lo llenan campo por campo.
	 */
	public PaginaDTO() {
		// Los valores se asignan con los setters.
	}

	/**
	 * Convierte una página de Spring Data a este formato.
	 *
	 * @param <E>       tipo de las entidades
	 * @param <T>       tipo de los elementos de salida
	 * @param page      página consultada
	 * @param conversor convierte cada entidad
	 * @return la página convertida
	 */
	public static <E, T> PaginaDTO<T> de(Page<E> page, Function<E, T> conversor) {
		return deLista(page, elementos -> elementos.stream().map(conversor).toList());
	}

	/**
	 * Crea la página convirtiendo todos sus elementos de una vez. Sirve cuando
	 * la conversión necesita datos de otras tablas: se pueden consultar para
	 * toda la página en lugar de elemento por elemento.
	 *
	 * @param <E>       tipo de las entidades
	 * @param <T>       tipo de los elementos de la página
	 * @param page      página de entidades
	 * @param conversor convierte la lista completa, en el mismo orden
	 * @return la página convertida
	 */
	public static <E, T> PaginaDTO<T> deLista(Page<E> page, Function<List<E>, List<T>> conversor) {
		PaginaDTO<T> resultado = new PaginaDTO<>();
		resultado.setContenido(conversor.apply(page.getContent()));
		resultado.setPagina(page.getNumber());
		resultado.setTamanio(page.getSize());
		resultado.setTotalElementos(page.getTotalElements());
		resultado.setTotalPaginas(page.getTotalPages());
		return resultado;
	}
}
