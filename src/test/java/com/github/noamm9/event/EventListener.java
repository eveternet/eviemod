package com.github.noamm9.event;
import com.github.noamm9.event.priority.EventPriority;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
/** Public ABI from the addendum, not a copy of Noamm implementation. */
public final class EventListener<T extends Event> {
    public static EventListener<?> last;
    public final Class<? extends Event> eventClass;
    public final EventPriority priority;
    public final boolean receiveCancelled;
    public final Function1<? super EventContext<T>, Unit> callback;
    public int registrations;
    public boolean active;
    public EventListener(Class<? extends Event> eventClass, EventPriority priority, boolean receiveCancelled,
                         Function1<? super EventContext<T>, Unit> callback) {
        this.eventClass = eventClass; this.priority = priority; this.receiveCancelled = receiveCancelled;
        this.callback = callback; last = this;
    }
    public void register() { registrations++; active = true; }
    public void unregister() { active = false; }
    public void post(T event) { if (active && event.getClass() == eventClass) callback.invoke(new EventContext<>(event)); }
}
