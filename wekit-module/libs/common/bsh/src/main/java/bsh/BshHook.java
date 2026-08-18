package bsh;

/**
 * Hook interface for intercepting BeanShell and JVM method/field/variable access
 * from within the BeanShell interpreter.
 * <p>
 * All methods are {@code default} — implementers only override what they need.
 * If a before-hook sets {@code param.isIntercepted = true}, the original
 * operation is skipped and {@code param.getReturnValue()} is used as the result.
 * The corresponding after-hook still fires (with {@code isIntercepted == true})
 * for cleanup. After-hooks also run after successful original operations, but
 * not when the original operation throws.
 */
public interface BshHook {

    // === BeanShell local methods (bare name resolution from script code) ===

    default void beforeLocalMethod(LocalMethodHookParam param) {}
    default void afterLocalMethod(LocalMethodHookParam param) {}

    // === JVM instance/static methods (obj.method() / Class.method()) ===

    default void beforeJavaMethod(JavaMethodHookParam param) {}
    default void afterJavaMethod(JavaMethodHookParam param) {}

    // === JVM fields (obj.field / Class.STATIC_FIELD, read & write) ===

    default void beforeJavaField(JavaFieldHookParam param) {}
    default void afterJavaField(JavaFieldHookParam param) {}

    // === BeanShell variables (bare name variable read & write) ===

    default void beforeVariable(VariableHookParam param) {}
    default void afterVariable(VariableHookParam param) {}
}
