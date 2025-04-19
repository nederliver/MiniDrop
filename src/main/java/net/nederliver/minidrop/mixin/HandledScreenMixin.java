package net.nederliver.minidrop.mixin; // Make sure this package matches your project structure

// Minecraft Imports
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;       // For Shulker Boxes
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen; // Often used for Barrels (and others, see note below)
import net.minecraft.client.gui.screen.ingame.HopperScreen;           // Needed for exclusion check
// Add these imports for screen handlers
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler; // Base type
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.text.Text;
// If your tooltip method requires a list:
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
// Mixin Imports
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Graphics Imports
import com.mojang.blaze3d.systems.RenderSystem;

// Local Imports
import net.nederliver.minidrop.MiniDropConfig; // Import the config class


@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;
    @Shadow protected int backgroundHeight;
    @Shadow public abstract net.minecraft.screen.ScreenHandler getScreenHandler();
    @Shadow protected Slot focusedSlot;

    // Helper method to check mouse hover over a slot (16x16 interaction area)
    private boolean isMouseOverSlot(Slot slot, double mouseX, double mouseY) {
        int slotScreenX = this.x + slot.x;
        int slotScreenY = this.y + slot.y;
        boolean isOver = mouseX >= slotScreenX && mouseX < slotScreenX + 16 &&
                mouseY >= slotScreenY && mouseY < slotScreenY + 16;
        if (isOver) {
            // System.out.println("[MiniDrop Debug] isMouseOverSlot=true for Slot ID: " + slot.id + " Index: " + slot.getIndex() + " at [" + slotScreenX + "," + slotScreenY + "] vs Mouse [" + mouseX + "," + mouseY + "]");
        }
        return isOver;
    }


    // Texture Identifiers
    private static final Identifier MINIDROP_PANEL_TEXTURE = Identifier.of("minidrop", "textures/gui/minidrop_panel.png");
    private static final Identifier MINIDROP_SWITCH_TEXTURE = Identifier.of("minidrop", "textures/gui/switch.png");

    @Mixin(HandledScreen.class)
    interface HandledScreenLocalAccessor {
        @Accessor("titleX") int getTitleX();
        @Accessor("titleY") int getTitleY();
        @Accessor("x") int getX();
        @Accessor("y") int getY();
    }

    // --- State Variables (Mixin Instance Specific) ---
    private int miniDrop_titleRelX;
    private int miniDrop_titleRelY;
    private int miniDrop_titleWidth;
    private int miniDrop_titleHeight;
    private boolean miniDrop_shouldApply = false;

    // --- Constants ---
    private static final int SLOT_SIZE = 18; // Visual size
    private static final int ITEM_SIZE = 16; // Rendered item size
    private static final int ITEM_RENDER_OFFSET = 1; // Default offset for 16x16 item in 18x18 slot
    private static final int PADDING = 4; // General padding
    private static final int SWITCH_VERTICAL_PADDING = 4; // Padding between panel and switch
    private static final int SWITCH_ICON_WIDTH = 32;
    private static final int SWITCH_ICON_HEIGHT = 32;
    private static final int SWITCH_TEXTURE_WIDTH = 32;
    private static final int SWITCH_TEXTURE_HEIGHT = 64;
    private static final int PANEL_TEXTURE_WIDTH = 32;
    private static final int PANEL_TEXTURE_HEIGHT = 68;
    private static final int SLOT_AREA_X_OFFSET = 7; // Centering 18px in 32px panel
    private static final int SLOT_AREA_Y_OFFSET = 5; // Centering 58px content (3*18 + 2*2) in 68px panel
    private static final int SLOT_PADDING_VERTICAL = 2;


    // Log persistence state when screen starts initializing
    @Inject(method = "init", at = @At("HEAD"))
    private void logOnInitStart(CallbackInfo ci) {
        // System.out.println("[MiniDrop Debug] Screen Init Start. Class: " + this.getClass().getSimpleName());
        try {
            String s1 = MiniDropConfig.slot1Stack.isEmpty() ? "Empty" : MiniDropConfig.slot1Stack.getItem().toString();
            String s2 = MiniDropConfig.slot2Stack.isEmpty() ? "Empty" : MiniDropConfig.slot2Stack.getItem().toString();
            String s3 = MiniDropConfig.slot3Stack.isEmpty() ? "Empty" : MiniDropConfig.slot3Stack.getItem().toString();
            // System.out.println("[MiniDrop Debug] Persistence Check - Slot1: " + s1 + ", Slot2: " + s2 + ", Slot3: " + s3 +
            //                   ", MenuVisible: " + MiniDropConfig.isMenuVisible + ", SwitchToggled: " + MiniDropConfig.isSwitchToggled);
        } catch (Throwable t) {
            System.err.println("[MiniDrop Error] Failed to access MiniDropConfig on init start: " + t.getMessage());
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void initMiniDrop(CallbackInfo ci) {
        HandledScreen<?> handledScreen = (HandledScreen<?>) (Object) this;
        Screen screen = (Screen)(Object) this;
        HandledScreenLocalAccessor localAccessor = (HandledScreenLocalAccessor) this;

        // Reset apply flag for this screen instance
        this.miniDrop_shouldApply = false;

        // --- Check if the current screen/handler is one of the allowed types ---
        boolean isAllowed = false;
        ScreenHandler handler = handledScreen.getScreenHandler(); // Get the handler

        if (handledScreen instanceof ShulkerBoxScreen) {
            // Shulker boxes have a specific screen, check it directly
            isAllowed = true;
        } else if (handledScreen instanceof GenericContainerScreen) {
            // Check if the handler is the correct *consolidated* type
            if (handler instanceof GenericContainerScreenHandler) {
                // Cast to the specific handler type to access its methods
                GenericContainerScreenHandler genericHandler = (GenericContainerScreenHandler) handler;
                int rows = genericHandler.getRows(); // Get the number of rows

                // Check if the number of rows corresponds to chests/barrels (3 rows) or double chests (6 rows)
                if (rows == 3 || rows == 6) {
                    // This covers GENERIC_9X3 (Chests, Trapped Chests, Barrels)
                    // and GENERIC_9X6 (Double Chests, Trapped Double Chests)
                    isAllowed = true;
                }
            }
            // Note: This still implicitly excludes Dispensers (DispenserScreenHandler)
            // and Hoppers (HopperScreenHandler) because they won't pass the
            // `handler instanceof GenericContainerScreenHandler` check.
        }

        // If the screen type/handler combination is allowed, set the flag and calculate title properties
        if (isAllowed) {
            this.miniDrop_shouldApply = true;
            System.out.println("[MiniDrop Debug] Applying MiniDrop panel to screen: " + handledScreen.getClass().getSimpleName() + " with handler: " + handler.getClass().getSimpleName() + " (Rows: " + ((handler instanceof GenericContainerScreenHandler) ? ((GenericContainerScreenHandler)handler).getRows() : "N/A") + ")"); // Optional: Updated debug log

            // Calculate title position/size ONLY if we should apply the panel
            try {
                this.miniDrop_titleRelX = localAccessor.getTitleX();
                this.miniDrop_titleRelY = localAccessor.getTitleY();
                TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
                Text titleText = screen.getTitle();
                if (textRenderer != null && titleText != null) {
                    this.miniDrop_titleWidth = textRenderer.getWidth(titleText);
                    this.miniDrop_titleHeight = textRenderer.fontHeight;
                } else {
                    this.miniDrop_titleWidth = 60;
                    this.miniDrop_titleHeight = 9;
                }
            } catch (Exception e) {
                System.err.println("[MiniDrop Error] Failed during init title setup: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // Optional: Update debug log for disallowed screens too
            System.out.println("[MiniDrop Debug] Not applying MiniDrop panel to screen: " + handledScreen.getClass().getSimpleName() + " with handler: " + handler.getClass().getSimpleName());
        }
    }

    // --- NEW: Inject into removed() method to reset state on screen close ---
    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        // Only reset if the feature was applied to this screen instance
        if (this.miniDrop_shouldApply) {
            System.out.println("[MiniDrop Debug] Screen removed. Resetting menu visibility and switch toggle state.");
            MiniDropConfig.isMenuVisible = false;
            MiniDropConfig.isSwitchToggled = false;
            // Item slots in MiniDropConfig remain persistent
        }
    }


    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClickMiniDrop(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!this.miniDrop_shouldApply) return;

        int screenXBase = this.x;
        int screenYBase = this.y;

        // --- Handle Title Click ---
        // Restore title click calculation:
        int absoluteTitleX = screenXBase + this.miniDrop_titleRelX;
        int absoluteTitleY = screenYBase + this.miniDrop_titleRelY;
        boolean clickedTitle = mouseX >= absoluteTitleX && mouseX < absoluteTitleX + this.miniDrop_titleWidth &&
                mouseY >= absoluteTitleY && mouseY < absoluteTitleY + this.miniDrop_titleHeight;

        if (button == 0 && clickedTitle) {
            MiniDropConfig.isMenuVisible = !MiniDropConfig.isMenuVisible;
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 0.8F));
            cir.setReturnValue(true); cir.cancel(); return;
        }


        if (MiniDropConfig.isMenuVisible) {
            // Calculate positions (restore these calculations)
            int bgWidth = this.backgroundWidth; if (bgWidth <= 0) bgWidth = 176;
            int menuPanelX_absolute = screenXBase + bgWidth + PADDING - 1;
            int menuPanelY_absolute = screenYBase + PADDING - 4;
            int switchX_absolute = menuPanelX_absolute;
            int switchY_absolute = menuPanelY_absolute + PANEL_TEXTURE_HEIGHT + SWITCH_VERTICAL_PADDING;
            int slotVisualBaseX_abs = menuPanelX_absolute + SLOT_AREA_X_OFFSET;
            int slotVisualBaseY_abs = menuPanelY_absolute + SLOT_AREA_Y_OFFSET;
            int slot1VisualY_abs = slotVisualBaseY_abs;
            int slot2VisualY_abs = slot1VisualY_abs + SLOT_SIZE + SLOT_PADDING_VERTICAL;
            int slot3VisualY_abs = slot2VisualY_abs + SLOT_SIZE + SLOT_PADDING_VERTICAL;


            // Restore switch click calculation:
            boolean clickedSwitch = mouseX >= switchX_absolute && mouseX < switchX_absolute + SWITCH_ICON_WIDTH &&
                    mouseY >= switchY_absolute && mouseY < switchY_absolute + SWITCH_ICON_HEIGHT;

            if (button == 0 && clickedSwitch) {
                MiniDropConfig.isSwitchToggled = !MiniDropConfig.isSwitchToggled;
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                cir.setReturnValue(true); cir.cancel(); return;
            }


            // --- Handle MiniDrop Slot Clicks (LEFT CLICK - ONLY CLEARING SOUND HERE) ---
            // *** RESTORED SLOT CALCULATIONS HERE ***
            boolean clickedSlot1 = mouseX >= slotVisualBaseX_abs && mouseX < slotVisualBaseX_abs + SLOT_SIZE && mouseY >= slot1VisualY_abs && mouseY < slot1VisualY_abs + SLOT_SIZE;
            boolean clickedSlot2 = mouseX >= slotVisualBaseX_abs && mouseX < slotVisualBaseX_abs + SLOT_SIZE && mouseY >= slot2VisualY_abs && mouseY < slot2VisualY_abs + SLOT_SIZE;
            boolean clickedSlot3 = mouseX >= slotVisualBaseX_abs && mouseX < slotVisualBaseX_abs + SLOT_SIZE && mouseY >= slot3VisualY_abs && mouseY < slot3VisualY_abs + SLOT_SIZE;

            if (button == 0) { // Left Click
                ItemStack cursorStack = this.getScreenHandler().getCursorStack();

                // Only handle CLEARING the slot on the initial click (press)
                if (cursorStack.isEmpty()) { // Only act if cursor is empty
                    boolean cleared = false;
                    if (clickedSlot1 && !MiniDropConfig.slot1Stack.isEmpty()) {
                        MiniDropConfig.slot1Stack = ItemStack.EMPTY; cleared = true;
                    } else if (clickedSlot2 && !MiniDropConfig.slot2Stack.isEmpty()) {
                        MiniDropConfig.slot2Stack = ItemStack.EMPTY; cleared = true;
                    } else if (clickedSlot3 && !MiniDropConfig.slot3Stack.isEmpty()) {
                        MiniDropConfig.slot3Stack = ItemStack.EMPTY; cleared = true;
                    }

                    if (cleared) {
                        // Play REMOVE sound ONLY when clearing
                        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, 0.8F, 1.0f));
                        cir.setReturnValue(true); cir.cancel(); return; // Consume the click
                    }
                }
                // If we clicked a slot (even with an item on cursor), consume the event
                // to prevent vanilla potentially interacting weirdly.
                if (clickedSlot1 || clickedSlot2 || clickedSlot3) {
                    cir.setReturnValue(true); cir.cancel(); return;
                }
            } // End Left Click Slot Check


            // --- Handle Panel Background Click ---
            // Restore panel area calculation:
            boolean clickedPanelArea = mouseX >= menuPanelX_absolute && mouseX < menuPanelX_absolute + PANEL_TEXTURE_WIDTH &&
                    mouseY >= menuPanelY_absolute && mouseY < menuPanelY_absolute + PANEL_TEXTURE_HEIGHT;
            if (clickedPanelArea && !(clickedSlot1 || clickedSlot2 || clickedSlot3 || clickedSwitch)) {
                cir.setReturnValue(true); cir.cancel(); return; // Consume click on background
            }

            // --- Handle Right-Click on Inventory Slots (Keep As Is from your original code) ---
            if (button == 1) { // Right Mouse Button
                // Make sure your original right-click logic is here
                boolean consumed = false; Slot hoveredSlot = null;
                // Use your isMouseOverSlot helper method here
                for (Slot slot : this.getScreenHandler().slots) {
                    if (slot != null) {
                        boolean isOver = isMouseOverSlot(slot, mouseX, mouseY); if (isOver) hoveredSlot = slot; // Use helper
                        if (slot.hasStack() && isOver) {
                            ItemStack clickedStack = slot.getStack();
                            // Your original logic for filling slots 1, 2, 3
                            if (MiniDropConfig.slot1Stack.isEmpty()) { MiniDropConfig.slot1Stack = clickedStack.copy(); MiniDropConfig.slot1Stack.setCount(1); MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, 0.7F, 1.0f)); consumed = true; break; }
                            else if (MiniDropConfig.slot2Stack.isEmpty()) { MiniDropConfig.slot2Stack = clickedStack.copy(); MiniDropConfig.slot2Stack.setCount(1); MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, 0.7F, 1.0f)); consumed = true; break; }
                            else if (MiniDropConfig.slot3Stack.isEmpty()) { MiniDropConfig.slot3Stack = clickedStack.copy(); MiniDropConfig.slot3Stack.setCount(1); MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, 0.7F, 1.0f)); consumed = true; break; }
                            else { MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_DISPENSER_FAIL, 0.7F, 1.2f)); consumed = true; break; }
                        }
                    }
                }
                if (consumed) { cir.setReturnValue(true); cir.cancel(); return; }
                // Your original debug log if needed
                // else { if (hoveredSlot != null && !hoveredSlot.hasStack()) { System.out.println("[MiniDrop Debug] Right-click not consumed (hovered slot " + hoveredSlot.id + " was empty?)."); } }
            } // End Right-Click Handling

        } // End Menu Visible Check
    }


    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleaseMiniDrop(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!this.miniDrop_shouldApply || !MiniDropConfig.isMenuVisible || button != 0) {
            return; // Only handle left button releases when menu is visible
        }

        ItemStack cursorStack = this.getScreenHandler().getCursorStack();

        // Calculate positions (restore these calculations)
        int screenXBase = this.x;
        int screenYBase = this.y;
        int bgWidth = this.backgroundWidth; if (bgWidth <= 0) bgWidth = 176;
        int menuPanelX_absolute = screenXBase + bgWidth + PADDING - 1;
        int menuPanelY_absolute = screenYBase + PADDING - 4;
        int switchX_absolute = menuPanelX_absolute;
        int switchY_absolute = menuPanelY_absolute + PANEL_TEXTURE_HEIGHT + SWITCH_VERTICAL_PADDING;
        int slotVisualBaseX_abs = menuPanelX_absolute + SLOT_AREA_X_OFFSET;
        int slotVisualBaseY_abs = menuPanelY_absolute + SLOT_AREA_Y_OFFSET;
        int slot1VisualY_abs = slotVisualBaseY_abs;
        int slot2VisualY_abs = slot1VisualY_abs + SLOT_SIZE + SLOT_PADDING_VERTICAL;
        int slot3VisualY_abs = slot2VisualY_abs + SLOT_SIZE + SLOT_PADDING_VERTICAL;


        // --- Check Release over Switch ---
        // Restore switch release calculation:
        boolean releasedOnSwitch = mouseX >= switchX_absolute && mouseX < switchX_absolute + SWITCH_ICON_WIDTH &&
                mouseY >= switchY_absolute && mouseY < switchY_absolute + SWITCH_ICON_HEIGHT;
        if (releasedOnSwitch) { cir.setReturnValue(true); cir.cancel(); return; }


        // --- Check Release over MiniDrop Slots (SET ITEM & PLAY ADD SOUND) ---
        if (!cursorStack.isEmpty()) { // Only act if releasing *with* an item
            // *** RESTORED SLOT RELEASE CALCULATIONS HERE ***
            boolean releasedOnSlot1 = mouseX >= slotVisualBaseX_abs && mouseX < slotVisualBaseX_abs + SLOT_SIZE && mouseY >= slot1VisualY_abs && mouseY < slot1VisualY_abs + SLOT_SIZE;
            boolean releasedOnSlot2 = mouseX >= slotVisualBaseX_abs && mouseX < slotVisualBaseX_abs + SLOT_SIZE && mouseY >= slot2VisualY_abs && mouseY < slot2VisualY_abs + SLOT_SIZE;
            boolean releasedOnSlot3 = mouseX >= slotVisualBaseX_abs && mouseX < slotVisualBaseX_abs + SLOT_SIZE && mouseY >= slot3VisualY_abs && mouseY < slot3VisualY_abs + SLOT_SIZE;

            boolean handled = false;
            if (releasedOnSlot1) {
                MiniDropConfig.slot1Stack = cursorStack.copy(); MiniDropConfig.slot1Stack.setCount(1);
                handled = true;
            } else if (releasedOnSlot2) {
                MiniDropConfig.slot2Stack = cursorStack.copy(); MiniDropConfig.slot2Stack.setCount(1);
                handled = true;
            } else if (releasedOnSlot3) {
                MiniDropConfig.slot3Stack = cursorStack.copy(); MiniDropConfig.slot3Stack.setCount(1);
                handled = true;
            }

            if (handled) {
                // Play ADD sound here, associated with the release action completing the placement
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, 0.8F, 1.0f));
                cir.setReturnValue(true); cir.cancel(); return; // Consume the release event
            }
        }

        // --- Check Release over Panel Background ---
        // Restore panel background release calculation:
        boolean releasedOnPanelBg = mouseX >= menuPanelX_absolute && mouseX < menuPanelX_absolute + PANEL_TEXTURE_WIDTH &&
                mouseY >= menuPanelY_absolute && mouseY < menuPanelY_absolute + PANEL_TEXTURE_HEIGHT;
        if (releasedOnPanelBg && !(releasedOnSwitch)) { // Don't consume if it was the switch release
            cir.setReturnValue(true); cir.cancel(); return;
        }

        // If the release wasn't over any interactive MiniDrop element, let vanilla handle it.
    }
    @Inject(method = "render", at = @At("TAIL"))
    private void renderMiniDropAndTooltip(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) { // Renamed handler method for clarity

        // --- 1. Render the MiniDrop Panel (Existing Logic) ---
        // Keep your existing panel rendering logic here
        if (this.miniDrop_shouldApply && MiniDropConfig.isMenuVisible) {
            try {
                int screenXBase = this.x;
                int screenYBase = this.y;
                int bgWidth = this.backgroundWidth; if (bgWidth <= 0) bgWidth = 176;

                // Calculate positions for panel elements
                int menuPanelX_absolute = screenXBase + bgWidth + PADDING - 1;
                int menuPanelY_absolute = screenYBase + PADDING - 4;
                int switchX_absolute = menuPanelX_absolute;
                int switchY_absolute = menuPanelY_absolute + PANEL_TEXTURE_HEIGHT + SWITCH_VERTICAL_PADDING;
                int slotVisualBaseX_abs = menuPanelX_absolute + SLOT_AREA_X_OFFSET;
                int slotVisualBaseY_abs = menuPanelY_absolute + SLOT_AREA_Y_OFFSET;
                int slot1VisualY_abs = slotVisualBaseY_abs;
                int slot2VisualY_abs = slot1VisualY_abs + SLOT_SIZE + SLOT_PADDING_VERTICAL;
                int slot3VisualY_abs = slot2VisualY_abs + SLOT_SIZE + SLOT_PADDING_VERTICAL;
                int itemRenderBaseX = slotVisualBaseX_abs + ITEM_RENDER_OFFSET;

                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

                // Draw Panel Background Texture
                context.drawTexture(RenderLayer::getGuiTextured, MINIDROP_PANEL_TEXTURE, menuPanelX_absolute, menuPanelY_absolute, 0, 0, PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT, PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT);

                // Render Items in Slots
                if (!MiniDropConfig.slot1Stack.isEmpty()) {
                    context.drawItem(MiniDropConfig.slot1Stack, itemRenderBaseX, slot1VisualY_abs + ITEM_RENDER_OFFSET + 2);
                }
                if (!MiniDropConfig.slot2Stack.isEmpty()) {
                    context.drawItem(MiniDropConfig.slot2Stack, itemRenderBaseX, slot2VisualY_abs + ITEM_RENDER_OFFSET);
                }
                if (!MiniDropConfig.slot3Stack.isEmpty()) {
                    context.drawItem(MiniDropConfig.slot3Stack, itemRenderBaseX, slot3VisualY_abs - 1);
                }

                // Draw Switch Texture
                float switchV = MiniDropConfig.isSwitchToggled ? (float) SWITCH_ICON_HEIGHT : 0.0f;
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                context.drawTexture(
                        RenderLayer::getGuiTextured, MINIDROP_SWITCH_TEXTURE,
                        switchX_absolute, switchY_absolute,
                        0.0f, switchV,
                        SWITCH_ICON_WIDTH, SWITCH_ICON_HEIGHT,
                        SWITCH_TEXTURE_WIDTH, SWITCH_TEXTURE_HEIGHT
                );

            } catch (Exception e) {
                System.err.println("[MiniDrop Error] Failed during panel render: " + e.getMessage()); // Clarified error source
                e.printStackTrace();
            }
        } // End of panel rendering block


        // --- 2. Render the Title Tooltip (New Logic) ---
        if (this.miniDrop_shouldApply) { // Check if the feature is active for this screen
            // Calculate absolute title coordinates using stored relative values and current screen position
            int absoluteTitleX = this.x + this.miniDrop_titleRelX;
            int absoluteTitleY = this.y + this.miniDrop_titleRelY;

            // Check if mouse is hovering over the title area calculated during init
            // Ensure title width/height were calculated correctly in init
            boolean isHoveringTitle = this.miniDrop_titleWidth > 0 && // Avoid check if title wasn't processed
                    mouseX >= absoluteTitleX && mouseX < absoluteTitleX + this.miniDrop_titleWidth &&
                    mouseY >= absoluteTitleY && mouseY < absoluteTitleY + this.miniDrop_titleHeight;

            if (isHoveringTitle) {
                // Create the tooltip text. Use Text.translatable for localization support if desired!
                Text tooltipText = Text.literal("Click to open Mini Item Drop menu");

                // Render the tooltip at the current mouse position
                // 'this.textRenderer' is inherited from Screen and should be available
                // The method might expect a List<Text> in some versions/contexts.
                context.drawTooltip(this.textRenderer, tooltipText, mouseX, mouseY);

                // If the above doesn't work, try wrapping the text in a list:
                // context.drawTooltip(this.textRenderer, List.of(tooltipText), mouseX, mouseY);
            }
        }
    } // End of render method injection

    @Inject(method = "render", at = @At("TAIL"))
    private void renderMiniDrop(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!this.miniDrop_shouldApply || !MiniDropConfig.isMenuVisible) {
            return;
        }

        int screenXBase = this.x;
        int screenYBase = this.y;

        try {
            int bgWidth = this.backgroundWidth; if (bgWidth <= 0) bgWidth = 176;

            // Calculate positions
            int menuPanelX_absolute = screenXBase + bgWidth + PADDING - 1;
            int menuPanelY_absolute = screenYBase + PADDING - 4;
            int switchX_absolute = menuPanelX_absolute;
            int switchY_absolute = menuPanelY_absolute + PANEL_TEXTURE_HEIGHT + SWITCH_VERTICAL_PADDING;
            int slotVisualBaseX_abs = menuPanelX_absolute + SLOT_AREA_X_OFFSET;
            int slotVisualBaseY_abs = menuPanelY_absolute + SLOT_AREA_Y_OFFSET;
            int slot1VisualY_abs = slotVisualBaseY_abs;
            int slot2VisualY_abs = slot1VisualY_abs + SLOT_SIZE + SLOT_PADDING_VERTICAL;
            int slot3VisualY_abs = slot2VisualY_abs + SLOT_SIZE + SLOT_PADDING_VERTICAL;

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            // 1. Draw Panel Background Texture
            context.drawTexture(RenderLayer::getGuiTextured, MINIDROP_PANEL_TEXTURE, menuPanelX_absolute, menuPanelY_absolute, 0, 0, PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT, PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT);

            // --- Render Items in Slots (with final final fine-tuned offsets) ---
            int itemRenderBaseX = slotVisualBaseX_abs + ITEM_RENDER_OFFSET; // Base X is same for all

            if (!MiniDropConfig.slot1Stack.isEmpty()) {
                // Slot 1 is correct (+3 offset relative to visual Y)
                context.drawItem(MiniDropConfig.slot1Stack, itemRenderBaseX, slot1VisualY_abs + ITEM_RENDER_OFFSET + 2);
            }
            if (!MiniDropConfig.slot2Stack.isEmpty()) {
                // Slot 2 is correct (+1 offset relative to visual Y)
                context.drawItem(MiniDropConfig.slot2Stack, itemRenderBaseX, slot2VisualY_abs + ITEM_RENDER_OFFSET);
            }
            if (!MiniDropConfig.slot3Stack.isEmpty()) {
                // --- FIX: Slot 3 needs -1 offset relative to visual Y ---
                context.drawItem(MiniDropConfig.slot3Stack, itemRenderBaseX, slot3VisualY_abs - 1);
            }

            // 3. Draw Switch Texture
            float switchV = MiniDropConfig.isSwitchToggled ? (float) SWITCH_ICON_HEIGHT : 0.0f;
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            context.drawTexture(
                    RenderLayer::getGuiTextured, MINIDROP_SWITCH_TEXTURE,
                    switchX_absolute, switchY_absolute,
                    0.0f, switchV,
                    SWITCH_ICON_WIDTH, SWITCH_ICON_HEIGHT,
                    SWITCH_TEXTURE_WIDTH, SWITCH_TEXTURE_HEIGHT
            );

        } catch (Exception e) {
            System.err.println("[MiniDrop Error] Failed during render: " + e.getMessage());
            e.printStackTrace();
        }
    }
}