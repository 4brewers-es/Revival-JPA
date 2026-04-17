package com.revival.jpa.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.revival.jpa.engine.SimplePersister;
import com.revival.jpa.exceptions.RevivalException;

/**
 * Main entry point for the Revival JPA.
 * Designed to be simple, fast, and Junior-friendly.
 */
public class Revival {
    // Initialize SLF4J Logger
    private static final Logger logger = LoggerFactory.getLogger(Revival.class);

    private final Connection connection;

    public Revival(Connection connection) {
        this.connection = connection;
    }

    public static Revival connectToMariaDB(String host, int port, String database, String user, String password) {
        String url = String.format("jdbc:mariadb://%s:%d/%s", host, port, database);
        try {
            Connection conn = DriverManager.getConnection(url, user, password);
            logger.info("Successfully connected to MariaDB at {}", host);
            return new Revival(conn);
        } catch (SQLException e) {
            throw new RevivalException("Could not connect to database '" + database
                    + "'. Check credentials and ensure MariaDB is running.", e);
        }
    }

    public <T> DeadLanguage<T> select(Class<T> clazz) {
        return new DeadLanguage<>(clazz, this.connection);
    }

    public void persist(Object entity) {
        try {
            SimplePersister persister = new SimplePersister(this.connection);
            persister.save(entity);

            // Replaced System.out with Logger
            logger.info("Successfully persisted {}", entity.getClass().getSimpleName());
        } catch (Exception e) {
            // Catching any underlying error and wrapping it in our custom exception
            throw new RevivalException("Could not save entity. " + e.getMessage(), e);
        }
    }
}