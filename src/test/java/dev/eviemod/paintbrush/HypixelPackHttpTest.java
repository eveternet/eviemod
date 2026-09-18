package dev.eviemod.paintbrush;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HypixelPackHttpTest {
    @TempDir Path root;
    @Test void downloaderBoundsBodiesRejectsErrorsAndDoesNotFollowRedirects() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        var downloads = new AtomicInteger();
        server.createContext("/pack", exchange -> {
            downloads.incrementAndGet(); byte[] data = new byte[128];
            exchange.sendResponseHeaders(200, data.length);
            try (var output = exchange.getResponseBody()) { output.write(data); }
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/pack"); exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/error", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.start();
        try {
            String base = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
            var fetch = HypixelPackStore.http(); Path target = root.resolve("download.tmp");
            fetch.get(URI.create(base + "/pack"), target, 128); assertEquals(128, Files.size(target));
            assertThrows(IOException.class, () -> fetch.get(URI.create(base + "/pack"), target, 127));
            assertTrue(Files.size(target) <= 127);
            assertThrows(IOException.class, () -> fetch.get(URI.create(base + "/redirect"), target, 128));
            assertEquals(2, downloads.get());
            assertThrows(IOException.class, () -> fetch.get(URI.create(base + "/error"), target, 128));
        } finally { server.stop(0); }
    }
}
