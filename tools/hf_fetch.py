"""Downloads generated art (Higgsfield results) so it can be reviewed and added to the app."""
import pathlib, sys, urllib.request

BASE = "https://d8j0ntlcm91z4.cloudfront.net/user_33nCCU5zdhBp1P3AToFhR3jJqvD/"
FILES = [
    "hf_20261020_095710_dad3adfa-c276-40d3-bd68-155a6cbdde08.png",
    "hf_20261020_095709_14f161dc-1ac6-4886-a28a-8fd74d843719.png",
    "hf_20261020_095708_f15ac0f4-26fc-499d-b4e6-b6cbae3db4c1.png",
    "hf_20261020_095710_3c0f19d9-3ef8-42c9-ac85-96cfe87f568a.png",
    "hf_20261020_095710_efd5758d-c28c-4144-bdfd-091f79e49bcc.png",
    "hf_20261020_095709_833446bf-582f-4738-b9e7-f418a59d4157.png",
    "hf_20261020_095709_770cdb8e-2eb4-489f-b66e-2dbb76a2c334.png",
    "hf_20261020_095709_6b9d2b74-27c0-4bd8-9612-2781e6ec40cc.png",
    "hf_20261019_153039_59863dcf-eec3-4ad9-95a3-4cd0345f129b.png",
    "hf_20261019_153040_6d8e60a5-4342-4082-8574-6e0597dabf2a.png",
    "hf_20261019_153000_d26f7d56-88bb-4e12-ba3f-aa63f4d1dfb0.png",
    "hf_20261019_153003_4396a3d2-6af0-4b91-aef8-09baed935182.png",
    "hf_20261019_153053_2ef99344-b918-4bdc-aed6-5b027934b4e8.png",
    "hf_20261019_153053_f60a2bf5-363b-4dc8-b789-a65fdc3eb9d3.png",
    "hf_20261019_153001_d1259ace-5e52-484d-b8eb-184f556f217a.png",
    "hf_20261019_153003_a1cfbfc6-18ed-4f08-9460-3338f9d5eb7b.png",
    "hf_20261019_153002_979b9d8d-08e4-4c14-be6a-f213c74cc396.png",
    "hf_20261019_153001_7f79c7e0-c289-4880-912c-8505696c18f9.png",
    "hf_20261019_153003_d1ac059f-0c6a-4c8c-ae0f-0c7082e3d0d7.png",
    "hf_20261019_153000_f179ccb7-85e8-4753-b832-69d4f75e298e.png",
    "hf_20261019_150622_45953756-0302-4254-986a-e0074bdc2a9d.png",
]
out = pathlib.Path(sys.argv[1]) / "hf"
out.mkdir(parents=True, exist_ok=True)
for f in FILES:
    data = urllib.request.urlopen(urllib.request.Request(BASE + f, headers={"User-Agent": "sakina-ci"}), timeout=60).read()
    (out / f).write_bytes(data)
    print(f, len(data))
