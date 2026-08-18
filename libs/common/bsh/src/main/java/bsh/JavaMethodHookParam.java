package bsh;

/**
 * Passed to {@link BshHook#beforeJavaMethod} and {@link BshHook#afterJavaMethod}
 * when a script calls {@code obj.method(args)} or {@code Class.staticMethod(args)}
 * that resolves to a JVM method.
 */
public class JavaMethodHookParam {
    private final Class<?> clazz;
    private final String methodName;
    private final Class<?>[] paramTypes;
    private final Object[] args;
    private final Class<?> returnType;
    private Object returnValue;
    private boolean isIntercepted;
    private final StackTraceElement[] callStack;

    public JavaMethodHookParam(
            Class<?> clazz, String methodName, Class<?>[] paramTypes,
            Object[] args, Class<?> returnType, Object returnValue,
            boolean isIntercepted, StackTraceElement[] callStack) {
        this.clazz = clazz;
        this.methodName = methodName;
        this.paramTypes = paramTypes;
        this.args = args;
        this.returnType = returnType;
        this.returnValue = returnValue;
        this.isIntercepted = isIntercepted;
        this.callStack = callStack;
    }

    // --- getters ---

    public Class<?> getClazz() { return clazz; }
    public String getMethodName() { return methodName; }
    public Class<?>[] getParamTypes() { return paramTypes; }
    public Object[] getArgs() { return args; }
    public Class<?> getReturnType() { return returnType; }
    public Object getReturnValue() { return returnValue; }
    public boolean isIntercepted() { return isIntercepted; }
    public StackTraceElement[] getCallStack() { return callStack; }

    // --- mutable ---

    public void setReturnValue(Object returnValue) {
        this.returnValue = returnValue;
    }

    public void setIntercepted(boolean isIntercepted) {
        this.isIntercepted = isIntercepted;
    }
}
