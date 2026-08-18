package bsh;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe hook registry.  Hooks are rarely added/removed and frequently
 * fired, so the backing list uses {@link CopyOnWriteArrayList}.
 * <p>
 * Every fire method catches and logs exceptions from individual hooks so that
 * one misbehaving hook does not break the interpreter.
 */
public class BshHookManager {

    private final CopyOnWriteArrayList<BshHook> hooks = new CopyOnWriteArrayList<>();

    public void addHook(BshHook hook) { hooks.addIfAbsent(hook); }
    public void removeHook(BshHook hook) { hooks.remove(hook); }
    public void clearHooks() { hooks.clear(); }

    // --- before / after local method ---

    public void fireBeforeLocalMethod(LocalMethodHookParam p) {
        for (BshHook h : hooks) {
            try { h.beforeLocalMethod(p); }
            catch (Exception e) { logHookError("beforeLocalMethod", e); }
        }
    }

    public void fireAfterLocalMethod(LocalMethodHookParam p) {
        for (BshHook h : hooks) {
            try { h.afterLocalMethod(p); }
            catch (Exception e) { logHookError("afterLocalMethod", e); }
        }
    }

    // --- before / after java method ---

    public void fireBeforeJavaMethod(JavaMethodHookParam p) {
        for (BshHook h : hooks) {
            try { h.beforeJavaMethod(p); }
            catch (Exception e) { logHookError("beforeJavaMethod", e); }
        }
    }

    public void fireAfterJavaMethod(JavaMethodHookParam p) {
        for (BshHook h : hooks) {
            try { h.afterJavaMethod(p); }
            catch (Exception e) { logHookError("afterJavaMethod", e); }
        }
    }

    // --- before / after java field ---

    public void fireBeforeJavaField(JavaFieldHookParam p) {
        for (BshHook h : hooks) {
            try { h.beforeJavaField(p); }
            catch (Exception e) { logHookError("beforeJavaField", e); }
        }
    }

    public void fireAfterJavaField(JavaFieldHookParam p) {
        for (BshHook h : hooks) {
            try { h.afterJavaField(p); }
            catch (Exception e) { logHookError("afterJavaField", e); }
        }
    }

    // --- before / after variable ---

    public void fireBeforeVariable(VariableHookParam p) {
        for (BshHook h : hooks) {
            try { h.beforeVariable(p); }
            catch (Exception e) { logHookError("beforeVariable", e); }
        }
    }

    public void fireAfterVariable(VariableHookParam p) {
        for (BshHook h : hooks) {
            try { h.afterVariable(p); }
            catch (Exception e) { logHookError("afterVariable", e); }
        }
    }

    private static void logHookError(String hookName, Exception e) {
        System.err.println("BshHookManager: error in " + hookName + ": " + e.getMessage());
    }
}
