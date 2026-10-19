package dev.eviemod.metal;

import dev.eviemod.metal.mtl.Mtl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.MAC)
class NativeSmokeTest {
    @Test void completedUploadsDoNotRetainEveryCommandBuffer() {
        NativeLoader.load();
        Mtl.init(0);
        try {
            for (int i = 0; i < 8; i++) copyAndRelease();
            long baseline = Mtl.allocatedBytes();
            for (int i = 0; i < 64; i++) copyAndRelease();
            long growth = Mtl.allocatedBytes() - baseline;
            System.out.println("Completed-upload allocation growth: " + growth + " bytes");
            assertTrue(growth < 16L * 1024 * 1024, "Completed uploads retained " + growth + " bytes");
        } finally { Mtl.shutdown(); }
    }

    @Test void completedRenderPassesReleaseAttachments() {
        NativeLoader.load(); Mtl.init(0);
        try {
            for (int i = 0; i < 8; i++) clearAndRelease();
            long baseline = Mtl.allocatedBytes();
            for (int i = 0; i < 64; i++) clearAndRelease();
            long growth = Mtl.allocatedBytes() - baseline;
            System.out.println("Completed-render allocation growth: " + growth + " bytes");
            assertTrue(growth < 16L * 1024 * 1024, "Completed passes retained " + growth + " bytes");
        } finally { Mtl.shutdown(); }
    }

    private static void clearAndRelease() {
        long texture = Mtl.newTexture(0, 1024, 1024, 1, false, true, "memory-regression");
        try {
            Mtl.beginPass(texture, true, 1, 0, 0, 1, 0, false, 1);
            Mtl.endPass();
            assertTrue(Mtl.fenceWait(Mtl.fence(), 5000));
        } finally { Mtl.release(texture); }
    }

    private static void copyAndRelease() {
        long source = Mtl.newBuffer(1024 * 1024), destination = Mtl.newBuffer(1024 * 1024);
        try {
            Mtl.copyBuffer(source, 0, destination, 0, 1024 * 1024);
            assertTrue(Mtl.fenceWait(Mtl.fence(), 5000));
        } finally { Mtl.release(source); Mtl.release(destination); }
    }

    @Test void repeatedContextsResetFencesAndProfiling() {
        NativeLoader.load();
        for (int cycle = 0; cycle < 3; cycle++) {
            Mtl.init(0);
            long texture = 0;
            try {
                assertEquals(1, Mtl.fence());
                assertEquals("", Mtl.takeGpuProfile());
                Mtl.setGpuProfiling(true);
                Mtl.setPassLabel("lifecycle-" + cycle);
                texture = Mtl.newTexture(0, 1, 1, 1, false, true, "lifecycle");
                Mtl.clearRegion(texture, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 1);
                assertTrue(Mtl.fenceWait(Mtl.fence(), 5000));
                Mtl.allocatedBytes(); // Wait for completion handlers before checking teardown.
                Mtl.checkError();
            } finally { Mtl.release(texture); Mtl.shutdown(); }
            assertEquals("", Mtl.takeGpuProfile());
        }
    }

    @Test void clearUploadReadbackFenceAndCleanup() {
        NativeLoader.load();
        Mtl.init(0);
        long texture = 0, upload = 0, readback = 0;
        try {
            assertNotEquals("none", Mtl.deviceName());
            assertTrue(Mtl.maxTextureSize() >= 4096);
            texture = Mtl.newTexture(0, 1, 1, 1, false, true, "smoke");
            upload = Mtl.newBuffer(256);
            readback = Mtl.newBuffer(256);
            assertNotEquals(0, texture);
            assertNotEquals(0, upload);
            assertNotEquals(0, readback);
            MemoryUtil.memPutInt(Mtl.bufferContents(readback), 12345);
            Mtl.copyBuffer(upload, 256, readback, 256, 0);
            assertEquals(12345, MemoryUtil.memGetInt(Mtl.bufferContents(readback)));
            Mtl.beginPass(texture, true, 1, 0, 0, 1, 0, false, 1);
            long generation = Mtl.renderEncoderGeneration();
            Mtl.copyBuffer(upload, 0, readback, 0, 0);
            assertEquals(generation, Mtl.renderEncoderGeneration(), "Empty copy interrupted the render pass");
            Mtl.endPass();
            Mtl.copyTextureToBuffer(texture, 0, 0, 0, 1, 1, readback, 0, 256);
            assertTrue(Mtl.fenceWait(Mtl.fence(), 5000));
            long address = Mtl.bufferContents(readback);
            assertEquals(255, Byte.toUnsignedInt(MemoryUtil.memGetByte(address)));
            assertEquals(0, MemoryUtil.memGetByte(address + 1));
            assertEquals(255, Byte.toUnsignedInt(MemoryUtil.memGetByte(address + 3)));
            long source = Mtl.bufferContents(upload);
            for (int i = 0; i < 4; i++) MemoryUtil.memPutByte(source + i, (byte) (11 + i));
            Mtl.copyBufferToTexture(upload, 0, 256, texture, 0, 0, 0, 0, 1, 1);
            Mtl.copyTextureToBuffer(texture, 0, 0, 0, 1, 1, readback, 0, 256);
            assertTrue(Mtl.fenceWait(Mtl.fence(), 5000));
            for (int i = 0; i < 4; i++) assertEquals(11 + i, MemoryUtil.memGetByte(address + i));
            Mtl.checkError();
        } finally {
            Mtl.release(texture); Mtl.release(upload); Mtl.release(readback);
            Mtl.shutdown();
        }
    }
}
