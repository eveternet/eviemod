"""Report the actual JUnit results and reject unexpected skips on native macOS."""
from pathlib import Path
import platform
import xml.etree.ElementTree as ET

native_mac = platform.system() == "Darwin"
for module in (".", "eviemod-26.2", "metal-addon", "metal-addon-26.2"):
    suites = sorted((Path(module) / "build/test-results/test").glob("TEST-*.xml"))
    assert suites, f"No JUnit results for {module}"
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    skipped = []
    for path in suites:
        suite = ET.parse(path).getroot()
        for field in totals:
            totals[field] += int(suite.attrib.get(field, "0"))
        for case in suite.findall("testcase"):
            reason = case.find("skipped")
            if reason is not None:
                skipped.append((case.attrib["classname"], case.attrib["name"], ET.tostring(reason, encoding="unicode")))
    assert totals["tests"] > 0 and totals["failures"] == totals["errors"] == 0, (module, totals)
    if native_mac:
        for owner, name, reason in skipped:
            assert (module == "metal-addon-26.2" and owner.endswith(".Metal26RenderTest")
                    and name.startswith("timestampInterruptionRestoresSlicedBindingsBlendDepthAndArea")
                    ), (module, owner, name, reason)
    print(f"EVIEMETAL_TESTS_OK module={module} platform={platform.system()} " +
          " ".join(f"{key}={value}" for key, value in totals.items()))
    for owner, name, reason in skipped:
        print(f"EVIEMETAL_TEST_SKIPPED {owner}.{name}: {reason.strip()}")
