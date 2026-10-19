"""Downloads generated art (Higgsfield results) so it can be reviewed and added to the app."""
import pathlib, sys, urllib.request

BASE = "https://d8j0ntlcm91z4.cloudfront.net/user_33nCCU5zdhBp1P3AToFhR3jJqvD/"
FILES = [
    "hf_20261019_150622_45953756-0302-4254-986a-e0074bdc2a9d.png",
    "hf_20261019_150622_795061f9-674c-434c-a090-895521a02410.png",
    "hf_20261019_152348_03bbbdba-51e0-4a35-96bb-f15602098ef8.png",
    "hf_20261019_152347_dedd2cad-31e5-4900-8f98-eaa9c55d2a0e.png",
    "hf_20261019_152348_fe78c45b-f613-4c98-bf23-e7204ac493e3.png",
    "hf_20261019_152346_8f19f3fb-0f22-4bcb-9249-28b19bf93754.png",
    "hf_20261019_152347_89b01aba-857e-449c-a3ad-921a90176c54.png",
]
out = pathlib.Path(sys.argv[1]) / "hf"
out.mkdir(parents=True, exist_ok=True)
for f in FILES:
    data = urllib.request.urlopen(urllib.request.Request(BASE + f, headers={"User-Agent": "sakina-ci"}), timeout=60).read()
    (out / f).write_bytes(data)
    print(f, len(data))
