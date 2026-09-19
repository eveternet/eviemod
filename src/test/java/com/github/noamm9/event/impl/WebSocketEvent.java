package com.github.noamm9.event.impl;
import com.github.noamm9.event.Event;
public class WebSocketEvent extends Event {
    public static final class Payload extends WebSocketEvent {
        private final String message;
        public Payload(String message) { this.message = message; }
        public String getMessage() { return message; }
    }
}
