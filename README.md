# KubeJS PneumaticCraft（kubejs_pneumaticcraft）

[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

KubeJS × [PneumaticCraft: Repressurized](https://github.com/TeamPneumatic/pnc-repressurized) 联动件：
**10 个气动专用配方组件 + 1 个 schema 函数类型 + 11 类配方 schema**，同一个 jar。

- 仓库：<https://github.com/IronHammer-Std/KubeJS-PneumaticCraft>
- 适用：Minecraft 1.21.1 · NeoForge 21.1.181+ · KubeJS 2101.7.2-build.303+ · PneumaticCraft 8.2+
- 发布页文案见 [`DESCRIPTION.md`](DESCRIPTION.md)

> 全网核查过（Modrinth / CurseForge / KubeJS 官方第三方列表）：在写这份东西之前
> **不存在** KubeJS × PnC 的联动件 —— PnC 本体只带 CraftTweaker 支持，jar 里 0 处 kubejs。

---

## 1. 它解决什么

KubeJS 的"配方类型"是 Java 的（由拥有该类型的 mod 注册，数据包/脚本加不了），所以 PnC 的
13 类配方本来只能 `event.custom({...})` 手写 JSON，而且**写错了不会有人告诉你**：
PnC 的 codec 会静默忽略多余/写错的键，或者到解析时才炸。

本件做两件事：

1. **注册 10 个 PnC 专用配方组件 + 1 个 schema 函数类型**（Java 插件）；
2. **提供 11 类配方的 schema**（数据，`data/pneumaticcraft/kubejs/recipe_schema/*.json`）。

于是可以：

```js
event.recipes.pneumaticcraft.refinery(          // 位置参数
	{ amount: 10, tag: 'c:crude_oil' },          // → SizedFluidIngredient（只认 fluid|tag + amount）
	[{ amount: 2, id: 'pneumaticcraft:diesel' },
	 { amount: 3, id: 'pneumaticcraft:kerosene' }]
).minTemp(373).id('taao:probe/refinery')         // 函数糖：设 temperature.min

event.recipes.pneumaticcraft.thermo_plant({      // 对象形态 = 直接给 PnC 的 JSON
	inputs: { fluid: { amount: 100, tag: 'c:diesel' }, item: [] },
	outputs: { fluid_output: { amount: 80, id: 'pneumaticcraft:kerosene' } },
	pressure: 2.0,
	temperature: { min: 573 }
}).id('taao:probe/thermo_plant')
```

写错的时候（位置参数 / 键函数路径）会在 KubeJS 层报错并列出合法键，例如：

```
pneumaticcraft:fluid_container_ingredient: PnC's heat_frame_cooling fluid input is
either(FluidStack, tag+amount) — the single-fluid key is 'id', not 'fluid' (this is the old trap).
Valid keys: [id, tag, amount]
```

---

## 2. 注册的组件（schema JSON 里用 `"type": "<id>"` 引用）

| 组件 id | Java 类型 | JSON 形状 | 用在 | 依据（实读 PnC 源码） |
|---|---|---|---|---|
| `pneumaticcraft:temperature_range` | `TemperatureRangeValue` | `{min?}` / `{max?}` / 缺省=any | refinery / thermo_plant 的 `temperature` | `TemperatureRange.CODEC`：min 缺省 0、max 缺省 `MAX_VALUE`、两者 ≥0、**严格 min &lt; max** |
| `pneumaticcraft:fluid_ingredient` | `SizedFluidIngredient` | `{fluid\|tag, amount?}`（amount 缺省 1000，>0） | refinery.input / fluid_mixer.input1,2 / thermo_plant.inputs.fluid | `SizedFluidIngredient.FLAT_CODEC` → `FluidIngredient.MAP_CODEC_NONEMPTY`（`fluid` / `tag` 二选一） |
| `pneumaticcraft:fluid_ingredient_unsized` | `FluidIngredient` | `{fluid\|tag}`，**不接受 amount** | fuel_quality.fluid | `FluidIngredient.CODEC_NON_EMPTY`（NeoForge 会静默忽略多余的 `amount`，所以这里显式拒绝） |
| `pneumaticcraft:fluid_container_ingredient` | `FluidContainerValue` | `{id\|tag, amount}`（amount **必填**） | heat_frame_cooling.input.fluid | `either(FluidStack.CODEC, TagWithAmount)` —— **不认 `fluid:`**，这就是 Tier A 踩过的"either 树"坑 |
| `pneumaticcraft:thermo_inputs` | `ThermoInputsValue` | `{fluid?, item?}`（`item: []` = 不消耗物品） | thermo_plant.inputs | `ThermoPlantRecipe.Inputs.CODEC` |
| `pneumaticcraft:thermo_outputs` | `ThermoOutputsValue` | `{fluid_output?, item_output?}` | thermo_plant.outputs | `ThermoPlantRecipe.Outputs.CODEC`（额外实现了 matches/replace ⇒ `{output}` 过滤可用） |
| `pneumaticcraft:fluid_stack` | `FluidStack` | `{id\|fluid, amount?}`（amount 缺省 1000，> 0） | refinery.outputs / thermo_plant.outputs.fluid_output | `FluidStack.CODEC`（PnC 的产出）：**替掉 KubeJS 内置件**，因为内置件在位置参数路径不认 `{amount,id}` |
| `pneumaticcraft:fluid_stack_optional` | `FluidStack` | 同上，另允许 `{}` = 空 | fluid_mixer.fluid_output | `FluidStack.OPTIONAL_CODEC` |
| `pneumaticcraft:amadron_resource` | `AmadronResourceValue` | `{resource:{id,count}}` 或 `{resource:{id,amount}}` | amadron.input / .output | `AmadronTradeResource.CODEC`（`either(ItemStack, FluidStack)`） |
| `pneumaticcraft:assembly_program` | `AssemblyProgram` | `'drill'` / `'laser'` | assembly_drill / assembly_laser 的 `program` | 枚举有第三个值 `drill_laser`，但 codec 显式拒绝（只由运行时合成链生成） |
| `pnc_set_field`（**函数类型**） | — | `{"type":"pnc_set_field","key":"temperature","field":"min"}` | schema 的 `functions` ⇒ `.minTemp(373)` / `.maxTemp(333)` | KubeJS 内置 `set` 只能整键赋值，做不到"设对象子字段" |

覆盖的配方类型（11 类 schema）：`pressure_chamber` `explosion_crafting` `assembly_drill` `assembly_laser`
`fluid_mixer` `refinery` `thermo_plant` `amadron` `heat_frame_cooling` `fuel_quality`（新）`heat_properties`（新）。
另有 `pressure_chamber_enchanting` / `_disenchanting`（只有 JEI 展示页）与 `assembly_drill_laser`
（运行时合成，不可撰写）按设计**不暴露**。

### 两条路径的差别（重要）

| 写法 | 值怎么解析 | 严格程度 |
|---|---|---|
| **对象形态** `type({...})` | KubeJS 把它当作"配方 JSON"直接交给各键的 **codec**（= PnC 自己的 codec） | 与 PnC 同等：未知键被忽略，`id:` 之类别名不生效 |
| **位置参数** `type(a, b, c)` / **键函数** `.temperature({...})` / 函数糖 | 走组件的 **`wrap()`** | 严格：拒绝未知键并列出合法键、支持 `id` 别名、校验 PnC 的数值规则 |

对象形态还保留了一个额外能力：**schema 没声明的键会原样透传**（例如 amadron 的 `whitelist`/`blacklist`）。

---

## 3. 目录结构

```
kubejs-pnc\
  build.ps1                    一键：javac 编译 → 组装 → 可复现打包 → jar 内静态校验（-Install 装机）
  tools\
    smoke.ps1                  离线 codec 冒烟测试（不开游戏）
    smoke\CodecSmokeTest.java  46 条判据：形状、往返、该拒的拒
    ensure-bom.ps1             给所有 .ps1 补 UTF-8 BOM（PS 5.1 没 BOM 会把中文注释按 ANSI 解）
  libs\                        编译用（不进产物）：neoforge-21.1.249-merged.jar + kubejs + rhino + DFU + gson + fastutil
  libs-run\                    冒烟测试运行时依赖（brigadier / guava / log4j …）
  src\main\java\dev\taao\kubejspnc\
    KubeJSPneumaticCraftPlugin.java   插件入口（只做两件事：注册组件、注册函数类型）
    PncComponents.java                组件类型注册表（10 个 id 都在这里）
    PncUtil.java                      JS 值读取 / id 解析 / 报错文案
    component\                        10 个组件实现 + FieldSettable 接口
    value\                            5 个值类型（含各自的 codec）
    function\SetFieldFunction.java    pnc_set_field 函数类型
  src\main\resources\
    kubejs.plugins.txt                一行：插件类名 + 前置 mod（pneumaticcraft）
    kubejs_pneumaticcraft_logo.png    模组列表图标（480×300 RGBA，由 mods.toml 的 logoFile 引用）
    META-INF\neoforge.mods.toml       mods.toml（kubejs / pneumaticcraft 都是 required）
    data\pneumaticcraft\kubejs\recipe_schema\*.json    11 份 schema
  dist\                         产物
  .study\                       反编译研究用（KubeJS / PnC 源码），不进产物
```

### 构建

```powershell
pwsh -File build.ps1                                        # 编译 + 打包 + 四层静态校验
pwsh -File build.ps1 -Install -Instance <整合包目录>          # 再复制进 mods\（需游戏已关闭）
pwsh -File tools\smoke.ps1                                  # 离线 codec 冒烟测试（不用开游戏）
```

- 产物 `dist\kubejs_pneumaticcraft-<version>.jar`：条目按路径排序、固定时间戳（2026-01-01）、
  正斜杠路径 ⇒ **同样的源码永远得到同样的 sha256**（现役 `55,475 B / 36 条目`，sha256 `eadb490a…9c84`）。
- 图标：`src\main\resources\kubejs_pneumaticcraft_logo.png`，由 `mods.toml` 的 `logoFile=` 引用；
  构建脚本会校验"声明了就必须在 jar 里、且是合法 PNG"。
- **校验分四层**：① jar 内 schema JSON 全部可解析；② schema 引用的每个组件 id 都在已注册清单里
  （本件 10 个 + KubeJS 内置白名单）；③ 插件主类 / `kubejs.plugins.txt` / `mods.toml` / 图标就位；
  ④ **每个 schema 的键名必须等于 PnC 的真实字段名、且 PnC 的必填字段必须声明**。
- 需要 **JDK 21+**（脚本按 `$env:PNC_JAVAC` → `JAVA_HOME` → 常见安装位置 → `PATH` 的顺序找）。
- `-Instance` 也可用环境变量 `TAAO_INSTANCE` 提供；只需编译时**不必**给。

### 从零构建（clone 之后怎么补齐 `libs\`）

`libs\` 与 `libs-run\` 里是第三方 jar，**不进仓库**（不再分发）。自己 clone 后需要准备：

| 文件 | 放哪 | 从哪来 |
|---|---|---|
| `kubejs-neoforge-2101.7.2-build.374.jar` | `libs\` | KubeJS 发布页 / 任意装了 KubeJS 的 1.21.1 整合包的 `mods\` |
| `rhino-2101.2.8-build.91.jar` | `libs\` | 同上（KubeJS 自带的前置） |
| `neoforge-21.1.249-merged.jar` | `libs\` | **打补丁的 MC+NeoForge 合并 jar**：任意一个用 NeoForge **ModDevGradle** 的 1.21.1 工程构建一次，产物在 `build\moddev\artifacts\neoforge-<ver>-merged.jar` |
| `datafixerupper-8.0.16.jar` / `gson-2.10.1.jar` / `fastutil-8.5.12.jar` | `libs\` | 从 gradle 缓存或 Maven 取（`com.mojang:datafixerupper`、`com.google.code.gson:gson`、`it.unimi.dsi:fastutil`） |
| MC/NeoForge 的一堆运行时依赖（brigadier / guava / log4j / netty …） | `libs-run\` | 只在跑 `tools\smoke.ps1` 时需要；可从 NeoForge 安装目录或 gradle 缓存批量拷入 |

关键是那个 **merged jar**：它由 ModDevGradle 在构建期生成，有了它 + kubejs + rhino，
`javac` 就能编译本工程 —— 所以本件**不需要 gradle、不需要联网**也能构建。

---

## 4. 对外介绍文案

发布用的介绍（短描述 / 长描述 / 英文版 / 版本表）：`DESCRIPTION.md`。

---

## 5. 许可与第三方

- 本件 **MIT**（见 `LICENSE`）。刻意**不引用任何 `me.desht.pneumaticcraft.*` 类**：所有规则都来自对
  PnC 源码的**实读**后写死在组件里，所以既不传染 GPLv3，也不会因为 PnC 内部重构而崩。
- 编译期只把 KubeJS / Rhino / NeoForge / Minecraft 当 `compileOnly`：**不进产物、不再分发**
  （`libs\` 已 gitignore）。
- 运行期依赖：**KubeJS**（LGPLv3）、**PneumaticCraft: Repressurized**（GPLv3）—— 由玩家自行安装，
  本仓库不包含它们的任何代码或资源。
- `.study\`（对 KubeJS / PnC 字节码的反编译研究产物）同样已 gitignore，不随仓库分发。
- 致谢：desht / TeamPneumatic（PneumaticCraft: Repressurized）、latvian.dev（KubeJS 及其
  插件 / 组件 / schema 机制）。
