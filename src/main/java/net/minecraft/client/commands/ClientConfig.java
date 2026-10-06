package net.minecraft.client.commands;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Every client mod setting in the game directory, in one file.
 *
 * <p>This is the single persistence point. Each mod still owns its own in-memory state and its
 * own accessors, so nothing about how a mod works has changed; what changed is that the values
 * are read from and written to {@value #FILE_NAME} instead of one file per mod. The per-mod
 * validation and clamping is reused rather than reimplemented: loading a section hands each
 * {@code key=value} pair straight to that mod's own parser.</p>
 *
 * <p>Settings that vanilla already keeps in {@code options.txt} - field of view, the two bobbing
 * switches, no hurt cam and always sprint - are stored here as well and re-applied to
 * {@code GameSettings} on load, so this file is the source of truth for all of them. To stop the
 * two files drifting, {@code GameSettings.saveOptions()} calls {@link #saveVanilla} afterwards,
 * which is the one place every vanilla Options "Done" button funnels through.</p>
 *
 * <p>The file is written on load as well, so it is always complete and any comment the previous
 * build wrote stays accurate. Older per-mod files are imported once, on the first run where
 * {@value #FILE_NAME} does not exist yet, and are then left on disk untouched as a backup.</p>
 */
public final class ClientConfig
{
    private static final Logger LOGGER = LogManager.getLogger();

    /** The one and only config file the mods write. */
    public static final String FILE_NAME = "client.cfg";

    /** Field of view, which vanilla keeps in options.txt. */
    public static final String SECTION_FOV = "fov";

    /** View bobbing, which vanilla keeps in options.txt. */
    public static final String SECTION_BOB = "bob";

    /** No hurt cam, which vanilla keeps in options.txt. */
    public static final String SECTION_NHK = "nhk";

    /** Always sprint, which vanilla keeps in options.txt. */
    public static final String SECTION_SPRINT = "sprint";

    /** {@link Animations}. */
    public static final String SECTION_ANIMATIONS = "animations";

    /** {@link FpsOverlay}. */
    public static final String SECTION_FPS = "fps";

    /** {@link FreeLook}. */
    public static final String SECTION_FREELOOK = "freelook";

    /** Settings for the coordinates and look direction readout. See {@link CoordsHud}. */
    public static final String SECTION_COORDS = "coords";

    /** Settings for handing the free look offset to the body. See {@link LookVector}. */
    public static final String SECTION_LOOKVECTOR = "lookvector";

    /** Settings for the gap between jumps. See {@link JumpDelay}. */
    public static final String SECTION_JUMPDELAY = "jumpdelay";

    /** Settings for the pinned client-side time of day. See {@link TimeChanger}. */
    public static final String SECTION_TIMECHANGER = "timechanger";

    /** Settings for skipping the click cooldown after a miss. See {@link NoHitDelay}. */
    public static final String SECTION_NOHITDELAY = "nohitdelay";

    /** Lowest field of view {@code /fov} accepts. */
    private static final float MIN_FOV = 1.0F;

    /** Highest field of view {@code /fov} accepts. */
    private static final float MAX_FOV = 179.0F;

    /** Slider bounds for the field of view row. Narrower than the command's own range on purpose. */
    private static final float GUI_MIN_FOV = 30.0F;
    private static final float GUI_MAX_FOV = 170.0F;

    /** Presets offered for the fixed text colour, since the GUI has no colour picker. */
    private static final String[] COLOUR_PRESETS = {
        "#FFFFFF", "#55FF55", "#FFFF55", "#FF5555", "#55FFFF", "#FF55FF", "#AAAAAA", "#000000"
    };

    /**
     * Set while loading, so the per-setting setters fired during the load do not each rewrite
     * the file. Without this a full load would write the file once per key.
     */
    private static boolean loading;

    /**
     * Set while a drag is in progress, for the same reason: a slider reports a new value for
     * every pixel the mouse moves, and writing the file on each of those would hammer the disk.
     */
    private static boolean deferring;

    /** A change was made while {@link #deferring} was set and still needs writing out. */
    private static boolean pending;

    /**
     * Set once {@link #load()} has run.
     *
     * <p>Vanilla calls {@code GameSettings.saveOptions()} from a few places before the game has
     * finished starting. Writing the file then would leave a complete-looking {@value #FILE_NAME}
     * behind, and since the migration below only runs when the file is absent, the old per-mod
     * configs would never be imported. Refusing to write before the load keeps migration safe.</p>
     */
    private static boolean loaded;

    private static final List<Setting> SETTINGS = buildSettings();

    /** {@code section.key} to the setting, so a row can find its master switch in one step. */
    private static final Map<String, Setting> BY_KEY = index(SETTINGS);

    private ClientConfig() {}

    private static Map<String, Setting> index(List<Setting> settings)
    {
        Map<String, Setting> map = new LinkedHashMap<String, Setting>();

        for (int i = 0; i < settings.size(); ++i)
        {
            map.put(settings.get(i).qualifiedKey(), settings.get(i));
        }

        return map;
    }

    // --- the settings table ------------------------------------------------------

    /** How the GUI should present a setting. */
    public enum Kind
    {
        /** A yes/no row. */
        TOGGLE,
        /** A row that cycles through a fixed list of words. */
        CHOICE,
        /** A draggable numeric row. */
        SLIDER
    }

    /** Reads a setting's current value as the string that would be written to the file. */
    public interface Getter
    {
        String get();
    }

    /**
     * Applies a value written in the file, or chosen in the GUI. Implementations are expected to
     * validate and clamp, and to leave the current value alone when the text is unusable.
     */
    public interface Setter
    {
        void set(String raw);
    }

    /**
     * One configurable value, with everything the GUI needs to draw a row for it.
     *
     * <p>Rows carry their owning section so the file can be written in the same order as the
     * table, and an optional master switch so a disabled mod's sub-settings can be greyed out
     * rather than silently doing nothing.</p>
     */
    public static final class Setting
    {
        private final String section;
        private final String key;
        private final String label;
        private final Kind kind;
        private final String[] choices;
        private final float min;
        private final float max;
        private final boolean integral;
        private final Getter getter;
        private final Setter setter;
        private final String masterSection;
        private final String masterKey;
        private final boolean invertGate;

        Setting(String section, String key, String label, Kind kind, String[] choices, float min, float max,
                boolean integral, Getter getter, Setter setter, String masterSection, String masterKey)
        {
            this(section, key, label, kind, choices, min, max, integral, getter, setter, masterSection, masterKey,
                false);
        }

        Setting(String section, String key, String label, Kind kind, String[] choices, float min, float max,
                boolean integral, Getter getter, Setter setter, String masterSection, String masterKey,
                boolean invertGate)
        {
            this.section = section;
            this.key = key;
            this.label = label;
            this.kind = kind;
            this.choices = choices;
            this.min = min;
            this.max = max;
            this.integral = integral;
            this.getter = getter;
            this.setter = setter;
            this.masterSection = masterSection;
            this.masterKey = masterKey;
            this.invertGate = invertGate;
        }

        public String getSection()
        {
            return this.section;
        }

        public String getKey()
        {
            return this.key;
        }

        public String getLabel()
        {
            return this.label;
        }

        public Kind getKind()
        {
            return this.kind;
        }

        /** The words a {@link Kind#TOGGLE} or {@link Kind#CHOICE} row cycles through. */
        public String[] getChoices()
        {
            return this.choices;
        }

        public float getMin()
        {
            return this.min;
        }

        public float getMax()
        {
            return this.max;
        }

        /** True when the value is a whole number, so the GUI should not show a decimal point. */
        public boolean isIntegral()
        {
            return this.integral;
        }

        /** The value as it would appear in the file. */
        public String get()
        {
            return this.getter.get();
        }

        /** Applies a value, clamped and validated by whoever owns the setting. */
        public void set(String raw)
        {
            this.setter.set(raw);
        }

        /** {@code section.key}, the only thing the file parser needs. */
        public String qualifiedKey()
        {
            return this.section + "." + this.key;
        }

        /**
         * True when this row has no effect right now and the GUI should grey it out.
         *
         * <p>Normally that is a master switch being off. The FPS colour row is the exception: it
         * stops mattering once colour-by-FPS is turned back on, so its gate is inverted.</p>
         */
        public boolean isGatedOff()
        {
            if (this.masterSection == null)
            {
                return false;
            }

            Setting master = BY_KEY.get(this.masterSection + "." + this.masterKey);

            if (master == null)
            {
                // A gate naming a setting that does not exist would silently disable the row, so
                // treat it as ungated instead.
                LOGGER.warn("Setting {}.{} names an unknown master {}.{}", this.section, this.key, this.masterSection,
                    this.masterKey);
                return false;
            }

            boolean masterOn = Boolean.parseBoolean(master.get());
            return this.invertGate ? masterOn : !masterOn;
        }

        /** The value rendered for a row, with the section's own formatting. */
        public String display()
        {
            String value = this.get();

            if (this.kind == Kind.TOGGLE)
            {
                return Boolean.parseBoolean(value) ? "ON" : "OFF";
            }

            if (this.kind == Kind.CHOICE)
            {
                return value;
            }

            return this.integral ? String.valueOf((long)parseNumber(value, 0.0F)) : trimNumber(value);
        }

        /** Index of the current value within {@link #getChoices()}, for cycling. */
        public int choiceIndex()
        {
            String value = this.get();

            if (this.choices == null)
            {
                return -1;
            }

            for (int i = 0; i < this.choices.length; ++i)
            {
                if (this.choices[i].equalsIgnoreCase(value))
                {
                    return i;
                }
            }

            return -1;
        }
    }

    private static List<Setting> buildSettings()
    {
        List<Setting> list = new ArrayList<Setting>();

        // --- field of view --------------------------------------------------------

        list.add(new Setting(SECTION_FOV, "value", "Field of view", Kind.SLIDER, null, GUI_MIN_FOV, GUI_MAX_FOV,
            false, new Getter()
            {
                public String get()
                {
                    return String.valueOf(fov());
                }
            }, new Setter()
            {
                public void set(String raw)
                {
                    setFov(parseNumber(raw, fov()));
                }
            }, null, null));

        // --- view bobbing ---------------------------------------------------------

        list.add(new Setting(SECTION_BOB, "hand", "Hand bobbing", Kind.TOGGLE, onOff(), 0.0F, 0.0F, false,
            new Getter()
            {
                public String get()
                {
                    GameSettings settings = gameSettings();
                    return settings == null ? "true" : String.valueOf(settings.viewBobbing);
                }
            }, new Setter()
            {
                public void set(String raw)
                {
                    GameSettings settings = gameSettings();

                    if (settings != null)
                    {
                        settings.viewBobbing = parseBooleanOr(raw, settings.viewBobbing);
                    }
                }
            }, null, null));

        list.add(new Setting(SECTION_BOB, "camera", "Camera bobbing", Kind.TOGGLE, onOff(), 0.0F, 0.0F, false,
            new Getter()
            {
                public String get()
                {
                    GameSettings settings = gameSettings();
                    return settings == null ? "false" : String.valueOf(settings.cameraBobbing);
                }
            }, new Setter()
            {
                public void set(String raw)
                {
                    GameSettings settings = gameSettings();

                    if (settings != null)
                    {
                        settings.cameraBobbing = parseBooleanOr(raw, settings.cameraBobbing);
                    }
                }
            }, null, null));

        // --- no hurt cam ----------------------------------------------------------

        list.add(new Setting(SECTION_NHK, "enabled", "No hurt cam", Kind.TOGGLE, onOff(), 0.0F, 0.0F, false,
            new Getter()
            {
                public String get()
                {
                    GameSettings settings = gameSettings();
                    return settings == null ? "false" : String.valueOf(settings.noHurtCam);
                }
            }, new Setter()
            {
                public void set(String raw)
                {
                    GameSettings settings = gameSettings();

                    if (settings != null)
                    {
                        settings.noHurtCam = parseBooleanOr(raw, settings.noHurtCam);
                    }
                }
            }, null, null));

        // --- always sprint --------------------------------------------------------

        list.add(new Setting(SECTION_SPRINT, "enabled", "Always sprint", Kind.TOGGLE, onOff(), 0.0F, 0.0F, false,
            new Getter()
            {
                public String get()
                {
                    GameSettings settings = gameSettings();
                    return settings == null ? "false" : String.valueOf(settings.alwaysSprint);
                }
            }, new Setter()
            {
                public void set(String raw)
                {
                    GameSettings settings = gameSettings();

                    if (settings != null)
                    {
                        settings.alwaysSprint = parseBooleanOr(raw, settings.alwaysSprint);
                    }
                }
            }, null, null));

        // --- old animations -------------------------------------------------------
        // The nine flags share one row builder, because Animations already exposes them by name.

        String[] animationFlags = {"blockHit", "pushing", "pushingParticle", "sneak", "smoothSneak", "health",
            "armorDamage", "itemSwitch", "rod"};
        String[] animationLabels = {"Block hit swing", "Pushing", "Pushing particles", "Sneaking", "Smooth sneak",
            "Frozen hearts", "Untinted armour", "Item switch", "Flat rod line"};

        for (int i = 0; i < animationFlags.length; ++i)
        {
            final String flag = animationFlags[i];

            list.add(new Setting(SECTION_ANIMATIONS, flag, animationLabels[i], Kind.TOGGLE, onOff(), 0.0F, 0.0F,
                false, new Getter()
                {
                    public String get()
                    {
                        return String.valueOf(Animations.rawFlag(flag));
                    }
                }, new Setter()
                {
                    public void set(String raw)
                    {
                        Animations.setFlag(flag, parseBooleanOr(raw, Animations.rawFlag(flag)));
                    }
                }, SECTION_ANIMATIONS, "enabled"));
        }

        list.add(new Setting(SECTION_ANIMATIONS, "smoothSneakSpeed", "Sneak speed", Kind.SLIDER, null,
            Animations.getMinSmoothSneakSpeed(), Animations.getMaxSmoothSneakSpeed(), false, new Getter()
            {
                public String get()
                {
                    return String.valueOf(Animations.getSmoothSneakSpeed());
                }
            }, new Setter()
            {
                public void set(String raw)
                {
                    Animations.setSmoothSneakSpeed(parseNumber(raw, Animations.getSmoothSneakSpeed()));
                }
            }, SECTION_ANIMATIONS, "enabled"));

        list.add(masterToggle(SECTION_ANIMATIONS, "enabled", "Old animations"));

        // --- fps display ----------------------------------------------------------

        list.add(masterToggle(SECTION_FPS, "enabled", "FPS display"));

        list.add(new Setting(SECTION_FPS, "mode", "FPS mode", Kind.CHOICE, new String[] {"normal", "smooth"},
            0.0F, 0.0F, false, new Getter()
            {
                public String get()
                {
                    return FpsOverlay.getMode().name().toLowerCase();
                }
            }, new Setter()
            {
                public void set(String raw)
                {
                    for (FpsOverlay.Mode mode : FpsOverlay.Mode.values())
                    {
                        if (mode.name().equalsIgnoreCase(raw))
                        {
                            FpsOverlay.setMode(mode.name());
                            return;
                        }
                    }

                    LOGGER.warn("Unknown FPS mode \"{}\", keeping {}", raw, FpsOverlay.getMode());
                }
            }, SECTION_FPS, "enabled"));

        list.add(new Setting(SECTION_FPS, "size", "Text size", Kind.SLIDER, null, FpsOverlay.getMinSize(),
            FpsOverlay.getMaxSize(), false, new Getter()
            {
                public String get()
                {
                    return String.valueOf(FpsOverlay.getSize());
                }
            }, new Setter()
            {
                public void set(String raw)
                {
                    FpsOverlay.setSize(parseNumber(raw, FpsOverlay.getSize()));
                }
            }, SECTION_FPS, "enabled"));

        list.add(new Setting(SECTION_FPS, "posX", "Position X", Kind.SLIDER, null, -400.0F, 400.0F, true, new Getter()
        {
            public String get()
            {
                return String.valueOf(FpsOverlay.getPosX());
            }
        }, new Setter()
        {
            public void set(String raw)
            {
                FpsOverlay.setPos(Math.round(parseNumber(raw, FpsOverlay.getPosX())), FpsOverlay.getPosY(),
                    FpsOverlay.getAlpha());
            }
        }, SECTION_FPS, "enabled"));

        list.add(new Setting(SECTION_FPS, "posY", "Position Y", Kind.SLIDER, null, -400.0F, 400.0F, true, new Getter()
        {
            public String get()
            {
                return String.valueOf(FpsOverlay.getPosY());
            }
        }, new Setter()
        {
            public void set(String raw)
            {
                FpsOverlay.setPos(FpsOverlay.getPosX(), Math.round(parseNumber(raw, FpsOverlay.getPosY())),
                    FpsOverlay.getAlpha());
            }
        }, SECTION_FPS, "enabled"));

        list.add(new Setting(SECTION_FPS, "alpha", "Opacity", Kind.SLIDER, null, 0.0F, 255.0F, true, new Getter()
        {
            public String get()
            {
                return String.valueOf(FpsOverlay.getAlpha());
            }
        }, new Setter()
        {
            public void set(String raw)
            {
                FpsOverlay.setPos(FpsOverlay.getPosX(), FpsOverlay.getPosY(),
                    Math.round(parseNumber(raw, FpsOverlay.getAlpha())));
            }
        }, SECTION_FPS, "enabled"));

        list.add(new Setting(SECTION_FPS, "autoColour", "Colour by FPS", Kind.TOGGLE, onOff(), 0.0F, 0.0F, false,
            new Getter()
            {
                public String get()
                {
                    return String.valueOf(FpsOverlay.isAutoColour());
                }
            }, new Setter()
            {
                public void set(String raw)
                {
                    FpsOverlay.setAutoColour(parseBooleanOr(raw, FpsOverlay.isAutoColour()));
                }
            }, null, null));

        list.add(new Setting(SECTION_FPS, "colour", "Fixed colour", Kind.CHOICE, COLOUR_PRESETS, 0.0F, 0.0F, false,
            new Getter()
            {
                public String get()
                {
                    return String.format("#%06X", FpsOverlay.getColour());
                }
            }, new Setter()
            {
                public void set(String raw)
                {
                    // Deliberately not FpsOverlay.setColour: the two keys are independent here,
                    // so picking a preset in the GUI never silently turns colour-by-FPS off.
                    FpsOverlay.setColourOnly(FpsOverlay.parseHexOr(raw, FpsOverlay.getColour()));
                }
            }, SECTION_FPS, "autoColour", true));

        list.add(new Setting(SECTION_FPS, "shadow", "Text shadow", Kind.TOGGLE, onOff(), 0.0F, 0.0F, false,
            new Getter()
            {
                public String get()
                {
                    return String.valueOf(FpsOverlay.isShadow());
                }
            }, new Setter()
            {
                public void set(String raw)
                {
                    FpsOverlay.setShadow(parseBooleanOr(raw, FpsOverlay.isShadow()));
                }
            }, null, null));

        // --- free look ------------------------------------------------------------

        list.add(masterToggle(SECTION_FREELOOK, "enabled", "Free look"));

        list.add(new Setting(SECTION_FREELOOK, "autoF5", "Auto third person", Kind.TOGGLE, onOff(), 0.0F, 0.0F,
            false, new Getter()
            {
                public String get()
                {
                    return String.valueOf(FreeLook.isAutoF5());
                }
            }, new Setter()
            {
                public void set(String raw)
                {
                    FreeLook.setAutoF5(parseBooleanOr(raw, FreeLook.isAutoF5()));
                }
            }, null, null));

        list.add(new Setting(SECTION_FREELOOK, "mode", "Free look mode", Kind.CHOICE, new String[] {"hold", "toggle"},
            0.0F, 0.0F, false, new Getter()
            {
                public String get()
                {
                    return FreeLook.getMode().name().toLowerCase();
                }
            }, new Setter()
            {
                public void set(String raw)
                {
                    for (FreeLook.Mode mode : FreeLook.Mode.values())
                    {
                        if (mode.name().equalsIgnoreCase(raw))
                        {
                            FreeLook.setMode(mode);
                            return;
                        }
                    }

                    LOGGER.warn("Unknown free look mode \"{}\", keeping {}", raw, FreeLook.getMode());
                }
            }, null, null));

        list.add(new Setting(SECTION_FREELOOK, "view", "Free look view", Kind.CHOICE,
            new String[] {"both", "first", "third"}, 0.0F, 0.0F, false, new Getter()
            {
                public String get()
                {
                    return FreeLook.getView().name().toLowerCase();
                }
            }, new Setter()
            {
                public void set(String raw)
                {
                    for (FreeLook.View view : FreeLook.View.values())
                    {
                        if (view.name().equalsIgnoreCase(raw))
                        {
                            FreeLook.setView(view);
                            return;
                        }
                    }

                    LOGGER.warn("Unknown free look view \"{}\", keeping {}", raw, FreeLook.getView());
                }
            }, null, null));

        // --- position HUD -----------------------------------------------------------

        list.add(masterToggle(SECTION_COORDS, "enabled", "Position HUD"));

        list.add(new Setting(SECTION_COORDS, "size", "Text size", Kind.SLIDER, null, 0.5F, 3.0F, false, new Getter()
        {
            public String get()
            {
                return String.valueOf(CoordsHud.getSize());
            }
        }, new Setter()
        {
            public void set(String raw)
            {
                CoordsHud.setSize(parseNumber(raw, CoordsHud.getSize()));
            }
        }, SECTION_COORDS, "enabled"));

        list.add(new Setting(SECTION_COORDS, "posX", "Position X", Kind.SLIDER, null, -400.0F, 400.0F, true, new Getter()
        {
            public String get()
            {
                return String.valueOf(CoordsHud.getPosX());
            }
        }, new Setter()
        {
            public void set(String raw)
            {
                CoordsHud.setPos(Math.round(parseNumber(raw, CoordsHud.getPosX())), CoordsHud.getPosY());
            }
        }, SECTION_COORDS, "enabled"));

        list.add(new Setting(SECTION_COORDS, "posY", "Position Y", Kind.SLIDER, null, -400.0F, 400.0F, true, new Getter()
        {
            public String get()
            {
                return String.valueOf(CoordsHud.getPosY());
            }
        }, new Setter()
        {
            public void set(String raw)
            {
                CoordsHud.setPos(CoordsHud.getPosX(), Math.round(parseNumber(raw, CoordsHud.getPosY())));
            }
        }, SECTION_COORDS, "enabled"));

        // --- real look vector -------------------------------------------------------

        list.add(masterToggle(SECTION_LOOKVECTOR, "enabled", "Real look vector"));

        // --- jump delay ------------------------------------------------------------

        list.add(masterToggle(SECTION_JUMPDELAY, "enabled", "Jump delay"));

        list.add(new Setting(SECTION_JUMPDELAY, "delay", "Delay in ticks", Kind.SLIDER, null,
            (float)JumpDelay.MIN_DELAY, (float)JumpDelay.MAX_DELAY, false, new Getter()
        {
            public String get()
            {
                return String.valueOf(JumpDelay.getDelay());
            }
        }, new Setter()
        {
            public void set(String raw)
            {
                JumpDelay.setDelay(Math.round(parseNumber(raw, JumpDelay.getDelay())));
            }
        }, SECTION_JUMPDELAY, "enabled"));

        // --- time changer ----------------------------------------------------------

        list.add(masterToggle(SECTION_TIMECHANGER, "enabled", "Time changer"));

        list.add(new Setting(SECTION_TIMECHANGER, "time", "Time of day", Kind.SLIDER, null,
            (float)TimeChanger.MIN_TIME, (float)TimeChanger.MAX_TIME, false, new Getter()
        {
            public String get()
            {
                return String.valueOf(TimeChanger.getTime());
            }
        }, new Setter()
        {
            public void set(String raw)
            {
                TimeChanger.setTime(Math.round(parseNumber(raw, TimeChanger.getTime())));
            }
        }, SECTION_TIMECHANGER, "enabled"));

        // --- no hit delay ----------------------------------------------------------

        list.add(masterToggle(SECTION_NOHITDELAY, "enabled", "No hit delay"));

        return Collections.unmodifiableList(list);
    }

    /** A master switch row. Its own value is not gated by anything. */
    private static Setting masterToggle(final String section, final String key, String label)
    {
        return new Setting(section, key, label, Kind.TOGGLE, onOff(), 0.0F, 0.0F, false, new Getter()
        {
            public String get()
            {
                return String.valueOf(isMasterOn(section, key));
            }
        }, new Setter()
        {
            public void set(String raw)
            {
                boolean value = parseBooleanOr(raw, isMasterOn(section, key));

                if (SECTION_ANIMATIONS.equals(section))
                {
                    Animations.setEnabled(value);
                }
                else if (SECTION_FPS.equals(section))
                {
                    FpsOverlay.setEnabled(value);
                }
                else if (SECTION_FREELOOK.equals(section))
                {
                    FreeLook.setEnabled(value);
                }
                else if (SECTION_COORDS.equals(section))
                {
                    CoordsHud.setEnabled(value);
                }
                else if (SECTION_LOOKVECTOR.equals(section))
                {
                    LookVector.setEnabled(value);
                }
                else if (SECTION_JUMPDELAY.equals(section))
                {
                    JumpDelay.setEnabled(value);
                }
                else if (SECTION_TIMECHANGER.equals(section))
                {
                    TimeChanger.setEnabled(value);
                }
                else if (SECTION_NOHITDELAY.equals(section))
                {
                    NoHitDelay.setEnabled(value);
                }
            }
        }, null, null);
    }

    private static boolean isMasterOn(String section, String key)
    {
        if (SECTION_ANIMATIONS.equals(section))
        {
            return Animations.isEnabled();
        }

        if (SECTION_FPS.equals(section))
        {
            return FpsOverlay.isEnabled();
        }

        if (SECTION_COORDS.equals(section))
        {
            return CoordsHud.isEnabled();
        }

        if (SECTION_LOOKVECTOR.equals(section))
        {
            return LookVector.isEnabled();
        }

        if (SECTION_JUMPDELAY.equals(section))
        {
            return JumpDelay.isEnabled();
        }

        if (SECTION_TIMECHANGER.equals(section))
        {
            return TimeChanger.isEnabled();
        }

        if (SECTION_NOHITDELAY.equals(section))
        {
            return NoHitDelay.isEnabled();
        }

        return FreeLook.isEnabled();
    }

    private static String[] onOff()
    {
        return new String[] {"false", "true"};
    }

    // --- public API --------------------------------------------------------------

    /** Every setting, in the order the file is written. */
    public static List<Setting> settings()
    {
        return SETTINGS;
    }

    /** A human readable name for a section, used as a heading in the GUI. */
    public static String sectionTitle(String section)
    {
        if (SECTION_FOV.equals(section))
        {
            return "Field of view";
        }

        if (SECTION_BOB.equals(section))
        {
            return "View bobbing";
        }

        if (SECTION_NHK.equals(section))
        {
            return "No hurt cam";
        }

        if (SECTION_SPRINT.equals(section))
        {
            return "Movement";
        }

        if (SECTION_ANIMATIONS.equals(section))
        {
            return "Old animations";
        }

        if (SECTION_FPS.equals(section))
        {
            return "FPS display";
        }

        if (SECTION_FREELOOK.equals(section))
        {
            return "Free look";
        }

        if (SECTION_COORDS.equals(section))
        {
            return "Position HUD";
        }

        if (SECTION_LOOKVECTOR.equals(section))
        {
            return "Real look vector";
        }

        if (SECTION_JUMPDELAY.equals(section))
        {
            return "Jump delay";
        }

        if (SECTION_TIMECHANGER.equals(section))
        {
            return "Time changer";
        }

        if (SECTION_NOHITDELAY.equals(section))
        {
            return "No hit delay";
        }

        return section;
    }

    /** The single config file in the game directory. */
    public static File getConfigFile()
    {
        Minecraft mc = Minecraft.getMinecraft();
        File dir = mc == null ? new File(".") : mc.mcDataDir;
        return new File(dir, FILE_NAME);
    }

    // --- loading -----------------------------------------------------------------

    /**
     * Reads the config file, migrating the old per-mod files on the first run, then rewrites it
     * so it is always complete. Never throws.
     */
    public static void load()
    {
        loading = true;

        try
        {
            // Start from the defaults so a setting missing from the file lands on its default
            // rather than on whatever a previous session happened to leave in memory. The four
            // settings vanilla owns are deliberately not reset: their untouched values come from
            // options.txt, which loadOptions has just read, and that stays a valid source when
            // client.cfg is silent or unreadable.
            Animations.resetToDefaults();
            FpsOverlay.resetToDefaults();
            FreeLook.resetToDefaults();
            CoordsHud.resetToDefaults();
            LookVector.resetToDefaults();
        JumpDelay.resetToDefaults();
        TimeChanger.resetToDefaults();
        NoHitDelay.resetToDefaults();

            File file = getConfigFile();
            Map<String, Map<String, String>> parsed = parse(file);

            if (!file.exists())
            {
                LOGGER.info("No {} found, migrating any older mod configs.", FILE_NAME);
                migrate(parsed);
            }

            for (int i = 0; i < SETTINGS.size(); ++i)
            {
                Setting setting = SETTINGS.get(i);
                Map<String, String> section = parsed.get(setting.section);
                String value = section == null ? null : section.get(setting.key);

                if (value == null)
                {
                    // A setting the file does not mention keeps the default it was just given.
                    continue;
                }

                setting.set(value);
            }

            fixFpsColourPair(parsed.get(SECTION_FPS));
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to read " + getConfigFile().getAbsolutePath() + ", using defaults.", exception);
        }
        finally
        {
            loading = false;
            loaded = true;
        }

        // Written on every start so the file always exists and always lists every setting.
        save();
    }

    /**
     * Reconciles the FPS section's two colour keys.
     *
     * <p>{@code /fps color auto} leaves {@code autoColour=true} in the file, and {@code /fps
     * color <hex>} writes {@code colour} while turning {@code autoColour} off. The pair is
     * therefore authoritative when both keys are present. A file carrying only {@code colour},
     * which a hand edit or an older build can produce, means the colour was chosen on purpose,
     * so auto colouring has to yield - otherwise the colour would be read and then ignored.</p>
     *
     * @param fps the parsed {@code [fps]} section, or null when the file has none
     */
    private static void fixFpsColourPair(Map<String, String> fps)
    {
        if (fps == null || !fps.containsKey("colour") || fps.containsKey("autoColour"))
        {
            return;
        }

        FpsOverlay.setAutoColour(false);
    }

    /**
     * Folds the old per-mod files into the parsed content, for sections the new file does not
     * already carry. The old files are left on disk as a backup.
     */
    private static void migrate(Map<String, Map<String, String>> parsed)
    {
        copyLegacy(parsed, SECTION_ANIMATIONS, "animations.cfg");
        copyLegacy(parsed, SECTION_FPS, "fpsdisplay.cfg");
        copyLegacy(parsed, SECTION_FREELOOK, "freelook.cfg");

        if (parsed.containsKey(SECTION_ANIMATIONS) || parsed.containsKey(SECTION_FPS) || parsed.containsKey(SECTION_FREELOOK))
        {
            LOGGER.info("Imported older mod config files into {}", FILE_NAME);
        }
    }

    private static void copyLegacy(Map<String, Map<String, String>> parsed, String section, String legacyName)
    {
        File dir = getConfigFile().getParentFile();
        File legacy = new File(dir == null ? new File(".") : dir, legacyName);

        if (!legacy.exists())
        {
            return;
        }

        // An old file has no section headers, so all of its keys sit under the empty name.
        Map<String, String> keys = parse(legacy).get("");

        if (keys == null || keys.isEmpty())
        {
            return;
        }

        // Lift the flat key set into this build's section so the apply pass finds it.
        Map<String, String> existing = parsed.get(section);

        if (existing == null)
        {
            existing = new LinkedHashMap<String, String>();
            parsed.put(section, existing);
        }

        existing.putAll(keys);
        LOGGER.info("Imported {} keys from {}", keys.size(), legacyName);
    }

    /** Parses {@code key=value} lines, grouping by {@code [section]} headers when present. */
    private static Map<String, Map<String, String>> parse(File file)
    {
        Map<String, Map<String, String>> sections = new LinkedHashMap<String, Map<String, String>>();

        if (!file.exists())
        {
            return sections;
        }

        FileInputStream stream = null;

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream = new FileInputStream(file), "UTF-8"));

            // Keys seen before any header sit under the empty name, which is how the legacy
            // per-mod files are read: they have no headers at all.
            Map<String, String> current = new LinkedHashMap<String, String>();
            sections.put("", current);
            String line;

            while ((line = reader.readLine()) != null)
            {
                line = line.trim();

                if (line.isEmpty() || line.charAt(0) == '#')
                {
                    continue;
                }

                if (line.charAt(0) == '[' && line.endsWith("]"))
                {
                    String name = line.substring(1, line.length() - 1).trim();
                    current = sections.get(name);

                    if (current == null)
                    {
                        current = new LinkedHashMap<String, String>();
                        sections.put(name, current);
                    }

                    continue;
                }

                int split = line.indexOf('=');

                if (split < 1 || current == null)
                {
                    continue;
                }

                current.put(line.substring(0, split).trim(), line.substring(split + 1).trim());
            }
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to read " + file.getAbsolutePath(), exception);
        }
        finally
        {
            closeQuietly(stream);
        }

        return sections;
    }

    // --- saving ------------------------------------------------------------------

    /** Writes the single config file. A no-op while loading or deferring, to avoid a write per key. */
    public static void save()
    {
        if (loading || !loaded)
        {
            return;
        }

        if (deferring)
        {
            pending = true;
            return;
        }

        write();
    }

    /**
     * Holds writes until {@link #endBulkChange()}.
     *
     * <p>Used while a slider is being dragged, so a sweep across it costs one write rather than
     * one per pixel. This is a single flag, not a counter, so the two calls have to be balanced;
     * the settings screen opens one for a drag and closes it on release or on screen close.</p>
     */
    public static void beginBulkChange()
    {
        deferring = true;
    }

    /** Releases a {@link #beginBulkChange()} and writes once, if anything changed. */
    public static void endBulkChange()
    {
        deferring = false;

        if (pending)
        {
            pending = false;
            write();
        }
    }

    /** Writes the file. Never throws. */
    private static void write()
    {
        File file = getConfigFile();
        File parent = file.getParentFile();

        if (parent != null && !parent.exists() && !parent.mkdirs())
        {
            LOGGER.warn("Could not create {} to save {}", parent.getAbsolutePath(), FILE_NAME);
            return;
        }

        PrintWriter writer = null;

        try
        {
            writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            writer.println("# " + FILE_NAME + " - every client mod setting, one file.");
            writer.println("# Written by the in-game Client Settings screen and by /config.");
            writer.println("# Changes made in the vanilla Options menus are folded back in on save.");

            String section = null;

            for (int i = 0; i < SETTINGS.size(); ++i)
            {
                Setting setting = SETTINGS.get(i);

                if (!setting.section.equals(section))
                {
                    section = setting.section;
                    writer.println();
                    writer.println("[" + section + "]");
                }

                writer.println(setting.key + "=" + setting.get());
            }
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to write " + file.getAbsolutePath(), exception);
        }
        finally
        {
            if (writer != null)
            {
                writer.close();
            }
        }
    }

    /**
     * Re-reads the settings vanilla owns and writes them out.
     *
     * <p>Called at the end of {@code GameSettings.saveOptions()}, which every vanilla Options
     * "Done" button calls. Without it, moving a slider in Options would change options.txt but
     * not this file, and the next start would put the old value back.</p>
     */
    public static void saveVanilla()
    {
        if (!loaded)
        {
            return;
        }

        save();
    }

    /** Restores every setting to its default and rewrites the file. */
    public static void resetAll()
    {
        loading = true;

        try
        {
            Animations.reset();
            FpsOverlay.reset();
            FreeLook.reset();
            CoordsHud.reset();
            LookVector.reset();
            JumpDelay.reset();
            TimeChanger.reset();
            NoHitDelay.reset();

            GameSettings settings = gameSettings();

            if (settings != null)
            {
                settings.fovSetting = 70.0F;
                settings.viewBobbing = true;
                settings.cameraBobbing = false;
                settings.noHurtCam = false;
                settings.alwaysSprint = false;
            }
        }
        finally
        {
            loading = false;
        }

        save();
    }

    // --- helpers -----------------------------------------------------------------

    private static GameSettings gameSettings()
    {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null ? null : mc.gameSettings;
    }

    private static float fov()
    {
        GameSettings settings = gameSettings();
        return settings == null ? 70.0F : settings.fovSetting;
    }

    private static void setFov(float value)
    {
        GameSettings settings = gameSettings();

        if (settings == null)
        {
            return;
        }

        if (Float.isNaN(value) || Float.isInfinite(value))
        {
            return;
        }

        // Written straight to the field rather than through Options.FOV, which is what
        // setOptionFloatValue ends up doing for that option anyway.
        settings.fovSetting = Math.max(MIN_FOV, Math.min(MAX_FOV, value));
    }

    /**
     * Lenient float parse for file values, because every owner clamps for itself and reports
     * its own warning. Non-numeric text keeps the current value.
     */
    private static float parseNumber(String raw, float fallback)
    {
        try
        {
            return Float.parseFloat(raw.trim());
        }
        catch (Exception exception)
        {
            return fallback;
        }
    }

    /** Strict boolean parse, so a typo cannot silently flip a setting. */
    private static boolean parseBooleanOr(String raw, boolean fallback)
    {
        if ("true".equalsIgnoreCase(raw))
        {
            return true;
        }

        if ("false".equalsIgnoreCase(raw))
        {
            return false;
        }

        LOGGER.warn("Expected true or false but got \"{}\", keeping {}", raw, fallback);
        return fallback;
    }

    /** Drops a trailing ".0" so a whole number reads as "6" rather than "6.0". */
    private static String trimNumber(String value)
    {
        try
        {
            float number = Float.parseFloat(value);

            if (number == Math.round(number))
            {
                return String.valueOf((long)number);
            }

            return String.valueOf(Math.round(number * 100.0F) / 100.0F);
        }
        catch (Exception exception)
        {
            return value;
        }
    }

    private static void closeQuietly(FileInputStream stream)
    {
        if (stream != null)
        {
            try
            {
                stream.close();
            }
            catch (IOException ignored) {}
        }
    }
}
