# Block JSON Wand (Fabric, Minecraft 26.3)

Hold an **iron shovel** and **sneak**:

| Action                      | What it does                                         |
|-----------------------------|------------------------------------------------------|
| Left-click a block          | Set position 1 (the block is NOT broken)             |
| Right-click a block         | Set position 2 (no dirt path is made)                |
| Right-click the air         | Export the JSON for every block between the two      |

* One block selected: the block's JSON is printed in chat.
* Up to 24 blocks: the full region JSON is printed in chat too.
* Always: the full export is written to `<game folder>/blockjson/export-<time>.json`
  and `<game folder>/blockjson/latest.json`.
  (On a dedicated server the folder is on the server.)

Export format:

```json
{
  "pos1": [10, 64, 10], "pos2": [12, 65, 11],
  "min": [10, 64, 10], "size": [3, 2, 2],
  "palette": [ {"Name": "minecraft:stone"}, {"Name": "minecraft:oak_stairs", "Properties": {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"}} ],
  "blocks": [ {"pos": [0, 0, 0], "state": 0}, {"pos": [1, 0, 0], "state": 1} ]
}
```
`pos` is relative to `min`; `state` indexes into `palette`. Air is skipped.

## Settings (top of the Java files)

* `BlockJsonMod.REQUIRE_SNEAK` - set to `false` to make the shovel a wand all the time
  (then it can no longer dig or make paths).
* `RegionExporter.MAX_VOLUME` - largest selection allowed (default 200,000 blocks).
* `RegionExporter.INCLUDE_AIR` - include air blocks in the export.
* `RegionExporter.MAX_CHAT_BLOCKS` - how big a region may be and still print in chat.

## Build without installing anything (GitHub)

Upload this project to a free GitHub repository. The workflow in `.github/workflows/build.yml`
builds the jar automatically; download it from the run's **Artifacts** section (`blockjson-mod`).

## Build locally

Needs JDK 25. Then:

    ./gradlew build        (Windows: gradlew.bat build)

The mod jar appears in `build/libs/` (use `blockjson-1.0.0.jar`, not the `-sources` one).
Put it in your `mods` folder next to Fabric API for 26.3.

To test in a dev client: `./gradlew runClient`.
