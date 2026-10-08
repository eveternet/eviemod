package dev.eviemod.metal.device;

import dev.eviemod.metal.NativeLoader;
import dev.eviemod.metal.mtl.Mtl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.MAC)
class UploadRingTest {
    @Test void mixedUploadSizesShrinkAfterCompletion() {
        NativeLoader.load(); Mtl.init(0);
        try {
            UploadRing.reserve(4L << 20); // The old allocator's too-small head blocked reuse forever.
            assertTrue(Mtl.fenceWait(Mtl.fence(), 5000));
            for (int i = 0; i < 24; i++) {
                UploadRing.reserve(5L << 20);
                assertTrue(Mtl.fenceWait(Mtl.fence(), 5000));
                assertTrue(UploadRing.retainedBytes() <= 16L << 20, "Completed staging chunks accumulated");
            }
            UploadRing.trimCompleted();
            assertTrue(UploadRing.retainedBytes() <= 4L << 20);
        } finally { UploadRing.close(); Mtl.shutdown(); }
        assertEquals(0, UploadRing.retainedBytes());
    }

    @Test void pendingUploadsAreBoundedAndKeepTheirContents() {
        NativeLoader.load(); Mtl.init(0);
        long destination = Mtl.newBuffer(256);
        try {
            for (int i = 0; i < 40; i++) {
                var slice = UploadRing.reserve(4L << 20);
                MemoryUtil.memPutInt(slice.address(), i);
                Mtl.copyBuffer(slice.buffer(), slice.offset(), destination, i * 4L, 4);
                assertTrue(UploadRing.retainedBytes() <= 64L << 20, "Staging exceeded its submission budget");
            }
            assertTrue(Mtl.fenceWait(Mtl.fence(), 5000));
            for (int i = 0; i < 40; i++) assertEquals(i, MemoryUtil.memGetInt(Mtl.bufferContents(destination) + i * 4L));
            UploadRing.trimCompleted();
            assertTrue(UploadRing.retainedBytes() <= 4L << 20);
            Mtl.checkError();
        } finally { UploadRing.close(); Mtl.release(destination); Mtl.shutdown(); }
    }
}
