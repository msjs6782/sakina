"""Downloads generated art (Higgsfield results) so it can be reviewed and added to the app."""
import pathlib, sys, urllib.request

BASE = "https://d8j0ntlcm91z4.cloudfront.net/user_33nCCU5zdhBp1P3AToFhR3jJqvD/"
FILES = [
    "hf_20261019_150622_45953756-0302-4254-986a-e0074bdc2a9d.png",
    "hf_20261019_150622_9231d880-fc05-4014-a58c-a53bfbe0b617.svg",
    "hf_20261019_150622_795061f9-674c-434c-a090-895521a02410.png",
]
out = pathlib.Path(sys.argv[1]) / "hf"
out.mkdir(parents=True, exist_ok=True)
for f in FILES:
    data = urllib.request.urlopen(urllib.request.Request(BASE + f, headers={"User-Agent": "sakina-ci"}), timeout=60).read()
    (out / f).write_bytes(data)
    print(f, len(data))
