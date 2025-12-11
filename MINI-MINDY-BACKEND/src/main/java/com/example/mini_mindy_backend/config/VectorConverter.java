package com.example.mini_mindy_backend.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

import java.sql.SQLException;
import java.util.Arrays;

@Converter
public class VectorConverter implements AttributeConverter<float[], Object> {

    @Override
    public Object convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            PGobject pgObject = new PGobject();
            pgObject.setType("vector");
            // Format pgvector: [0.1,0.2,0.3,...]
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < attribute.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(attribute[i]);
            }
            sb.append("]");
            pgObject.setValue(sb.toString());
            return pgObject;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur conversion vector", e);
        }
    }

    @Override
    public float[] convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return null;
        }
        String value;
        if (dbData instanceof PGobject) {
            value = ((PGobject) dbData).getValue();
        } else {
            value = dbData.toString();
        }
        // Parse [0.1,0.2,0.3,...] → float[]
        if (value == null || value.isEmpty()) return null;
        value = value.replace("[", "").replace("]", "");
        String[] parts = value.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}
