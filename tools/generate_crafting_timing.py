"""Rebuild the ITE manufacturing ledger from source components. Author: NingMeng123.

Run normally to regenerate, or with --check to verify that recipe edits have not
silently introduced an instantaneous recipe. The adjacent base repository owns
the frozen R196 registration evidence; no old mod code is executed here.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
BASE = ROOT.parent / "miteto1.21.1neoforge"
LEDGER = RES / "modern_ite/timed-crafting.json"
STATIC_TYPES = {"minecraft:crafting_shaped", "minecraft:crafting_shapeless"}


def f32(value: float) -> float:
    return struct.unpack("!f", struct.pack("!f", value))[0]


def generate() -> dict:
    registry_path = BASE / "reference-data/runtime-registry.json"
    registry = json.loads(registry_path.read_text(encoding="utf-8"))
    items = {item["id"]: item for item in registry["items"]}
    components: dict[str, tuple[float, str]] = {}

    def component(name: str, value: float, evidence: str) -> None:
        components[name] = (f32(value), evidence)

    # Numeric identities and component values are frozen by the guarded R196
    # registration dump. Charcoal is coal metadata 1, with the same component.
    old_items = {
        "minecraft:oak_planks": 5, "#minecraft:logs": 17,
        "minecraft:glass": 20, "minecraft:obsidian": 49,
        "minecraft:torch": 50, "minecraft:furnace": 61,
        "minecraft:coal_block": 173, "minecraft:coal": 263,
        "minecraft:charcoal": 263, "minecraft:diamond": 264,
        "minecraft:iron_ingot": 265, "minecraft:gold_ingot": 266,
        "minecraft:string": 287, "minecraft:book": 340,
        "minecraft:glowstone_dust": 348, "minecraft:ender_pearl": 368,
        "minecraft:gold_nugget": 371, "minecraft:blaze_powder": 377,
        "mite:copper_nugget": 956, "mite:silver_nugget": 957,
        "minecraft:iron_nugget": 958, "minecraft:copper_ingot": 962,
        "mite:silver_ingot": 963, "mite:mithril_ingot": 964,
        "mite:adamantium_ingot": 965, "mite:sinew": 1174,
        "mite:bottle_of_disenchanting": 1214,
    }
    for name, old_id in old_items.items():
        component(name, items[old_id]["scalars"]["yc.crafting_difficulty_as_component"],
                  f"R196 runtime-registry.json item {old_id}: crafting_difficulty_as_component")

    component("minecraft:anvil", 24800,
              "R196 item 145 component -1; RecipeHelper fallback to lowest recipe, source_order 196")
    component("mite:netherrack_furnace", 1280,
              "R196 item 224 component -1; RecipeHelper fallback to lowest recipe, source_order 95")
    component("mite:iron_crafting_table", 160,
              "R196 item 58 / BlockWorkbench hardness 1.6 * 100; metadata 7")
    component("minecraft:redstone_block", 0,
              "R196 runtime-registry.json item 152 component 0; preserve explicit zero")
    component("minecraft:skeleton_skull", 0,
              "R196 item 397 has no component or producing recipe; RecipeHelper skips unresolved negative components")
    component("minecraft:ghast_tear", 5,
              "ITE MITEITEItemRegistryInit.registerRecipes explicitly sets ghastTear component to 5F")
    component("mite:vibranium_ingot", 51200,
              "ITE EarlyRiser durability 512F; ItemIngot component = material durability * 100F")
    component("modern_ite:diamond_chunk", 400,
              "ITE MITEITEItemRegistryInit DIAMOND_CHUNK: diamond component 1600F / 4F")
    component("modern_ite:fancy_red", 1600,
              "Extreme ItemFancyRed extends ItemRock with diamond material: explicit rock component 1600F precedes lowest-production override 1F")
    component("modern_ite:stack_torch", 1e-9,
              "Extreme EXItemsRegistryInit.registerItems: Stack_Torch component 1.0E-9F")
    for name in ("fishing", "planting", "villager"):
        component(f"modern_ite:voucher_{name}", 1,
                  "Extreme EXItemsRegistryInit.registerItems: voucher lowest crafting difficulty override 1F")
    for material in ("iron", "mithril", "adamantium"):
        component(f"modern_ite:{material}_forging_table", 800,
                  "ITE MITEITEBlockRegistryInit: every BlockForgingTable metadata has hardness 8F; Block component = hardness * 100F")
    component("modern_ite:maple_log", components["#minecraft:logs"][0],
              "Extreme BlockEXLog uses BlockHardness.log; same component as R196 log")
    component("modern_ite:maple_planks", components["minecraft:oak_planks"][0],
              "Extreme BlockEXWood inherits BlockWood component; same as R196 planks")

    # Rank and gem modifier do not alter the explicit component value.
    for path in (RES / "data/modern_ite/recipe").rglob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("type") not in STATIC_TYPES:
            continue
        ingredients = list(data.get("key", {}).values()) + data.get("ingredients", [])
        for ingredient in ingredients:
            name = ingredient.get("item", "")
            if name.startswith(("modern_ite:gem_shard_", "modern_ite:gem_")):
                component(name, 25,
                          "Extreme ItemEnhanceGem / ItemGemShard constructors explicitly set component to 25F")

    aliases = {}
    for filename in ("timed-crafting-016.json", "timed-crafting-r196-common.json"):
        aliases.update(json.loads((BASE / "src/main/resources/mite" / filename).read_text(encoding="utf-8"))["recipes"])

    recipes = {}
    for path in sorted((RES / "data/modern_ite/recipe").rglob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("type") not in STATIC_TYPES:
            continue
        name = path.relative_to(RES / "data/modern_ite/recipe").with_suffix("").as_posix()
        result = data["result"]["id"]
        ingredient_list = data["ingredients"] if data["type"].endswith("shapeless") else [
            data["key"][character] for row in data["pattern"] for character in row if character != " "
        ]
        component_names = [ingredient.get("item", "#" + ingredient.get("tag", "")) for ingredient in ingredient_list]
        difficulty = 0.0
        for component_name in component_names:
            if component_name not in components:
                raise ValueError(f"Unmapped crafting component {component_name} in {name}")
            difficulty = f32(difficulty + components[component_name][0])
        evidence = "RecipeHelper whole-batch float32 sum of the registered modern ingredients: " + "; ".join(
            f"{count} * {item} ({components[item][0]})" for item, count in Counter(component_names).items())
        required_bench = None
        source_order = -1
        if name.startswith("club_"):
            required_bench = name.removeprefix("club_")
        elif name == "voucher_club_core":
            required_bench = "copper"
            evidence += "; Extreme ItemMobVoucher declares copper material"
        elif name in ("iron_enhance_stone", "mithril_enhance_stone", "adamantium_enhance_stone"):
            required_bench = name.removesuffix("_enhance_stone")
        elif name.endswith("_forging_table"):
            required_bench = "iron"
            evidence += "; old BlockForgingTable.addItemBlockMaterials uses iron for all metadata"
        elif name in ("vibranium_chest", "vibranium_furnace"):
            required_bench = "vibranium"
        elif name.startswith("dynamic_core_"):
            evidence += "; modern material-lantern adaptation uses the existing modern recipe; preserve its existing absence of a workbench-material gate"
        if name.startswith("gem_") and name[-1:].isdigit():
            evidence += "; R196 CraftingManager.addShapelessRecipe expands counted ItemStacks into single-item components"
        if name == "metal_upgrading_furnace":
            evidence += "; modern-only furnace adaptation uses eight obsidian and the regular furnace component"
        if name.startswith("maple_"):
            borrowed = aliases["minecraft:oak_" + name.removeprefix("maple_")]
            difficulty, required_bench, source_order = borrowed["difficulty"], borrowed["required_bench"], borrowed["source_order"]
            evidence += "; same wood operation as the existing R196 oak manufacturing rule"
        if name == "nether_ender_eye":
            difficulty = 100.0
            evidence = "Extreme RecipesArmorMixin.addRecipes explicitly calls setDifficulty(100F) for fortress eye"
        if name == "resurrection_array":
            difficulty = 25.0
            evidence = "Extreme RecipesToolsMixin.addRecipes explicitly sets 25F; select explicit source override over duplicate EXBlocksRegistryInit registration"
        recipes["modern_ite:" + name] = {"difficulty": difficulty, "required_bench": required_bench,
            "source_order": source_order, "evidence": evidence}

    assert len(recipes) == 154, f"Review added/removed static recipes: {len(recipes)}"
    assert recipes["modern_ite:stack_torch_string_coal"]["difficulty"] == 195.0
    assert recipes["modern_ite:unpack_torches_1"]["difficulty"] == f32(1e-9)

    # The modern vanilla lantern exactly replaces Extreme's block lantern:
    # eight iron nuggets around one torch, with circuits as the output material.
    lantern_difficulty = 0.0
    for component_name in ["minecraft:iron_nugget"] * 4 + ["minecraft:torch"] + ["minecraft:iron_nugget"] * 4:
        lantern_difficulty = f32(lantern_difficulty + components[component_name][0])
    recipes["minecraft:lantern"] = {
        "difficulty": lantern_difficulty, "required_bench": None, "source_order": -1,
        "evidence": "Extreme EXBlocksRegistryInit.registerRecipes line 209: eight iron nuggets and one torch; the modern vanilla recipe has identical inputs. RecipeHelper float32 sum = 711.1111F; old blockLantern uses circuits, so no metal workbench gate."
    }
    assert sum(name.startswith("modern_ite:") for name in recipes) == 154
    assert len(recipes) == 155
    return {
        "author": "NingMeng123",
        "source": "R196 RecipeHelper / frozen runtime-registry.json, MITE-ITE 2.0.28 and Extreme LH 0.6.6 decompiled registrations; explicit overrides precede float32 whole-batch component sums",
        "registry_sha256": hashlib.sha256(registry_path.read_bytes()).hexdigest(),
        "notes": [
            "Difficulty is per whole recipe batch, never multiplied by output count; existing base level, quality, bench, curse and beacon speed modifiers remain authoritative.",
            "The source minimum manufacturing period is 25 ticks, including the 1E-9 torch-bundle unpacking difficulty.",
            "Gem upgrades preserve the old counted-stack expansion into three components; lanterns and the metal upgrading furnace use explicitly identified modern adaptations.",
            "Coverage: 154 static extension recipes plus the vanilla lantern recipe that reuses Extreme's identical eight-nugget-and-torch recipe.",
            "Maintenance, smithing, smelting and metal conversion recipes use their respective existing controllers and are outside this static crafting table."
        ],
        "components": {name: {"difficulty": value, "evidence": evidence} for name, (value, evidence) in sorted(components.items())},
        "recipes": recipes,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    result = generate()
    rendered = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if not LEDGER.exists() or LEDGER.read_text(encoding="utf-8") != rendered:
            raise SystemExit("Manufacturing ledger is stale; run tools/generate_crafting_timing.py")
    else:
        LEDGER.write_text(rendered, encoding="utf-8")
    print(f"{'Verified' if args.check else 'Generated'} {len(result['recipes'])} static manufacturing rules")
