package com.revival.jpa.engine;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier; // <-- New import
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import com.revival.jpa.exceptions.RevivalException;

/**
 * Handles the automatic persistence (Insert/Update) of any object.
 * Uses Reflection to inspect the object's state and generate safe SQL.
 * As an 'engine' class, this is strictly for internal framework use.
 */
public class SimplePersister {
    private final Connection connection;

    public SimplePersister(Connection connection) {
        this.connection = connection;
    }

    /**
     * Inspects the object and decides whether to INSERT or UPDATE.
     */
    public void save(Object entity) throws Exception {
        Class<?> clazz = entity.getClass();
        String tableName = clazz.getSimpleName().toLowerCase();

        // 1. Buscamos el campo 'id' para saber si es un registro nuevo o existente
        Field idField;
        try {
            idField = clazz.getDeclaredField("id");
            idField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RevivalException("Entity " + clazz.getSimpleName() + " must have a field named 'id'.");
        }

        Object idValue = idField.get(entity);

        // 2. Lógica de decisión: Si el ID es nulo o 0, es un INSERT. Si no, es un
        // UPDATE.
        if (idValue == null || (idValue instanceof Number && ((Number) idValue).longValue() == 0)) {
            executeInsert(tableName, entity, clazz);
        } else {
            executeUpdate(tableName, entity, clazz, idField, idValue);
        }
    }
    // ... (El constructor y el método save se quedan igual) ...

    private void executeInsert(String tableName, Object entity, Class<?> clazz) throws Exception {
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
        StringBuilder placeholders = new StringBuilder("VALUES (");

        List<Object> values = new ArrayList<>();
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            int modifiers = field.getModifiers();

            // Skip static and transient fields as they don't belong to the database schema
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }

            if (field.getName().equals("id")) {
                continue; // The database auto-generates the ID
            }

            field.setAccessible(true);
            Object value = field.get(entity);

            // We only insert non-null fields to respect default database values
            if (value != null) {
                if (!values.isEmpty()) {
                    sql.append(", ");
                    placeholders.append(", ");
                }
                sql.append(field.getName());
                placeholders.append("?");
                values.add(value);
            }
        }

        sql.append(") ").append(placeholders).append(")");

        // Safe execution to prevent SQL Injection
        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < values.size(); i++) {
                stmt.setObject(i + 1, values.get(i));
            }
            stmt.executeUpdate();
        }
    }

    private void executeUpdate(String tableName, Object entity, Class<?> clazz, Field idField, Object idValue)
            throws Exception {
        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");

        List<Object> values = new ArrayList<>();
        Field[] fields = clazz.getDeclaredFields();
        boolean isFirst = true;

        for (Field field : fields) {
            int modifiers = field.getModifiers();

            // Skip static and transient fields
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }

            if (field.getName().equals("id")) {
                continue; // The ID is never updated
            }

            field.setAccessible(true);
            Object value = field.get(entity);

            // In a real JPA we might want to update nulls, but for this MVP we skip them
            if (value != null) {
                if (!isFirst) {
                    sql.append(", ");
                }
                sql.append(field.getName()).append(" = ?");
                values.add(value);
                isFirst = false;
            }
        }

        if (values.isEmpty()) {
            return; // Nothing to update
        }

        sql.append(" WHERE id = ?");
        values.add(idValue);

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < values.size(); i++) {
                stmt.setObject(i + 1, values.get(i));
            }
            stmt.executeUpdate();
        }
    }
}