package com.artivisi.snapsimulator.dto;

import java.util.List;

public record AccountView(String email, List<ConnectionView> connections) {

    public AccountView {
        connections = List.copyOf(connections);
    }
}
