package dev.eviemod.features.scoresync;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/** The sole Noamm ABI boundary. Only public members from the supplied JVM contract are used. */
final class NoammScoreHook {
    private Object listener;
    private Method unregister;

    // Called only at CLIENT_STARTED, after the Fabric mod-presence check.
    boolean register(boolean present, ClassLoader loader, Consumer<String> frames, Runnable failed) throws ReflectiveOperationException {
        if (!present) return false;
        if (listener != null) return true;
        Class<?> noamm = Class.forName("com.github.noamm9.NoammAddons", false, loader);
        if (!noamm.getField("isLoaded").getBoolean(null)) return false;
        Class<?> payload = Class.forName("com.github.noamm9.event.impl.WebSocketEvent$Payload", false, loader);
        Class<?> context = Class.forName("com.github.noamm9.event.EventContext", false, loader);
        Class<?> priority = Class.forName("com.github.noamm9.event.priority.EventPriority", false, loader);
        Class<?> listenerClass = Class.forName("com.github.noamm9.event.EventListener", false, loader);
        Method getEvent = context.getMethod("getEvent");
        Method getMessage = payload.getMethod("getMessage");
        Method register = listenerClass.getMethod("register");
        unregister = listenerClass.getMethod("unregister");
        Function1<Object, Unit> callback = eventContext -> {
            try { frames.accept((String) getMessage.invoke(getEvent.invoke(eventContext))); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError e) { failed.run(); }
            return Unit.INSTANCE;
        };
        listener = listenerClass.getConstructor(Class.class, priority, boolean.class, Function1.class)
            .newInstance(payload, priority.getField("NORMAL").get(null), false, callback);
        register.invoke(listener);
        return true;
    }

    void close() {
        if (listener == null) return;
        try { unregister.invoke(listener); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { /* Inert callback remains safe. */ }
        listener = null;
    }
}
