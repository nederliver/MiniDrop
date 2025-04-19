package net.nederliver.minidrop.util; // Make sure this package matches where you save the file

// Imports needed for reflection and types
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

public class ReflectionUtils {

    // --- Field Access ---

    private static Field findField(Class<?> startClass, String fieldName) throws NoSuchFieldException {
        Class<?> currentClass = startClass;
        while (currentClass != null && currentClass != Object.class) {
            try {
                // Try getting the declared field from the current class
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                // Field not in this class, try superclass
                currentClass = currentClass.getSuperclass();
            }
        }
        // Field not found in the hierarchy
        throw new NoSuchFieldException("Field '" + fieldName + "' not found in " + startClass.getName() + " or its superclasses.");
    }

    // Helper to get any field value
    public static Object getFieldValue(Class<?> startClass, Object instance, String fieldName) {
        try {
            Field field = findField(startClass, fieldName);
            field.setAccessible(true); // Bypass private/protected
            return field.get(instance);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            System.err.println("Reflection Error getting field '" + fieldName + "': " + e.getMessage());
            return null;
        } catch (NullPointerException e) {
            System.err.println("Reflection Error getting field '" + fieldName + "': instance was null?");
            return null;
        }
    }

    // Convenience method for integers
    public static int getIntField(Class<?> startClass, Object instance, String fieldName, int defaultValue) {
        Object value = getFieldValue(startClass, instance, fieldName);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        // System.err.println("Reflection Warning: Field '" + fieldName + "' was not an Integer."); // Optional warning
        return defaultValue;
    }

    // Convenience method for Text
    public static Text getTextField(Class<?> startClass, Object instance, String fieldName) {
        Object value = getFieldValue(startClass, instance, fieldName);
        if (value instanceof Text) { return (Text) value; }
        return null;
    }

    // Convenience method for TextRenderer
    public static TextRenderer getTextRendererField(Class<?> startClass, Object instance, String fieldName) {
        Object value = getFieldValue(startClass, instance, fieldName);
        if (value instanceof TextRenderer) { return (TextRenderer) value; }
        return null;
    }

    // --- Method Invocation ---

    private static Method findMethod(Class<?> startClass, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Class<?> currentClass = startClass;
        while (currentClass != null && currentClass != Object.class) {
            try {
                // Try getting the declared method from the current class
                return currentClass.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                // Method not in this class, try superclass
                currentClass = currentClass.getSuperclass();
            }
        }
        // Method not found in the hierarchy
        throw new NoSuchMethodException("Method '" + methodName + "' with parameters " + Arrays.toString(parameterTypes) + " not found in " + startClass.getName() + " or its superclasses.");
    }

    // Helper to invoke any method, returns Object or null on failure
    public static Object invokeMethod(Class<?> startClass, Object instance, String methodName, Class<?>[] parameterTypes, Object[] args) {
        try {
            Method method = findMethod(startClass, methodName, parameterTypes);
            method.setAccessible(true); // Bypass private/protected
            return method.invoke(instance, args);
        } catch (Exception e) { // Catch broad exceptions from reflection
            System.err.println("Reflection Error invoking method '" + methodName + "': " + e.getMessage());
            e.printStackTrace(); // Print stack trace for method invocation errors
            return null;
        }
    }

    // Specific helper for addDrawableChild
    public static void invokeAddDrawableChild(Object screenInstance, Object childWidget) {
        // Look for addDrawableChild(Element) in Screen class hierarchy
        // Use Element.class as the parameter type signature
        Class<?>[] paramTypes = { Element.class };
        Object[] args = { childWidget };
        // We expect Screen class to define this method
        invokeMethod(net.minecraft.client.gui.screen.Screen.class, screenInstance, "addDrawableChild", paramTypes, args);
        // Ignore return value
    }
}