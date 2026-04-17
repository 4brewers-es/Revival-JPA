package com.revival.jpa.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import com.revival.jpa.engine.SimplePersister;
import com.revival.jpa.exceptions.RevivalException;

/**
 * Main entry point for the Revival JPA.
 * Designed to be simple, fast, and Junior-friendly.
 */
public class Revival {
    private final Connection connection;

    // El constructor recibe la conexión (inyectada por el usuario o un pool)
    public Revival(Connection connection) {
        this.connection = connection;
    }

    public static Revival connectToMariaDB(String host, int port, String database, String user, String password) {
        String url = String.format("jdbc:mariadb://%s:%d/%s", host, port, database);
        try {
            Connection conn = DriverManager.getConnection(url, user, password);
            System.out.println("Revival: Successfully connected to MariaDB at " + host);
            return new Revival(conn);
        } catch (SQLException e) {
            // ¡Adiós RuntimeException, hola RevivalException!
            throw new RevivalException("Could not connect to database '" + database
                    + "'. Check credentials and ensure MariaDB is running.", e);
        }
    }

    /**
     * Start a read operation using the Dead Language.
     * 
     * @param clazz The entity class we want to populate.
     */
    public <T> DeadLanguage<T> select(Class<T> clazz) {
        return new DeadLanguage<>(clazz, this.connection);
    }

    /**
     * Save or Update an entity.
     * It automatically detects if it's a new record or an existing one based on the
     * 'id' field.
     */
    public void persist(Object entity) {
        try {
            SimplePersister persister = new SimplePersister(this.connection);
            persister.save(entity);
            // Confirmación visual para el Junior de que todo fue bien
            System.out.println("Revival: Successfully persisted " + entity.getClass().getSimpleName());
        } catch (Exception e) {
            throw new RuntimeException("Revival Error: Could not save entity. " + e.getMessage(), e);
        }
    }
}