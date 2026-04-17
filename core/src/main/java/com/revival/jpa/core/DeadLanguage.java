package com.revival.jpa.core;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;

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

    /**
     * Specifies which fields to bring from the database.
     */
    public DeadLanguage<T> fill(String... fields) {
        this.columns = fields;
        return this;
    }

    /**
     * Executes the query filtering by ID and hydrates the object.
     * Prevents SQL injection by using PreparedStatement.
     */
    public T from(Object id) {
        if (columns == null || columns.length == 0) {
            throw new RuntimeException("Revival Error: You must call fill() before from() with at least one column.");
        }

        // 1. Construimos el SQL de forma dinámica y segura
        String tableName = clazz.getSimpleName().toLowerCase();
        String selectedColumns = String.join(", ", columns);
        String sql = "SELECT " + selectedColumns + " FROM " + tableName + " WHERE id = ?";

        // 2. Ejecutamos contra la base de datos (Protección contra Inyección SQL)
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, id); // Insertamos el ID de forma segura

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return hydrate(rs); // 3. Llenamos el objeto
                } else {
                    return null; // No se encontró el registro
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Revival SQL Error: Failed to execute 'Dead Language' query: " + sql, e);
        }
    }

    /**
     * The Magic Box: Converts a SQL ResultSet into a Java Object using Reflection.
     */
    private T hydrate(ResultSet rs) throws Exception {
        // Instanciamos el objeto vacío (requiere constructor sin parámetros)
        T instance = clazz.getDeclaredConstructor().newInstance();

        // Inyectamos cada columna solicitada en el atributo correspondiente
        for (String colName : columns) {
            try {
                // Buscamos el atributo en la clase Java
                Field field = clazz.getDeclaredField(colName);
                field.setAccessible(true); // Rompemos el private para inyectar directo (JPA Style)

                // Sacamos el valor de la base de datos y lo metemos en el objeto
                Object value = rs.getObject(colName);
                field.set(instance, value);

            } catch (NoSuchFieldException e) {
                // Mensaje amigable para el Junior
                System.err.println(
                        "Revival Warning: Column '" + colName + "' requested in fill(), but there is no attribute '"
                                + colName + "' in class " + clazz.getSimpleName());
            }
        }

        return instance;
    }
}