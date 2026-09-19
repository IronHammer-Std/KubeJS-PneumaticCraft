# KubeJS PneumaticCraft · Tier C 设计稿 v0.1

> 定位：把 **Tier A**（纯数据 jar，9 个 schema）升级为"数据 + 插件"的正式联动件 —— 用 Java 注册**气动专用配方组件**，
> 让 schema 的字段类型严格贴合 PnC 的规则（写错在 KubeJS 层就报错），并补齐剩余类型。
> 建立于 2026-09-19；**同日已按本稿落地**（结果见 §9，工程见 `taao-dev\kubejs-pnc\`），
> 机制背景见 `KubeJS配方类型与schema-机制说明-v0.1.md` §11。
> 文中带「落地修正」的段落是撰写过程中改掉的设计假设，保留原样以便复盘。

---

## 1. 可行性论证（四条硬证据，全部来自本机字节码 / 工程实况）

| 维度 | 证据（实读，非文档推测） | 结论 |
|---|---|---|
| **① API 够用** | `KubeJSPlugin` 的扩展点（javap 实读）：`registerRecipeComponents(RecipeComponentTypeRegistry)`、`registerRecipeSchemas(RecipeSchemaRegistry)`、`registerRecipeSchemaFunctionTypes(RecipeSchemaFunctionRegistry)`、`registerCustomRecipeSchemaFunctions(CustomRecipeSchemaFunctionRegistry)`；<br>`RecipeComponentType.unit(ResourceLocation, RecipeComponent)` / `.dynamic(ResourceLocation, RecipeComponentCodecFactory)`；<br>`RecipeComponent<T>` 只需实现 `type()` / `codec()` / `typeInfo()`（`wrap` / `matches` / `replace` / `validate` / `allowEmpty` 都是 default，按需覆盖） | ✅ 注册一个自定义组件 ≈ **一行注册 + 一个接口实现** |
| **② 有现成先例（而且就在本包内）** | **Custom Machinery**：注册 `custommachinery:rl` / `:requirements` / `:appearance` / `:gui_element`，并在自己的 schema JSON 里引用；<br>**kubejs-create**：`create:heat_condition` / `:processing_output` / `:sized_fluid_ingredient` + 21 个 schema；<br>**kubejs-mekanism**：`mekanism:chemical_stack(_ingredient)` / `:optional_chemical_stack` + 41 个 schema | ✅ 我们要干的**正是这三家已经干成的事**，模式完全一致 |
| **③ 工具链现成、且不需要联网** | `taao-core` 有 gradle wrapper + 成功构建史（`build-0.13.28.log`、产物 `build\libs\taao-core-0.13.28-dev.jar`）；其 `build.gradle` 用 **`compileOnly files('libs/xxx.jar')`** 挂本地 jar（Jade / JEI / CM 都这么挂） | ✅ 把 `kubejs-neoforge-2101.7.2-build.374.jar`（+ 如需 `rhino`）丢进 `libs\` 即可编译，**无需 maven 解析** |
| **④ 装载机制无未知数** | Tier A 已实测：**jar 的 `data/` 会被 KubeJS 的 schema loader 读取**；Tier C 只是再加 `classes/` + `kubejs.plugins.txt`（E16 / CM 同款结构，本包已验证可加载） | ✅ 没有"能不能装上"的风险 |

**唯一真实成本 = 写作与测试工作量**，没有未知的技术门槛。

---

## 2. 核心设计：PnC 专用组件（Java）

> **落地修正（2026-09-19 撰写时）**：设计阶段估的是 4 个组件。真去读 PnC 的 codec 之后发现
> **流体输入有三套不同的 codec**（写混了 PnC 只会静默忽略或报 either 树错误），所以拆成 3 个；
> 另外补了 `assembly_program`（枚举校验）。最终 **8 个组件 + 1 个函数类型**，见 §9。

设计原则：**只实现 KubeJS 接口、绝不引用 PnC 的类** ⇒ ① 许可可保持宽松（MIT），② 不随 PnC 内部实现变动而崩，③ 组件只负责"JSON 形状与校验"。

| # | JSON 组件 id | 序列化形状 | 替换掉 Tier A 的什么 | 收益 |
|---|---|---|---|---|
| 1 | `pneumaticcraft:temperature_range` | `{"min":373}` / `{"max":333}` / `{"min":303,"max":333}` / 可缺省 | `map<string,float>` | **解决 `"optional": {}` 会报 `Component … is not allowed to be empty!` 的坑**；可空、可校验 **严格 min < max**（PnC 的规则就是严格小于）；`temperature` 终于能写成可选 |
| 2a | `pneumaticcraft:fluid_ingredient` | `{"amount":N,"fluid":"…"}` 或 `{"amount":N,"tag":"…"}`（amount 可缺省=1000） | `flat_sized_fluid_ingredient` | PnC 侧是 `SizedFluidIngredient.FLAT_CODEC`：单流体键是 **`fluid`**（不是 `id`），`tag` 二选一，amount &gt; 0 |
| 2b | `pneumaticcraft:fluid_ingredient_unsized` | `{"fluid":"…"}` 或 `{"tag":"…"}`（**不接受 amount**） | （Tier A 无） | fuel_quality 用的是 `FluidIngredient.CODEC_NON_EMPTY`：**没有数量**。NeoForge 会静默忽略多写的 `amount` ⇒ 组件显式拒绝，避免"以为限定了数量其实没有" |
| 2c | `pneumaticcraft:fluid_container_ingredient` | `{"amount":N,"id":"…"}` 或 `{"amount":N,"tag":"…"}`（amount **必填**） | `flat_sized_fluid_ingredient`（heat_frame_cooling.input.fluid） | 这里是 `either(FluidStack.CODEC, TagWithAmount)`：单流体键是 **`id`**，**写 `fluid:` 两边都不匹配** —— 正是 Tier A 时期实测踩到的坑。现在搬到 KubeJS 层报错 |
| 3 | `pneumaticcraft:thermo_inputs` / `:thermo_outputs` | `inputs` = `{"fluid":{…}?,"item":{…}\|[]?}`；`outputs` = `{"item_output":{…}?,"fluid_output":{…}?}` | Tier A 只声明了 `outputs`（custom_object） | **解锁 24 条 `thermo_plant` 的完整撰写**（inputs/temperature/pressure/speed 都能用类型化 API 写）；`item: []`（空数组=不消耗物品）被正确接受；额外实现 matches/replace ⇒ `{output}` 过滤对 thermo_plant 可用 |
| 4 | `pneumaticcraft:amadron_resource` | `{"resource":{"count":N,"id":"…"}}` 或 `{"resource":{"amount":N,"id":"…"}}` | `custom_object + either(item_stack, fluid_stack)` | 语义明确、报错清晰；物品必须有 `count`、流体必须有 `amount`；实现 matches/replace ⇒ `{output}` 过滤可用 |
| 5 | `pneumaticcraft:assembly_program` | `'drill'` / `'laser'` | `string` | 枚举有三值但 codec 显式拒绝 `drill_laser`（源码实读）⇒ 组件只放行两个，写错即报错 |

**函数糖（原设计 §3 的 c 档，已落地）**：`pnc_set_field` —— 注册自定义 `RecipeSchemaFunctionType`，
在 schema 的 `functions` 里声明"哪个键的哪个子字段"，于是 JS 里可以写 `.minTemp(373)` / `.maxTemp(333)`。
内置的 `set` 只能整键赋值，做不到这件事。

---

## 3. schema 层改造

**① 已有 9 类**：把对应键换成新组件（其余不动，向后兼容 —— 形状完全相同，只是校验更严）：

| 类型 | 改动的键 |
|---|---|
| `refinery` | `input` → `pnc:fluid_ingredient`；`temperature` → `pnc:temperature_range`（可空，`optional: {min:373}` 对上 PnC 的缺省）；加 `minTemp`/`maxTemp` 函数糖 |
| `fluid_mixer` | `input1`/`input2` → `pnc:fluid_ingredient` |
| `heat_frame_cooling` | `input.fluid` → `pnc:fluid_container_ingredient`（**不是** `fluid_ingredient`：这里 PnC 只认 `id`/`tag`）；`temperature` → `non_negative_int`；`bonus*` → `non_negative_float` |
| `thermo_plant` | `inputs` → `pnc:thermo_inputs`；`outputs` → `pnc:thermo_outputs`；`temperature` → `pnc:temperature_range`（`default_optional`）；补 `pressure`/`speed`/`air_use_multiplier`/`exothermic` 为可选；加 `minTemp`/`maxTemp` 函数糖 |
| `amadron` | `input`/`output` → `pnc:amadron_resource`；补 `offer_id`/`level`/`static`/`villager_trade`/`maxStock`/`inStock` |
| `assembly_drill` / `assembly_laser` | `program` → `pnc:assembly_program`（只放行 drill / laser） |
| `fuel_quality`（新） | `fluid` → `pnc:fluid_ingredient_unsized`；`air_per_bucket` → `positive_int`；`burn_rate` → `positive_float`（可选） |
| `heat_properties`（新） | `block` → `block`；`temperature`/`heatCapacity` → `non_negative_int`；`thermalResistance` → `double`；`transforms` → custom_object（四个 blockstate 字符串）；`predicates` → `map<string,string>`；`description` → `string` |
| 其余 3 类 | 不变（字段已是标量/数组） |

**② 函数糖：先试纯 JSON，不行再写 Java** —— schema JSON 支持 `function_names`（别名方法）与 `functions`（`set` / `addToList` 等），先例：KubeJS 内置 `shaped.json`（`kjsMirror`）、CM（`singleCore` / `hide`）、Create（`heated` / `superheated`）。
⚠️ **但要说清楚适用边界**：`set` 是"把某个键设成常量"、`addToList` 是"往列表键追加"，**对"设对象里的某个子字段"（如 `temperature.min = 373`）并不直接适用** ⇒ 这类糖有三种做法，按成本从低到高：

| 做法 | 说明 | 是否需要 Java |
|---|---|---|
| a. 只给别名，用对象形态 | `function_names` 让 `.temperature({min:373})` 也能写（不省事） | ❌ |
| b. 把组件设计成"接受两段式"| 让 `temperature_range` 的构造支持 `[373]` / `[303,333]` 这类简写 | ❌（组件本身已有） |
| c. 注册自定义函数类型 | `RecipeSchemaFunctionType` + `RecipeSchemaFunction`（`registerRecipeSchemaFunctionTypes` / `registerCustomRecipeSchemaFunctions`）⇒ 真正实现 `.minTemp(373)` / `.maxTemp(333)` | ✅ 少量代码 |

⇒ **落地结论**：a/b/c 三档**都做了**。c 档（`pnc_set_field`）实测可用（见 §9），成本约 70 行。

示意（**已落地，JS 写法见 PnC 撰写指南 v0.3**）：

```js
event.recipes.pneumaticcraft.refinery(
	{ amount: 10, tag: 'c:crude_oil' },
	[{ amount: 2, id: 'pneumaticcraft:diesel' }, { amount: 3, id: 'pneumaticcraft:kerosene' }]
).temperature({ min: 373 })      // a/b：对象形态
 .maxTemp(393)                   // c：函数糖（pnc_set_field）
event.recipes.pneumaticcraft.pressure_chamber(…).pressure(2.5)   // 标量键：键名即函数名
event.recipes.pneumaticcraft.assembly_drill(…).program('drill')  // 同上
```

**③ 补齐剩余 4 类**：

| 类型 | 做法 | 落地 |
|---|---|---|
| `fuel_quality` | 补 schema（`fluid` 用"无数量"变体 `pnc:fluid_ingredient_unsized` + `air_per_bucket`/`burn_rate`） | ✅ 已补 |
| `heat_properties` | `block`/`temperature`/`thermalResistance`/`heatCapacity`/`transforms`/`predicates`/`description` | ✅ 已补（没有单独做 `pnc:heat_transforms` 组件：`transforms` 四个字段都是 blockstate **字符串**，用 `custom_object`+`string` 就够，见 §9） |
| `pressure_chamber_enchanting` / `_disenchanting` | 仅 JEI 展示页 | 维持不补（结论不变） |
| `assembly_drill_laser` | 运行时合成，**不可撰写** | 维持结论（结论不变） |

---

## 4. 工程结构（实际落地）

> **落地修正**：设计阶段打算沿用 gradle。实际写的时候发现编译只需要
> "打补丁的 MC+NeoForge 合并 jar + kubejs + rhino + DFU + gson"这几只 jar（都在 `libs\`），
> taao-core 的 moddev 已经把它们缓存出来了 ⇒ **不需要 gradle、不需要联网**：
> `build.ps1` 里 `javac` 一次 + 手工可复现打包即可。少一层工具链就少一类失败模式。

```
taao-dev\kubejs-pnc\
  build.ps1                     ← javac 编译 → 组装 → 可复现打包 → jar 内三层静态校验（-Install 装机）
  tools\smoke.ps1               ← 离线 codec 冒烟测试（不用开游戏）
  tools\smoke\CodecSmokeTest.java
  tools\ensure-bom.ps1          ← 给 .ps1 补 UTF-8 BOM（PS 5.1 没 BOM 会把中文按 ANSI 解码 ⇒ 语法错）
  libs\                         ← 编译用（不进产物）
  libs-run\                     ← 冒烟测试运行时依赖
  src\main\java\dev\taao\kubejspnc\
      KubeJSPneumaticCraftPlugin.java   ← extends KubeJSPlugin：registerRecipeComponents + registerRecipeSchemaFunctionTypes
      PncComponents.java / PncUtil.java
      component\ ×8 + FieldSettable
      value\ ×5
      function\SetFieldFunction.java
  src\main\resources\
      kubejs.plugins.txt                ← 一行：<插件类全名> pneumaticcraft（第二个 token 是"前置 mod 门"）
      data\pneumaticcraft\kubejs\recipe_schema\*.json   ← 11 个
  dist\                                 ← 产物
```

要点：
- **插件类与 schema 放同一个 jar**（Tier A 的 `data/` 原样并入 `src\main\resources\`）。
- `kubejs.plugins.txt` 的第二个 token 不是命名空间，而是**前置 mod 门**（KubeJS 源码实读：
  `ModList.get().isLoaded(line[i])`）⇒ 写 `pneumaticcraft` 让它只在装了 PnC 时才加载。
- `neoforge.mods.toml`：`kubejs` / `pneumaticcraft` 都 required；版本号升 **`2101.1.0`**。
- **Tier A 与 Tier C 不能同时装**（同一批 `data/pneumaticcraft/kubejs/recipe_schema/` 路径）⇒ 取代关系。

---

## 5. 构建与验收（沿用本包装机协议）

**构建**：`pwsh -File build.ps1`（`-Install` 装机）→ 产物 `dist\kubejs_pneumaticcraft-2101.1.0.jar`，
可复现（固定时间戳 + 排序条目 + 正斜杠路径）。
**离线预检**：`pwsh -File tools\smoke.ps1` → 40 条 codec 判据（形状 / 往返 / 该拒的拒）。

**装机验收（一 Round 启动即可）**：

| 判据 | 期望 | 状态 |
|---|---|---|
| R11 / id 检查 | `VIOLATIONS 0` / `MISSING=0` | ✅ 已过（117 jar） |
| 插件被加载 | 日志出现 `Found plugin source kubejs_pneumaticcraft`（且**不再**有 "does not have required mod … skipping"） | ⏳ 待实机 |
| 十一类写入 | 探针逐类写一条：位置参数与对象形态都 OK | ✅ **第二轮 12/12 + 8/8 全过**（§9.7） |
| **新组件的核心卖点** | 4 条负例（heat_frame_cooling 写 `fluid:` / fuel_quality 带 `amount` / temperature min≥max / program 写 `drill_laser`）⇒ **都应在 KubeJS 层报错** | ✅ **4/4 都在 KubeJS 层被拦下**（第二轮汇总恰 4 条 failed recipe）；D1/D2 的报错文案已抄进指南 |
| `thermo_plant` 完整撰写 | 带 `inputs`+`temperature` 的一条能写成、JEI 可见 | ✅ 首轮暴露键名 bug（`input` vs `inputs`）⇒ 已修，第二轮 B7 通过 |
| 回归 | 各类型配方数回基线、`0 failed recipes`（探针负例除外）、reload 后 KubeJS ERROR/WARN 0 | ✅ 九类基线精确复现；全局 `Failed to parse recipe` 仍 = 18（基线）；第二轮 `failed recipes` = 4（= 有意写错的负例），探针撤掉后回归 0 |
| 反向 | 卸掉 Tier C、装回 Tier A ⇒ 一切照常（`staging\tierA-fallback\` 有整份回退材料） | ⏳ 备用 |

---

## 6. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 组件 `mapCodec` / `codec` 写法有误 | schema 加载失败、该类型配方掉出配方表（会明显报错，不是静默） | 组件逐个上线、每轮 reload 看"配方数回基线"；出问题立刻回退 Tier A 的 jar |
| KubeJS 升级导致插件 API 细节变动 | 需重新编译 | 版本区间钉 `[2101.7.2-build.303,)`；升级时重跑 §5 判据 |
| 组件的 `matches()`/`replace()` 未覆盖 | `{output}` 过滤/替换对个别键失效 | 组件实现 `matches`（输出角色）并实测三例（thermo_plant/amadron/heat_frame_cooling） |
| 许可 | —— | 刻意**不引用 PnC 类** ⇒ 可继续 MIT；若将来必须引用，则整体转 GPLv3 |
| 与 Tier A 并存 | 路径冲突 | 明确取代关系 + 版本号区分 + 装机脚本先删旧件 |

---

## 7. 工作量与收益（初估）

| 项 | 量 |
|---|---|
| Java（4–5 个组件 + 插件壳 + 构建脚本） | **约 300–500 行** |
| schema 改写 9 个 + 新增 4 个 | JSON 为主，逐类对照 PnC 自带配方 |
| 测试 | 1 轮启动（探针 6 条判据）+ 1 轮回归 |
| 合计 | **半天到一天**（不含 PnC 版本升级的适配） |

**收益**（相对 Tier A）：
1. **写错即报错**：`fluid:` 之类违反 PnC 规则的写法在 KubeJS 层就被拦下（现在是运行期才炸，或静默不生效）；
2. **`thermo_plant` 从"只能管产出"变成"完整可写"**（24 条配方的最大一块）；
3. `temperature` 等对象字段**可空、有校验**，摆脱 `map` 的坑；
4. 剩余 2 类（`fuel_quality` / `heat_properties`）可补齐；
5. 成为**全网第一个** KubeJS × PneumaticCraft 联动件（Tier A 已可作为它的数据底座）。

---

## 8. 明确不做（边界）

- 不改气动的机器行为、不加内容、不动注册表；
- 不实现 PnC 的 CraftTweaker 那套（那是本体自带）；
- 不做 JEI/配方查看器集成（PnC 本体已有）；
- 不引用 PnC 内部类（保持解耦与宽松许可）。

---

## 9. 落地结果（2026-09-19）

### 9.1 产物

| 项 | 值 |
|---|---|
| 工程 | `C:\MyProgram\MC\taao-dev\kubejs-pnc\` |
| 产物 | `dist\kubejs_pneumaticcraft-2101.1.0.jar`（**55,475 B / 36 条目 = 20 class + 11 schema + 4 元数据 + 1 图标**；四代：首轮装机 49,319 B → 缺陷修复 53,388 B → 加图标 55,685 B → **修正 mods.toml 对外描述** 55,475 B，见 §9.6） |
| sha256 | `eadb490a41e9fde5a66f85b2afb7a6c363921e472177fef8342e24e2a2fd9c84`（可复现：同源码同哈希；前三代为 `7229f133…083c` / `0c67d876…8328` / `eabffbff…59e5`） |
| 图标 | `kubejs_pneumaticcraft_logo.png`（480×300 RGBA，4,708 B，源文件 `taao-dev\new-assets\KubeJS_PnC_Icon.png`），`mods.toml` 用 `logoFile=` 引用；构建脚本会校验"声明了就一定在 jar 里且是合法 PNG" |
| 装机 | `mods\kubejs_pneumaticcraft-2101.1.0.jar`（Tier A 的 `2101.0.1` 已被脚本删掉；同名替换，mods 仍 117 jar） |
| 源码规模 | Java 17 个文件 / 20 个 class（约 1,500 行，含注释）；schema 11 份；构建与测试脚本 3 个 |
| 回退 | `..\staging\tierA-fallback\`（Tier A 的 jar + 原始 pack 目录） |

### 9.2 离线预检：codec 冒烟测试 46/46

`tools\smoke.ps1` 在**不开游戏**的情况下 Bootstrap 了 MC 的注册表，然后逐条验证
"组件写出来的 JSON 正好是 PnC 要的形状 / 读得回来 / 该拒的拒"。关键判据：

| 判据 | 结果 |
|---|---|
| `temperature_range`：`{min}` / `{max}` / `{min,max}` / `{}` / 写出时**不补 max** | ✅ |
| `temperature_range`：`min≥max`、`min=373&max=373`、负数 ⇒ 全部拒绝 | ✅（PnC 是**严格** min &lt; max） |
| `temperature_range`：未知键 `{minimum:373}` 被 codec 忽略（与 PnC 本体一致） | ✅（这一条写进文档：**"写错即报错"只在位置参数/键函数路径成立**） |
| sized-fluid：`{tag,amount}` / `{fluid,amount}` / amount 缺省 = 1000 | ✅ |
| sized-fluid：`{id,amount}` 被 codec 拒（`id` 别名只在 `wrap()` 层支持） | ✅ |
| unsized-fluid：`{tag,amount}` 里 NeoForge **静默忽略** amount ⇒ 正是组件要拦的 | ✅（确认了设计动机） |
| container-fluid：`{tag,amount}` / `{id,amount}` 通过；**`{fluid,amount}` 被拒** | ✅（Tier A 的坑被复现并拦下） |
| `thermo_inputs`：`item: []` 解码为空 Ingredient、写出仍是 `[]` | ✅ |
| `amadron_resource`：物品走 count 分支、流体走 amount 分支、缺 `resource` 被拒 | ✅ |
| `fluid_stack`（§9.6 新增）：`{id,amount}` / `{tag}` 拒 / `{}` 拒 / **`{id}` 无 amount 拒**（PnC 的 `FluidStack.CODEC` 里 amount 必填）/ optional 允许空 | ✅ |

### 9.3 装机检查

| 检查 | 结果 |
|---|---|
| R11 依赖区间 | `jars scanned 117 / ranges evaluated 99 / VIOLATIONS 0 / unverifiable 4`（占位符 `${file.jarVersion}`） |
| 配置类 id 检查 | `jars=117 / MISSING=0`（顺手修掉一处探针里的 `c:ingots/iron`：该 tag 由 NeoForge 本体提供，检查脚本看不到 ⇒ 换成具体物品，并把"检查脚本看不见 loader 自带 tag"记为已知盲点） |
| 归档 | 代码 `TAAO_code-20260919-162225.zip`（1734 文件 ok）；基线 `TAAO_Alpha-baseline-20260920-003133.zip`（117 jar，内含 55,475 B 的现役版；与上一份名单 +0/−0 = 同名替换） |
| 实机 | ✅ **两轮都跑完**：首轮暴露 2 个真缺陷 + 1 处缺口（§9.6 已修）；**第二轮（16:48）全绿**，见 §9.7 |

### 9.4 落地时的三处设计修正

1. **流体输入不是一个组件而是三个**：PnC 在三个地方用了三套不同的 codec
   （`SizedFluidIngredient.FLAT_CODEC` 认 `fluid`、`FluidIngredient.CODEC_NON_EMPTY` 不要数量、
   `either(FluidStack, TagWithAmount)` 只认 `id`）。设计稿原本把它们当一个，落地时拆开，并把
   "写 `fluid:` 会报错"精确到**只有 heat_frame_cooling 那一处**成立。
2. **不用 gradle**（见 §4）：javac + 手工可复现打包，少一层工具链。
3. **对象形态 vs 位置参数是两条路径**（见 §2 末表）：KubeJS 在 `RecipeTypeFunction.createRecipe`
   里对"单参数且是 Map"走 `schema.deserialize`（= 直接按 PnC JSON 解析，走 **codec**），
   其余走构造器 + 组件 `wrap()`。所以"严格校验"只在前者之外的路径生效 —— 这一点必须写进用户文档，
   否则会出现"我按对象形态写错了，为什么没报错"的误解。

### 9.5 已知未做 / 观察项

- `heat_properties` 的 `predicates` 用的是 PnC codec 里真实的字段名；PnC 自带的
  `createlowheated/basic_burner_*.json` 写的是 `statePredicate` —— 按 codec 实读，**那个键会被忽略**
  （要么是 PnC 的历史遗留写法，要么是它的 bug）。本件按 `predicates` 提供，不模仿错误写法。
- `pressure`（pressure_chamber 的 [-1,20]）、`loss_rate`（[0,99]）仍是裸 `float`/`int`：
  要加范围校验得再注册两个 Scalar 组件，本轮不做（PnC 侧会报错，不至于静默）。
- `{output}` 过滤：**字符串只能匹配物品**（流体产出要用 `Fluid.of(...)`）；且 `countRecipes` **不数
  "本回调里刚写进去的配方"**（实测证据：探针 B9 也产出 ice，但同回调内 `{output:ice}` 仍是 1）
  ⇒ 判据只能用"官方自带配方的命中数"，探针自己写的那些看 JEI。

### 9.6 首轮实机校验与修正（第二轮装机）

**首轮通过项**：`Found plugin source kubejs_pneumaticcraft`（15:36:59，mod 列表 `2101.0.1 -> 2101.1.0`）；
九类基线精确复现（`15/3/4/4/2/3/24/12/3`）；位置参数、键函数、**函数糖 `.minTemp(393)` / `.minTemp(303).maxTemp(333)`**
全部无错（⇒ 自定义函数类型 `pnc_set_field` 实机可用）；两条负例精确命中组件文案
（*"the single-fluid key is 'id', not 'fluid' … Valid keys: [id, tag, amount]"*、*"PnC ignores 'amount' here"*）；
全局 `Failed to parse recipe` 仍 = 18（长期基线 CDG）。

**揪出并修掉的问题**：

| # | 问题 | 实机证据 | 修法 |
|---|---|---|---|
| 1 | `thermo_plant` schema 键名 `input` ≠ PnC 的 **`inputs`** | `Error parsing recipe … No key inputs in MapLike[…"input":{…}…]`；B7 报 *Recipe component key 'input' not found!* | 键名改回 `inputs` |
| 2 | `heat_properties` 只有 1 个必填键 ⇒ KubeJS 存在 **arity-1 构造器**，单参数对象形态被当成"给第一个键赋值" | *Unable to set 'block: block' to '{整份 JSON}'* —— 对象形态完全不可用 | `temperature` 提升为必填（PnC 缺省即 0，语义等价）⇒ arity 从 2 起 |
| 3 | KubeJS 内置 `fluid_stack` 在**位置参数**路径不认 PnC 的 `{amount,id}`（对象形态没事，那条走 codec） | C2/C3/C4/C6/D3 五条全挂：*Failed to read FluidStack from {amount: 3.0, id: …}: Fluid with ID minecraft: does not exist!* | 新增 **`pneumaticcraft:fluid_stack` / `:fluid_stack_optional`**，refinery.outputs / fluid_mixer.fluid_output / thermo_outputs 全改用它 |

**防呆升级**：`build.ps1` 新增**第 4 层校验** —— 每个 schema 的键名必须落在该类型的 **PnC 字段白名单**内、
且 PnC 的必填字段必须声明 ⇒ 问题 1 那类"键名拼错、写出的 JSON 没人认"从构造上出不了厂。
探针也改了：KubeJS 的 `RecipeTypeFunction.call` **会吞异常**只打 error ⇒ 原来的 `step()` 会出现
"假 PASS"；第二轮改用 **`event.failedCount` 增量**判成败。

**两个数字的真相**（都不是缺陷）：`fuel_quality = 7` 而非 9 —— jar 里 9 条中 `ethylene`/`hydrogen`
带 PnC 自己的 `pneumaticcraft:fluid_tag_present` 条件（`c:fuels/ethene`、`c:fuels/hydrogen`，本包无人提供；
后续与 Mekanism 氢/乙烯手工整合时会变 9）；`heat_properties = 16`（35 文件里 16 条条件满足）。

**第二轮待验**：`! KubeJS errors found` 应为 **4**（只 D 段那 4 条负例）；E 段
`thermo_plant {output:salmon_tempura}` 从 0 → **1**、两行 `{output: Fluid.of(...)}` ≥1；探针每行 `failedCount` 不再增长。

### 9.7 第二轮实机（16:48，修复版）—— **全绿**

| 段 | 结果 |
|---|---|
| A 基线 | 九类**全部** `✔`（`15/3/4/4/2/3/24/12/3`）；`fuel_quality = 7`（9 条里 2 条被 PnC 自己的 `fluid_tag_present` 挡住，正常）、`heat_properties = 16` |
| B 对象形态 | **12/12 PASS**（含首轮挂掉的 **B7 `thermo_plant`** 与 **B11 `heat_properties`**） |
| C 位置参数 / 键函数 / 函数糖 | **8/8 PASS**（含首轮挂掉的 C2/C3/C4/C6；`.minTemp(393)`、`.minTemp(303).maxTemp(333)` 均生效） |
| D 负例 | 4 条 NOTE；KubeJS 汇总 **`with 4 failed recipes`**、聊天栏 **`! KubeJS errors found [4]!`** ⇒ **恰好是那 4 条有意写错的**（首轮是 11） |
| E `{output}` 过滤 | **4/4 PASS**：`thermo_plant {output:salmon_tempura} = 1`（首轮 0 ⇒ 键名修复后官方配方也能被 KubeJS 正确读取）、`amadron {output:emerald} = 6`、`pressure_chamber = 1`、`heat_frame_cooling = 1` |
| E 追加（新事实） | `refinery {output:'diesel'}`（字符串）= **0**，而 `{output: Fluid.of('diesel')}` = **3** ⇒ **流体产出要用 `Fluid.of(...)` 才能命中**（字符串只按物品匹配）；`thermo_plant {output: Fluid.of('kerosene')} = 1` ⇒ 我的 `thermo_outputs` 组件对流体产出同样生效 |
| F 计数 | 当轮回调里新增的配方**不计入** `countRecipes`（已在文档里写明，避免误判） |

**探针处置**：按协议验完即删 —— `kubejs\server_scripts\tierc-probe.js` 已移除，草稿保留在 `kubejs\dev-probes\pnc-tierc-probe.js`（下次要用再复制）。
**收尾检查**：R11 `VIOLATIONS 0` / cfg id `MISSING=0`（117 jar，探针删除后 script-made-ids 107 → 84、refs 250 → 226）。
> ⚠️ 探针里 `event.failedCount` **JS 侧读不到**（返回 undefined ⇒ 我的 `failedCount()` 回退成 -1、不显示后缀），
> 所以"没抛异常 ≠ 建成"这件事最终是靠 KubeJS 自己的汇总行（`with N failed recipes` / `errors found [N]`）判定的 —— 这也是更可靠的判据。
