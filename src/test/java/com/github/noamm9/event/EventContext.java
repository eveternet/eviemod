package com.github.noamm9.event;
public final class EventContext<T extends Event> {
    private final T event;
    public EventContext(T event) { this.event = event; }
    public T getEvent() { return event; }
}
