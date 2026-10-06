package dev.eviemod.metal.smoke;
import dev.eviemod.metal.device.MetalRenderPass;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=MetalRenderPass.class,remap=false)
abstract class RenderProbeMixin {
    private static int probes;
    @Inject(method="drawIndexed",at=@At("HEAD"))
    private void inspect(int count,int instances,int first,int base,int startInstance,CallbackInfo ci) throws Exception {
        if(!Boolean.getBoolean("eviemod.metal.trace") || probes++>=20) return;
        Object pipeline=read(this,"pipeline");
        if(pipeline==null)return;
        var info=(RenderPipeline)read(pipeline,"info");
        var vertices=(GpuBufferSlice[])read(this,"vertexBuffers");
        String pos="none";
        if(vertices[0]!=null){long address=(Long)read(vertices[0].buffer(),"contents")+vertices[0].offset()+(long)base*info.getVertexFormatBinding(0).getVertexSize();pos=MemoryUtil.memGetFloat(address)+","+MemoryUtil.memGetFloat(address+4)+","+MemoryUtil.memGetFloat(address+8);}
        System.out.println("RENDER_PROBE "+info.getLocation()+" count="+count+" instances="+instances+" first="+first+" base="+base+" vertex="+pos+" area="+read(this,"renderArea"));
        for(var entry:((Map<String,GpuBufferSlice>)read(this,"uniforms")).entrySet()){var slice=entry.getValue();long address=(Long)read(slice.buffer(),"contents")+slice.offset();String floats="";for(int i=0;i<Math.min(16,slice.length()/4);i++)floats+=MemoryUtil.memGetFloat(address+i*4L)+",";System.out.println("RENDER_UNIFORM "+entry.getKey()+" "+floats);}
    }
    private static Object read(Object value,String name)throws Exception{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
}
