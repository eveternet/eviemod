package dev.eviemod.metal;

import dev.eviemod.metal.mtl.Mtl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.MAC)
class NativeSmokeTest {
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
            Mtl.beginPass(texture, true, 1, 0, 0, 1, 0, false, 1);
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
