package bsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BshHookContractTest {
    private final List<BshHook> installedHooks = new ArrayList<>();

    @AfterEach
    void removeHooks() {
        installedHooks.forEach(Interpreter.bshHookManager::removeHook);
    }

    @Test
    void interceptedLocalMethodRunsBeforeAndAfterOnceAndCarriesContext() throws Exception {
        Interpreter interpreter = new Interpreter();
        AtomicInteger bodyCalls = new AtomicInteger();
        AtomicInteger beforeCalls = new AtomicInteger();
        AtomicInteger afterCalls = new AtomicInteger();

        BshHook hook = install(new BshHook() {
            @Override
            public void beforeLocalMethod(LocalMethodHookParam param) {
                if (!param.getMethodName().equals("target"))
                    return;
                beforeCalls.incrementAndGet();
                assertSame(interpreter.getNameSpace(), param.getInvocationNameSpace());
                param.setReturnValue(new Primitive(40));
                param.setIntercepted(true);
            }

            @Override
            public void afterLocalMethod(LocalMethodHookParam param) {
                if (!param.getMethodName().equals("target"))
                    return;
                afterCalls.incrementAndGet();
                assertTrue(param.isIntercepted());
                param.setReturnValue(new Primitive(42));
            }
        });

        interpreter.set("bodyCalls", bodyCalls);
        Object result = interpreter.eval(
                "int target() { bodyCalls.incrementAndGet(); return 1; } target();");

        assertEquals(42, Primitive.unwrap(result));
        assertEquals(0, bodyCalls.get());
        assertEquals(1, beforeCalls.get());
        assertEquals(1, afterCalls.get());
        assertNotNull(hook);
    }

    @Test
    void successfulLocalMethodRunsBeforeAndAfterOnce() throws Exception {
        Interpreter interpreter = new Interpreter();
        AtomicInteger beforeCalls = new AtomicInteger();
        AtomicInteger afterCalls = new AtomicInteger();

        install(new BshHook() {
            @Override
            public void beforeLocalMethod(LocalMethodHookParam param) {
                if (param.getMethodName().equals("target"))
                    beforeCalls.incrementAndGet();
            }

            @Override
            public void afterLocalMethod(LocalMethodHookParam param) {
                if (param.getMethodName().equals("target")) {
                    afterCalls.incrementAndGet();
                    param.setReturnValue(new Primitive(8));
                }
            }
        });

        assertEquals(8, Primitive.unwrap(interpreter.eval("int target() { return 7; } target();")));
        assertEquals(1, beforeCalls.get());
        assertEquals(1, afterCalls.get());
    }

    @Test
    void registeringSameHookTwiceFiresItOnce() throws Exception {
        AtomicInteger beforeCalls = new AtomicInteger();
        BshHook hook = install(new BshHook() {
            @Override
            public void beforeLocalMethod(LocalMethodHookParam param) {
                if (param.getMethodName().equals("target"))
                    beforeCalls.incrementAndGet();
            }
        });
        Interpreter.bshHookManager.addHook(hook);

        new Interpreter().eval("int target() { return 1; } target();");

        assertEquals(1, beforeCalls.get());
    }

    @Test
    void variableReadsAndWritesRunSymmetricHooks() throws Exception {
        Interpreter interpreter = new Interpreter();
        AtomicInteger beforeReads = new AtomicInteger();
        AtomicInteger afterReads = new AtomicInteger();
        AtomicInteger beforeWrites = new AtomicInteger();
        AtomicInteger afterWrites = new AtomicInteger();

        install(new BshHook() {
            @Override
            public void beforeVariable(VariableHookParam param) {
                if (!param.getName().equals("tracked"))
                    return;
                if (param.getAccessType() == VariableHookParam.ACCESS_READ)
                    beforeReads.incrementAndGet();
                else
                    beforeWrites.incrementAndGet();
            }

            @Override
            public void afterVariable(VariableHookParam param) {
                if (!param.getName().equals("tracked"))
                    return;
                if (param.getAccessType() == VariableHookParam.ACCESS_READ) {
                    afterReads.incrementAndGet();
                    param.setReturnValue(new Primitive(9));
                } else {
                    afterWrites.incrementAndGet();
                }
            }
        });

        interpreter.set("tracked", 1);
        beforeReads.set(0);
        afterReads.set(0);
        beforeWrites.set(0);
        afterWrites.set(0);
        interpreter.eval("tracked = 2;");
        Object result = interpreter.eval("tracked;");

        assertEquals(9, Primitive.unwrap(result));
        assertTrue(beforeReads.get() > 0);
        assertEquals(beforeReads.get(), afterReads.get());
        assertEquals(1, beforeWrites.get());
        assertEquals(1, afterWrites.get());
    }

    @Test
    void interceptedVariableReadAndWriteStillRunAfterHooks() throws Exception {
        Interpreter interpreter = new Interpreter();
        AtomicInteger afterReads = new AtomicInteger();
        AtomicInteger afterWrites = new AtomicInteger();

        interpreter.set("tracked", 1);
        install(new BshHook() {
            @Override
            public void beforeVariable(VariableHookParam param) {
                if (!param.getName().equals("tracked"))
                    return;
                if (param.getAccessType() == VariableHookParam.ACCESS_READ) {
                    param.setReturnValue(new Primitive(4));
                    param.setIntercepted(true);
                } else {
                    param.setIntercepted(true);
                }
            }

            @Override
            public void afterVariable(VariableHookParam param) {
                if (!param.getName().equals("tracked"))
                    return;
                if (param.getAccessType() == VariableHookParam.ACCESS_READ) {
                    afterReads.incrementAndGet();
                    param.setReturnValue(new Primitive(5));
                } else {
                    afterWrites.incrementAndGet();
                }
            }
        });

        interpreter.eval("tracked = 2;");
        Object result = interpreter.eval("tracked;");

        assertEquals(5, Primitive.unwrap(result));
        assertTrue(afterReads.get() > 0);
        assertEquals(1, afterWrites.get());
    }

    @Test
    void javaFieldReadsAndWritesRunSymmetricHooks() throws Exception {
        Interpreter interpreter = new Interpreter();
        FieldFixture fixture = new FieldFixture();
        AtomicInteger beforeReads = new AtomicInteger();
        AtomicInteger afterReads = new AtomicInteger();
        AtomicInteger beforeWrites = new AtomicInteger();
        AtomicInteger afterWrites = new AtomicInteger();

        install(fieldHook(beforeReads, afterReads, beforeWrites, afterWrites, false));
        interpreter.set("fixture", fixture);
        interpreter.eval("import bsh.BshHookContractTest.FieldFixture;");
        interpreter.eval("fixture.value = 2;");
        Object instanceResult = interpreter.eval("fixture.value;");
        interpreter.eval("FieldFixture.staticValue = 3;");
        Object staticResult = interpreter.eval("FieldFixture.staticValue;");

        assertEquals(12, Primitive.unwrap(instanceResult));
        assertEquals(13, Primitive.unwrap(staticResult));
        assertEquals(beforeReads.get(), afterReads.get());
        assertEquals(2, beforeWrites.get());
        assertEquals(2, afterWrites.get());
    }

    @Test
    void interceptedJavaFieldReadAndWriteStillRunAfterHooks() throws Exception {
        Interpreter interpreter = new Interpreter();
        FieldFixture fixture = new FieldFixture();
        AtomicInteger beforeReads = new AtomicInteger();
        AtomicInteger afterReads = new AtomicInteger();
        AtomicInteger beforeWrites = new AtomicInteger();
        AtomicInteger afterWrites = new AtomicInteger();

        install(fieldHook(beforeReads, afterReads, beforeWrites, afterWrites, true));
        interpreter.set("fixture", fixture);
        interpreter.eval("fixture.value = 2;");
        Object result = interpreter.eval("fixture.value;");

        assertEquals(12, Primitive.unwrap(result));
        assertEquals(1, fixture.value);
        assertTrue(afterReads.get() > 0);
        assertEquals(1, afterWrites.get());
    }

    @Test
    void interceptedStaticJavaFieldReadReturnsNullDirectly() throws Exception {
        Interpreter interpreter = new Interpreter();
        AtomicInteger afterReads = new AtomicInteger();

        install(new BshHook() {
            @Override
            public void beforeJavaField(JavaFieldHookParam param) {
                if (param.getFieldName().equals("staticValue")
                        && param.getAccessType() == JavaFieldHookParam.ACCESS_READ) {
                    param.setReturnValue(Primitive.NULL);
                    param.setIntercepted(true);
                }
            }

            @Override
            public void afterJavaField(JavaFieldHookParam param) {
                if (param.getFieldName().equals("staticValue")
                        && param.getAccessType() == JavaFieldHookParam.ACCESS_READ)
                    afterReads.incrementAndGet();
            }
        });

        interpreter.eval("import bsh.BshHookContractTest.FieldFixture;");
        assertEquals(null, interpreter.eval("FieldFixture.staticValue;"));
        assertEquals(1, afterReads.get());
    }
    private BshHook fieldHook(
            AtomicInteger beforeReads,
            AtomicInteger afterReads,
            AtomicInteger beforeWrites,
            AtomicInteger afterWrites,
            boolean intercept) {
        return new BshHook() {
            @Override
            public void beforeJavaField(JavaFieldHookParam param) {
                if (!param.getFieldName().equals("value") && !param.getFieldName().equals("staticValue"))
                    return;
                if (param.getAccessType() == JavaFieldHookParam.ACCESS_READ) {
                    beforeReads.incrementAndGet();
                    if (intercept) {
                        param.setReturnValue(new Primitive(2));
                        param.setIntercepted(true);
                    }
                } else {
                    beforeWrites.incrementAndGet();
                    if (intercept)
                        param.setIntercepted(true);
                }
            }

            @Override
            public void afterJavaField(JavaFieldHookParam param) {
                if (!param.getFieldName().equals("value") && !param.getFieldName().equals("staticValue"))
                    return;
                if (param.getAccessType() == JavaFieldHookParam.ACCESS_READ) {
                    afterReads.incrementAndGet();
                    param.setReturnValue(new Primitive(
                            ((Number) Primitive.unwrap(param.getReturnValue())).intValue() + 10));
                } else {
                    afterWrites.incrementAndGet();
                }
            }
        };
    }

    private BshHook install(BshHook hook) {
        installedHooks.add(hook);
        Interpreter.bshHookManager.addHook(hook);
        return hook;
    }

    public static class FieldFixture {
        public int value = 1;
        public static int staticValue = 1;
    }
}
