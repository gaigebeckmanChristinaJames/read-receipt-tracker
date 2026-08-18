package bsh;

/**
 * Passed to {@link BshHook#beforeVariable} and {@link BshHook#afterVariable}
 * when a script reads or writes a BeanShell variable.
 * <p>
 * {@code accessType} is 0 for reads, 1 for writes.
 * On reads, {@code value} is unused and {@code returnValue} holds the value being read;
 * on writes, {@code value} is the incoming value and {@code returnValue} is unused.
 */
public class VariableHookParam {
    public static final int ACCESS_READ = 0;
    public static final int ACCESS_WRITE = 1;

    private final String name;
    private final Class<?> type;
    private Object value;
    private Object returnValue;
    private final int accessType;
    private boolean isIntercepted;
    private final StackTraceElement[] callStack;

    public VariableHookParam(
            String name, Class<?> type, Object value, Object returnValue,
            int accessType, boolean isIntercepted,
            StackTraceElement[] callStack) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.returnValue = returnValue;
        this.accessType = accessType;
        this.isIntercepted = isIntercepted;
        this.callStack = callStack;
    }

    // --- getters ---

    public String getName() { return name; }
    public Class<?> getType() { return type; }
    public Object getValue() { return value; }
    public Object getReturnValue() { return returnValue; }
    public int getAccessType() { return accessType; }
    public boolean isIntercepted() { return isIntercepted; }
    public StackTraceElement[] getCallStack() { return callStack; }

    // --- mutable ---

    public void setValue(Object value) { this.value = value; }
    public void setReturnValue(Object returnValue) { this.returnValue = returnValue; }
    public void setIntercepted(boolean isIntercepted) { this.isIntercepted = isIntercepted; }
}
