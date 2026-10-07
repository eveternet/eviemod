"""Record freshly resolved Minecraft 26.2 source contracts for port review."""
import hashlib
import json
import os
from pathlib import Path
import zipfile

cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/fabric-loom"
manifest = json.loads((cache / "26.2/mojang_minecraft_info.json").read_text())
assert manifest["id"] == "26.2", manifest["id"]
required = [
    "com/mojang/blaze3d/systems/DeviceFeatures.java",
    "com/mojang/blaze3d/systems/DeviceLimits.java",
    "com/mojang/blaze3d/systems/DeviceInfo.java",
    "com/mojang/blaze3d/systems/GpuDeviceBackend.java",
    "com/mojang/blaze3d/systems/GpuSurfaceBackend.java",
    "com/mojang/blaze3d/systems/RenderPassBackend.java",
    "com/mojang/blaze3d/systems/TransientMemory.java",
    "com/mojang/blaze3d/buffers/GpuBufferSlice.java",
    "com/mojang/blaze3d/buffers/GpuBuffer.java",
    "com/mojang/blaze3d/systems/CommandEncoderBackend.java",
    "net/minecraft/client/PreferredGraphicsApi.java",
    "com/mojang/blaze3d/util/TransientBlockAllocator.java",
    "com/mojang/blaze3d/pipeline/RenderPipeline.java",
    "com/mojang/blaze3d/pipeline/BindGroupLayout.java",
    "com/mojang/blaze3d/vertex/VertexFormat.java",
]
extra = [
    "com/mojang/blaze3d/systems/GpuDevice.java",
    "com/mojang/blaze3d/systems/GpuSurface.java",
    "com/mojang/blaze3d/systems/RenderPass.java",
    "com/mojang/blaze3d/pipeline/RenderPipeline.java",
    "com/mojang/blaze3d/pipeline/BindGroupLayout.java",
    "com/mojang/blaze3d/vertex/VertexFormat.java",
    "com/mojang/blaze3d/platform/NativeLibrariesBootstrap.java",
    "net/minecraft/client/Minecraft.java",
]
candidates = []
for path in cache.rglob("*sources.jar"):
    if "26.2" not in str(path):
        continue
    with zipfile.ZipFile(path) as source:
        if all(name in source.namelist() for name in required):
            candidates.append(path)
assert len(candidates) == 1, candidates
path = candidates[0]
output = Path("build/metal-upstream-review")
output.mkdir(parents=True, exist_ok=True)
print(f"EVIEMETAL_UPSTREAM_SOURCE mc=26.2 clientSha1={manifest['downloads']['client']['sha1']} "
      f"sourceSha256={hashlib.sha256(path.read_bytes()).hexdigest()} path={path}")
with zipfile.ZipFile(path) as source:
    implementations = [name for name in source.namelist() if name.endswith("TransientMemory.java") and name not in required]
    for name in dict.fromkeys(required + extra + implementations):
        text = source.read(name).decode("utf-8")
        destination = output / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(text)
        print(f"UPSTREAM_FILE {name}")
        if name in required or name in implementations:
            print(text)
        else:
            lines = text.splitlines()
            needles = ("getBackendsToTry", "createDevice(", "createSurface(", "loadSpvc",
                       "getDeviceInfo(", "DeviceFeatures(", "DeviceLimits(", "drawIndexed(",
                       "draw(", "multiDraw", "writeTimestamp", "uploadGpu", "ensureCompatible",
                       "getVertexFormatBindings", "TimerQuery", "acquireNextTexture", "blitFromTexture", "present()")
            selected = set()
            for index, line in enumerate(lines):
                if any(needle in line for needle in needles):
                    selected.update(range(max(0, index - 10), min(len(lines), index + 65)))
            for index in sorted(selected):
                print(f"{index + 1}: {lines[index]}")
