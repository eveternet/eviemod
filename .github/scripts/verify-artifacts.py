"""Verify both supported Eviemod artifacts and their bundled dependencies."""
import glob
import json
import zipfile
import io


def artifact(pattern):
    paths = [p for p in glob.glob(pattern)
             if not p.endswith(("-sources.jar", "-dev.jar", "-shadow.jar"))]
    assert len(paths) == 1, paths
    return zipfile.ZipFile(paths[0])


for directory, target, dandelion, yacl, hm_api, kotlin, loader in (
    ("build", "26.3", "1.0.0-alpha.23+26.3", "3.9.7+26.3-fabric", "1.0.5+26.3", "1.14.2+kotlin.2.4.21", "0.20.1"),
    ("eviemod-26.2/build", "26.2", "1.0.0-alpha.22+26.2", "3.9.4+26.2-fabric", "1.0.4+26.2", "1.14.1+kotlin.2.4.20", "0.19.5"),
):
    with artifact(f"{directory}/libs/eviemod-*-mc{target}.jar") as main:
        mod = json.loads(main.read("fabric.mod.json"))
        assert mod["id"] == "eviemod"
        assert mod["depends"]["minecraft"] == target
        assert mod["depends"]["dandelion"] == f">={dandelion}"
        assert "${" not in main.read("fabric.mod.json").decode()
        notice = main.read("THIRD-PARTY.txt").decode()
        assert "${" not in notice
        assert all(version in notice for version in (dandelion, yacl, hm_api, kotlin))
        assert mod["depends"]["fabricloader"] == f">={loader}"
        assert "dev/eviemod/compat/ClientUi.class" in main.namelist()
        assert "dev/eviemod/compat/ClientInput.class" in main.namelist()
        assert not any("SmokeTest" in n or "VersionFixtures" in n for n in main.namelist())
        assert not any(n.startswith("dev/eviemod/metal/") for n in main.namelist())
        assert not any("eviemod-metal" in n for n in main.namelist())
        nested_mods = {}
        for entry in mod.get("jars", []):
            with zipfile.ZipFile(io.BytesIO(main.read(entry["file"]))) as nested:
                metadata = json.loads(nested.read("fabric.mod.json"))
                nested_mods[metadata["id"]] = metadata
        for mod_id, version in {
            "dandelion": dandelion,
            "yet_another_config_lib_v3": yacl,
            "hm-api": hm_api,
            "fabric-language-kotlin": kotlin,
        }.items():
            assert nested_mods[mod_id]["version"] == version, (target, mod_id)

print("26.3 and 26.2 Eviemod artifacts verified")
