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
