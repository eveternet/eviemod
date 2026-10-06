"""Verify both main-mod targets and the standalone Metal artifact."""
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
                metadata = json.loads(nested.read("fabric.mod.json"))
                nested_mods[metadata["id"]] = metadata
        assert nested_mods["dandelion"]["version"] == dandelion

with artifact("metal-addon/build/libs/eviemod-metal-*.jar") as addon:
    mod = json.loads(addon.read("fabric.mod.json"))
    assert mod["id"] == "eviemod_metal"
    assert mod["depends"]["minecraft"] == "26.1.2"
    assert "eviemod" not in mod["depends"]
    assert mod["license"] == "GPL-3.0-only"
    assert any(n.startswith("dev/eviemod/metal/") for n in addon.namelist())
    assert not any(n.startswith("dev/eviemod/paintbrush/") for n in addon.namelist())
    assert "LICENSE" in addon.namelist() and "NOTICE.md" in addon.namelist()

print("26.1.2 and 26.2 main mods and standalone 26.1.2 Metal addon verified")
