package bsh;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BshHookSecurityTest {

    @Test
    void hostCodeCanStillUseHookManager() {
        BshHook hook = new BshHook() {};

        assertDoesNotThrow(() -> {
            Interpreter.bshHookManager.addHook(hook);
            Interpreter.bshHookManager.removeHook(hook);
        });
    }

    @Test
    void scriptCannotReadGlobalHookManager() {
        EvalError error = assertThrows(EvalError.class,
                () -> new Interpreter().eval("bsh.Interpreter.bshHookManager.clearHooks();"));

        assertTrue(error.getMessage().toLowerCase().contains("security")
                || error.getCause() != null);
    }

    @Test
    void scriptCannotCallMethodsOnExposedHookManager() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.set("manager", Interpreter.bshHookManager);

        EvalError error = assertThrows(EvalError.class,
                () -> interpreter.eval("manager.clearHooks();"));

        assertTrue(error.getMessage().toLowerCase().contains("security")
                || error.getCause() != null);
    }
}
