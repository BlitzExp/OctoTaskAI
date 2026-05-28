package com.octotask.bot.data;

import java.time.LocalDateTime;

public class SemanticRoute {
    private int id;
    private String descripcionTexto;
    private String funcionBackend;
    private LocalDateTime fechaCreacion;

    public SemanticRoute() {
    }

    public SemanticRoute(int id, String descripcionTexto, String funcionBackend, LocalDateTime fechaCreacion) {
        this.id = id;
        this.descripcionTexto = descripcionTexto;
        this.funcionBackend = funcionBackend;
        this.fechaCreacion = fechaCreacion;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDescripcionTexto() {
        return descripcionTexto;
    }

    public void setDescripcionTexto(String descripcionTexto) {
        this.descripcionTexto = descripcionTexto;
    }

    public String getFuncionBackend() {
        return funcionBackend;
    }

    public void setFuncionBackend(String funcionBackend) {
        this.funcionBackend = funcionBackend;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
