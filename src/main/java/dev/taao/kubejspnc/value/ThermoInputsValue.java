package dev.taao.kubejspnc.value;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * {@code pneumaticcraft:thermo_plant} 的 {@code inputs} 镜像
 * （PnC {@code ThermoPlantRecipe.Inputs} 的无损镜像，两个字段都可缺省 —— 但 PnC 在配方级要求至少有一个）。
 *
 * <p>{@code item} 用 {@code Ingredient.CODEC}（不是 NON_EMPTY），所以 {@code "item": []}
 * 是合法的"不消耗物品"写法（PnC 自身 24 条配方里就有）。
 */
public record ThermoInputsValue(Optional<SizedFluidIngredient> fluid, Optional<Ingredient> item) {
	public static final Codec<ThermoInputsValue> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				SizedFluidIngredient.FLAT_CODEC.optionalFieldOf("fluid").forGetter(ThermoInputsValue::fluid),
				Ingredient.CODEC.optionalFieldOf("item").forGetter(ThermoInputsValue::item)
			)
			.apply(instance, ThermoInputsValue::new)
	);

	public static ThermoInputsValue of(SizedFluidIngredient fluid, Ingredient item) {
		return new ThermoInputsValue(Optional.ofNullable(fluid), Optional.ofNullable(item));
	}

	public boolean isEmpty() {
		return fluid.isEmpty() && item.isEmpty();
	}

	@Override
	public String toString() {
		return "thermo_inputs(fluid=" + fluid.map(Object::toString).orElse("-")
			+ ", item=" + item.map(Object::toString).orElse("-") + ")";
	}
}
