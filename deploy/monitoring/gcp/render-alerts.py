#!/usr/bin/env python3
"""Render expected-host alerts without committing infrastructure identifiers."""
import os
from pathlib import Path
import re
import sys


def main():
    instance = os.environ.get("GCP_MONITORED_INSTANCE", "")
    if len(sys.argv) != 2 or not Path(sys.argv[1]).is_absolute() or not re.fullmatch(r"[a-z][a-z0-9-]{0,61}[a-z0-9]", instance):
        raise SystemExit("An absolute output path and valid expected VM name are required")
    template = Path(__file__).with_name("prometheus") / "rules" / "alerts.yml"
    output = Path(sys.argv[1])
    output.write_text(template.read_text().replace("${GCP_MONITORED_INSTANCE}", instance))


if __name__ == "__main__":
    main()
