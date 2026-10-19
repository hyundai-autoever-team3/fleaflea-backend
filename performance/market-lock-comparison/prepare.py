"""Export a pinned commit into isolated comparison sources; never edit production sources."""

import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--ref", required=True, help="Committed baseline shared by all variants")
parser.add_argument(
    "--output", required=True, type=Path, help="New directory outside the repository"
)
args = parser.parse_args()
root = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip())
output = args.output.expanduser().resolve()
if output == root or root in output.parents or output.exists():
    parser.error("Output must be a new directory outside the repository")
commit = subprocess.check_output(
    ["git", "rev-parse", "--verify", args.ref + "^{commit}"], text=True
).strip()
archive = subprocess.check_output(["git", "archive", commit])
service_path = Path("src/main/java/com/anabada/fleaflea/domain/market/service/MarketService.java")
aspect_path = Path("src/main/java/com/anabada/fleaflea/global/lock/DistributedLockAspect.java")
pointcut = '@Around("@annotation(distributedLock)")'
comparison_pointcut = (
    '@Around("@annotation(distributedLock) && "\n'
    '            + "!execution(* com.anabada.fleaflea.domain.market.service.MarketService.updateMarket(..))")'
)
output.mkdir(parents=True)
manifest = {"baseline": commit, "scope": "MarketService.updateMarket only", "variants": {}}
for variant in ("plain", "db", "redisson-db"):
    target = output / variant
    target.mkdir()
    with tarfile.open(fileobj=io.BytesIO(archive)) as source:
        # git archive contains tracked repository files only; reject escaping members.
        for member in source.getmembers():
            destination = (target / member.name).resolve()
            if target not in destination.parents and destination != target:
                raise RuntimeError("Archive path escapes output")
        source.extractall(target)
    if variant != "redisson-db":
        aspect = target / aspect_path
        text = aspect.read_text()
        if text.count(pointcut) != 1:
            raise RuntimeError("Distributed lock pointcut changed; inspect baseline")
        aspect.write_text(text.replace(pointcut, comparison_pointcut))
    if variant == "plain":
        service = target / service_path
        text = service.read_text()
        start = text.index("public MarketUpdateResponse updateMarket(")
        end = text.index("public MarketInvitationResponse getInvitation(", start)
        section = text[start:end]
        before = "Market market = getLockedMarket(marketId);"
        if section.count(before) != 1:
            raise RuntimeError("Update lookup changed; inspect baseline")
        service.write_text(
            text[:start]
            + section.replace(before, "Market market = getMarket(marketId);")
            + text[end:]
        )
    manifest["variants"][variant] = {
        str(path): hashlib.sha256((target / path).read_bytes()).hexdigest()
        for path in (service_path, aspect_path)
    }
(output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
print("Prepared comparison sources at " + str(output))
print("Baseline: " + commit)
