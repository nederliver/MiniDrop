package net.nederliver.minidrop.mixin;

// Keep necessary imports
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.font.TextRenderer; // Keep import
import net.minecraft.text.Text;             // Keep import
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    // --- Accessor Interface for HandledScreen specific/non-final fields ---
    // Keep accessors for fields NOT handled by ScreenMixin.ScreenAccessor
    @Mixin(HandledScreen.class) // Target HandledScreen for these
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

    // --- Constants ---
    private static final Identifier WIDGETS_TEXTURE = Identifier.of("minecraft", "textures/gui/widgets.png");
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 4;
    private static final int MENU_WIDTH = SLOT_SIZE + PADDING * 2;

    @Inject(method = "init", at = @At("TAIL"))
    private void initMiniDrop(CallbackInfo ci) {
        // Cast 'this' to the appropriate interfaces
        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        // Cast 'this' directly to ScreenMixin (which targets Screen)
        ScreenMixin screenMixin = (ScreenMixin) this;
        // Keep accessor for HandledScreen-specific fields if needed
        HandledScreenLocalAccessor localAccessor = (HandledScreenLocalAccessor) this;


        this.miniDrop_shouldApply = false;

        if (screen instanceof CreativeInventoryScreen || screen instanceof InventoryScreen) {
            System.out.println("[MiniDrop Debug] Screen excluded: " + screen.getClass().getSimpleName());
        } else {
            this.miniDrop_shouldApply = true;
            System.out.println("[MiniDrop Debug] Screen included: " + screen.getClass().getSimpleName());
            try {
                // Get HandledScreen specific values via localAccessor
                this.miniDrop_titleRelX = localAccessor.getTitleX();
                this.miniDrop_titleRelY = localAccessor.getTitleY();

                // --- Get Screen values via ScreenMixin cast ---
                TextRenderer textRenderer = screenMixin.getTextRenderer(); // Call method on ScreenMixin
                Text title = screenMixin.getTitle();                // Call method on ScreenMixin

                if (textRenderer != null && title != null) {
                    this.miniDrop_titleWidth = textRenderer.getWidth(title);
                    this.miniDrop_titleHeight = textRenderer.fontHeight;
                } else {
                    System.err.println("[MiniDrop Warning] Could not get textRenderer or title via ScreenMixin. Using fallback.");
                    this.miniDrop_titleWidth = 60;
                    this.miniDrop_titleHeight = 9;
                }
                System.out.println("[MiniDrop Debug] Title Coords: x=" + this.miniDrop_titleRelX + ", y=" + this.miniDrop_titleRelY +
                        ", Calculated Size: w=" + this.miniDrop_titleWidth + ", h=" + this.miniDrop_titleHeight);
            } catch (Exception e) {
                System.err.println("[MiniDrop Error] Failed to get title coords/size: " + e.getMessage());
                this.miniDrop_shouldApply = false;
            }
        }
        this.miniDrop_isMenuVisible = false;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClickMiniDrop(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!this.miniDrop_shouldApply) { return; }

        if (button == 0) {
            // Use localAccessor for screen position
            HandledScreenLocalAccessor localAccessor = (HandledScreenLocalAccessor) this;
            int screenX = localAccessor.getX();
            int screenY = localAccessor.getY();

            int absoluteTitleX = screenX + this.miniDrop_titleRelX;
            int absoluteTitleY = screenY + this.miniDrop_titleRelY;

            boolean clickedTitle = mouseX >= absoluteTitleX && mouseX <= absoluteTitleX + this.miniDrop_titleWidth &&
                    mouseY >= absoluteTitleY && mouseY <= absoluteTitleY + this.miniDrop_titleHeight;

            if (clickedTitle) {
                this.miniDrop_isMenuVisible = !this.miniDrop_isMenuVisible;
                System.out.println("[MiniDrop Debug] Toggled Menu Visibility: " + this.miniDrop_isMenuVisible);
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderMiniDrop(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!this.miniDrop_shouldApply) { return; }

        HandledScreenLocalAccessor localAccessor = (HandledScreenLocalAccessor) this;
        int screenX = localAccessor.getX();
        int screenY = localAccessor.getY();

        // Debug Rect
        try {
            int absoluteTitleX = screenX + this.miniDrop_titleRelX;
            int absoluteTitleY = screenY + this.miniDrop_titleRelY;
            context.fill( absoluteTitleX, absoluteTitleY, absoluteTitleX + this.miniDrop_titleWidth, absoluteTitleY + this.miniDrop_titleHeight, 0x80FF0000 );
        } catch(Exception e) { /* Ignore debug error */ }

        if (!this.miniDrop_isMenuVisible) { return; }

        // MiniDrop Menu Render
        try {
            int bgWidth = localAccessor.getBackgroundWidth();
            if (bgWidth <= 0) bgWidth = 176;
            int menuX = screenX + bgWidth + PADDING;
            int menuY = screenY + PADDING;
            // ... rest of rendering code ...
            // (No changes needed here as it uses localAccessor or constants)
            int menuHeight = (SLOT_SIZE * 4) + (PADDING * 5);
            context.fill(menuX - 2, menuY - 2, menuX + MENU_WIDTH + 2, menuY + menuHeight + 2, 0xC0101010);
            int textureWidth = 256; int textureHeight = 256;
            for (int i = 0; i < 3; i++) {
                int slotX = menuX + PADDING; int slotY = menuY + PADDING + i * (SLOT_SIZE + PADDING);
                context.drawTexture(RenderLayer::getGuiTextured, WIDGETS_TEXTURE, slotX, slotY, SLOT_SIZE, SLOT_SIZE, 0, 0, SLOT_SIZE, SLOT_SIZE, textureWidth, textureHeight);
            }
            int buttonX = menuX + PADDING; int buttonY = menuY + PADDING + 3 * (SLOT_SIZE + PADDING);
            context.drawTexture(RenderLayer::getGuiTextured, WIDGETS_TEXTURE, buttonX, buttonY, SLOT_SIZE, SLOT_SIZE, 0, 66, SLOT_SIZE, SLOT_SIZE, textureWidth, textureHeight);

        } catch (Exception e) { System.err.println("[MiniDrop Error] Failed to render MiniDrop GUI: " + e.getMessage()); }
    }
}