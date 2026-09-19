PneumaticCraft integration for KubeJS. This mod allows you to add and properly edit recipes of PneumaticCraft: Repressurized in KubeJS scripts. All supported recipe types and examples are below.

Requires KubeJS and PneumaticCraft: Repressurized (both mandatory). Client and server.

Supported recipe types (all under `event.recipes.pneumaticcraft.`):

- pressure_chamber
- explosion_crafting
- assembly_drill
- assembly_laser
- fluid_mixer
- refinery (supports `.minTemp()` and `.maxTemp()`)
- thermo_plant (supports `.minTemp()` and `.maxTemp()`)
- heat_frame_cooling
- amadron
- fuel_quality
- heat_properties

Note: `pressure_chamber_enchanting` / `pressure_chamber_disenchanting` are JEI display pages only (their recipe JSON holds no inputs/outputs), and `assembly_drill_laser` is generated at runtime from a drill + laser pair — neither can be written.

Object form vs positional args — they are parsed differently, so please read this once:

- Object form, `event.recipes.pneumaticcraft.thermo_plant({ ... })`, is parsed with PneumaticCraft's own codecs. Unknown or misspelled keys are **silently ignored** (exactly like writing raw JSON), but any key this mod does not declare is passed through untouched — that is how you set e.g. amadron `whitelist` / `blacklist`.
- Positional args, key functions and the temperature sugar (`.pressure(2.0)`, `.minTemp(373)`) go through this mod's own reader: unknown keys are rejected with the list of legal keys, shorthand is accepted, and PneumaticCraft's numeric rules are enforced.

`event.recipes.pneumaticcraft.pressure_chamber(results[], inputs[], pressure)`
`results` are item stacks (or a single one), `inputs` are sized ingredients (or a single one: `{ count: 1, item: 'minecraft:glass' }` / `{ count: 1, tag: 'c:stones' }`). `pressure` is in bar; PneumaticCraft allows -1..20.

`event.recipes.pneumaticcraft.explosion_crafting(results[], input, loss_rate)`
`input` is a single sized ingredient. `loss_rate` is 0..99 (20 in the vanilla recipes).

`event.recipes.pneumaticcraft.assembly_drill(result, input, program)`
`event.recipes.pneumaticcraft.assembly_laser(result, input, program)`
`result` is a single item stack, `input` a single sized ingredient. `program` is `'drill'` or `'laser'` — `'drill_laser'` is rejected.

`event.recipes.pneumaticcraft.fluid_mixer(input1, input2, fluid_output, item_output, pressure, time)`
Both inputs are sized fluids: `{ amount: 25, tag: 'c:plantoil' }` or `{ amount: 25, fluid: 'minecraft:water' }` (`amount` optional, 1000 by default). `time` is in ticks.

`event.recipes.pneumaticcraft.refinery(input, outputs[], temperature?)`
`input` is a sized fluid, `outputs` must be **2 to 4** fluid stacks, `temperature` defaults to `{ min: 373 }`. Fluid stacks are `{ amount: 2, id: 'pneumaticcraft:diesel' }` — here `amount` **is** required (unlike fluid ingredients).

`event.recipes.pneumaticcraft.thermo_plant(inputs, outputs, temperature?, pressure?, speed?, air_use_multiplier?, exothermic?)`
`inputs` = `{ fluid: { amount: 100, tag: 'c:diesel' }, item: [] }` (`item: []` means "no item consumed"; at least one of `fluid` / `item` must be present), `outputs` = `{ fluid_output: { amount: 80, id: 'pneumaticcraft:kerosene' } }` and/or `{ item_output: { count: 1, id: 'minecraft:glass' } }`.

`event.recipes.pneumaticcraft.heat_frame_cooling(input, temperature, output, bonusMultiplier?, bonusLimit?)`
`input` is either a fluid container `{ fluid: { amount: 1000, tag: 'minecraft:water' } }` — inside it the key is `id` or `tag` and `amount` is required, **not** `fluid` — or a normal ingredient. `temperature` is the threshold in Kelvin.

`event.recipes.pneumaticcraft.amadron(offer_id, input, output, level?, static?, villager_trade?, maxStock?, inStock?)`
Trades are `{ resource: { count: 1, id: 'minecraft:emerald' } }` for items and `{ resource: { amount: 1000, id: 'pneumaticcraft:diesel' } }` for fluids. Positional args also accept a bare id string and work out item vs fluid themselves.

`event.recipes.pneumaticcraft.fuel_quality(fluid, air_per_bucket, burn_rate?)`
`fluid` is an **unsized** fluid ingredient: `{ tag: 'c:diesel' }` or `{ fluid: 'minecraft:water' }`. Writing `amount` here is an error — PneumaticCraft ignores it, so this mod rejects it instead of silently dropping it.

`event.recipes.pneumaticcraft.heat_properties(block, temperature, thermalResistance?, heatCapacity?, transforms?, predicates?, description?)`
`transforms` = `{ hot: 'minecraft:lava', cold: 'minecraft:ice', hot_flowing: ..., cold_flowing: ... }` (block state strings); `predicates` = `{ lit: 'true' }` (block state properties — the key really is `predicates`; the `statePredicate` seen in some of PneumaticCraft's own files is ignored).

Examples:

```js
event.recipes.pneumaticcraft.pressure_chamber(
	[{ count: 1, id: 'minecraft:blue_ice' }],
	[{ count: 4, item: 'minecraft:packed_ice' }],
	2.5
).id('mypack:pressure/blue_ice')
```

```js
event.recipes.pneumaticcraft.thermo_plant({
	inputs: { fluid: { amount: 100, tag: 'c:diesel' }, item: [] },
	outputs: { fluid_output: { amount: 80, id: 'pneumaticcraft:kerosene' } },
	pressure: 2.0,
	temperature: { min: 573 }
}).id('mypack:thermo/kerosene')
```

```js
event.recipes.pneumaticcraft.refinery(
	{ amount: 10, tag: 'c:crude_oil' },
	[
		{ amount: 2, id: 'pneumaticcraft:diesel' },
		{ amount: 3, id: 'pneumaticcraft:kerosene' }
	]
).temperature({ min: 373 }).id('mypack:refinery/crude')

// the same thing with the temperature sugar, plus a max bound:
event.recipes.pneumaticcraft.refinery(
	{ amount: 10, tag: 'c:crude_oil' },
	[
		{ amount: 3, id: 'pneumaticcraft:diesel' },
		{ amount: 3, id: 'pneumaticcraft:lpg' }
	]
).minTemp(303).maxTemp(333).id('mypack:refinery/crude_warm')
```

```js
event.recipes.pneumaticcraft.fluid_mixer(
	{ amount: 25, tag: 'c:plantoil' },
	{ amount: 25, tag: 'c:ethanol' },
	{ amount: 50, id: 'pneumaticcraft:biodiesel' },
	{ count: 1, id: 'pneumaticcraft:glycerol' },
	2.0,
	300
).id('mypack:mixer/biodiesel')
```

```js
event.recipes.pneumaticcraft.assembly_drill(
	{ count: 1, id: 'pneumaticcraft:pressure_chamber_valve' },
	{ count: 1, item: 'pneumaticcraft:compressed_iron_block' },
	'drill'
).id('mypack:assembly/valve')
```

```js
event.recipes.pneumaticcraft.heat_frame_cooling(
	{ fluid: { amount: 1000, tag: 'minecraft:water' } },
	273,
	{ count: 1, id: 'minecraft:ice' }
).id('mypack:cooling/ice')
```

```js
event.recipes.pneumaticcraft.amadron({
	offer_id: 'mypack:amadron/emerald_to_diesel',
	input: { resource: { id: 'minecraft:emerald', count: 1 } },
	output: { resource: { id: 'pneumaticcraft:diesel', amount: 1000 } },
	level: 0
}).id('mypack:amadron/emerald_to_diesel')
```

```js
event.recipes.pneumaticcraft.fuel_quality(
	{ tag: 'c:my_fuel' },
	500000,
	1.0
).id('mypack:fuel/my_fuel')
```

```js
event.recipes.pneumaticcraft.heat_properties({
	block: 'minecraft:magma_block',
	temperature: 900,
	thermalResistance: 1000,
	heatCapacity: 500,
	transforms: { cold: 'minecraft:netherrack' },
	predicates: { lit: 'true' }
}).id('mypack:heat/magma')
```

Removing and editing PneumaticCraft recipes now works — but always add the type:

```js
event.remove({ type: 'pneumaticcraft:refinery', output: Fluid.of('pneumaticcraft:diesel') })
event.remove({ type: 'pneumaticcraft:amadron', output: 'minecraft:emerald' })
event.remove({ type: 'pneumaticcraft:pressure_chamber', mod: 'pneumaticcraft' })
```

Note: filters are cross-type, so `{ output: ... }` without `type:` will also hit recipes from other mods. And for **fluid** outputs the filter value has to be a fluid, i.e. `Fluid.of('pneumaticcraft:diesel')` — a plain string is matched as an item and will never match a fluid output.

Note: PneumaticCraft ships 9 `fuel_quality` recipes, but two of them (`ethylene`, `hydrogen`) are gated behind PneumaticCraft's own `fluid_tag_present` condition, so they only load when some mod provides `c:fuels/ethene` / `c:fuels/hydrogen`. A missing tag means the recipe is skipped — that is intended behaviour, not a bug of this mod.

Note: `pressure` (pressure chamber, -1..20) and `loss_rate` (explosion crafting, 0..99) are not range-checked by this mod yet; out-of-range values are reported by PneumaticCraft itself.

Mistakes are reported instead of ignored — for example:

```text
# heat_frame_cooling fluid container written with a "fluid" key
pneumaticcraft:fluid_container_ingredient: PnC's heat_frame_cooling fluid input is either(FluidStack, tag+amount)
- the single-fluid key is 'id', not 'fluid' (this is the old trap). Valid keys: [id, tag, amount]

# amount on an unsized fluid ingredient (fuel_quality)
pneumaticcraft:fluid_ingredient_unsized: this PnC field takes an UNSIZED fluid ingredient - PnC ignores 'amount'
here, so it is rejected. Remove it, or use pneumaticcraft:fluid_ingredient for sized fields.

# temperature must be strictly min < max
temperature: min must be < max (PnC rule) - got 400 <= T <= 300
```

No mixins, no access transformers, no registry changes and no added content — this mod only registers recipe field types with KubeJS, so it can be added or removed at any time without touching your world.

MIT licensed. It references no `me.desht.pneumaticcraft.*` classes: every rule was read out of PneumaticCraft's sources and re-implemented here, so it neither inherits GPLv3 nor breaks when PneumaticCraft internals change.
