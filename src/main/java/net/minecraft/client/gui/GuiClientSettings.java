package net.minecraft.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.commands.ClientConfig;
import net.minecraft.client.commands.ClientConfig.Kind;
import net.minecraft.client.commands.ClientConfig.Setting;
import net.minecraft.client.resources.I18n;

/**
 * One screen holding every client mod setting, the same values {@code /config} and the chat
 * commands write.
 *
 * <p>Built from {@link ClientConfig#settings()}, so a setting only has to be registered once to
 * show up here: there is no second list to keep in step. Each row gets a control chosen from the
 * setting's kind - a button that cycles for a toggle or a choice, a slider for a number - and a
 * row whose master switch is off is greyed out, so it is obvious that changing it does nothing
 * right now.</p>
 *
 * <p>Everything lands in {@value ClientConfig#FILE_NAME} immediately. Slider drags are the one
 * exception: the value updates per pixel but the file is written once, on release.</p>
 */
public class GuiClientSettings extends GuiScreen
{
    private static final int ID_DONE = 0;
    private static final int ID_RESET_ALL = 1;

    /** Rows are this tall, with the control sitting inside the top 20 pixels. */
    private static final int ROW_HEIGHT = 24;

    /** Width of the control in each row. */
    private static final int CONTROL_WIDTH = 200;

    /** List width: a 200 wide control plus a little room either side. */
    private static final int LIST_WIDTH = CONTROL_WIDTH + 20;

    /** Left inset of a control inside the list, so it is roughly centred. */
    private static final int CONTROL_INSET = 10;

    /** One line in the list: either a section heading or a single setting with its control. */
    private static final class Row
    {
        final String section;
        final Setting setting;
        final GuiButton control;

        Row(String section, Setting setting, GuiButton control)
        {
            this.section = section;
            this.setting = setting;
            this.control = control;
        }
    }

    /**
     * A slider that lets the screen forward drags to it.
     *
     * <p>{@code GuiSlider.mouseDragged} is protected and {@code GuiScreen.mouseClickMove} is
     * empty in this build, so a slider inside a list would otherwise click but never drag.</p>
     */
    private static final class DragSlider extends GuiSlider
    {
        private DragSlider(GuiPageButtonList.GuiResponder responder, int id, int x, int y, String label,
                float min, float max, float value, FormatHelper formatter)
        {
            super(responder, id, x, y, label, min, max, value, formatter);
            this.setWidth(CONTROL_WIDTH);
        }

        @Override
        public void mouseDragged(Minecraft mc, int mouseX, int mouseY)
        {
            super.mouseDragged(mc, mouseX, mouseY);
        }
    }

    private final List<Row> rows = new ArrayList<Row>();
    private final SettingsList list;

    /** The control the mouse went down on, so a drag can continue past the row. */
    private GuiButton pressed;

    public GuiClientSettings(Minecraft mc)
    {
        this.mc = mc;
        this.fontRendererObj = mc.fontRendererObj;
        this.list = new SettingsList(mc);
    }

    public void initGui()
    {
        this.rows.clear();
        buildRows();

        this.buttonList.clear();
        this.buttonList.add(new GuiButton(ID_RESET_ALL, this.width / 2 - 204, this.height - 40, CONTROL_WIDTH, 20,
            "Reset All"));
        this.buttonList.add(new GuiButton(ID_DONE, this.width / 2 + 4, this.height - 40, CONTROL_WIDTH, 20,
            I18n.format("gui.done", new Object[0])));

        // 32 clears the title, 52 leaves room for the two buttons and the file-name hint.
        this.list.setDimensions(this.width, this.height, 32, this.height - 52);
    }

    /** One row per section heading followed by one row per setting, in file order. */
    private void buildRows()
    {
        List<Setting> settings = ClientConfig.settings();
        String section = null;

        for (int i = 0; i < settings.size(); ++i)
        {
            Setting setting = settings.get(i);

            if (!setting.getSection().equals(section))
            {
                section = setting.getSection();
                this.rows.add(new Row(section, null, null));
            }

            this.rows.add(new Row(section, setting, createControl(i, setting)));
        }
    }

    /** The widget that suits a setting: a cycling button, or a slider for a number. */
    private GuiButton createControl(int id, Setting setting)
    {
        if (setting.getKind() == Kind.SLIDER)
        {
            float value = numeric(setting);

            return new DragSlider(new SliderResponder(setting), id, 0, 0, setting.getLabel(),
                setting.getMin(), setting.getMax(), value, new SliderFormatter(setting));
        }

        return new GuiButton(id, 0, 0, CONTROL_WIDTH, 20, setting.getLabel() + ": " + setting.display());
    }

    /** The current value of a numeric setting as a slider position input. */
    private static float numeric(Setting setting)
    {
        try
        {
            return Float.parseFloat(setting.get().trim());
        }
        catch (Exception exception)
        {
            return setting.getMin();
        }
    }

    /** Applies a dragged slider value. Writes are held by the screen until the drag ends. */
    private static final class SliderResponder implements GuiPageButtonList.GuiResponder
    {
        private final Setting setting;

        SliderResponder(Setting setting)
        {
            this.setting = setting;
        }

        public void onTick(int id, float value)
        {
            this.setting.set(String.valueOf(value));
        }

        public void func_175321_a(int id, boolean enabled)
        {
            // Only the create-world pages call this, and they have no clickable rows.
        }

        public void func_175319_a(int id, String value)
        {
            // Only the create-world pages call this, and they have no text rows.
        }
    }

    /** Renders a slider's value the way the file writes it, rather than as a raw float. */
    private static final class SliderFormatter implements GuiSlider.FormatHelper
    {
        private final Setting setting;

        SliderFormatter(Setting setting)
        {
            this.setting = setting;
        }

        public String getText(int id, String name, float value)
        {
            if (this.setting.isIntegral())
            {
                return name + ": " + (long)value;
            }

            return name + ": " + Math.round(value * 100.0F) / 100.0F;
        }
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks)
    {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRendererObj, I18n.format("options.clientSettings.title", new Object[0]),
            this.width / 2, 12, 0xFFFFFF);

        this.list.drawScreen(mouseX, mouseY, partialTicks);
        super.drawScreen(mouseX, mouseY, partialTicks);

        this.drawCenteredString(this.fontRendererObj,
            I18n.format("options.clientSettings.file", new Object[] {ClientConfig.FILE_NAME}), this.width / 2,
            this.height - 16, 0xA0A0A0);
    }

    public void handleMouseInput() throws IOException
    {
        super.handleMouseInput();
        this.list.handleMouseInput();
    }

    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException
    {
        if (mouseButton == 0)
        {
            Row row = rowAt(mouseX, mouseY);

            if (row != null && row.control != null)
            {
                if (row.setting.getKind() == Kind.SLIDER)
                {
                    // A slider reports a value for every pixel of the drag, so the file write is
                    // held open until the mouse comes back up.
                    ClientConfig.beginBulkChange();

                    if (row.control.mousePressed(this.mc, mouseX, mouseY))
                    {
                        this.pressed = row.control;
                        return;
                    }

                    ClientConfig.endBulkChange();
                }
                else if (row.control.enabled && row.control.mousePressed(this.mc, mouseX, mouseY))
                {
                    this.pressed = row.control;
                    row.control.playPressSound(this.mc.getSoundHandler());
                    cycle(row.setting);
                    return;
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick)
    {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);

        if (this.pressed instanceof DragSlider)
        {
            ((DragSlider)this.pressed).mouseDragged(this.mc, mouseX, mouseY);
        }
    }

    protected void mouseReleased(int mouseX, int mouseY, int state)
    {
        if (this.pressed != null)
        {
            if (state == 0)
            {
                this.pressed.mouseReleased(mouseX, mouseY);
            }

            // Cleared whatever button came up, so a release the slider never saw cannot leave it
            // holding a drag for the rest of the session.
            this.pressed = null;
        }

        // Always release, even if the mouse came up somewhere else entirely.
        ClientConfig.endBulkChange();
        super.mouseReleased(mouseX, mouseY, state);
    }

    public void onGuiClosed()
    {
        // Escape can close the screen mid-drag, leaving a held write behind.
        ClientConfig.endBulkChange();
        this.pressed = null;
        super.onGuiClosed();
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
            refresh();
        }
    }

    /** Rewrites every row from the current values, after a reset changed many at once. */
    private void refresh()
    {
        for (int i = 0; i < this.rows.size(); ++i)
        {
            Row row = this.rows.get(i);

            if (row.setting != null)
            {
                updateLabel(row);
            }
        }
    }

    /** Advances a toggle or a choice to its next value. */
    private static void cycle(Setting setting)
    {
        if (setting.getKind() == Kind.TOGGLE)
        {
            setting.set(Boolean.parseBoolean(setting.get()) ? "false" : "true");
            return;
        }

        String[] choices = setting.getChoices();
        int next = setting.choiceIndex() + 1;

        if (next >= choices.length)
        {
            next = 0;
        }

        setting.set(choices[next]);
    }

    /** The row under the mouse, or null when the click missed the list. */
    private Row rowAt(int mouseX, int mouseY)
    {
        if (mouseY < this.list.top || mouseY > this.list.bottom)
        {
            return null;
        }

        int index = this.list.getSlotIndexFromScreenCoords(mouseX, mouseY);
        return index < 0 || index >= this.rows.size() ? null : this.rows.get(index);
    }

    /** Repositions and redraws a row's control, keeping its text in step with the value. */
    private void drawRow(Row row, int x, int y, int mouseX, int mouseY)
    {
        if (row.control == null)
        {
            return;
        }

        row.control.xPosition = x;
        row.control.yPosition = y;
        row.control.enabled = !row.setting.isGatedOff();
        row.control.visible = true;

        if (row.control instanceof DragSlider)
        {
            // A value changed outside this row, e.g. by /fov, has to show up here too.
            ((DragSlider)row.control).func_175218_a(clamped(row.setting), false);
        }
        else
        {
            updateLabel(row);
        }

        row.control.drawButton(this.mc, mouseX, mouseY);
    }

    /** The setting's current value, pulled into the slider's own range. */
    private static float clamped(Setting setting)
    {
        return Math.max(setting.getMin(), Math.min(setting.getMax(), numeric(setting)));
    }

    private void updateLabel(Row row)
    {
        if (row.control instanceof DragSlider)
        {
            return;
        }

        row.control.displayString = row.setting.getLabel() + ": " + row.setting.display();
    }

    /** The scrolling list of rows. */
    private final class SettingsList extends GuiSlot
    {
        SettingsList(Minecraft mcIn)
        {
            // Placeholder dimensions: initGui calls setDimensions with the real scaled ones as soon
            // as this screen is opened, before anything is drawn or clicked.
            super(mcIn, 0, 0, 32, 0, ROW_HEIGHT);
        }

        protected int getSize()
        {
            return rows.size();
        }

        protected void elementClicked(int slotIndex, boolean isDoubleClick, int mouseX, int mouseY)
        {
            // Clicks are handled by the screen, which needs to know about drags.
        }

        protected boolean isSelected(int slotIndex)
        {
            return false;
        }

        protected void drawBackground()
        {
        }

        protected void drawSlot(int entryID, int x, int y, int height, int mouseXIn, int mouseYIn)
        {
            if (y + height < top || y > bottom)
            {
                // GuiSlot draws every row, not just the visible ones.
                return;
            }

            Row row = rows.get(entryID);

            if (row.control == null)
            {
                drawCenteredString(fontRendererObj, ClientConfig.sectionTitle(row.section), left + width / 2,
                    y + 7, 0xFFFF55);
                return;
            }

            drawRow(row, x + CONTROL_INSET, y + 2, mouseXIn, mouseYIn);
        }

        public int getListWidth()
        {
            return LIST_WIDTH;
        }
    }
}
