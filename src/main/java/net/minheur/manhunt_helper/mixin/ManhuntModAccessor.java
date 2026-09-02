package net.minheur.manhunt_helper.mixin;

import com.alphaduck.manhunt.ManHunt;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({ManHunt.class})
public interface ManhuntModAccessor {
    @Invoker("setMode")
    int accessSetMod(ServerCommandSource source, boolean locator);

    @Invoker("createHunterCompass")
    ItemStack mkCompass();
}
