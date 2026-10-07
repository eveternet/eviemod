"""Keep packaged fixtures strict while identifying the hosted 26.2 timestamp limitation."""
import os
from pathlib import Path
import subprocess
import sys

module, flavor = sys.argv[1:]
log = Path(f"metal-{flavor.lower()}.log")
with log.open("w") as output:
    result = subprocess.run(["./gradlew", f":{module}:packaged{flavor}Smoke", "--no-configuration-cache"],
                            stdout=output, stderr=subprocess.STDOUT,
                            timeout=1000 if flavor in ("Vanilla", "Sodium") else 240)
text = log.read_text()
print(text, flush=True)
required = ["EVIEMETAL_DEVICE_CLOSED_OK"]
if flavor.endswith("Title"):
    required.append("EVIEMOD_METAL_FRAME_OK")
    assert result.returncode != 0 or (Path(module) / "build/packagedSmoke" / flavor.lower() / "metal-smoke.png").stat().st_size > 0
else:
    required.append(f"EVIEMETAL_{flavor.upper()}_LIFECYCLE_OK")
if flavor.startswith("Sodium"):
    required.append("EVIEMETAL_PINNED_SODIUM_OK")
if flavor == "SumrTitle":
    required.append("EVIEMETAL_SUMR_RECOVERY_OK vanilla/modded/cache/pixels/diagnostics/hot-reload")
    assert result.returncode != 0 or "not handled by SUMR" not in text
elif flavor in ("SumrDefaultTitle", "SumrAbsentTitle"):
    required.append("EVIEMETAL_SUMR_GUARD_OK mode=" + ("default" if flavor == "SumrDefaultTitle" else "absent"))
if flavor == "Sodium":
    if module == "metal-addon":
        required.append("EVIEMETAL_SODIUM_TIER1_OK")
    else:
        required += ["EVIEMETAL_SODIUM_ARENA_OK growthRuns=12 sharedRelocation=1",
                     "EVIEMETAL_SODIUM_RESIZE_OK timestampResizes=3"]
        if result.returncode == 0:
            assert text.count("EVIEMETAL_SODIUM_OWNERSHIP_OK") == 2, "Missing both world unload checks"
if result.returncode == 0 and "Shutdown failure" in text:
    raise AssertionError(f"Client logged a shutdown failure; see {log}")
if result.returncode == 0 and all(marker in text for marker in required):
    print(f"EVIEMETAL_PACKAGED_FIXTURE_OK module={module} flavor={flavor}", flush=True)
    sys.exit(0)
expected_assertion = "java.lang.AssertionError: Expected active Metal backend"
unexpected_assertions = [line for line in text.splitlines() if "AssertionError:" in line and expected_assertion not in line]
if (module == "metal-addon-26.2" and os.environ.get("GITHUB_ACTIONS") == "true"
        and result.returncode != 0
        and "Metal 26.2 requires stage-boundary timestamp sampling; device=Apple Paravirtual device" in text
        and "EVIEMETAL_DEVICE_CLOSED_OK" in text and not unexpected_assertions):
    message = (f"{module} {flavor}: NOT VALIDATED. Hosted Apple Paravirtual device lacks the "
               "timestamp counters required by Minecraft 26.2. Native preflight rejected it and cleaned up; "
               "the full final-revision route still requires a timestamp-capable physical Mac.")
    print(f"::notice::{message}", flush=True)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as output:
            output.write(f"- {message}\n")
    sys.exit(0)
raise AssertionError(f"Packaged fixture failed: exit={result.returncode}, "
                     f"missing={[marker for marker in required if marker not in text]}; see {log}")
