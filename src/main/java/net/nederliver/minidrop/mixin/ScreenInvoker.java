package net.nederliver.minidrop.mixin; // Or your chosen package

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Selectable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class) // Target Screen directly
public interface ScreenInvoker {

    @Invoker("addDrawableChild") // Target the protected method in Screen
    <T extends Element & Drawable & Selectable> T callAddDrawableChild(T drawableElement);

}