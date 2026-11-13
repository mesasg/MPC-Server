package com.example.labmcp.service;

import com.example.labmcp.model.Tarea;
import com.example.labmcp.repository.TareaRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * CRUD de tareas expuesto como herramientas MCP.
 *
 * Cada método @Tool se publica automáticamente por el servidor MCP:
 *   - nombre      = nombre del método
 *   - descripción = atributo description
 *   - JSON schema      = derivado de los parámetros (incluye @ToolParam)
 *
 * Claude (desde Claude Desktop) podrá invocar estos métodos cuando el usuario
 * hable en lenguaje natural: "créame una tarea para el lunes", "márcalas todas
 * como completadas", etc.
 */
@Component
public class TareaToolsService {

    private final TareaRepository repo;

    public TareaToolsService(TareaRepository repo) {
        this.repo = repo;
    }

    // ---------- READ ----------

    @Tool(description = "Devuelve la lista completa de tareas almacenadas.")
    public List<Tarea> listarTareas() {
        return repo.findAll();
    }

    @Tool(description = "Devuelve únicamente las tareas pendientes (completado = false).")
    public List<Tarea> listarPendientes() {
        return repo.findByCompletado(false);
    }

    @Tool(description = "Devuelve únicamente las tareas ya completadas (completado = true).")
    public List<Tarea> listarCompletadas() {
        return repo.findByCompletado(true);
    }

    @Tool(description = "Devuelve el número total de tareas almacenadas, sin importar su estado. "
            + "Úsala cuando el usuario pregunte cuántas tareas existen; no devuelve el detalle de cada tarea.")
    public long contarTareas() {
        return repo.count();
    }

    @Tool(description = "Busca tareas cuyo título contiene el fragmento indicado (case-insensitive).")
    public List<Tarea> buscarTareas(
            @ToolParam(description = "Fragmento de texto a buscar en el título") String fragmento) {
        return repo.findByTituloContainingIgnoreCase(fragmento);
    }

    // ---------- CREATE ----------

    @Tool(description = "Crea una nueva tarea con el título y fecha indicados. Por defecto queda como pendiente.")
    @Transactional
    public Tarea crearTarea(
            @ToolParam(description = "Título descriptivo de la tarea") String titulo,
            @ToolParam(description = "Fecha de la tarea en formato ISO yyyy-MM-dd, por ejemplo 2026-11-20") String fecha) {
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El título es obligatorio");
        }
        return repo.save(new Tarea(titulo.trim(), validarFecha(fecha), Boolean.FALSE));
    }

    // ---------- UPDATE ----------

    @Tool(description = "Marca como completada la tarea con el id indicado.")
    @Transactional
    public Tarea marcarCompletada(
            @ToolParam(description = "Identificador único de la tarea") Long id) {
        Tarea t = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la tarea " + id));
        t.setCompletado(Boolean.TRUE);
        return repo.save(t);
    }

    @Tool(description = "Actualiza el título o la fecha de una tarea existente. Los parámetros nulos no se modifican.")
    @Transactional
    public Tarea actualizarTarea(
            @ToolParam(description = "Id de la tarea a actualizar") Long id,
            @ToolParam(description = "Nuevo título (opcional, null = no cambiar)", required = false) String titulo,
            @ToolParam(description = "Nueva fecha en formato ISO yyyy-MM-dd (opcional, null = no cambiar)", required = false) String fecha) {
        Tarea t = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la tarea " + id));
        if (titulo != null && !titulo.isBlank()) t.setTitulo(titulo.trim());
        if (fecha != null && !fecha.isBlank()) t.setFecha(validarFecha(fecha));
        return repo.save(t);
    }

    // ---------- DELETE ----------

    @Tool(description = "Elimina la tarea con el id indicado. Devuelve un mensaje de confirmación.")
    @Transactional
    public String eliminarTarea(
            @ToolParam(description = "Id de la tarea a eliminar") Long id) {
        if (!repo.existsById(id)) {
            return "La tarea " + id + " no existe.";
        }
        repo.deleteById(id);
        return "Tarea " + id + " eliminada correctamente.";
    }

    // ---------- VALIDACIÓN ----------

    /** Exige una fecha real en formato ISO: rechaza 20/11/2026 y también 2026-02-30. */
    private static String validarFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            throw new IllegalArgumentException("La fecha es obligatoria y debe tener formato yyyy-MM-dd");
        }
        String valor = fecha.trim();
        try {
            LocalDate.parse(valor, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha inválida '" + valor + "': use el formato yyyy-MM-dd");
        }
        return valor;
    }
}
