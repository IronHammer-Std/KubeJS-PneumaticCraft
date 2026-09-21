package dev.taao.kubejspnc.smoke;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import dev.taao.kubejspnc.AssemblyProgram;
import dev.taao.kubejspnc.value.AmadronResourceValue;
import dev.taao.kubejspnc.value.FluidContainerValue;
import dev.taao.kubejspnc.value.TemperatureRangeValue;
import dev.taao.kubejspnc.value.ThermoInputsValue;
import dev.taao.kubejspnc.value.ThermoOutputsValue;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * 离线 codec 冒烟测试（不属于产物；源码在 tools\smoke\ 下）。
 *
 * <p>验证"组件写出来的 JSON 正好是 PnC 要的形状、读得回来、该拒的拒"，从而在启动游戏之前
 * 排掉绝大部分风险。运行：tools\smoke.ps1
 */
public class CodecSmokeTest {
	private static int pass;
	private static int fail;
	private static DynamicOps<JsonElement> ops;
	private static java.io.Writer report;

	/** 报告同时写 UTF-8 文件（控制台在 Windows 上会被按 OEM 代码页解码，中文会乱） */
	private static void log(String line) {
		System.out.println(line);
		if (report != null) {
			try {
				report.write(line);
				report.write('\n');
				report.flush();
			} catch (java.io.IOException ignored) {
				// 报告写不进去不影响测试结论
			}
		}
	}

	public static void main(String[] args) throws Exception {
		java.nio.file.Path reportPath = java.nio.file.Path.of(args.length > 0 ? args[0] : "build/smoke-report.txt");
		java.nio.file.Files.createDirectories(reportPath.toAbsolutePath().getParent());
		report = java.nio.file.Files.newBufferedWriter(reportPath, java.nio.charset.StandardCharsets.UTF_8);

		SharedConstants.tryDetectVersion();
		installFakeLoadingModList();
		try {
			Bootstrap.bootStrap();
			log("== codec 冒烟测试（完整 Bootstrap：物品/流体注册表可用） ==");
		} catch (Throwable t) {
			log("== codec 冒烟测试（Bootstrap 失败，退化为仅标签路径）: " + brief(t) + " ==");
		}
		HolderLookup.Provider provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		ops = RegistryOps.create(JsonOps.INSTANCE, provider);

		// ---------- 1. temperature_range ----------
		log("\n-- pneumaticcraft:temperature_range --");
		decodeOk("T1 {min:373}", TemperatureRangeValue.CODEC, "{\"min\":373}", v -> v.min() == 373 && !v.hasMax());
		decodeOk("T2 {max:333}", TemperatureRangeValue.CODEC, "{\"max\":333}", v -> !v.hasMin() && v.max() == 333);
		decodeOk("T3 {min:303,max:333}", TemperatureRangeValue.CODEC, "{\"min\":303,\"max\":333}", v -> v.min() == 303 && v.max() == 333);
		decodeOk("T4 {} = any", TemperatureRangeValue.CODEC, "{}", TemperatureRangeValue::isAny);
		decodeFail("T5 {min:400,max:300} 应拒", TemperatureRangeValue.CODEC, "{\"min\":400,\"max\":300}");
		decodeFail("T6 {min:373,max:373} 应拒（PnC 是严格 <）", TemperatureRangeValue.CODEC, "{\"min\":373,\"max\":373}");
		decodeFail("T7 {min:-5} 应拒", TemperatureRangeValue.CODEC, "{\"min\":-5}");
		// 注意：codec 路径与 PnC 本体一样**忽略未知键**（DFU 的 RecordCodecBuilder 不校验多余键），
		// 所以对象形态写错键名不会报错 —— 真正"写错即报错"的是位置参数/键函数路径（组件的 wrap）。
		decodeOk("T8 {minimum:373} 未知键被忽略（与 PnC 本体一致；wrap 路径才会报错）",
			TemperatureRangeValue.CODEC, "{\"minimum\":373}", TemperatureRangeValue::isAny);
		encode("T9 写出 {min:373}（不带 max）", TemperatureRangeValue.CODEC, TemperatureRangeValue.min(373), "{\"min\":373}");
		encode("T10 写出 {}（any）", TemperatureRangeValue.CODEC, TemperatureRangeValue.ANY, "{}");

		// ---------- 2. fluid_ingredient（sized flat） ----------
		log("\n-- pneumaticcraft:fluid_ingredient（= NeoForge SizedFluidIngredient.FLAT_CODEC） --");
		decodeOk("F1 {fluid,amount}", SizedFluidIngredient.FLAT_CODEC, "{\"fluid\":\"minecraft:water\",\"amount\":1000}", v -> v.amount() == 1000);
		decodeOk("F2 {tag,amount}", SizedFluidIngredient.FLAT_CODEC, "{\"tag\":\"c:diesel\",\"amount\":100}", v -> v.amount() == 100);
		decodeOk("F3 {tag}（amount 缺省）", SizedFluidIngredient.FLAT_CODEC, "{\"tag\":\"c:diesel\"}", v -> v.amount() == 1000);
		decodeFail("F4 {id,amount} 应拒（id 别名只在 wrap 层支持）", SizedFluidIngredient.FLAT_CODEC, "{\"id\":\"minecraft:water\",\"amount\":1000}");
		decodeFail("F5 {tag,fluid} 应拒（xor）", SizedFluidIngredient.FLAT_CODEC, "{\"tag\":\"c:diesel\",\"fluid\":\"minecraft:water\"}");
		encode("F6 写出形状", SizedFluidIngredient.FLAT_CODEC,
			new SizedFluidIngredient(FluidIngredient.tag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, net.minecraft.resources.ResourceLocation.parse("c:crude_oil"))), 10),
			"{\"tag\":\"c:crude_oil\",\"amount\":10}");

		// ---------- 3. fluid_ingredient_unsized ----------
		log("\n-- pneumaticcraft:fluid_ingredient_unsized（= FluidIngredient.CODEC_NON_EMPTY） --");
		decodeOk("U1 {tag}", FluidIngredient.CODEC_NON_EMPTY, "{\"tag\":\"c:diesel\"}", v -> !v.isEmpty());
		decodeOk("U2 {fluid}", FluidIngredient.CODEC_NON_EMPTY, "{\"fluid\":\"minecraft:water\"}", v -> !v.isEmpty());
		decodeOk("U3 {tag,amount} —— NeoForge 会**静默忽略** amount（这正是组件要拦的）", FluidIngredient.CODEC_NON_EMPTY, "{\"tag\":\"c:diesel\",\"amount\":1000}", v -> !v.isEmpty());
		decodeFail("U4 [] 应拒（NON_EMPTY）", FluidIngredient.CODEC_NON_EMPTY, "[]");

		// ---------- 4. fluid_container_ingredient（heat_frame_cooling） ----------
		log("\n-- pneumaticcraft:fluid_container_ingredient --");
		decodeOk("C1 {tag,amount}", FluidContainerValue.CODEC, "{\"tag\":\"minecraft:water\",\"amount\":1000}", v -> v.isTag() && v.amount() == 1000);
		decodeOk("C2 {id,amount}", FluidContainerValue.CODEC, "{\"id\":\"minecraft:water\",\"amount\":1000}", v -> !v.isTag() && v.amount() == 1000);
		decodeFail("C3 {fluid,amount} 应拒（这就是 Tier A 踩过的坑）", FluidContainerValue.CODEC, "{\"fluid\":\"minecraft:water\",\"amount\":1000}");
		decodeFail("C4 {tag} 无 amount 应拒", FluidContainerValue.CODEC, "{\"tag\":\"minecraft:water\"}");
		encode("C5 写出 tag 形状", FluidContainerValue.CODEC,
			FluidContainerValue.of(new FluidContainerValue.TagAmount(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, net.minecraft.resources.ResourceLocation.parse("minecraft:water")), 1000)),
			"{\"tag\":\"minecraft:water\",\"amount\":1000}");
		encode("C6 写出 id 形状", FluidContainerValue.CODEC,
			FluidContainerValue.of(new FluidStack(BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse("minecraft:water")), 1000)),
			"{\"id\":\"minecraft:water\",\"amount\":1000}");

		// ---------- 5. thermo_inputs / thermo_outputs ----------
		log("\n-- pneumaticcraft:thermo_inputs / thermo_outputs --");
		decodeOk("I1 {fluid,item:[]}", ThermoInputsValue.CODEC, "{\"fluid\":{\"amount\":100,\"tag\":\"c:diesel\"},\"item\":[]}",
			v -> v.fluid().isPresent() && v.item().isPresent() && v.item().get().isEmpty());
		decodeOk("I2 {item}", ThermoInputsValue.CODEC, "{\"item\":{\"tag\":\"c:seeds\"}}", v -> v.item().isPresent() && v.fluid().isEmpty());
		decodeFail("I3 {fluid:{fluid:...}} 应拒（thermo_plant 用 fluid 键，不是 id）", ThermoInputsValue.CODEC, "{\"fluid\":{\"id\":\"minecraft:water\",\"amount\":1000}}");
		encode("I4 写出 {item:[]}", ThermoInputsValue.CODEC,
			ThermoInputsValue.of(null, net.minecraft.world.item.crafting.Ingredient.of()), "{\"item\":[]}");
		decodeOk("O1 {fluid_output}", ThermoOutputsValue.CODEC, "{\"fluid_output\":{\"amount\":80,\"id\":\"minecraft:water\"}}",
			v -> !v.fluid().isEmpty() && v.item().isEmpty());
		decodeOk("O2 {item_output}", ThermoOutputsValue.CODEC, "{\"item_output\":{\"count\":1,\"id\":\"minecraft:ice\"}}",
			v -> v.fluid().isEmpty() && !v.item().isEmpty());
		encode("O3 写出 item_output", ThermoOutputsValue.CODEC,
			ThermoOutputsValue.of(null, new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("minecraft:ice")), 2)),
			"{\"item_output\":{\"count\":2,\"id\":\"minecraft:ice\"}}");

		// ---------- 6. amadron_resource ----------
		log("\n-- pneumaticcraft:amadron_resource --");
		decodeOk("A1 物品 {resource:{id,count}}", AmadronResourceValue.CODEC, "{\"resource\":{\"id\":\"minecraft:emerald\",\"count\":8}}",
			v -> v.resource().left().isPresent() && v.resource().left().get().getCount() == 8);
		decodeOk("A2 流体 {resource:{id,amount}}", AmadronResourceValue.CODEC, "{\"resource\":{\"id\":\"minecraft:lava\",\"amount\":4000}}",
			v -> v.resource().right().isPresent() && v.resource().right().get().getAmount() == 4000);
		decodeOk("A3 水（既非物品名，走流体分支）", AmadronResourceValue.CODEC, "{\"resource\":{\"id\":\"minecraft:water\",\"amount\":1000}}",
			v -> v.resource().right().isPresent());
		// 说明：PnC 自己的物品/流体 id（pneumaticcraft:*）在离线环境里不在注册表（没装 PnC），
		// 所以这里一律用原版 id 验证"形状与分支选择"。
		decodeFail("A4 缺 resource 键应拒", AmadronResourceValue.CODEC, "{\"id\":\"minecraft:emerald\",\"count\":1}");
		encode("A5 写出形状", AmadronResourceValue.CODEC,
			AmadronResourceValue.of(new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("minecraft:emerald")), 1)),
			"{\"resource\":{\"count\":1,\"id\":\"minecraft:emerald\"}}");

		// ---------- 7. assembly_program ----------
		log("\n-- pneumaticcraft:assembly_program --");
		check("P1 枚举名", AssemblyProgram.DRILL.getSerializedName().equals("drill") && AssemblyProgram.LASER.getSerializedName().equals("laser"));
		check("P2 只有两个值（drill_laser 不存在）", AssemblyProgram.values().length == 2);

		// ---------- 8. item_ingredient（数量！2026-09-20 修的那个坑） ----------
		log("\n-- pneumaticcraft:item_ingredient（= NeoForge SizedIngredient.FLAT_CODEC） --");
		decodeOk("N1 {item,count:4}", SizedIngredient.FLAT_CODEC, "{\"item\":\"minecraft:iron_ingot\",\"count\":4}", v -> v.count() == 4);
		decodeOk("N2 {tag,count:4}（= 压缩铁那条写法）", SizedIngredient.FLAT_CODEC, "{\"tag\":\"c:ingots/compressed_iron\",\"count\":4}", v -> v.count() == 4);
		decodeOk("N3 {tag}（count 缺省 1）", SizedIngredient.FLAT_CODEC, "{\"tag\":\"c:ingots/compressed_iron\"}", v -> v.count() == 1);
		decodeFail("N4 {count:0} 应拒（NeoForge 要求 count >= 1）", SizedIngredient.FLAT_CODEC, "{\"item\":\"minecraft:iron_ingot\",\"count\":0}");
		decodeFail("N5 {item,tag} 同时给应拒（NeoForge 用 xor，两条都读得通就报错）",
			SizedIngredient.FLAT_CODEC, "{\"item\":\"minecraft:iron_ingot\",\"tag\":\"c:ingots/iron\"}");
		encode("N6 写出 {tag,count:4}（PnC 要的形状）", SizedIngredient.FLAT_CODEC,
			new SizedIngredient(Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.parse("c:ingots/compressed_iron"))), 4),
			"{\"tag\":\"c:ingots/compressed_iron\",\"count\":4}");
		SizedIngredient four = new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 4);
		check("N7 数量语义：1 个不匹配、4 个才匹配（NeoForge 是 >= count）",
			!four.test(new ItemStack(Items.IRON_INGOT, 1)) && four.test(new ItemStack(Items.IRON_INGOT, 4)));

		// ---------- 9. fluid_stack（产出用；位置参数路径下替掉 KubeJS 内置件） ----------
		log("\n-- pneumaticcraft:fluid_stack / :fluid_stack_optional（codec = NeoForge FluidStack.CODEC） --");
		decodeOk("S1 {id,amount}", FluidStack.CODEC, "{\"id\":\"minecraft:water\",\"amount\":500}", v -> v.getAmount() == 500);
		decodeFail("S2 {id} 无 amount 应拒（PnC 的 FluidStack codec 里 amount 是必填；组件 wrap 路径才会补 1000）", FluidStack.CODEC, "{\"id\":\"minecraft:lava\"}");
		decodeFail("S3 {tag} 应拒（FluidStack 表达不了标签）", FluidStack.CODEC, "{\"tag\":\"minecraft:water\"}");
		decodeFail("S4 {} 应拒", FluidStack.CODEC, "{}");
		decodeOk("S5 optional 允许空", FluidStack.OPTIONAL_CODEC, "{}", FluidStack::isEmpty);
		encode("S6 写出形状", FluidStack.CODEC,
			new FluidStack(BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse("minecraft:water")), 250),
			"{\"id\":\"minecraft:water\",\"amount\":250}");

		log("\n== 结果: pass=" + pass + " fail=" + fail + " ==");
		report.close();
		if (fail > 0) {
			System.exit(1);
		}
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * NeoForge 打了补丁的 {@code FeatureFlags.<clinit>} 会去问 FML 的
	 * {@code LoadingModList.get().getModFiles()}；脱离游戏启动时它是 null ⇒ Bootstrap 崩。
	 * 这里用 Unsafe 造一个"空 mod 列表"的实例塞进去（只是为了离线跑 bootstrap，纯粹测试用）。
	 */
	private static void installFakeLoadingModList() {
		try {
			Class<?> c = Class.forName("net.neoforged.fml.loading.LoadingModList");
			Class<?> unsafeC = Class.forName("sun.misc.Unsafe");
			java.lang.reflect.Field theUnsafe = unsafeC.getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			Object unsafe = theUnsafe.get(null);
			Object instance = unsafeC.getMethod("allocateInstance", Class.class).invoke(unsafe, c);
			for (java.lang.reflect.Field f : c.getDeclaredFields()) {
				if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
					continue;
				}
				if (java.util.List.class.isAssignableFrom(f.getType())) {
					f.setAccessible(true);
					f.set(instance, java.util.List.of());
				} else if (java.util.Map.class.isAssignableFrom(f.getType())) {
					f.setAccessible(true);
					f.set(instance, java.util.Map.of());
				}
			}
			java.lang.reflect.Field inst = c.getDeclaredField("INSTANCE");
			inst.setAccessible(true);
			inst.set(null, instance);
			log("  [shim] 已注入空 LoadingModList");
		} catch (Throwable t) {
			log("  [shim] 注入失败（忽略）: " + t);
		}
	}

	private static JsonElement json(String s) {
		return JsonParser.parseString(s);
	}

	private static <T> void decodeOk(String label, Codec<T> codec, String in, Predicate<T> check) {
		try {
			T v = codec.parse(ops, json(in)).getOrThrow(m -> new IllegalStateException(m));
			if (!check.test(v)) {
				bad(label, "解码成功但断言不通过: " + v);
				return;
			}
			ok(label + "  <= " + in);
		} catch (Throwable e) {
			bad(label, "意外失败: " + brief(e));
		}
	}

	private static <T> void decodeFail(String label, Codec<T> codec, String in) {
		try {
			T v = codec.parse(ops, json(in)).getOrThrow(m -> new IllegalStateException(m));
			bad(label, "本该被拒，却解出: " + v);
		} catch (Throwable e) {
			ok(label + "  <= " + in + "   [已拒: " + brief(e) + "]");
		}
	}

	private static <T> void encode(String label, Codec<T> codec, T value, String expect) {
		try {
			JsonElement out = codec.encodeStart(ops, value).getOrThrow(m -> new IllegalStateException(m));
			String s = out.toString();
			// 比较 JSON 树（与键顺序无关），因为 DFU 写出的键顺序不必与手写样例一致
			if (!out.equals(json(expect))) {
				bad(label, "写出 " + s + "，期望 " + expect);
				return;
			}
			ok(label + "  => " + s);
		} catch (Throwable e) {
			bad(label, "写出失败: " + brief(e));
		}
	}

	private static void check(String label, boolean condition) {
		if (condition) {
			ok(label);
		} else {
			bad(label, "断言不通过");
		}
	}

	private static String brief(Throwable e) {
		String m = e.getMessage();
		if (m == null) {
			return e.getClass().getSimpleName();
		}
		int nl = m.indexOf('\n');
		return nl > 0 ? m.substring(0, nl) : m;
	}

	private static void ok(String msg) {
		pass++;
		log("  PASS  " + msg);
	}

	private static void bad(String msg, String why) {
		fail++;
		log("  FAIL  " + msg + " :: " + why);
	}
}
