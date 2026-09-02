package net.minheur.manhunt_helper.mixin;

import com.alphaduck.manhunt.ManHunt;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({ManHunt.class})
public interface ManhuntModAccessor {
    @Invoker("setMode")
    static int accessSetMod(ServerCommandSource source, boolean locator) {
        throw new AssertionError();
    }

    @Invoker("createHunterCompass")
    static ItemStack mkCompass() {
        throw new AssertionError();
    }

    @Accessor("resetRequestedAt")
    static void setResetReqAt(long value) {
        throw new AssertionError();
    }
    @Invoker("confirmReset")
    static int reset(ServerCommandSource source) {
        throw new AssertionError();
    }
}
