from __future__ import annotations


__all__ = ["tag", "version", "commit"]


# ========= =========== ===================
#           release     development
# ========= =========== ===================
# tag       X.Y         X.Y (upcoming)
# version   X.Y         X.Y.dev1+g5678cde
# commit    X.Y         5678cde
# ========= =========== ===================


# When tagging a release, set `released = True`.
# After tagging a release, set `released = False` and increment `tag`.

released = False

tag = version = commit = "11.0"


if not released:  # pragma: no cover
    import pathlib
    import re
    import subprocess

    def get_version(tag: str) -> str:
        # Since setup.py executes the contents of src/websockets/version.py,
        # __file__ can point to either of these two files.
        file_path = pathlib.Path(__file__)
        root_dir = file_path.parents[0 if file_path.name == "setup.py" else 2]

        # Read version from git if available. This prevents reading stale
        # information from src/websockets.egg-info after building a sdist.
        try:
            description = subprocess.run(
                ["git", "describe", "--dirty", "--tags", "--long"],
                capture_output=True,
                cwd=root_dir,
                timeout=1,
                check=True,
                text=True,
            ).stdout.strip()
        # subprocess.run raises FileNotFoundError if git isn't on $PATH.
        except (FileNotFoundError, subprocess.CalledProcessError):
            pass
        else:
            description_re = r"[0-9.]+-([0-9]+)-(g[0-9a-f]{7,}(?:-dirty)?)"
            match = re.fullmatch(description_re, description)
            assert match is not None
            distance, remainder = match.groups()
            remainder = remainder.replace("-", ".")  # required by PEP 440
            return f"{tag}.dev{distance}+{remainder}"

        # Read version from package metadata if it is installed.
        try:
            import importlib.metadata  # move up when dropping Python 3.7

            return importlib.metadata.version("websockets")
        except ImportError:
            pass

        # Avoid crashing if the development version cannot be determined.
        return f"{tag}.dev0+gunknown"

    version = get_version(tag)