package com.revival.jpa.core;

import com.revival.jpa.exceptions.RevivalException;

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

    /**
     * Specifies specific fields to fetch.
     */
    public DeadLanguage<T> fill(String... fields) {
        this.columns = fields;
        return this;
    }

    /**
     * Magic method: Uses reflection to automatically select all fields in the
     * class.
     * Perfect for "SELECT *" behavior without writing the columns manually.
     */
    public DeadLanguage<T> all() {
        Field[] fields = clazz.getDeclaredFields();
        this.columns = new String[fields.length];

        // Extract all variable names from the Java class
        for (int i = 0; i < fields.length; i++) {
            this.columns[i] = fields[i].getName();
        }
        return this;
    }

    /**
     * Executes the query filtering by ID and hydrates the object.
     */
    public T from(Object id) {
        if (columns == null || columns.length == 0) {
            throw new RevivalException("You must call fill() or all() before from() with at least one column.");
        }

        String tableName = clazz.getSimpleName().toLowerCase();
        // If the user requested 'all', we could use '*', but specifying columns is
        // safer for mapping
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
     * Deletes a record directly from the database by its ID.
     * Terminal operation: does not return 'this', but executes immediately.
     */
    public void destroy(Object id) {
        String tableName = clazz.getSimpleName().toLowerCase();
        String sql = "DELETE FROM " + tableName + " WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, id);
            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                System.out.println("Revival Info: Tried to destroy " + clazz.getSimpleName() + " with ID " + id
                        + " but it didn't exist.");
            } else {
                System.out.println("Revival: Successfully destroyed " + clazz.getSimpleName() + " with ID " + id);
            }
        } catch (Exception e) {
            throw new RevivalException("Could not destroy entity " + clazz.getSimpleName() + " with ID " + id, e);
        }
    }

    /**
     * Converts a SQL ResultSet into a Java Object using Reflection.
     */
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
                System.err.println("Revival Warning: Column '" + colName + "' requested, but there is no attribute '"
                        + colName + "' in class " + clazz.getSimpleName());
            } catch (Exception e) {
                throw new RevivalException("Could not inject database value into field '" + colName + "' of class "
                        + clazz.getSimpleName() + ". Check data types.", e);
            }
        }

        return instance;
    }
}