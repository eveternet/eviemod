package dev.eviemod.features.scoresync;

import com.github.noamm9.NoammAddons;
import com.github.noamm9.event.Event;
import com.github.noamm9.event.EventListener;
import com.github.noamm9.event.impl.WebSocketEvent;
import com.github.noamm9.event.priority.EventPriority;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoammScoreHookTest {
    @Test void absentModNeverLoadsNoammClasses() throws Exception {
        var loader = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.github.noamm9")) fail("Absent Noamm must not be referenced");
                return super.loadClass(name, resolve);
            }
        };
        assertFalse(new NoammScoreHook().register(false, loader, raw -> fail(), () -> fail()));
    }
    @Test void notReadyNeverTouchesEventClasses() throws Exception {
        NoammAddons.isLoaded = false;
        var loader = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.github.noamm9.event")) fail("Not ready must not touch event bus");
                return super.loadClass(name, resolve);
            }
        };
        assertFalse(new NoammScoreHook().register(true, loader, raw -> fail(), () -> fail()));
    }
    @Test @SuppressWarnings("unchecked") void exactPayloadSubscriptionPersistsUntilUnregistered() throws Exception {
        NoammAddons.isLoaded = true;
        var frames = new ArrayList<String>(); var hook = new NoammScoreHook();
        assertTrue(hook.register(true, getClass().getClassLoader(), frames::add, () -> fail()));
        var listener = (EventListener<Event>) EventListener.last;
        assertEquals(WebSocketEvent.Payload.class, listener.eventClass);
        assertEquals(EventPriority.NORMAL, listener.priority); assertFalse(listener.receiveCancelled);
        listener.post(new WebSocketEvent()); assertTrue(frames.isEmpty());
        listener.post(new WebSocketEvent.Payload("first")); listener.post(new WebSocketEvent.Payload("after reconnect"));
        assertTrue(hook.register(true, getClass().getClassLoader(), frames::add, () -> fail()));
        assertEquals(1, listener.registrations); assertEquals(java.util.List.of("first", "after reconnect"), frames);
        hook.close(); assertFalse(listener.active); listener.post(new WebSocketEvent.Payload("ignored"));
        assertEquals(2, frames.size());
    }
    @Test void incompatibleApiFailsClosed() {
        var loader = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.github.noamm9")) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        assertThrows(ReflectiveOperationException.class, () -> new NoammScoreHook().register(true, loader, raw -> fail(), () -> fail()));
    }
}
