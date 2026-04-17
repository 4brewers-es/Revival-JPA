package com.revival.jpa.core;

import com.revival.jpa.exceptions.RevivalException; // <-- Importamos nuestra excepción

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * The Dead Language query builder.
 * Allows fluent SQL execution for Juniors without writing actual SQL.
 */
public class DeadLanguage<T> {
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

    public T from(Object id) {
        if (columns == null || columns.length == 0) {
            throw new RevivalException("You must call fill() before from() with at least one column.");
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

    /**
     * Converts a SQL ResultSet into a Java Object using Reflection.
     * Uses explicit exception handling to guide Junior developers.
     */
    private T hydrate(ResultSet rs) {
        T instance;
        try {
            // Instanciamos el objeto vacío.
            // Si esto falla, suele ser porque la entidad no tiene constructor vacío.
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
                // Mantenemos el warning amigable en consola, no rompemos la app
                System.err.println(
                        "Revival Warning: Column '" + colName + "' requested in fill(), but there is no attribute '"
                                + colName + "' in class " + clazz.getSimpleName());
            } catch (Exception e) {
                // Atrapamos errores raros, como intentar meter un String en un int
                throw new RevivalException("Could not inject database value into field '" + colName + "' of class "
                        + clazz.getSimpleName() + ". Check data types.", e);
            }
        }

        return instance;
    }
}