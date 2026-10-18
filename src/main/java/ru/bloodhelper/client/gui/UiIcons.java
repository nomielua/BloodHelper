package ru.bloodhelper.client.gui;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/** Shared GUI/HUD symbols with a consistent 24-unit canvas and two-unit stroke. */
public final class UiIcons {
    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath("bloodhelper", "textures/gui/icons_sdf.png");
    private static final int CELL = 64, COLUMNS = 6, ATLAS_WIDTH = 384, ATLAS_HEIGHT = 256;
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("bloodhelper", "pipeline/gui_icons"))
            .withVertexShader(Identifier.fromNamespaceAndPath("bloodhelper", "core/gui_icons"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("bloodhelper", "core/gui_icons"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private UiIcons() {}

    /** Coordinates describe the complete square canvas, including its optical inset. */
    public static void draw(GuiGraphics graphics, String name, int x, int y, int size, int color) {
        if (size <= 0 || color >>> 24 == 0) return;
        int cell = switch (name) {
            case "modules" -> 0;
            case "palette", "appearance" -> 1;
            case "profiles" -> 2;
            case "about", "info" -> 3;
            case "music" -> 4;
            case "clock", "cooldowns" -> 5;
            case "shield", "pvp" -> 6;
            case "search" -> 7;
            case "close" -> 8;
            case "chevron" -> 9;
            case "back" -> 10;
            case "reset" -> 11;
            case "edit" -> 12;
            case "previous" -> 13;
            case "play" -> 14;
            case "pause" -> 15;
            case "next" -> 16;
            case "check" -> 17;
            case "headphones" -> 18;
            case "grid", "hud" -> 19;
            case "heart" -> 20;
            case "volume" -> 21;
            case "external" -> 22;
            case "sun" -> 23;
            default -> 3;
        };
        // TextureManager owns the atlas and reloads it normally. Its .mcmeta enables
        // bilinear sampling; the fragment shader computes coverage at final GUI scale.
        graphics.blit(PIPELINE, ATLAS, x, y, (cell % COLUMNS) * CELL, (cell / COLUMNS) * CELL,
                size, size, CELL, CELL, ATLAS_WIDTH, ATLAS_HEIGHT, color);
    }
}
