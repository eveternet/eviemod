"""Verify independently loadable main mods and fixed Minecraft Metal distributions."""
import glob
import json
import zipfile
import io


def artifact(pattern):
    paths = [p for p in glob.glob(pattern)
             if not p.endswith(("-sources.jar", "-dev.jar", "-shadow.jar"))]
    assert len(paths) == 1, paths
    return zipfile.ZipFile(paths[0])


for directory, target, dandelion in (
    ("build", "26.1.2", "1.0.0-alpha.21+26.1"),
    ("eviemod-26.2/build", "26.2", "1.0.0-alpha.22+26.2"),
):
    with artifact(f"{directory}/libs/eviemod-*-mc{target}.jar") as main:
        mod = json.loads(main.read("fabric.mod.json"))
        assert mod["id"] == "eviemod"
        assert mod["depends"]["minecraft"] == target
        assert mod["depends"]["dandelion"] == f">={dandelion}"
        assert "${" not in main.read("fabric.mod.json").decode()
        assert "dev/eviemod/compat/ClientUi.class" in main.namelist()
        assert not any(n.startswith("dev/eviemod/metal/") for n in main.namelist())
        assert not any("eviemod-metal" in n for n in main.namelist())
        nested_mods = {}
        for entry in mod.get("jars", []):
            with zipfile.ZipFile(io.BytesIO(main.read(entry["file"]))) as nested:
                # The pinned backport has repeated central-directory entries and
                # shaded LibNinePatch metadata. Try the directory aliases for each
                # physical entry, retaining ZipFile's overlap and CRC checks.
                metadata_entries = {}
                for info in nested.infolist():
                    if info.filename == "fabric.mod.json":
                        metadata_entries.setdefault(info.header_offset, []).append(info)
                for candidates in metadata_entries.values():
                    for info in candidates:
                        try:
                            content = nested.read(info)
                            break
                        except zipfile.BadZipFile:
                            if info is candidates[-1]:
                                raise
                    metadata = json.loads(content)
                    nested_mods[metadata["id"]] = metadata
        assert nested_mods["dandelion"]["version"] == dandelion

versions = set()
for target in ("26.1.2", "26.2"):
    with artifact(f"build/distributions/metal/eviemod-metal-mc{target}-*.jar") as addon:
        mod = json.loads(addon.read("fabric.mod.json"))
        versions.add(mod["version"])
        assert mod["id"] == "eviemod_metal"
        assert mod["depends"]["minecraft"] == target
        assert "eviemod" not in mod["depends"]
        assert mod["license"] == "GPL-3.0-only"
        assert addon.filename.endswith(f"mc{target}-{mod['version']}.jar")
        assert "${" not in addon.read("fabric.mod.json").decode()
        assert "dev/eviemod/metal/mtl/Mtl.class" in addon.namelist()
        assert "dev/eviemod/metal/shader/ShaderTranslator.class" in addon.namelist()
        assert ("dev/eviemod/metal/device/MetalSurface.class" in addon.namelist()) == (target == "26.2")
        assert not any(n.startswith("dev/eviemod/paintbrush/") for n in addon.namelist())
        assert "LICENSE" in addon.namelist() and "NOTICE.md" in addon.namelist()
        nested = [entry["file"] for entry in mod["jars"]]
        assert len(nested) == 4, nested
        assert all("3.4.1" in name for name in nested), nested
        assert not any("sodium" in name for name in nested)
        if "natives/libeviemod_metal.dylib" in addon.namelist():
            import struct
            magic, cpu = struct.unpack_from("<II", addon.read("natives/libeviemod_metal.dylib"))
            assert magic == 0xfeedfacf and cpu == 0x0100000c, "Expected thin arm64 Mach-O native bridge"
assert len(versions) == 1, versions
assert len(glob.glob("build/distributions/metal/*.jar")) == 2
print("26.1.2 and 26.2 main mods and separately versioned Metal addons verified")
