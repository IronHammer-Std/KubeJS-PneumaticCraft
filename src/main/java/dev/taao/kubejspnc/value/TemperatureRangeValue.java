package dev.taao.kubejspnc.value;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/**
 * PnC {@code TemperatureRange} 的<b>无损镜像</b>（不引用 PnC 类）。
 *
 * <p>对应 JSON：{@code {"min":373}} / {@code {"max":333}} / {@code {"min":303,"max":333}} / 缺省（any）。
 * 规则与 PnC {@code me.desht.pneumaticcraft.api.crafting.TemperatureRange} 完全一致（源码实读）：
 * <ul>
 *   <li>{@code min} 缺省 0、{@code max} 缺省 {@link Integer#MAX_VALUE}；</li>
 *   <li>两者都必须 &ge; 0；</li>
 *   <li>必须 <b>min &lt; max</b>（是严格小于：{@code {"min":373,"max":373}} 在 PnC 侧也是非法的）。</li>
 * </ul>
 */
public record TemperatureRangeValue(int min, int max) {
	public static final int ANY_MIN = 0;
	public static final int ANY_MAX = Integer.MAX_VALUE;
	public static final TemperatureRangeValue ANY = new TemperatureRangeValue(ANY_MIN, ANY_MAX);

	public static final Codec<TemperatureRangeValue> CODEC = RecordCodecBuilder.<TemperatureRangeValue>create(
			instance -> instance.group(
					Codec.INT.optionalFieldOf("min").forGetter(v -> v.hasMin() ? Optional.of(v.min()) : Optional.empty()),
					Codec.INT.optionalFieldOf("max").forGetter(v -> v.hasMax() ? Optional.of(v.max()) : Optional.empty())
				)
				.apply(instance, (min, max) -> of(min.orElse(ANY_MIN), max.orElse(ANY_MAX)))
		)
		.validate(TemperatureRangeValue::check);

	public static TemperatureRangeValue of(int min, int max) {
		return min == ANY_MIN && max == ANY_MAX ? ANY : new TemperatureRangeValue(min, max);
	}

	public static TemperatureRangeValue min(int min) {
		return of(min, ANY_MAX);
	}

	public static TemperatureRangeValue max(int max) {
		return of(ANY_MIN, max);
	}

	public boolean isAny() {
		return min == ANY_MIN && max == ANY_MAX;
	}

	public boolean hasMin() {
		return min != ANY_MIN;
	}

	public boolean hasMax() {
		return max != ANY_MAX;
	}

	public TemperatureRangeValue withField(String field, int value) {
		return switch (field) {
			case "min" -> of(value, max);
			case "max" -> of(min, value);
			default -> throw new IllegalArgumentException("Unknown temperature field '" + field + "'");
		};
	}

	private static DataResult<TemperatureRangeValue> check(TemperatureRangeValue v) {
		try {
			return DataResult.success(v.validateOrThrow());
		} catch (IllegalArgumentException e) {
			return DataResult.error(e::getMessage);
		}
	}

	/** 同一套规则的手抛版本（wrap 路径与组件 validate 用）。 */
	public TemperatureRangeValue validateOrThrow() {
		if (min < 0 || max < 0) {
			throw new IllegalArgumentException("temperature: negative temperatures are not accepted! got " + describe());
		}
		if (min >= max) {
			throw new IllegalArgumentException("temperature: min must be < max (PnC rule) — got " + describe());
		}
		return this;
	}

	public String describe() {
		if (isAny()) {
			return "any";
		}
		if (hasMin() && hasMax()) {
			return min + " <= T <= " + max;
		}
		return hasMin() ? "T >= " + min : "T <= " + max;
	}

	@Override
	public String toString() {
		return "temperature(" + describe() + ")";
	}
}
