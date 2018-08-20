package stroom.properties.global.impl.db;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FieldMapper {
    public static void copy(Object from, Object to) {
        FieldMapper.copy(from, to, Object.class);
    }

    public static void copy(Object from, Object to, Class depth) {
        try {
            Class fromClass = from.getClass();
            Class toClass = to.getClass();
            List<Field> fromFields = collectFields(fromClass, depth);
            List<Field> toFields = collectFields(toClass, depth);
            Field target;
            for (Field source : fromFields) {
                if ((target = findAndRemove(source, toFields)) != null) {
                    target.set(to, source.get(from));
                }
            }
        } catch (final IllegalAccessException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static List<Field> collectFields(Class c, Class depth) {
        List<Field> accessibleFields = new ArrayList<>();
        do {
            int modifiers;
            for (Field field : c.getDeclaredFields()) {
                modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)) {
                    accessibleFields.add(field);
                }
            }
            c = c.getSuperclass();
        } while (c != null && c != depth);
        return accessibleFields;
    }

    private static Field findAndRemove(Field field, List<Field> fields) {
        Field actual;
        for (Iterator<Field> i = fields.iterator(); i.hasNext(); ) {
            actual = i.next();
            if (field.getName().equals(actual.getName())
                    && field.getType().equals(actual.getType())) {
                i.remove();
                return actual;
            }
        }
        return null;
    }
}