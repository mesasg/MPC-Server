package com.example.labmcp.service;

import com.example.labmcp.model.Tarea;
import com.example.labmcp.repository.TareaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class TareaToolsServiceTests {

    @Autowired
    private TareaToolsService tools;

    @Autowired
    private TareaRepository repo;

    @BeforeEach
    void limpiar() {
        repo.deleteAll();
    }

    @Test
    void contarTareasDevuelveElTotalSinImportarElEstado() {
        Tarea t = tools.crearTarea("Pendiente", "2026-11-20");
        tools.crearTarea("Otra", "2026-11-21");
        tools.marcarCompletada(t.getId());

        assertThat(tools.contarTareas()).isEqualTo(2);
    }

    @Test
    void listarCompletadasYPendientesSonComplementarias() {
        Tarea hecha = tools.crearTarea("Hecha", "2026-11-20");
        Tarea pendiente = tools.crearTarea("Pendiente", "2026-11-21");
        tools.marcarCompletada(hecha.getId());

        assertThat(tools.listarCompletadas()).extracting(Tarea::getId).containsExactly(hecha.getId());
        assertThat(tools.listarPendientes()).extracting(Tarea::getId).containsExactly(pendiente.getId());
    }

    @Test
    void crearTareaAceptaFechaIsoYLimpiaElTitulo() {
        Tarea t = tools.crearTarea("  Preparar laboratorio  ", "2026-11-20");

        assertThat(t.getTitulo()).isEqualTo("Preparar laboratorio");
        assertThat(t.getFecha()).isEqualTo("2026-11-20");
        assertThat(t.getCompletado()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"20/11/2026", "2026-11-5", "2026-02-30", "mañana", " "})
    void crearTareaRechazaFechasInvalidas(String fecha) {
        assertThatThrownBy(() -> tools.crearTarea("Tarea", fecha))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repo.count()).isZero();
    }

    @Test
    void crearTareaRechazaTituloVacio() {
        assertThatThrownBy(() -> tools.crearTarea("   ", "2026-11-20"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("título");
    }

    @Test
    void actualizarTareaValidaFechaYConservaLoQueNoCambia() {
        Tarea t = tools.crearTarea("Original", "2026-11-20");

        Tarea actualizada = tools.actualizarTarea(t.getId(), null, "2026-12-01");
        assertThat(actualizada.getTitulo()).isEqualTo("Original");
        assertThat(actualizada.getFecha()).isEqualTo("2026-12-01");

        assertThatThrownBy(() -> tools.actualizarTarea(t.getId(), null, "01-12-2026"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eliminarTareaInexistenteInformaSinFallar() {
        assertThat(tools.eliminarTarea(9999L)).contains("no existe");
    }
}
