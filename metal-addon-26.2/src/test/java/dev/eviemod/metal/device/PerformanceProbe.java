// SPDX-License-Identifier: GPL-3.0-only
// Opt-in CPU microbenchmark, not an FPS or GPU-throughput benchmark. No window is created.
// Four setup rounds and three orphan rounds warm each process; compare several fresh JVMs.
// setup() is invoked directly so draw submission/fill cost does not obscure binding resolution.
package dev.eviemod.metal.device;
import com.mojang.blaze3d.*;
import com.mojang.blaze3d.buffers.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.shaders.*;
import com.mojang.blaze3d.systems.*;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.*;
import dev.eviemod.metal.NativeLoader;
import dev.eviemod.metal.mtl.Mtl;
import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
public class PerformanceProbe {
 public static void main(String[] args) throws Throwable {
  NativeLoader.load();
  var device = new MetalDevice(0, (id,stage) -> stage == ShaderType.VERTEX
   ? "#version 330\nin vec3 Position;void main(){gl_Position=vec4(Position,1);}"
   : "#version 330\nlayout(std140)uniform A{vec4 a;};layout(std140)uniform B{vec4 b;};layout(std140)uniform C{vec4 c;};layout(std140)uniform D{vec4 d;};uniform isamplerBuffer Times;out vec4 color;void main(){color=a+b+c+d+vec4(texelFetch(Times,0));}");
  System.out.println("DEVICE="+device.getRenderer()+" JAVA="+System.getProperty("java.version"));
  try(var vb=new MetalBuffer(GpuBuffer.USAGE_VERTEX,36);
      var ub=new MetalBuffer(GpuBuffer.USAGE_UNIFORM,1024);
      var times=new MetalBuffer(GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER,256);
      var tex=device.createTexture("probe",GpuTexture.USAGE_RENDER_ATTACHMENT,GpuFormat.RGBA8_UNORM,1,1,1,1);
      var view=device.createTextureView(tex)) {
   MemoryUtil.memSet(vb.address(),0,36);MemoryUtil.memSet(ub.address(),0,1024);MemoryUtil.memSet(times.address(),0,256);
   var layout=BindGroupLayout.builder().withUniform("A",UniformType.UNIFORM_BUFFER).withUniform("B",UniformType.UNIFORM_BUFFER).withUniform("C",UniformType.UNIFORM_BUFFER).withUniform("D",UniformType.UNIFORM_BUFFER).withUniform("Times",UniformType.TEXEL_BUFFER,GpuFormat.R32_SINT).build();
   var pipeline=RenderPipeline.builder().withLocation(Identifier.parse("probe:setup")).withVertexShader(Identifier.parse("probe:setup")).withFragmentShader(Identifier.parse("probe:setup")).withVertexBinding(0,DefaultVertexFormat.POSITION).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withBindGroupLayout(layout).withColorTargetState(ColorTargetState.DEFAULT).withCull(false).build();
   var setup=MethodHandles.privateLookupIn(MetalRenderPass.class,MethodHandles.lookup()).findVirtual(MetalRenderPass.class,"setup",MethodType.methodType(boolean.class));
   var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean(); long thread=Thread.currentThread().threadId();
   try(var pass=(MetalRenderPass)device.createCommandEncoder().createRenderPass(RenderPassDescriptor.create(()->"probe").withColorAttachment(view))) {
    pass.setPipeline(pipeline);pass.setVertexBuffer(0,vb.slice());
    var a=ub.slice(0,16);var b=ub.slice(256,16);var c=ub.slice(512,16);var d=ub.slice(768,16);var t=times.slice();
    for(int round=0;round<10;round++) {
     long allocation=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
     for(int i=0;i<200000;i++) {
      pass.setUniform("A",a);pass.setUniform("B",b);pass.setUniform("C",c);pass.setUniform("D",d);pass.setUniform("Times",t);
      boolean ready=(boolean)setup.invokeExact(pass);if(!ready)throw new AssertionError();
     }
     long elapsed=System.nanoTime()-start, bytes=bean.getThreadAllocatedBytes(thread)-allocation;
     System.out.printf(Locale.ROOT,"SETUP round=%d ns/op=%.2f bytes/op=%.2f%n",round,elapsed/200000.,bytes/200000.);
    }
   }
   for(int length:new int[]{4096,131072,258048}) {
    var source=MemoryUtil.memAlloc(length);MemoryUtil.memSet(source,0x23);
    try(var buffer=new MetalBuffer(GpuBuffer.USAGE_COPY_DST,262144)) {
     for(int round=0;round<8;round++) {
      long start=System.nanoTime();
      for(int i=0;i<1000;i++)if(!buffer.tryOrphanWrite(2048,MemoryUtil.memAddress(source),length))throw new AssertionError();
      System.out.printf(Locale.ROOT,"ORPHAN length=%d round=%d ns/op=%.2f%n",length,round,(System.nanoTime()-start)/1000.);
     }
    } finally {MemoryUtil.memFree(source);}
   }
   Mtl.checkError();
  } finally {device.close();}
 }
}
