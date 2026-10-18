package ru.bloodhelper.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import ru.bloodhelper.client.mixin.FontProviderAccessor;

/** Native world-space lyrics only. Menu, HUD and fields use CustomFont exclusively. */
public final class UiFonts {
    private static final FontDescription WORLD_FACE = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath("bloodhelper", "ui"));
    private static Font world, owner;
    private UiFonts() {}

    public static Font world() {
        Font base = Minecraft.getInstance().font;
        if (base != owner) {
            Font.Provider provider = ((FontProviderAccessor)base).bloodhelper$provider();
            world = new Font(new Font.Provider() {
                @Override public GlyphSource glyphs(FontDescription requested) {
                    return provider.glyphs(FontDescription.DEFAULT.equals(requested) ? WORLD_FACE : requested);
                }
                @Override public EffectGlyph effect() { return provider.effect(); }
            });
            owner = base;
        }
        return world;
    }
}
