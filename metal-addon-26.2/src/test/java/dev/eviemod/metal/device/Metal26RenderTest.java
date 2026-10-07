// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.device;

import com.mojang.blaze3d.*;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.*;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.*;
import dev.eviemod.metal.NativeLoader;
import dev.eviemod.metal.mtl.Mtl;
import java.util.Optional;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.lwjgl.system.MemoryUtil;
import net.minecraft.resources.Identifier;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.MAC)
class Metal26RenderTest {
    @Test void slicedBindingsIndexedArgumentsBlendDepthAndArea() {
        renderSlicedBindings(false);
    }

    @Test void timestampInterruptionRestoresSlicedBindingsBlendDepthAndArea() {
        renderSlicedBindings(true);
    }

    private void renderSlicedBindings(boolean timestamps) {
        NativeLoader.load();
        var device = new MetalDevice(0, (id, stage) -> stage == ShaderType.VERTEX
                ? "#version 330\nin vec3 Position;in vec4 Color;out vec4 tint;void main(){gl_Position=vec4(Position,1);tint=Color;}"
                : "#version 330\nin vec4 tint;layout(std140)uniform Tint{vec4 multiplier;};out vec4 color;void main(){color=tint*multiplier;}");
        var encoder = device.createCommandEncoder();
        try {
        if (timestamps) org.junit.jupiter.api.Assumptions.assumeTrue(Mtl.supportsTimestampSampling(),
                "Active Metal device has no stage-boundary timestamp counters; pixel coverage runs separately");
        try (var vb = new MetalBuffer(GpuBuffer.USAGE_VERTEX, 256);
             var colors = new MetalBuffer(GpuBuffer.USAGE_VERTEX, 64);
             var ib = new MetalBuffer(GpuBuffer.USAGE_INDEX, 64);
             var uniform = new MetalBuffer(GpuBuffer.USAGE_UNIFORM, 512);
             var texture = (MetalTexture) device.createTexture("pixel", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC, GpuFormat.RGBA8_UNORM, 4, 4, 1, 1);
             var depth = device.createTexture("depth", GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.D32_FLOAT, 4, 4, 1, 1);
             var view = device.createTextureView(texture); var depthView = device.createTextureView(depth);
             var readback = new MetalBuffer(GpuBuffer.USAGE_COPY_DST, 1024);
             var queries = timestamps ? device.createTimestampQueryPool(2) : null) {
            // Sliced vertex offset, then baseVertex=3 skips three degenerate vertices.
            float[] triangle = {-1,-1,.25f, 3,-1,.25f, -1,3,.25f, -1,-1,-.75f, 3,-1,-.75f, -1,3,-.75f};
            MemoryUtil.memSet(vb.address(), 0, vb.size());
            for (int i=0; i<triangle.length; i++) MemoryUtil.memPutFloat(vb.address()+16+36+i*4L, triangle[i]);
            float[] rgba = {0,0,0,1, 1,0,0,.5f, 0,1,0,1};
            for (int i=0;i<rgba.length;i++) MemoryUtil.memPutFloat(colors.address()+i*4L,rgba[i]);
            int[] indices = {0,0,0,0,1,2};
            for (int i=0;i<indices.length;i++) MemoryUtil.memPutInt(ib.address()+i*4L,indices[i]);
            for (int i=0;i<4;i++) MemoryUtil.memPutFloat(uniform.address()+256+i*4L,1);
            var layout = BindGroupLayout.builder().withUniform("Tint", UniformType.UNIFORM_BUFFER).build();
            var pipeline = RenderPipeline.builder().withLocation(Identifier.parse("fixture:bindings"))
                    .withVertexShader(Identifier.parse("fixture:bindings")).withFragmentShader(Identifier.parse("fixture:bindings"))
                    .withVertexBinding(0,DefaultVertexFormat.POSITION)
                    .withVertexBinding(1,VertexFormat.builder(1).addAttribute("Color",GpuFormat.RGBA32_FLOAT).build())
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withBindGroupLayout(layout)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN,true)).withCull(false).build();
            encoder.clearColorAndDepthTextures(texture,new Vector4f(0,0,1,1),depth,0);
            try (var pass = (MetalRenderPass) encoder.createRenderPass(RenderPassDescriptor.create(()->"bindings")
                    .withColorAttachment(view).withDepthAttachment(depthView).withRenderArea(new RenderPass.RenderArea(1,1,2,2)))) {
                pass.setPipeline(pipeline); pass.setVertexBuffer(0,vb.slice(16,108)); pass.setVertexBuffer(1,colors.slice());
                pass.setUniform("Tint",uniform.slice(256,16)); pass.setIndexBuffer(ib,IndexType.INT);
                assertThrows(IllegalArgumentException.class, () -> pass.setVertexBuffer(0, new com.mojang.blaze3d.buffers.GpuBufferSlice(vb, 255, 16)));
                assertThrows(IllegalArgumentException.class, () -> pass.draw(-1, 1, 0, 0));
                assertThrows(IllegalArgumentException.class, () -> pass.draw(10, 1, 0, 0));
                assertThrows(IllegalArgumentException.class, () -> pass.drawIndexed(3, 1, 15, 0, 0));
                assertThrows(IllegalArgumentException.class, () -> pass.drawIndexed(3, 1, 3, 3, 4));
                assertThrows(IllegalArgumentException.class, () -> pass.enableScissor(0, 0, -1, 1));
                pass.enableScissor(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
                pass.drawIndexed(3,1,3,3,1);
                if (timestamps) pass.writeTimestamp(queries,0);
                // Farther green geometry must fail reversed depth, after the timestamp encoder interruption.
                pass.drawIndexed(3,1,3,6,2);
                if (timestamps) pass.writeTimestamp(queries,1);
                assertThrows(UnsupportedOperationException.class,()->pass.drawIndexedIndirect(ib.slice(),1));
            }
            Mtl.copyTextureToBuffer(texture.handle,0,0,0,4,4,readback.handle,0,256);
            assertTrue(Mtl.fenceWait(Mtl.fence(),5000));
            long inside=readback.address()+256+4, outside=readback.address();
            assertEquals(128,Byte.toUnsignedInt(MemoryUtil.memGetByte(inside)),1);
            assertEquals(0,Byte.toUnsignedInt(MemoryUtil.memGetByte(inside+1)));
            assertEquals(128,Byte.toUnsignedInt(MemoryUtil.memGetByte(inside+2)),1);
            assertEquals(255,Byte.toUnsignedInt(MemoryUtil.memGetByte(inside+3)));
            assertEquals(0,Byte.toUnsignedInt(MemoryUtil.memGetByte(outside)));
            assertEquals(255,Byte.toUnsignedInt(MemoryUtil.memGetByte(outside+2)));
            if (timestamps) {
                assertTrue(queries.getValue(0).isPresent()); assertTrue(queries.getValue(1).isPresent());
                assertTrue(queries.getValue(1).getAsLong()>=queries.getValue(0).getAsLong());
            }
            Mtl.checkError();
        }
        } finally { device.close(); }
    }

    @Test void texelSliceAndNumericBindingPlanFollowStorageChangesWithoutRecompiling() {
        NativeLoader.load();
        var device=new MetalDevice(0,(id,type)->type==ShaderType.VERTEX
                ? "#version 330\nin vec3 Position;void main(){gl_Position=vec4(Position,1);}"
                : "#version 330\nuniform isamplerBuffer Times;uniform vec3 Arbitrary;uniform int Other;out vec4 color;void main(){color=vec4(float(texelFetch(Times,0).r)/255.,Arbitrary.y,float(Other)/255.,1);}");
        try(var vertices=new MetalBuffer(GpuBuffer.USAGE_VERTEX,36);
            var times=new MetalBuffer(GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER|GpuBuffer.USAGE_MAP_WRITE,512);
            var staged=new MetalBuffer(GpuBuffer.USAGE_MAP_WRITE,512*1024);
            var target=(MetalTexture)device.createTexture("texel",GpuTexture.USAGE_RENDER_ATTACHMENT|GpuTexture.USAGE_COPY_SRC,GpuFormat.RGBA8_UNORM,4,2,1,1);
            var view=device.createTextureView(target);
            var readback=new MetalBuffer(GpuBuffer.USAGE_COPY_DST,512)) {
            float[] triangle={-1,-1,0,3,-1,0,-1,3,0};
            for(int i=0;i<triangle.length;i++) MemoryUtil.memPutFloat(vertices.address()+i*4L,triangle[i]);
            try(var map=times.map(256,256,false,true)){map.data().putInt(64);}
            var layout=BindGroupLayout.builder().withUniform("Times",UniformType.TEXEL_BUFFER,GpuFormat.R32_SINT).build();
            var pipeline=RenderPipeline.builder().withLocation(Identifier.parse("fixture:texel"))
                    .withVertexShader(Identifier.parse("fixture:texel")).withFragmentShader(Identifier.parse("fixture:texel"))
                    .withVertexBinding(0,DefaultVertexFormat.POSITION).withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withBindGroupLayout(layout).withColorTargetState(ColorTargetState.DEFAULT).withCull(false).build();
            var compiled=device.getOrCompilePipeline(pipeline);
            long state=compiled.state(MetalFormats.texture(GpuFormat.RGBA8_UNORM),-1);
            var encoder=device.createCommandEncoder(); encoder.clearColorTexture(target,new Vector4f(0,0,0,1));
            try(var pass=(MetalRenderPass)encoder.createRenderPass(RenderPassDescriptor.create(()->"texel").withColorAttachment(view))) {
                pass.setPipeline(pipeline);pass.setVertexBuffer(0,vertices.slice());pass.setUniform("Times",times.slice(256,256));
                pass.setDefaultFloat3("Arbitrary",0,.5f,0);pass.setDefaultInt("Other",128);pass.enableScissor(0,0,2,2);pass.draw(3,1,0,0);
                // Same Java buffer and uniform binding, fresh native storage; earlier draws retain the original view.
                try(var map=times.map(256,256,false,true)){map.data().putInt(192);}
                // Large mapped writes need a blit. Reject them before interrupting the live render encoder.
                var unsupported = staged.map(0, 4, false, true);
                unsupported.data().putInt(0, 42);
                assertThrows(UnsupportedOperationException.class, unsupported::close);
                assertDoesNotThrow(unsupported::close);
                pass.setDefaultFloat3("Arbitrary",0,1,0);pass.setDefaultInt("Other",64);pass.enableScissor(2,0,2,2);pass.draw(3,1,0,0);
            }
            Mtl.copyTextureToBuffer(target.handle,0,0,0,4,2,readback.handle,0,256);
            assertTrue(Mtl.fenceWait(Mtl.fence(),5000));
            assertEquals(64,Byte.toUnsignedInt(MemoryUtil.memGetByte(readback.address())),1);
            assertEquals(128,Byte.toUnsignedInt(MemoryUtil.memGetByte(readback.address()+1)),1);
            assertEquals(128,Byte.toUnsignedInt(MemoryUtil.memGetByte(readback.address()+2)),1);
            assertEquals(192,Byte.toUnsignedInt(MemoryUtil.memGetByte(readback.address()+8)),1);
            assertEquals(255,Byte.toUnsignedInt(MemoryUtil.memGetByte(readback.address()+9)),1);
            assertEquals(64,Byte.toUnsignedInt(MemoryUtil.memGetByte(readback.address()+10)),1);
            assertSame(compiled,device.getOrCompilePipeline(pipeline));
            assertEquals(state,compiled.state(MetalFormats.texture(GpuFormat.RGBA8_UNORM),-1));
            assertThrows(IllegalStateException.class,()->times.texelView(GpuFormat.R32_SINT,1,256));
            Mtl.checkError();
        }finally{device.close();}
    }

    @Test void equalVertexFormatsWithDifferentStepRatesDoNotAliasPipelineLayouts() {
        NativeLoader.load();
        var device = new MetalDevice(0, (id, type) -> type == ShaderType.VERTEX
                ? "#version 330\nin vec3 Position;in vec4 Color;out vec4 tint;void main(){gl_Position=vec4(Position,1);tint=Color;}"
                : "#version 330\nin vec4 tint;out vec4 color;void main(){color=tint;}");
        try {
            var one = VertexFormat.builder(1).addAttribute("Color", GpuFormat.RGBA32_FLOAT).build();
            var two = VertexFormat.builder(2).addAttribute("Color", GpuFormat.RGBA32_FLOAT).build();
            assertEquals(one, two); // Upstream equals deliberately does not include step rate.
            var a = layoutPipeline(one); var b = layoutPipeline(two);
            var compiledA = device.getOrCompilePipeline(a); var compiledB = device.getOrCompilePipeline(b);
            assertNotSame(compiledA, compiledB);
            assertSame(compiledA, device.getOrCompilePipeline(a));
            assertNotEquals(compiledA.state(MetalFormats.texture(GpuFormat.RGBA8_UNORM), -1),
                    compiledB.state(MetalFormats.texture(GpuFormat.RGBA8_UNORM), -1));
            var matrix = VertexFormat.builder(1).addAttribute("Color", GpuFormat.RGBA32_FLOAT, 2).build();
            assertThrows(UnsupportedOperationException.class, () -> device.getOrCompilePipeline(layoutPipeline(matrix)));
            Mtl.checkError();
        } finally { device.close(); }
    }

    private static RenderPipeline layoutPipeline(VertexFormat color) {
        return RenderPipeline.builder().withLocation(Identifier.parse("fixture:same-layout-location"))
                .withVertexShader(Identifier.parse("fixture:layout")).withFragmentShader(Identifier.parse("fixture:layout"))
                .withVertexBinding(0, DefaultVertexFormat.POSITION).withVertexBinding(1, color)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withColorTargetState(ColorTargetState.DEFAULT).withCull(false).build();
    }

    @Test void invalidCopySlicesAndHeapSourcesFailBeforeNativeAccessAndMappingsCloseOnce() {
        NativeLoader.load(); var device = new MetalDevice(0, (id, type) -> null);
        var bytes = MemoryUtil.memAlloc(8);
        try (var source = new MetalBuffer(GpuBuffer.USAGE_COPY_SRC | GpuBuffer.USAGE_COPY_DST, 64);
             var destination = new MetalBuffer(GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_MAP_WRITE, 64)) {
            var encoder = device.createCommandEncoder();
            bytes.putLong(0, 0x123456789abcdefL);
            assertThrows(IllegalArgumentException.class, () -> encoder.writeToBuffer(new com.mojang.blaze3d.buffers.GpuBufferSlice(source, 63, 8), bytes));
            assertThrows(IllegalArgumentException.class, () -> encoder.copyToBuffer(new com.mojang.blaze3d.buffers.GpuBufferSlice(source, Long.MAX_VALUE, 8), destination.slice(0, 8)));
            assertThrows(IllegalArgumentException.class, () -> device.createBuffer(null, GpuBuffer.USAGE_VERTEX, java.nio.ByteBuffer.allocate(8)));
            assertEquals(Math.min(Integer.MAX_VALUE, Mtl.maxBufferLength()), device.getDeviceInfo().limits().maxMemoryAllocationSize());
            assertThrows(com.mojang.blaze3d.GpuOutOfMemoryException.class, () -> device.createBuffer(null, GpuBuffer.USAGE_VERTEX, device.getDeviceInfo().limits().maxMemoryAllocationSize() + 1));
            encoder.writeToBuffer(source.slice(8, 8), bytes);
            encoder.copyToBuffer(source.slice(8, 8), destination.slice(0, 8));
            try (var read = destination.map(0, 8, true, false)) { assertEquals(bytes.getLong(0), read.data().getLong(0)); }
            var write = destination.map(0, 8, false, true);
            write.data().putLong(0, 42);
            write.close(); write.close();
            try (var read = destination.map(0, 8, true, false)) { assertEquals(42, read.data().getLong(0)); }
            Mtl.checkError();
        } finally { MemoryUtil.memFree(bytes); device.close(); }
    }

    @Test void textureCopyUsesSliceSourceOriginStrideAndDestinationOrigin() {
        NativeLoader.load(); var device=new MetalDevice(0,(id,type)->null);
        try (var source=new MetalBuffer(GpuBuffer.USAGE_COPY_SRC,128);
             var texture=(MetalTexture)device.createTexture("copy",GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_COPY_SRC|GpuTexture.USAGE_RENDER_ATTACHMENT,GpuFormat.RGBA8_UNORM,4,4,1,1);
             var readback=new MetalBuffer(GpuBuffer.USAGE_COPY_DST,1024)) {
            MemoryUtil.memSet(source.address(),0,128);
            MemoryUtil.memPutInt(source.address()+16+(1+1*4)*4,0xff00ff00);
            var encoder=device.createCommandEncoder(); encoder.clearColorTexture(texture,new Vector4f(0,0,1,1));
            encoder.copyBufferToTexture(source.slice(16,64),1,1,4,4,texture,2,1,1,1,0,0);
            encoder.copyBufferToTexture(source.slice(16,64),0,0,4,4,texture,4,4,0,0,0,0);
            Mtl.copyTextureToBuffer(texture.handle,0,0,0,4,4,readback.handle,0,256);
            assertTrue(Mtl.fenceWait(Mtl.fence(),5000));
            assertEquals(0xff00ff00,MemoryUtil.memGetInt(readback.address()+256+2*4));
            assertEquals(0xffff0000,MemoryUtil.memGetInt(readback.address()));
            Mtl.checkError();
        } finally {device.close();}
    }

    @Test void repeatedResizeCopiesPartialMapsRelocationAndTimestampViewReplacement() {
        NativeLoader.load(); var device=new MetalDevice(0,(id,type)->null);
        MetalBuffer buffer=new MetalBuffer(GpuBuffer.USAGE_COPY_SRC|GpuBuffer.USAGE_COPY_DST|GpuBuffer.USAGE_MAP_WRITE|GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER,1024);
        try {
            for(int run=0;run<12;run++) {
                try(var mapping=buffer.map(false,true)) {mapping.data().putInt(100+run);}
                long view=buffer.texelView(GpuFormat.R32_SINT,0,buffer.size());
                long generation=buffer.storageGeneration();
                var bigger=new MetalBuffer(buffer.usage(),buffer.size()+1024);
                device.createCommandEncoder().copyToBuffer(buffer.slice(),bigger.slice(256,buffer.size()));
                try(var mapping=bigger.map(false,true)) {
                    // Sodium's resize maps the full new buffer but updates only the new tail.
                    MemoryUtil.memSet(MemoryUtil.memAddress(mapping.data())+256+buffer.size(),0xff,bigger.size()-256-buffer.size());
                }
                assertTrue(Mtl.fenceWait(Mtl.fence(),5000));
                assertEquals(100+run,MemoryUtil.memGetInt(bigger.address()+256));
                assertEquals(-1,MemoryUtil.memGetInt(bigger.address()+bigger.size()-4));
                assertNotEquals(0,view); assertNotEquals(0,bigger.texelView(GpuFormat.R32_SINT,256,1024));
                try(var mapping=buffer.map(4,4,false,true)) {mapping.data().putInt(0,run);}
                assertTrue(buffer.storageGeneration()>generation);
                assertNotEquals(0,buffer.texelView(GpuFormat.R32_SINT,0,buffer.size()));
                buffer.close(); buffer=bigger;
            }
            Mtl.checkError();
        }finally {buffer.close();device.close();}
    }
}
