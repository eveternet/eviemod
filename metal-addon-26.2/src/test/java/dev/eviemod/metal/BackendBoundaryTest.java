// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal;
import net.minecraft.client.PreferredGraphicsApi;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
class BackendBoundaryTest {
    @Test void disabledMetalPreservesBothVanillaCandidateOrders() {
        String old=System.getProperty("eviemod.metal");
        try {
            System.setProperty("eviemod.metal","false");
            for(var preference:PreferredGraphicsApi.values()) {
                var candidates=preference.getBackendsToTry();
                assertSame(candidates,MetalBootstrap.selectBackends(candidates));
            }
        }finally{if(old==null)System.clearProperty("eviemod.metal");else System.setProperty("eviemod.metal",old);}
    }
    @Test void onlyExactPublishedSodiumBuildIsAccepted() {
        assertDoesNotThrow(()->MetalBootstrap.requireSodiumVersion("0.9.2+mc26.2"));
        for(String other:new String[]{"0.9.2+mc26.1.2","0.9.1+mc26.2","0.9.3+mc26.2","0.9.2"}) {
            var error=assertThrows(IllegalStateException.class,()->MetalBootstrap.requireSodiumVersion(other));
            assertTrue(error.getMessage().contains(other));assertTrue(error.getMessage().contains("0.9.2+mc26.2"));
        }
    }
    @Test void actualMinecraftAndSodiumFactoryAnchorsRemainUnique() throws Exception {
        assertEquals(1,count("net/minecraft/client/Minecraft","<init>","net/minecraft/client/PreferredGraphicsApi","getBackendsToTry","()[Lcom/mojang/blaze3d/systems/GpuBackend;"));
        assertEquals(1,count("net/caffeinemc/mods/sodium/client/gpu/device/context/DrawContext","create","net/caffeinemc/mods/sodium/client/gpu/device/context/GLDrawContext","<init>","()V"));
    }
    private static long count(String target,String method,String owner,String name,String descriptor)throws Exception {
        try(var in=BackendBoundaryTest.class.getClassLoader().getResourceAsStream(target+".class")) {
            assertNotNull(in);var node=new ClassNode();new ClassReader(in).accept(node,0);
            return node.methods.stream().filter(m->m.name.equals(method)).flatMap(m->java.util.stream.StreamSupport.stream(m.instructions.spliterator(),false))
                    .filter(i->i instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(descriptor)).count();
        }
    }
}
