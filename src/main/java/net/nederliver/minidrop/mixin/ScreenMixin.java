package net.nederliver.minidrop.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// This Mixin targets the Screen class directly
@Mixin(Screen.class)
public interface ScreenMixin { // Keep as interface or make abstract class

    // --- Define Accessor methods directly within this @Mixin interface ---
    @Accessor("textRenderer")
    TextRenderer getTextRenderer(); // Accessor for textRenderer

    @Accessor("title")
    Text getTitle(); // Accessor for the title Text object

    // No nested ScreenAccessor interface needed
}