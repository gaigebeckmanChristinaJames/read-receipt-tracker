package bsh;

/**
 * Passed to {@link BshHook#beforeJavaField} and {@link BshHook#afterJavaField}
 * when a script reads or writes {@code obj.field} / {@code Class.STATIC_FIELD}.
 * <p>
 * {@code accessType} is 0 for reads, 1 for writes.
 * On reads, {@code value} is unused and {@code returnValue} is the value being read;
 * on writes, {@code value} is the incoming value and {@code returnValue} is unused.
 */
public class JavaFieldHookParam {
    public static final int ACCESS_READ = 0;
    public static final int ACCESS_WRITE = 1;

    private final Class<?> clazz;
    private final String fieldName;
    private final Class<?> fieldType;
    private Object value;
    private Object returnValue;
    private final int accessType;
    private boolean isIntercepted;
    private final StackTraceElement[] callStack;

    public JavaFieldHookParam(
            Class<?> clazz, String fieldName, Class<?> fieldType,
            Object value, Object returnValue, int accessType,
            boolean isIntercepted, StackTraceElement[] callStack) {
        this.clazz = clazz;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.value = value;
        this.returnValue = returnValue;
        this.accessType = accessType;
        this.isIntercepted = isIntercepted;
        this.callStack = callStack;
    }

    // --- getters ---

    public Class<?> getClazz() { return clazz; }
    public String getFieldName() { return fieldName; }
    public Class<?> getFieldType() { return fieldType; }
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
