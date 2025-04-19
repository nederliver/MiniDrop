package net.nederliver.minidrop; // Adjust package if necessary

import net.minecraft.item.ItemStack;

/**
 * Simple static storage for MiniDrop configuration/state.
 * State stored here will persist as long as the game is running.
 */
public class MiniDropConfig {

    // Item Textures
    public static ItemStack slot1Stack = ItemStack.EMPTY;
    public static ItemStack slot2Stack = ItemStack.EMPTY;
    public static ItemStack slot3Stack = ItemStack.EMPTY;

    // UI State (Now persistent)
    public static boolean isMenuVisible = false;
    public static boolean isSwitchToggled = false; // Optional: Or keep this non-persistent if preferred

}
