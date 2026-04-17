package com.revival.jpa.core;

import com.revival.jpa.exceptions.RevivalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * The Dead Language query builder.
 * Allows fluent SQL execution for Juniors without writing actual SQL.
 */
public class DeadLanguage<T> {
    // Initialize SLF4J Logger
    private static final Logger logger = LoggerFactory.getLogger(DeadLanguage.class);

    private final Class<T> clazz;
    private final Connection connection;
    private String[] columns;

    public DeadLanguage(Class<T> clazz, Connection connection) {
        this.clazz = clazz;
        this.connection = connection;
    }

    public DeadLanguage<T> fill(String... fields) {
        this.columns = fields;
        return this;
    }

    public DeadLanguage<T> all() {
        Field[] fields = clazz.getDeclaredFields();

        // Use a dynamic list to collect only valid database columns
        List<String> validColumns = new ArrayList<>();

        for (Field field : fields) {
            int modifiers = field.getModifiers();

            // Skip fields that should not be mapped to the database
            if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
                validColumns.add(field.getName());
            }
        }

        // Convert the dynamic list back to our array structure
        this.columns = validColumns.toArray(new String[0]);
        return this;
    }

    public T from(Object id) {
        if (columns == null || columns.length == 0) {
            throw new RevivalException("You must call fill() or all() before from() with at least one column.");
        }

        String tableName = clazz.getSimpleName().toLowerCase();
        String selectedColumns = String.join(", ", columns);
        String sql = "SELECT " + selectedColumns + " FROM " + tableName + " WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return hydrate(rs);
                } else {
                    return null;
                }
            }
        } catch (Exception e) {
            throw new RevivalException("Failed to execute 'Dead Language' query: " + sql, e);
        }
    }

    public void destroy(Object id) {
        String tableName = clazz.getSimpleName().toLowerCase();
        String sql = "DELETE FROM " + tableName + " WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, id);
            int affectedRows = stmt.executeUpdate();

            // Replaced System.out with appropriate log levels
            if (affectedRows == 0) {
                logger.warn("Tried to destroy {} with ID {} but it didn't exist.", clazz.getSimpleName(), id);
            } else {
                logger.info("Successfully destroyed {} with ID {}", clazz.getSimpleName(), id);
            }
        } catch (Exception e) {
            throw new RevivalException("Could not destroy entity " + clazz.getSimpleName() + " with ID " + id, e);
        }
    }

    private T hydrate(ResultSet rs) {
        T instance;
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RevivalException("Entity '" + clazz.getSimpleName()
                    + "' must have a public no-arguments constructor so Revival can create it.", e);
        }

        for (String colName : columns) {
            try {
                Field field = clazz.getDeclaredField(colName);
                field.setAccessible(true);

                Object value = rs.getObject(colName);
                field.set(instance, value);

            } catch (NoSuchFieldException e) {
                // Replaced System.err with Logger warning
                logger.warn("Column '{}' requested, but there is no attribute '{}' in class {}", colName, colName,
                        clazz.getSimpleName());
            } catch (Exception e) {
                throw new RevivalException("Could not inject database value into field '" + colName + "' of class "
                        + clazz.getSimpleName() + ". Check data types.", e);
            }
        }

        return instance;
    }
}