// SPDX-License-Identifier: GPL-3.0-only
package net.caffeinemc.mods.sodium.client.gpu.arena;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.stream.Stream;
import net.caffeinemc.mods.sodium.client.gpu.arena.staging.MojangStagingBuffer;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;

/** Package-local access is fixture-only: force small real arenas through resize, relocation and recycling. */
public final class MetalArenaStress {
    public static void run() {
        var staging = new MojangStagingBuffer(4096);
        var parent = new ArenaAggregator(staging);
        int[] relocated = {0};
        var changes = new RegionAllocatorHandle.AllocationChangeConsumer() {
            public void onBufferChanged() { relocated[0]++; }
            public void onSegmentChanged(int index) { relocated[0]++; }
        };
        try {
            for (int iteration = 0; iteration < 12; iteration++) {
                var region = new RenderRegion(0, 0, 0, parent);
                var initial = parent.getBufferOfSizeAtLeast(256);
                var arena = new SingleOwnerBufferArena(parent, initial, initial.size() / 4, 4);
                var owner = new RegionAllocatorHandle(region, changes, arena);
                var seed = upload(owner, 16, iteration + 101, 0);
                long oldOffset = seed.getOffset();
                var large = upload(owner, 128, iteration + 201, 1);
                if (owner.getBufferObject() == initial) throw new AssertionError("Fixture did not force arena growth");
                verify(owner, seed, iteration + 101);
                verify(owner, large, iteration + 201);
                if (seed.getOffset() == oldOffset) throw new AssertionError("Seed did not relocate on compaction");
                owner.free(large); owner.free(seed);
                owner.deleteSingleOwner();
                // A retired source is retained by Sodium's recycler, then borrowed with exactly the same identity.
                var recycled = parent.getBufferOfSizeAtLeast(initial.size());
                if (recycled != initial) throw new AssertionError("Retired arena was not reused");
                parent.releaseBufferForReuse(recycled);
                if (owner.getDeviceUsedMemory() != 0) throw new AssertionError("Arena retained live segments");
            }
            // Shared-owner eviction exercises the new 26.2 allocation-change callback and copied segment offsets.
            var initial = parent.getBufferOfSizeAtLeast(256);
            var shared = new SharedBufferArena(parent, initial, 64, 4);
            var owner = new RegionAllocatorHandle(new RenderRegion(0, 0, 0, parent), changes, shared);
            var seed = upload(owner, 16, 301, 0);
            var large = upload(owner, 128, 401, 1);
            if (owner.getBackingArena() == shared) throw new AssertionError("Shared owner did not relocate");
            verify(owner, seed, 301); verify(owner, large, 401);
            owner.free(large); owner.free(seed); owner.deleteSingleOwner(); shared.deleteShared();
            if (relocated[0] == 0) throw new AssertionError("No relocation callbacks");
        } finally { parent.delete(); staging.delete(); }
        System.out.println("EVIEMETAL_SODIUM_ARENA_OK growthRuns=12 sharedRelocation=1 callbacks=" + relocated[0]);
    }
    private static BufferSegment upload(RegionAllocatorHandle owner, int elements, int value, int index) {
        var data = new NativeBuffer(elements * 4);
        try {
            var bytes = data.getDirectBuffer();
            while (bytes.hasRemaining()) bytes.putInt(value);
            var upload = new PendingUpload(data, index);
            owner.upload(Stream.of(upload));
            return upload.getResult();
        } finally { data.free(); }
    }
    private static void verify(RegionAllocatorHandle owner, BufferSegment segment, int expected) {
        long bytes = segment.getLength() * 4;
        try (var readback = RenderSystem.getDevice().createBuffer(() -> "Arena fixture readback", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, bytes)) {
            RenderSystem.getDevice().createCommandEncoder().copyToBuffer(owner.getBufferObject().slice(segment.getOffset() * 4, bytes), readback.slice());
            try (var map = readback.map(true, false)) {
                while (map.data().hasRemaining()) if (map.data().getInt() != expected) throw new AssertionError("Relocated terrain data corrupted");
            }
        }
    }
}
