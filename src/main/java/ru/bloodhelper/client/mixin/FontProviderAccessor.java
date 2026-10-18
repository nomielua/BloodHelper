package ru.bloodhelper.client.mixin;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Shares Minecraft's reload-aware provider without replacing its global font. */
@Mixin(Font.class)
public interface FontProviderAccessor {
    @Accessor("provider") Font.Provider bloodhelper$provider();
}
