package dev.taao.kubejspnc.value;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * {@code pneumaticcraft:thermo_plant} 的 {@code outputs} 镜像（PnC {@code ThermoPlantRecipe.Outputs} 的无损镜像）。
 *
 * <p>两个字段都可缺省（缺省 = 空），但至少要写一个才有意义。
 */
public record ThermoOutputsValue(FluidStack fluid, ItemStack item) {
	public static final Codec<ThermoOutputsValue> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				FluidStack.CODEC.optionalFieldOf("fluid_output", FluidStack.EMPTY).forGetter(ThermoOutputsValue::fluid),
				ItemStack.CODEC.optionalFieldOf("item_output", ItemStack.EMPTY).forGetter(ThermoOutputsValue::item)
			)
			.apply(instance, ThermoOutputsValue::new)
	);

	public static ThermoOutputsValue of(FluidStack fluid, ItemStack item) {
		return new ThermoOutputsValue(fluid == null ? FluidStack.EMPTY : fluid, item == null ? ItemStack.EMPTY : item);
	}

	public boolean isEmpty() {
		return fluid.isEmpty() && item.isEmpty();
	}

	@Override
	public String toString() {
		return "thermo_outputs(fluid=" + (fluid.isEmpty() ? "-" : fluid)
			+ ", item=" + (item.isEmpty() ? "-" : item) + ")";
	}
}
