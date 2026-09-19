package dev.taao.kubejspnc.value;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * {@code pneumaticcraft:amadron} 的 {@code input}/{@code output} 镜像：{@code {"resource": <物 or 流体>}}。
 *
 * <p>PnC 侧为 {@code AmadronTradeResource(Either<ItemStack, FluidStack>)}
 * （codec 实读：{@code Codec.either(ItemStack.CODEC, FluidStack.CODEC).fieldOf("resource")}）。
 * 解码顺序与 PnC 一致：先试物品（{@code count}），失败再试流体（{@code amount}）。
 */
public record AmadronResourceValue(Either<ItemStack, FluidStack> resource) {
	public static final Codec<AmadronResourceValue> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Codec.either(ItemStack.CODEC, FluidStack.CODEC).fieldOf("resource").forGetter(AmadronResourceValue::resource)
			)
			.apply(instance, AmadronResourceValue::new)
	);

	public static AmadronResourceValue of(ItemStack stack) {
		return new AmadronResourceValue(Either.left(stack));
	}

	public static AmadronResourceValue of(FluidStack stack) {
		return new AmadronResourceValue(Either.right(stack));
	}

	public boolean isEmpty() {
		return resource.map(ItemStack::isEmpty, FluidStack::isEmpty);
	}

	public String describe() {
		return resource.map(
			stack -> stack.getCount() + "x " + stack.getItem(),
			stack -> stack.getAmount() + "mB " + stack.getFluid()
		);
	}

	@Override
	public String toString() {
		return "amadron_resource(" + describe() + ")";
	}
}
