package dev.taao.kubejspnc.component;

import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.component.RecipeValidationContext;
import dev.latvian.mods.kubejs.recipe.component.UniqueIdBuilder;
import dev.latvian.mods.kubejs.util.OpsContainer;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.type.JSOptionalParam;
import dev.latvian.mods.rhino.type.JSObjectTypeInfo;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.taao.kubejspnc.PncUtil;
import dev.taao.kubejspnc.value.TemperatureRangeValue;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code pneumaticcraft:temperature_range} —— PnC 的 {@code TemperatureRange} 字段
 * （refinery / thermo_plant 的 {@code temperature}）。
 *
 * <p>解决 Tier A 的两个问题：
 * <ol>
 *   <li>Tier A 用的是 {@code map<string,float>}，于是 {@code "optional": {}} 会被 KubeJS 以
 *       "Component 'map&lt;string, float&gt;' is not allowed to be empty!" 拒绝，{@code temperature} 只能写成必填；</li>
 *   <li>{@code map} 不校验 PnC 的规则（min&nbsp;&lt;&nbsp;max、非负），写错了要等 PnC 解析才炸。</li>
 * </ol>
 * JS 侧接受：{@code 373} / {@code [303,333]} / {@code {min:303,max:333}} / {@code 'any'} / 缺省。
 */
public class TemperatureRangeComponent implements RecipeComponent<TemperatureRangeValue>, FieldSettable<TemperatureRangeValue> {
	private static final TypeInfo TYPE_INFO = new JSObjectTypeInfo(List.of(
		new JSOptionalParam("min", TypeInfo.INT, true),
		new JSOptionalParam("max", TypeInfo.INT, true)
	));

	private final RecipeComponentType<?> type;

	public TemperatureRangeComponent(RecipeComponentType<?> type) {
		this.type = type;
	}

	@Override
	public RecipeComponentType<?> type() {
		return type;
	}

	@Override
	public Codec<TemperatureRangeValue> codec() {
		return TemperatureRangeValue.CODEC;
	}

	@Override
	public TypeInfo typeInfo() {
		return TYPE_INFO;
	}

	@Override
	public boolean allowEmpty() {
		return true;
	}

	@Override
	public boolean isEmpty(TemperatureRangeValue value) {
		// "any"（缺省）是一个有意义的取值，不能被当成空值丢掉
		return false;
	}

	@Override
	public TemperatureRangeValue wrap(RecipeScriptContext cx, Object from) {
		Context c = cx.cx();
		if (from == null) {
			return TemperatureRangeValue.ANY;
		}
		if (from instanceof TemperatureRangeValue v) {
			return check(v);
		}
		if (from instanceof Number n) {
			return check(TemperatureRangeValue.min(n.intValue()));
		}
		if (from instanceof CharSequence cs) {
			return check(parseString(cs.toString()));
		}

		Map<String, Object> map = PncUtil.stringMap(c, from);
		if (map != null) {
			PncUtil.rejectUnknownKeys(map, List.of("min", "max"), "temperature");
			int min = map.containsKey("min") ? PncUtil.asInt(c, map.get("min"), "temperature.min") : TemperatureRangeValue.ANY_MIN;
			int max = map.containsKey("max") ? PncUtil.asInt(c, map.get("max"), "temperature.max") : TemperatureRangeValue.ANY_MAX;
			return check(TemperatureRangeValue.of(min, max));
		}

		List<Object> list = c.optionalListOf(from, TypeInfo.NONE);
		if (list != null) {
			if (list.size() == 1) {
				return check(TemperatureRangeValue.min(PncUtil.asInt(c, list.getFirst(), "temperature[0]")));
			}
			if (list.size() == 2) {
				return check(TemperatureRangeValue.of(
					PncUtil.asInt(c, list.get(0), "temperature[0]"),
					PncUtil.asInt(c, list.get(1), "temperature[1]")
				));
			}
			throw new IllegalArgumentException("temperature: array form is [min] or [min, max], got " + list.size() + " element(s)");
		}

		throw new IllegalArgumentException(
			"temperature: expected a number, [min, max] array or {min, max} object, got " + PncUtil.describe(c, from)
		);
	}

	private static TemperatureRangeValue parseString(String raw) {
		String s = raw.trim();
		if (s.equalsIgnoreCase("any") || s.isEmpty()) {
			return TemperatureRangeValue.ANY;
		}
		int dash = s.indexOf('-');
		if (dash > 0) {
			return TemperatureRangeValue.of(Integer.parseInt(s.substring(0, dash).trim()), Integer.parseInt(s.substring(dash + 1).trim()));
		}
		return TemperatureRangeValue.min(Integer.parseInt(s));
	}

	/** wrap 路径上也要过一遍 PnC 的规则（codec 的 validate 只在 JSON 解码时跑）。 */
	private static TemperatureRangeValue check(TemperatureRangeValue v) {
		return v.validateOrThrow();
	}

	@Override
	public void validate(RecipeValidationContext ctx, TemperatureRangeValue value) {
		check(value);
	}

	@Override
	public void buildUniqueId(UniqueIdBuilder builder, TemperatureRangeValue value) {
		builder.append("temp_" + value.min() + "_" + value.max());
	}

	@Override
	public String toString(OpsContainer ops, TemperatureRangeValue value) {
		return value.toString();
	}

	@Override
	public String toString() {
		return type.toString();
	}

	// --- FieldSettable：支撑 .minTemp(373) / .maxTemp(333) ---

	@Override
	public TemperatureRangeValue defaultValue() {
		return TemperatureRangeValue.ANY;
	}

	@Override
	public TemperatureRangeValue withField(RecipeScriptContext cx, TemperatureRangeValue current, String field, Object value) {
		int v = PncUtil.asInt(cx.cx(), value, "temperature." + field);
		return check(current.withField(field, v));
	}

	@Override
	public Set<String> fieldNames() {
		return Set.of("min", "max");
	}
}
