package org.tradeplanner.ui;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Reflection that the script classloader will not block. Direct use of
 * {@code java.lang.reflect.Method} / {@code Field} throws
 * {@code SecurityException: File access and reflection are not allowed to scripts}.
 * Same approach as MagicLib: load those classes from the bootstrap loader and
 * call them through {@link MethodHandle}.
 */
final class ScriptSafeReflect {

    private static final MethodHandle CLASS_GET_METHOD;
    private static final MethodHandle CLASS_GET_DECLARED_METHOD;
    private static final MethodHandle CLASS_GET_FIELD;
    private static final MethodHandle CLASS_GET_DECLARED_FIELD;
    private static final MethodHandle CLASS_GET_SUPERCLASS;
    private static final MethodHandle METHOD_INVOKE;
    private static final MethodHandle METHOD_SET_ACCESSIBLE;
    private static final MethodHandle FIELD_GET;
    private static final MethodHandle FIELD_SET_ACCESSIBLE;
    private static final boolean READY;

    static {
        MethodHandle classGetMethod = null;
        MethodHandle classGetDeclaredMethod = null;
        MethodHandle classGetField = null;
        MethodHandle classGetDeclaredField = null;
        MethodHandle classGetSuperclass = null;
        MethodHandle methodInvoke = null;
        MethodHandle methodSetAccessible = null;
        MethodHandle fieldGet = null;
        MethodHandle fieldSetAccessible = null;
        boolean ready = false;
        try {
            ClassLoader boot = Class.class.getClassLoader();
            Class<?> methodClass = Class.forName("java.lang.reflect.Method", false, boot);
            Class<?> fieldClass = Class.forName("java.lang.reflect.Field", false, boot);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            classGetMethod = lookup.findVirtual(Class.class, "getMethod",
                    MethodType.methodType(methodClass, String.class, Class[].class));
            classGetDeclaredMethod = lookup.findVirtual(Class.class, "getDeclaredMethod",
                    MethodType.methodType(methodClass, String.class, Class[].class));
            methodSetAccessible = lookup.findVirtual(methodClass, "setAccessible",
                    MethodType.methodType(void.class, boolean.class));
            methodInvoke = lookup.findVirtual(methodClass, "invoke",
                    MethodType.methodType(Object.class, Object.class, Object[].class));
            classGetField = lookup.findVirtual(Class.class, "getField",
                    MethodType.methodType(fieldClass, String.class));
            classGetDeclaredField = lookup.findVirtual(Class.class, "getDeclaredField",
                    MethodType.methodType(fieldClass, String.class));
            fieldSetAccessible = lookup.findVirtual(fieldClass, "setAccessible",
                    MethodType.methodType(void.class, boolean.class));
            fieldGet = lookup.findVirtual(fieldClass, "get",
                    MethodType.methodType(Object.class, Object.class));
            classGetSuperclass = lookup.findVirtual(Class.class, "getSuperclass",
                    MethodType.methodType(Class.class));
            ready = true;
        } catch (Throwable ignored) {
            // HUD will stay hidden; CampaignUiAccess logs this.
        }
        CLASS_GET_METHOD = classGetMethod;
        CLASS_GET_DECLARED_METHOD = classGetDeclaredMethod;
        CLASS_GET_FIELD = classGetField;
        CLASS_GET_DECLARED_FIELD = classGetDeclaredField;
        CLASS_GET_SUPERCLASS = classGetSuperclass;
        METHOD_INVOKE = methodInvoke;
        METHOD_SET_ACCESSIBLE = methodSetAccessible;
        FIELD_GET = fieldGet;
        FIELD_SET_ACCESSIBLE = fieldSetAccessible;
        READY = ready;
    }

    private ScriptSafeReflect() {
    }

    static boolean isReady() {
        return READY;
    }

    static Object invoke(Object target, String name) {
        if (!READY || target == null || name == null) {
            return null;
        }
        Object method = findMethod(target.getClass(), name);
        if (method == null) {
            return null;
        }
        try {
            return METHOD_INVOKE.invoke(method, target, new Object[0]);
        } catch (Throwable t) {
            return null;
        }
    }

    static Object invokeStatic(Class<?> type, String name) {
        if (!READY || type == null || name == null) {
            return null;
        }
        Object method = findMethod(type, name);
        if (method == null) {
            return null;
        }
        try {
            return METHOD_INVOKE.invoke(method, null, new Object[0]);
        } catch (Throwable t) {
            return null;
        }
    }

    static Object field(Object target, String name) {
        if (!READY || target == null || name == null) {
            return null;
        }
        Object field = findField(target.getClass(), name);
        if (field == null) {
            return null;
        }
        try {
            return FIELD_GET.invoke(field, target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object findMethod(Class<?> type, String name) {
        Class<?>[] none = new Class[0];
        Class<?> walk = type;
        while (walk != null) {
            try {
                Object method = CLASS_GET_DECLARED_METHOD.invoke(walk, name, none);
                METHOD_SET_ACCESSIBLE.invoke(method, true);
                return method;
            } catch (Throwable ignored) {
            }
            try {
                walk = (Class<?>) CLASS_GET_SUPERCLASS.invoke(walk);
            } catch (Throwable t) {
                break;
            }
        }
        try {
            return CLASS_GET_METHOD.invoke(type, name, none);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object findField(Class<?> type, String name) {
        Class<?> walk = type;
        while (walk != null) {
            try {
                Object field = CLASS_GET_DECLARED_FIELD.invoke(walk, name);
                FIELD_SET_ACCESSIBLE.invoke(field, true);
                return field;
            } catch (Throwable ignored) {
            }
            try {
                walk = (Class<?>) CLASS_GET_SUPERCLASS.invoke(walk);
            } catch (Throwable t) {
                break;
            }
        }
        try {
            Object field = CLASS_GET_FIELD.invoke(type, name);
            FIELD_SET_ACCESSIBLE.invoke(field, true);
            return field;
        } catch (Throwable t) {
            return null;
        }
    }
}
