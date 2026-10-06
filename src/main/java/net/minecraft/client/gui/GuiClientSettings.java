package net.minecraft.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.commands.ClientConfig;
import net.minecraft.client.commands.ClientConfig.Kind;
import net.minecraft.client.commands.ClientConfig.Setting;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * One screen holding every client mod, the same values {@code /config} and the chat commands
 * write.
 *
 * <p>Built from {@link ClientConfig#settings()}, so a setting only has to be registered once to
 * show up here: there is no second list to keep in step. The screen lists one pill card per mod -
 * a click flips it on or off - and a right-click opens that mod's own settings, where a click
 * flips a toggle or steps a choice and a press-and-drag across a card moves a slider. A row whose
 * master switch is off is greyed out, so it is obvious that changing it does nothing right
 * now.</p>
 *
 * <p>Everything lands in {@value ClientConfig#FILE_NAME} immediately. Slider drags are the one
 * exception: the value updates per pixel but the file is written once, on release.</p>
 */
public class GuiClientSettings extends GuiScreen
{
    private static final int ID_DONE = 0;
    private static final int ID_RESET_ALL = 1;
    private static final int ID_BACK = 2;

    /** Card height, and the gap between cards. */
    private static final int CARD_HEIGHT = 26;
    private static final int GAP = 10;

    /** Search box size, top right. */
    private static final int SEARCH_WIDTH = 150;
    private static final int SEARCH_HEIGHT = 18;

    /** Space above the grid for the title row, and below it for the buttons. */
    private static final int GRID_TOP = 44;
    private static final int GRID_BOTTOM_MARGIN = 56;

    private static final int BG_COLOR = 0xA01E1E1E;
    private static final int BG_HOVER_COLOR = 0xA02E2E2E;
    private static final int BORDER_ON_COLOR = 0xFFB8B8B8;
    private static final int BORDER_OFF_COLOR = 0xFF5A5A5A;
    private static final int DOT_ON_COLOR = 0xFF58D68D;
    private static final int DOT_OFF_COLOR = 0xFF4A4A4A;
    private static final int DOT_NEUTRAL_COLOR = 0xFFB0B0B0;
    private static final int LABEL_COLOR = 0xFFFFFFFF;
    private static final int LABEL_DIM_COLOR = 0xFF808080;
    private static final int VALUE_COLOR = 0xFFAAAAAA;

    /** Mod sections passing the search box, in file order. Rebuilt when the query changes. */
    private final List<String> mods = new ArrayList<String>();

    /** Section being configured, or null for the mod list. */
    private String detailSection;

    private String query = "";
    private boolean searchFocused;
    private int scrollY;
    private int hoveredIndex = -1;

    /** Slider card under the mouse button, until it comes back up. */
    private Setting dragging;

    public GuiClientSettings(Minecraft mc)
    {
        this.mc = mc;
        this.fontRendererObj = mc.fontRendererObj;
    }

    public void initGui()
    {
        this.scrollY = 0;
        this.dragging = null;
        rebuildMods();
        layoutButtons();
    }

    private void layoutButtons()
    {
        this.buttonList.clear();

        if (this.detailSection == null)
        {
            this.buttonList.add(new GuiButton(ID_RESET_ALL, this.width / 2 - 204, this.height - 40, 200, 20,
                "Reset All"));
            this.buttonList.add(new GuiButton(ID_DONE, this.width / 2 + 4, this.height - 40, 200, 20,
                I18n.format("gui.done", new Object[0])));
        }
        else
        {
            this.buttonList.add(new GuiButton(ID_BACK, this.width / 2 - 233, this.height - 40, 150, 20,
                "Back"));
            this.buttonList.add(new GuiButton(ID_RESET_ALL, this.width / 2 - 75, this.height - 40, 150, 20,
                "Reset All"));
            this.buttonList.add(new GuiButton(ID_DONE, this.width / 2 + 83, this.height - 40, 150, 20,
                I18n.format("gui.done", new Object[0])));
        }
    }

    /** Mod sections whose title matches the search box, in file order. */
    private void rebuildMods()
    {
        this.mods.clear();
        String needle = this.query.trim().toLowerCase();
        String last = null;

        for (Setting setting : ClientConfig.settings())
        {
            String section = setting.getSection();

            if (section.equals(last))
            {
                continue;
            }

            last = section;

            if (needle.isEmpty()
                || ClientConfig.sectionTitle(section).toLowerCase().indexOf(needle) >= 0)
            {
                this.mods.add(section);
            }
        }

        this.scrollY = 0;
    }

    private static List<Setting> settingsOf(String section)
    {
        List<Setting> list = new ArrayList<Setting>();

        for (Setting setting : ClientConfig.settings())
        {
            if (setting.getSection().equals(section))
            {
                list.add(setting);
            }
        }

        return list;
    }

    /**
     * A section's own switch: the toggle keyed {@code enabled}. Sections without one, like the
     * field of view slider, have no mod-level switch and open straight into their settings.
     */
    private static Setting masterOf(String section)
    {
        for (Setting setting : ClientConfig.settings())
        {
            if (setting.getSection().equals(section) && setting.getKind() == Kind.TOGGLE
                && setting.getKey().equals("enabled"))
            {
                return setting;
            }
        }

        return null;
    }

    /** Cards on screen right now: one per mod, or one per setting inside a mod. */
    private int itemCount()
    {
        return this.detailSection == null ? this.mods.size() : settingsOf(this.detailSection).size();
    }

    private int columns()
    {
        return this.width >= 720 ? 3 : (this.width >= 480 ? 2 : 1);
    }

    private int cardWidth()
    {
        int cols = columns();
        return Math.min(260, (this.width - 40 - (cols - 1) * GAP) / cols);
    }

    private int gridWidth()
    {
        int cols = columns();
        return cols * cardWidth() + (cols - 1) * GAP;
    }

    private int gridLeft()
    {
        return (this.width - gridWidth()) / 2;
    }

    private int gridBottom()
    {
        return this.height - GRID_BOTTOM_MARGIN;
    }

    private int maxScroll()
    {
        int rows = (itemCount() + columns() - 1) / columns();
        int content = rows * (CARD_HEIGHT + GAP) - GAP;
        return Math.max(0, content - (gridBottom() - GRID_TOP));
    }

    /** Card rectangle for a visible index, or null when it is scrolled out of view. */
    private int[] cardRect(int index)
    {
        int cols = columns();
        int cardW = cardWidth();
        int row = index / cols;
        int col = index % cols;
        int x = gridLeft() + col * (cardW + GAP);
        int y = GRID_TOP + row * (CARD_HEIGHT + GAP) - this.scrollY;

        if (y + CARD_HEIGHT < GRID_TOP || y > gridBottom())
        {
            return null;
        }

        return new int[] {x, y, cardW, CARD_HEIGHT};
    }

    private int indexAt(int mouseX, int mouseY)
    {
        if (mouseY < GRID_TOP || mouseY > gridBottom())
        {
            return -1;
        }

        for (int i = 0; i < itemCount(); ++i)
        {
            int[] rect = cardRect(i);

            if (rect != null && mouseX >= rect[0] && mouseX < rect[0] + rect[2] && mouseY >= rect[1]
                && mouseY < rect[1] + rect[3])
            {
                return i;
            }
        }

        return -1;
    }

    private int[] searchRect()
    {
        return new int[] {this.width - SEARCH_WIDTH - 12, 12, SEARCH_WIDTH, SEARCH_HEIGHT};
    }

    private static boolean active(Setting setting)
    {
        if (setting.isGatedOff())
        {
            return false;
        }

        return setting.getKind() != Kind.TOGGLE || Boolean.parseBoolean(setting.get());
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks)
    {
        this.drawDefaultBackground();
        drawRect(0, 0, this.width, this.height, 0xA0000000);

        this.hoveredIndex = indexAt(mouseX, mouseY);

        if (this.detailSection == null)
        {
            this.fontRendererObj.drawStringWithShadow(I18n.format("options.clientSettings.title", new Object[0]),
                12.0F, 15.0F, 0xFFFFFF);
            drawSearchBox();
        }
        else
        {
            this.fontRendererObj.drawStringWithShadow(ClientConfig.sectionTitle(this.detailSection), 12.0F, 15.0F,
                0xFFFFFF);
            String hint = "Right-click a mod to configure - Esc goes back";
            int hintWidth = this.fontRendererObj.getStringWidth(hint);
            this.fontRendererObj.drawStringWithShadow(hint, (float)(this.width - hintWidth - 12), 15.0F, 0x707070);
        }

        if (itemCount() == 0)
        {
            this.drawCenteredString(this.fontRendererObj, "No matches", this.width / 2,
                GRID_TOP + 20, 0x808080);
        }

        for (int i = 0; i < itemCount(); ++i)
        {
            int[] rect = cardRect(i);

            if (rect == null)
            {
                continue;
            }

            if (this.detailSection == null)
            {
                drawModCard(this.mods.get(i), rect[0], rect[1], rect[2], rect[3], i == this.hoveredIndex);
            }
            else
            {
                drawSettingCard(settingsOf(this.detailSection).get(i), rect[0], rect[1], rect[2], rect[3],
                    i == this.hoveredIndex);
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        this.drawCenteredString(this.fontRendererObj,
            I18n.format("options.clientSettings.file", new Object[] {ClientConfig.FILE_NAME}), this.width / 2,
            this.height - 16, 0xA0A0A0);
    }

    private void drawSearchBox()
    {
        int[] rect = searchRect();
        drawRect(rect[0] - 1, rect[1] - 1, rect[0] + rect[2] + 1, rect[1] + rect[3] + 1,
            this.searchFocused ? BORDER_ON_COLOR : BORDER_OFF_COLOR);
        drawRect(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3], 0xFF141414);

        String text = this.query;

        if (text.isEmpty() && !this.searchFocused)
        {
            text = "Search...";
        }
        else if (this.searchFocused && Minecraft.getSystemTime() / 500L % 2L == 0L)
        {
            text = text + "_";
        }

        String shown = this.fontRendererObj.trimStringToWidth(text, rect[2] - 8);
        this.fontRendererObj.drawStringWithShadow(shown, (float)(rect[0] + 4), (float)(rect[1] + 5),
            this.query.isEmpty() && !this.searchFocused ? 0x707070 : 0xFFFFFF);
    }

    /** One card per mod: its switch state, or nothing to flip for switch-less mods. */
    private void drawModCard(String section, int x, int y, int w, int h, boolean hovered)
    {
        Setting master = masterOf(section);
        boolean on = master != null && active(master);

        int border = master == null ? BORDER_OFF_COLOR : (on ? BORDER_ON_COLOR : BORDER_OFF_COLOR);
        int bg = hovered ? BG_HOVER_COLOR : BG_COLOR;

        drawCapsule(x - 1, y - 1, w + 2, h + 2, border);
        drawCapsule(x, y, w, h, bg);

        int dotColor = master == null ? DOT_NEUTRAL_COLOR : (on ? DOT_ON_COLOR : DOT_OFF_COLOR);
        drawCircle(x + 14, y + h / 2, 4.0F, dotColor);

        int labelColor = master != null && !on ? LABEL_DIM_COLOR : LABEL_COLOR;
        this.fontRendererObj.drawStringWithShadow(ClientConfig.sectionTitle(section), (float)(x + 24),
            (float)(y + (h - 8) / 2), labelColor);

        if (master != null)
        {
            String value = master.display();
            int valueWidth = this.fontRendererObj.getStringWidth(value);
            this.fontRendererObj.drawStringWithShadow(value, (float)(x + w - 8 - valueWidth),
                (float)(y + (h - 8) / 2), on ? VALUE_COLOR : LABEL_DIM_COLOR);
        }
    }

    private void drawSettingCard(Setting setting, int x, int y, int w, int h, boolean hovered)
    {
        boolean gated = setting.isGatedOff();
        boolean on = active(setting);

        int border = gated ? BORDER_OFF_COLOR : (on ? BORDER_ON_COLOR : BORDER_OFF_COLOR);
        int bg = hovered && !gated ? BG_HOVER_COLOR : BG_COLOR;

        drawCapsule(x - 1, y - 1, w + 2, h + 2, border);
        drawCapsule(x, y, w, h, bg);

        int dotColor = on ? DOT_ON_COLOR : DOT_OFF_COLOR;
        drawCircle(x + 14, y + h / 2, 4.0F, dotColor);

        int labelColor = gated ? LABEL_DIM_COLOR : (on ? LABEL_COLOR : LABEL_DIM_COLOR);
        this.fontRendererObj.drawStringWithShadow(setting.getLabel(), (float)(x + 24),
            (float)(y + (h - 8) / 2), labelColor);

        if (setting.getKind() != Kind.TOGGLE)
        {
            String value = setting.display();
            int valueWidth = this.fontRendererObj.getStringWidth(value);
            this.fontRendererObj.drawStringWithShadow(value, (float)(x + w - 8 - valueWidth),
                (float)(y + (h - 8) / 2), gated ? LABEL_DIM_COLOR : VALUE_COLOR);
        }
    }

    /** A filled capsule: a bar capped with a disc at each end. */
    private static void drawCapsule(int x, int y, int w, int h, int color)
    {
        float r = h / 2.0F;
        float cy = y + h / 2.0F;
        fill((float)x + r, (float)y, (float)x + w - r, (float)y + h, color);
        drawCircle((float)x + r, cy, r, color);
        drawCircle((float)x + w - r, cy, r, color);
    }

    private static void drawCircle(float cx, float cy, float r, int color)
    {
        float a = (float)(color >> 24 & 255) / 255.0F;
        float red = (float)(color >> 16 & 255) / 255.0F;
        float green = (float)(color >> 8 & 255) / 255.0F;
        float blue = (float)(color & 255) / 255.0F;

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(6, DefaultVertexFormats.POSITION_COLOR);
        renderer.pos((double)cx, (double)cy, 0.0D).color(red, green, blue, a).endVertex();

        int segments = 20;

        for (int i = 0; i <= segments; ++i)
        {
            double angle = Math.PI * 2.0D * (double)i / (double)segments;
            renderer.pos((double)cx + Math.cos(angle) * (double)r, (double)cy + Math.sin(angle) * (double)r, 0.0D)
                .color(red, green, blue, a).endVertex();
        }

        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static void fill(float x1, float y1, float x2, float y2, int color)
    {
        float a = (float)(color >> 24 & 255) / 255.0F;
        float red = (float)(color >> 16 & 255) / 255.0F;
        float green = (float)(color >> 8 & 255) / 255.0F;
        float blue = (float)(color & 255) / 255.0F;

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        renderer.pos((double)x1, (double)y2, 0.0D).color(red, green, blue, a).endVertex();
        renderer.pos((double)x2, (double)y2, 0.0D).color(red, green, blue, a).endVertex();
        renderer.pos((double)x2, (double)y1, 0.0D).color(red, green, blue, a).endVertex();
        renderer.pos((double)x1, (double)y1, 0.0D).color(red, green, blue, a).endVertex();
        tessellator.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException
    {
        if (this.detailSection == null && mouseButton == 0)
        {
            int[] search = searchRect();

            if (mouseX >= search[0] && mouseX < search[0] + search[2] && mouseY >= search[1]
                && mouseY < search[1] + search[3])
            {
                this.searchFocused = true;
                super.mouseClicked(mouseX, mouseY, mouseButton);
                return;
            }
        }

        this.searchFocused = false;

        int index = (mouseButton == 0 || mouseButton == 1) ? indexAt(mouseX, mouseY) : -1;

        if (index >= 0)
        {
            if (this.detailSection == null)
            {
                openMod(this.mods.get(index), mouseButton);
                return;
            }

            Setting setting = settingsOf(this.detailSection).get(index);

            if (!setting.isGatedOff() && mouseButton == 0)
            {
                if (setting.getKind() == Kind.SLIDER)
                {
                    // A slider reports a value for every pixel of the drag, so the file write is
                    // held open until the mouse comes back up.
                    ClientConfig.beginBulkChange();
                    this.dragging = setting;
                    slideTo(setting, mouseX);
                    return;
                }

                cycle(setting);
                return;
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /**
     * Left-click flips a mod's own switch, or opens a switch-less mod straight into its settings.
     * Right-click always opens the mod's settings.
     */
    private void openMod(String section, int mouseButton)
    {
        Setting master = masterOf(section);

        if (mouseButton == 0 && master != null)
        {
            cycle(master);
            return;
        }

        this.detailSection = section;
        this.dragging = null;
        this.scrollY = 0;
        layoutButtons();
    }

    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick)
    {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);

        if (this.dragging != null)
        {
            slideTo(this.dragging, mouseX);
        }
    }

    protected void mouseReleased(int mouseX, int mouseY, int state)
    {
        this.dragging = null;
        ClientConfig.endBulkChange();
        super.mouseReleased(mouseX, mouseY, state);
    }

    public void onGuiClosed()
    {
        // Escape can close the screen mid-drag, leaving a held write behind.
        this.dragging = null;
        ClientConfig.endBulkChange();
        super.onGuiClosed();
    }

    /** Moves a slider to the value under the mouse. */
    private void slideTo(Setting setting, int mouseX)
    {
        List<Setting> settings = settingsOf(this.detailSection);
        int index = settings.indexOf(setting);

        if (index < 0)
        {
            return;
        }

        int[] rect = cardRect(index);

        if (rect == null)
        {
            return;
        }

        float fraction = (float)(mouseX - rect[0]) / (float)rect[2];
        fraction = Math.max(0.0F, Math.min(1.0F, fraction));

        float value = setting.getMin() + fraction * (setting.getMax() - setting.getMin());

        if (setting.isIntegral())
        {
            value = (float)Math.round(value);
        }

        setting.set(String.valueOf(value));
    }

    /** Flips a toggle or steps a choice to its next value. */
    private static void cycle(Setting setting)
    {
        if (setting.getKind() == Kind.TOGGLE)
        {
            setting.set(Boolean.parseBoolean(setting.get()) ? "false" : "true");
            return;
        }

        String[] choices = setting.getChoices();

        if (choices == null || choices.length == 0)
        {
            return;
        }

        int next = 0;

        for (int i = 0; i < choices.length; ++i)
        {
            if (choices[i].equalsIgnoreCase(setting.get()))
            {
                next = (i + 1) % choices.length;
                break;
            }
        }

        setting.set(choices[next]);
    }

    public void handleMouseInput() throws IOException
    {
        super.handleMouseInput();

        int wheel = Mouse.getEventDWheel();

        if (wheel != 0)
        {
            this.scrollY = Math.max(0, Math.min(maxScroll(), this.scrollY - wheel / 120 * 30));
        }
    }

    protected void keyTyped(char typedChar, int keyCode) throws IOException
    {
        if (this.searchFocused)
        {
            if (keyCode == 1)
            {
                this.searchFocused = false;
                return;
            }

            if (keyCode == Keyboard.KEY_BACK)
            {
                if (!this.query.isEmpty())
                {
                    this.query = this.query.substring(0, this.query.length() - 1);
                    rebuildMods();
                }

                return;
            }

            if (typedChar >= 32 && typedChar != 127)
            {
                String next = this.query + typedChar;

                if (this.fontRendererObj.getStringWidth(next) <= SEARCH_WIDTH - 12)
                {
                    this.query = next;
                    rebuildMods();
                }
            }

            return;
        }

        if (keyCode == 1 && this.detailSection != null)
        {
            this.detailSection = null;
            this.dragging = null;
            this.scrollY = 0;
            layoutButtons();
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    protected void actionPerformed(GuiButton button) throws IOException
    {
        if (!button.enabled)
        {
            return;
        }

        if (button.id == ID_DONE)
        {
            ClientConfig.endBulkChange();
            this.mc.gameSettings.saveOptions();
            this.mc.displayGuiScreen((GuiScreen)null);
        }
        else if (button.id == ID_RESET_ALL)
        {
            ClientConfig.resetAll();
        }
        else if (button.id == ID_BACK)
        {
            this.detailSection = null;
            this.dragging = null;
            this.scrollY = 0;
            layoutButtons();
        }
    }
}
