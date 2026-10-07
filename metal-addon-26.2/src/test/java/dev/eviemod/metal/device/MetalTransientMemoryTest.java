// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.device;

import com.mojang.blaze3d.buffers.GpuBuffer;
import dev.eviemod.metal.NativeLoader;
import dev.eviemod.metal.mtl.Mtl;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.MAC)
class MetalTransientMemoryTest {
    @Test void multipleUploadSourcesKeepUpstreamAlignmentAndPartialAllocation() {
        NativeLoader.load(); var device = new MetalDevice(0, (id, type) -> null);
        var first = MemoryUtil.memAlloc(3); var second = MemoryUtil.memAlloc(17);
        try (var memory = new MetalTransientMemory()) {
            first.put(0, (byte) 11).put(1, (byte) 12).put(2, (byte) 13);
            for (int i = 0; i < 17; i++) second.put(i, (byte) (21 + i));
            var full = memory.uploadGpu(List.of(first, second), 16, GpuBuffer.USAGE_COPY_SRC);
            assertEquals(48, full.length());
            try (var map = full.map(true, false)) {
                assertEquals(13, map.data().get(2)); assertEquals(21, map.data().get(16)); assertEquals(37, map.data().get(32));
            }
            memory.allocateGpu(524288 - 48 - 32, 16, GpuBuffer.USAGE_VERTEX);
            var partial = memory.uploadStaging(List.of(first, second), 16, GpuBuffer.USAGE_COPY_SRC, 16, 1);
            assertEquals(32, partial.length());
            try (var map = partial.map(true, false)) {
                assertEquals(13, map.data().get(2)); assertEquals(21, map.data().get(16)); assertEquals(36, map.data().get(31));
            }
            assertEquals(0, first.position()); assertEquals(0, second.position());
            Mtl.checkError();
        } finally { MemoryUtil.memFree(first); MemoryUtil.memFree(second); device.close(); }
    }

    @Test void partialUploadAndRotationKeepSubmittedCopiesIntact() {
        NativeLoader.load(); var device=new MetalDevice(0,(id,type)->null);
        var source=MemoryUtil.memAlloc(256);
        try(var memory=new MetalTransientMemory();var readback=new MetalBuffer(GpuBuffer.USAGE_COPY_DST|GpuBuffer.USAGE_MAP_READ,128)) {
            memory.allocateGpu(524288-128,16,GpuBuffer.USAGE_VERTEX,524288-128,4);
            for(int i=0;i<64;i++)source.putInt(i*4,0x234500+i);
            var partial=memory.uploadGpu(List.of(source),16,GpuBuffer.USAGE_COPY_SRC,64,4);
            assertEquals(128,partial.length());
            device.createCommandEncoder().copyToBuffer(partial,readback.slice());
            memory.rotate();
            for(int run=0;run<12;run++) {
                try(var allocation=memory.allocateGpuMapped(128,16,GpuBuffer.USAGE_VERTEX,128,4)) {MemoryUtil.memSet(allocation.data(),0xaa);}
                Mtl.submit();memory.rotate();
            }
            try(var mapped=readback.map(true,false)) {for(int i=0;i<32;i++)assertEquals(0x234500+i,mapped.data().getInt());}
            Mtl.checkError();
        }finally{MemoryUtil.memFree(source);device.close();}
    }
}
