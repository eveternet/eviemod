"""Verify that the two Fabric artifacts remain independently loadable."""
import glob
import json
import zipfile


def artifact(pattern):
    paths = [p for p in glob.glob(pattern) if not p.endswith("-sources.jar")]
    assert len(paths) == 1, paths
    return zipfile.ZipFile(paths[0])


with artifact("build/libs/eviemod-*.jar") as main:
    assert json.loads(main.read("fabric.mod.json"))["id"] == "eviemod"
    assert not any(n.startswith("dev/eviemod/metal/") for n in main.namelist())
    assert not any("eviemod-metal" in n for n in main.namelist())

with artifact("metal-addon/build/libs/eviemod-metal-*.jar") as addon:
    mod = json.loads(addon.read("fabric.mod.json"))
    assert mod["id"] == "eviemod_metal"
    assert "eviemod" not in mod["depends"]
    assert mod["license"] == "GPL-3.0-only"
    assert any(n.startswith("dev/eviemod/metal/") for n in addon.namelist())
    assert not any(n.startswith("dev/eviemod/paintbrush/") for n in addon.namelist())
    assert "LICENSE" in addon.namelist() and "NOTICE.md" in addon.namelist()

print("Main mod and Metal addon artifact separation verified")
