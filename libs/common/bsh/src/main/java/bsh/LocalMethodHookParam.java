package bsh;

/**
 * Passed to {@link BshHook#beforeLocalMethod} and {@link BshHook#afterLocalMethod}
 * when a script calls a bare {@code methodName(args)} that resolves to a BshMethod
 * in the local namespace.
 */
public class LocalMethodHookParam {
    private final Interpreter interpreter;
    private final NameSpace invocationNameSpace;
    private final String methodName;
    private final Class<?>[] paramTypes;
    private final Object[] args;
    private final Class<?> returnType;
    private Object returnValue;
    private boolean isIntercepted;
    private final StackTraceElement[] callStack;

    public LocalMethodHookParam(
            Interpreter interpreter, NameSpace invocationNameSpace,
            String methodName, Class<?>[] paramTypes, Object[] args,
            Class<?> returnType, Object returnValue, boolean isIntercepted,
            StackTraceElement[] callStack) {
        this.interpreter = interpreter;
        this.invocationNameSpace = invocationNameSpace;
        this.methodName = methodName;
        this.paramTypes = paramTypes;
        this.args = args;
        this.returnType = returnType;
        this.returnValue = returnValue;
        this.isIntercepted = isIntercepted;
        this.callStack = callStack;
    }

    public LocalMethodHookParam(
            String methodName, Class<?>[] paramTypes, Object[] args,
            Class<?> returnType, Object returnValue, boolean isIntercepted,
            StackTraceElement[] callStack) {
        this(null, null, methodName, paramTypes, args, returnType,
                returnValue, isIntercepted, callStack);
    }


    public Interpreter getInterpreter() { return interpreter; }
    public NameSpace getInvocationNameSpace() { return invocationNameSpace; }
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
