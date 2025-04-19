package net.nederliver.minidrop.mixin; // Make sure this package matches your project structure

// Minecraft Imports
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.Screen;
// No longer need widget imports here
// import net.minecraft.client.gui.widget.ClickableWidget;
// import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
// import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundEvents;

// Mixin Imports
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
// No longer need Invoker import
// import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Graphics Imports
import com.mojang.blaze3d.systems.RenderSystem;


@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    // Shadow fields
    @Shadow protected int y;
    @Shadow protected int x;

    // Texture Identifiers
    private static final Identifier MINIDROP_PANEL_TEXTURE = Identifier.of("minidrop", "textures/gui/minidrop_panel.png");
    private static final Identifier MINIDROP_SWITCH_TEXTURE = Identifier.of("minidrop", "textures/gui/switch.png");

    // Accessors for HandledScreen
    @Mixin(HandledScreen.class)
    interface HandledScreenLocalAccessor {
        @Accessor("titleX") int getTitleX();
        @Accessor("titleY") int getTitleY();
        @Accessor("backgroundWidth") int getBackgroundWidth();
        @Accessor("x") int getX();
        @Accessor("y") int getY();
    }

    // --- State Variables ---
    private int miniDrop_titleRelX;
    private int miniDrop_titleRelY;
    private int miniDrop_titleWidth;
    private int miniDrop_titleHeight;
    private boolean miniDrop_isMenuVisible = false;
    private boolean miniDrop_shouldApply = false;
    private boolean miniDrop_isSwitchToggled = false;

    // --- REMOVED Widget Reference ---
    // private ClickableWidget miniDrop_switchClickArea;

    // --- Constants ---
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 4;
    private static final int MENU_WIDTH = SLOT_SIZE + PADDING * 2; // Currently unused
    private static final int SWITCH_ICON_WIDTH = 32;
    private static final int SWITCH_ICON_HEIGHT = 32;
    private static final int SWITCH_TEXTURE_WIDTH = 32;
    private static final int SWITCH_TEXTURE_HEIGHT = 64;


    @Inject(method = "init", at = @At("TAIL"))
    private void initMiniDrop(CallbackInfo ci) {
        HandledScreen<?> handledScreen = (HandledScreen<?>) (Object) this;
        Screen screen = (Screen)(Object) this;
        HandledScreenLocalAccessor localAccessor = (HandledScreenLocalAccessor) this;

        // Reset state
        this.miniDrop_shouldApply = false;
        this.miniDrop_isMenuVisible = false; // Reset to false initially
        this.miniDrop_isSwitchToggled = false;
        // No widget to initialize

        // Screen Exclusion Check
        if (handledScreen instanceof CreativeInventoryScreen || handledScreen instanceof InventoryScreen) {
            System.out.println("[MiniDrop Debug] Screen excluded: " + handledScreen.getClass().getSimpleName());
            return;
        }

        // Proceed if not excluded
        this.miniDrop_shouldApply = true;
        System.out.println("[MiniDrop Debug] Screen included: " + handledScreen.getClass().getSimpleName());

        try {
            // Calculate title position/size
            this.miniDrop_titleRelX = localAccessor.getTitleX();
            this.miniDrop_titleRelY = localAccessor.getTitleY();
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            Text title = screen.getTitle();
            if (textRenderer != null && title != null) {
                this.miniDrop_titleWidth = textRenderer.getWidth(title);
                this.miniDrop_titleHeight = textRenderer.fontHeight;
                System.out.println("[MiniDrop Debug] Title Coords/Size OK.");
            } else {
                System.err.println("[MiniDrop Warning] Could not get textRenderer or title. Using fallback size.");
                this.miniDrop_titleWidth = 60; this.miniDrop_titleHeight = 9;
            }
            System.out.println("[MiniDrop Debug] Title Coords relative to background: x=" + this.miniDrop_titleRelX + ", y=" + this.miniDrop_titleRelY + ", Size: w=" + this.miniDrop_titleWidth + ", h=" + this.miniDrop_titleHeight);

            // No widget creation needed here anymore

        } catch (Exception e) {
            System.err.println("[MiniDrop Error] Failed during init setup: " + e.getMessage());
            e.printStackTrace();
            this.miniDrop_shouldApply = false; // Disable feature if setup fails
        }
    }

    /**
     * Injected before the screen handles mouse clicks.
     * Checks for clicks on the title area OR the switch button area.
     * Consumes the click if handled.
     */
    // --- UNCOMMENT this method ---
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClickMiniDrop(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!this.miniDrop_shouldApply) { return; } // Check if feature should run

        HandledScreenLocalAccessor localAccessor = (HandledScreenLocalAccessor) this;
        int screenXBase = localAccessor.getX(); // Top-left X of the handled screen background
        int screenYBase = localAccessor.getY(); // Top-left Y of the handled screen background

        // --- Handle LEFT Click ---
        if (button == 0) {
            // 1. Check Title Click
            int absoluteTitleX = screenXBase + this.miniDrop_titleRelX;
            int absoluteTitleY = screenYBase + this.miniDrop_titleRelY;
            boolean clickedTitle = mouseX >= absoluteTitleX && mouseX < absoluteTitleX + this.miniDrop_titleWidth &&
                    mouseY >= absoluteTitleY && mouseY < absoluteTitleY + this.miniDrop_titleHeight;

            if (clickedTitle) {
                this.miniDrop_isMenuVisible = !this.miniDrop_isMenuVisible;
                System.out.println("[MiniDrop Debug] Toggled Menu Visibility: " + this.miniDrop_isMenuVisible);
                cir.setReturnValue(true); // Consume click
                cir.cancel();
                return; // Handled
            }

            // --- Interactions within the panel area (ONLY if menu is visible) ---
            if (this.miniDrop_isMenuVisible) {
                // Calculate panel and switch positions dynamically
                int bgWidth = localAccessor.getBackgroundWidth(); if (bgWidth <= 0) bgWidth = 176;
                int menuX_absolute = screenXBase + bgWidth + PADDING;
                int menuY_absolute = screenYBase + PADDING;

                // Define Panel Area (based on where MINIDROP_PANEL_TEXTURE is drawn)
                int panelX = menuX_absolute - 1;
                int panelY = menuY_absolute - 4;
                int panelW = 32; // Width of panel texture
                int panelH = 68; // Height of panel texture

                // Define Switch Area (within the panel area)
                int switchX_absolute = menuX_absolute - 1; // Same X as panel
                int switchY_absolute = menuY_absolute + 68; // Below panel

                // 2. Check Switch Click FIRST (it's on top visually)
                boolean clickedSwitch = mouseX >= switchX_absolute && mouseX < switchX_absolute + SWITCH_ICON_WIDTH &&
                        mouseY >= switchY_absolute && mouseY < switchY_absolute + SWITCH_ICON_HEIGHT;

                if (clickedSwitch) {
                    // Toggle the state
                    this.miniDrop_isSwitchToggled = !this.miniDrop_isSwitchToggled;
                    System.out.println("[MiniDrop Debug] Toggled Switch State via Manual Click: " + this.miniDrop_isSwitchToggled);
                    MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    cir.setReturnValue(true); // Consume click
                    cir.cancel();
                    return; // Handled
                }

                // --- 3. Check Panel Background Click (if switch wasn't clicked) ---
                boolean clickedPanel = mouseX >= panelX && mouseX < panelX + panelW &&
                        mouseY >= panelY && mouseY < panelY + panelH;

                if (clickedPanel) {
                    // Click was on the panel background area (and not the switch)
                    System.out.println("[MiniDrop Debug] Consuming mouseClicked event over panel background.");
                    // Consume the click to prevent item drop, but do nothing else
                    cir.setReturnValue(true);
                    cir.cancel();
                    return; // Handled
                }
            }
        }
        // If not left click, or click wasn't on title/switch/panel (when visible), let vanilla handle it
    }



    @Inject(method = "render", at = @At("TAIL"))
    private void renderMiniDrop(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!this.miniDrop_shouldApply) { return; }

        // --- REMOVED widget visibility update ---

        if (!this.miniDrop_isMenuVisible) { return; } // Don't render menu if hidden

        HandledScreenLocalAccessor localAccessor = (HandledScreenLocalAccessor) this;
        int screenX = localAccessor.getX(); int screenY = localAccessor.getY();

        // Optional Debug Title Box
        // try { context.fill(...) } catch (Exception e) {}

        try {
            int bgWidth = localAccessor.getBackgroundWidth(); if (bgWidth <= 0) bgWidth = 176;
            int menuX = screenX + bgWidth + PADDING; int menuY = screenY + PADDING;

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f); // Reset color tint

            // Draw Panel Background
            context.drawTexture(RenderLayer::getGuiTextured, MINIDROP_PANEL_TEXTURE, menuX - 1, menuY - 4, 0, 0, 32, 68, 32, 68);

            // --- Manual Switch Texture Rendering ---
            // Calculate position again for rendering (consistent with click check)
            int switchX_absolute = menuX - 1;
            int switchY_absolute = menuY + 68;
            float switchV = this.miniDrop_isSwitchToggled ? (float) SWITCH_ICON_HEIGHT : 0.0f;

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f); // Reset color again

            // Draw the correct state of the switch texture
            context.drawTexture(
                    RenderLayer::getGuiTextured,MINIDROP_SWITCH_TEXTURE, switchX_absolute, switchY_absolute, 0.0f, switchV,
                    SWITCH_ICON_WIDTH, SWITCH_ICON_HEIGHT, SWITCH_TEXTURE_WIDTH, SWITCH_TEXTURE_HEIGHT
            );

            // --- REMOVED widget state logging and debug border ---

            // Render slots placeholder...

        } catch (Exception e) {
            System.err.println("[MiniDrop Error] Failed during render: " + e.getMessage()); e.printStackTrace();
        }
    }
    // Add this entire new method to your HandledScreenMixin class

    /**
     * Injected before the screen handles mouse releases.
     * Checks if the mouse was released over the switch button area (if visible).
     * Consumes the event if it was, potentially preventing item drops triggered on release.
     */
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleaseMiniDrop(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // Only intercept left mouse button release when our feature should apply and the menu is visible
        if (!this.miniDrop_shouldApply || !this.miniDrop_isMenuVisible || button != 0) {
            return;
        }

        // Calculate panel and switch positions dynamically
        HandledScreenLocalAccessor localAccessor = (HandledScreenLocalAccessor) this;
        int screenXBase = localAccessor.getX();
        int screenYBase = localAccessor.getY();
        int bgWidth = localAccessor.getBackgroundWidth(); if (bgWidth <= 0) bgWidth = 176;
        int menuX_absolute = screenXBase + bgWidth + PADDING;
        int menuY_absolute = screenYBase + PADDING;

        // Define Panel Area
        int panelX = menuX_absolute - 1;
        int panelY = menuY_absolute - 4;
        int panelW = 32;
        int panelH = 68;

        // Define Switch Area
        int switchX_absolute = menuX_absolute - 1;
        int switchY_absolute = menuY_absolute + 68;

        // 1. Check Switch Release FIRST
        boolean releasedOnSwitch = mouseX >= switchX_absolute && mouseX < switchX_absolute + SWITCH_ICON_WIDTH &&
                mouseY >= switchY_absolute && mouseY < switchY_absolute + SWITCH_ICON_HEIGHT;

        if (releasedOnSwitch) {
            System.out.println("[MiniDrop Debug] Consuming mouseReleased event over switch area.");
            cir.setReturnValue(true); // Consume the event
            cir.cancel();
            return; // Handled
        }

        // --- 2. Check Panel Background Release (if not on switch) ---
        boolean releasedOnPanel = mouseX >= panelX && mouseX < panelX + panelW &&
                mouseY >= panelY && mouseY < panelY + panelH;

        if (releasedOnPanel) {
            System.out.println("[MiniDrop Debug] Consuming mouseReleased event over panel background.");
            cir.setReturnValue(true); // Consume the event
            cir.cancel();
            return; // Handled
        }

        // If release wasn't on switch or panel, let vanilla handle it
    }
}