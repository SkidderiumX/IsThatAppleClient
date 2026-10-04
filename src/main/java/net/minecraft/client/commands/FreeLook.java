package net.minecraft.client.commands;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

/**
 * Free look: the camera swings off the player's body rotation while the bind is held, or
 * while a toggle is on, so you can aim sideways without turning your character.
 *
 * <p>Vanilla funnels every mouse-look update through {@code EntityPlayerSP.setAngles}, which
 * adds to the body rotation. That single call is where this class takes over: while free look
 * is active the deltas accumulate into private yaw/pitch offsets instead, and the body never
 * turns. {@code EntityRenderer.orientCamera} adds the offsets back onto the view, and
 * {@code EntityPlayerSP.getLook} adds them too so the crosshair, the block outline and block
 * breaking follow the camera rather than the body.</p>
 *
 * <p>The body rotation is deliberately never modified. That keeps walking direction, the
 * rendered player model, and the packets sent to the server all matching each other, which is
 * what makes this behave instead of just look different.</p>
 *
 * <p>{@link View} restricts free look to first person, third person, or both. When the current
 * view is not allowed the bind is ignored outright and any banked offset is dropped, so the
 * camera is always exactly where the view restriction says it should be.</p>
 *
 * <p>{@link #setAutoF5} optionally takes the camera with it: engaging free look moves to the view
 * behind the player and releasing hands it back to first person. It is the one thing here that
 * touches a setting the player did not change by hand, so it only fires on the engage and release
 * edges, never while free look is being held, and it stands down entirely when {@link View} is
 * {@link View#FIRST}.</p>
 *
 * <p>Settings are persisted in {@value ClientConfig#FILE_NAME} in the game directory, alongside
 * every other mod setting. The key itself is a plain vanilla key bind, so it appears under
 * Options &gt; Controls and is stored in options.txt.</p>
 */
public final class FreeLook
{
    /** How the bind turns free look on. */
    public enum Mode
    {
        /** Free look is on exactly while the key is down. */
        HOLD,
        /** Pressing the key flips free look until pressed again. */
        TOGGLE
    }

    /** Which camera view free look is allowed to work in. */
    public enum View
    {
        /** Both first and third person, which is how free look has always behaved. */
        BOTH,
        /** Only with the camera behind the player. */
        FIRST,
        /** Only with the camera in front of the player. */
        THIRD
    }

    /** Largest free-look pitch offset, in degrees. Past this the world would start to invert. */
    private static final float MAX_PITCH = 90.0F;

    /** Time constant for easing the camera back onto the body, in seconds. */
    private static final double RETURN_SECONDS = 0.09D;

    /** Below this an offset snaps to zero, so easing cannot leave a permanent sliver behind. */
    private static final float EPSILON = 0.01F;

    /** Multiplier vanilla's {@code Entity.setAngles} applies to raw mouse deltas. */
    private static final float VANILLA_GAIN = 0.15F;

    private static Mode mode = Mode.HOLD;

    /** Which view the bind is allowed to work in. */
    private static View view = View.BOTH;

    /**
     * Whether engaging free look also moves the camera to third person.
     *
     * <p>First person puts the camera inside the player's head, so the offset is hard to judge
     * against the crosshair. See {@link #autoF5Applies()} for exactly when this takes effect.</p>
     */
    private static boolean autoF5;

    /**
     * True while auto-F5 is the reason the camera is in third person.
     *
     * <p>Only the switch that moved the camera is allowed to move it back. Without this the
     * release would drag the camera back to first person even for a player who was already in
     * third person before ever touching free look.</p>
     */
    private static boolean autoF5Held;

    /** Master switch. Nothing happens while this is false, whatever the bind says. */
    private static boolean enabled = true;

    /** Camera yaw offset from the body, in degrees. */
    private static float yaw;

    /** Camera pitch offset from the body, in degrees. */
    private static float pitch;

    /**
     * The offset as it stood on the last engaged frame.
     *
     * <p>Hold mode starts easing the live offset away the instant the bind comes up, with a
     * {@value #RETURN_SECONDS} second time constant, so by the time a chat command has been typed
     * the live offset is already zero. These keep the direction the camera was actually pointing
     * so there is still something to act on. Cleared wherever the live offsets are cleared, since
     * a reset, a disable or a view the player cannot free look in all invalidate it just as
     * thoroughly.</p>
     */
    private static float lastYaw;

    /** The pitch half of {@link #lastYaw}. */
    private static float lastPitch;

    /** True while the camera is being held off the body. */
    private static boolean active;

    /** Toggle mode's latched state. */
    private static boolean latched;

    /** Key state last frame, so toggle mode can spot the press edge. */
    private static boolean keyWasDown;

    /** Wall clock of the last mouse-look update, so easing is frame-rate independent. */
    private static long lastMs;

    private FreeLook() {}

    // --- accessors ---------------------------------------------------------------

    public static Mode getMode()
    {
        return mode;
    }

    public static void setMode(Mode value)
    {
        mode = value == null ? Mode.HOLD : value;
        latched = false;
        save();
    }

    public static View getView()
    {
        return view;
    }

    /**
     * Restricts free look to one view, or {@link View#BOTH} to lift the restriction.
     *
     * <p>Switching away from the current view has to drop any offset already banked, otherwise
     * the camera would sit off the body in a view where the bind is supposed to be inert.</p>
     */
    public static void setView(View value)
    {
        view = value == null ? View.BOTH : value;
        save();
        dropIfDisallowed();

        if (view == View.FIRST)
        {
            // Auto-F5 cannot act in first person, so it has to give the camera back rather than
            // hold it in a view the restriction now forbids.
            releaseAutoF5();
        }
    }

    /**
     * Whether free look is allowed to act in the given view.
     *
     * @param thirdPerson true when the camera is behind the player
     */
    public static boolean allowsView(boolean thirdPerson)
    {
        switch (view)
        {
            case FIRST:
                return !thirdPerson;

            case THIRD:
                return thirdPerson;

            default:
                return true;
        }
    }

    /**
     * Whether engaging free look should also move the camera to third person.
     *
     * <p>Turning this on overrides whatever view the player is in: free look goes to the view
     * behind the player and hands it back to first person on release. A {@link View#FIRST}
     * restriction is the one exception, since auto-F5 and "never in third person" cannot both be
     * honoured, so there the switch stays inert rather than quietly overriding the
     * restriction.</p>
     */
    public static boolean isAutoF5()
    {
        return autoF5;
    }

    /**
     * Turns the automatic third-person switch on or off.
     *
     * <p>Turning it off while it currently holds the camera hands the camera back immediately,
     * so the view is never left somewhere free look would not have put it. A player who was
     * already in third person is left exactly where they were.</p>
     */
    public static void setAutoF5(boolean value)
    {
        if (autoF5 == value)
        {
            return;
        }

        autoF5 = value;

        if (!autoF5)
        {
            releaseAutoF5();
        }

        save();
    }

    /** True when auto-F5 is on and the view restriction leaves it room to act. */
    private static boolean autoF5Applies()
    {
        return autoF5 && view != View.FIRST;
    }

    /** Third person from behind, which is the view auto-F5 asks for. */
    private static final int THIRD_PERSON_BACK = 1;

    /** First person, which is where auto-F5 hands the camera back. */
    private static final int FIRST_PERSON = 0;

    /**
     * Applies the auto-F5 camera move for an engage or a release.
     *
     * <p>Engaging sends the camera behind the player. Releasing only does anything if the engage
     * is what moved the camera, so free look never claims a view the player chose themselves.
     * Nothing happens unless the switch is on and {@link View#FIRST} is not in force.</p>
     *
     * @param toThirdPerson true on engage, false on release
     */
    private static void applyAutoF5(boolean toThirdPerson)
    {
        if (!autoF5Applies())
        {
            return;
        }

        if (toThirdPerson)
        {
            setThirdPersonView(THIRD_PERSON_BACK);
            autoF5Held = true;
        }
        else
        {
            releaseAutoF5();
        }
    }

    /**
     * Hands the camera back to first person, but only if auto-F5 is the one that moved it.
     *
     * <p>Safe to call at any time and more than once: the flag is cleared whether or not a game
     * is running to move a camera in.</p>
     */
    private static void releaseAutoF5()
    {
        if (!autoF5Held)
        {
            return;
        }

        autoF5Held = false;
        setThirdPersonView(FIRST_PERSON);
    }

    /**
     * Writes the camera view, ignoring the headless case where there is no game settings object.
     */
    private static void setThirdPersonView(int value)
    {
        GameSettings settings = gameSettings();

        if (settings == null)
        {
            // No game running, which is what the headless tests look like.
            return;
        }

        settings.thirdPersonView = value;
    }

    public static boolean isEnabled()
    {
        return enabled;
    }

    public static void setEnabled(boolean value)
    {
        enabled = value;
        latched = false;
        clearOffsets();

        if (!enabled)
        {
            // Cleared here rather than left to the next engage edge, since this drops the offsets
            // immediately whether or not the game is ticking.
            releaseAutoF5();
        }

        save();
    }

    /** True while the camera is currently off the body. */
    public static boolean isActive()
    {
        return active;
    }

    /**
     * Camera yaw offset in degrees. Zero unless free look is in use, so the camera hooks can
     * add it blindly. It stays non-zero for a moment after the bind is released while the
     * view eases back onto the body.
     */
    public static float getYaw()
    {
        return yaw;
    }

    /** Camera pitch offset in degrees. See {@link #getYaw()}. */
    public static float getPitch()
    {
        return pitch;
    }

    /**
     * Yaw offset to actually apply, or zero when free look is not permitted in the current view.
     *
     * <p>The camera hooks call this rather than {@link #getYaw()} so the view restriction holds
     * in one place. Without it an offset banked in an allowed view would survive the F5 key
     * press that moves the camera into a disallowed one, because the offsets are only refreshed
     * once a tick while the camera is drawn every frame.</p>
     *
     * @param thirdPerson true when the camera is behind the player
     */
    public static float getYawOffset(boolean thirdPerson)
    {
        return allowsView(thirdPerson) ? yaw : 0.0F;
    }

    /** Pitch offset to actually apply. See {@link #getYawOffset(boolean)}. */
    public static float getPitchOffset(boolean thirdPerson)
    {
        return allowsView(thirdPerson) ? pitch : 0.0F;
    }

    /**
     * Whether free look is permitted in the view the player is actually in right now. Treated as
     * permitted whenever the game is not running, so the state machine stays testable headless.
     */
    private static boolean allowsCurrentView()
    {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null || mc.gameSettings == null || allowsView(mc.gameSettings.thirdPersonView > 0);
    }

    /**
     * Drops the toggle and any banked offset when the current view forbids free look. Called both
     * when the restriction changes and on every mouse update, so pressing F5 into a disallowed
     * view hands the camera straight back to the body instead of leaving it stranded off it.
     */
    private static void dropIfDisallowed()
    {
        if (!allowsCurrentView())
        {
            latched = false;
            clearOffsets();
        }
    }

    // --- mouse handling ----------------------------------------------------------

    /**
     * Called from {@code EntityPlayerSP.setAngles} with the raw per-frame mouse deltas.
     *
     * @return true if the deltas were consumed and the body must not be turned
     */
    public static boolean onMouseLook(float yawDelta, float pitchDelta)
    {
        // A disallowed view wins over the bind. The deltas are handed to the body untouched and
        // the offset is dropped outright rather than eased, so F5 lands the camera exactly where
        // it was instead of drifting back over a few frames.
        if (!allowsCurrentView())
        {
            dropIfDisallowed();
            return false;
        }

        KeyBinding bind = bind();

        if (bind == null || bind.getKeyCode() == 0)
        {
            // Nothing is bound, so a latched toggle would otherwise survive forever.
            latched = false;
            return step(yawDelta, pitchDelta, false);
        }

        return step(yawDelta, pitchDelta, GameSettings.isKeyDown(bind));
    }

    /**
     * The whole state machine, with the bind state handed in.
     *
     * <p>Package-private so it can be exercised without a running game, where there is no
     * {@code Minecraft} to ask about a key bind.</p>
     *
     * @return true if the deltas were consumed and the body must not be turned
     */
    static boolean step(float yawDelta, float pitchDelta, boolean down)
    {
        long now = System.currentTimeMillis();
        float elapsed = lastMs == 0L ? 0.0F : (now - lastMs) / 1000.0F;
        lastMs = now;

        boolean wasActive = active;

        if (!enabled)
        {
            latched = false;
            active = false;
        }
        else
        {
            if (mode == Mode.TOGGLE && down && !keyWasDown)
            {
                latched = !latched;
            }

            active = mode == Mode.HOLD ? down : latched;
        }

        if (active != wasActive)
        {
            // On an edge only, so holding free look never fights a player pressing F5 themselves.
            applyAutoF5(active);
        }

        keyWasDown = down;

        if (active)
        {
            // Same gain and pitch sign as Entity.setAngles, so releasing the bind hands the
            // mouse straight back to the body with no jump.
            yaw = wrapDegrees(yaw + yawDelta * VANILLA_GAIN);
            pitch = clampPitch(pitch - pitchDelta * VANILLA_GAIN);
            // Remembered every engaged frame, so a release that then eases the live offset to
            // zero still leaves the direction the camera was pointing.
            lastYaw = yaw;
            lastPitch = pitch;
            return true;
        }

        if (yaw != 0.0F || pitch != 0.0F)
        {
            easeBack(elapsed);
            return true;
        }

        return false;
    }

    /**
     * Exponential approach to zero. Both the offsets and the elapsed time are real, so this
     * looks the same at 30 fps and at 240 fps.
     */
    private static void easeBack(float elapsed)
    {
        if (elapsed <= 0.0F)
        {
            return;
        }

        float factor = (float)Math.exp(-elapsed / RETURN_SECONDS);
        yaw *= factor;
        pitch *= factor;

        if (Math.abs(yaw) < EPSILON)
        {
            yaw = 0.0F;
        }

        if (Math.abs(pitch) < EPSILON)
        {
            pitch = 0.0F;
        }
    }

    private static void clearOffsets()
    {
        yaw = 0.0F;
        pitch = 0.0F;
        // Taken with the live offsets rather than left behind: a disable, a reset or a switch to
        // a view free look may not use all mean the player is no longer pointing that way, so a
        // commit offered afterwards would turn the body somewhere they are not looking.
        lastYaw = 0.0F;
        lastPitch = 0.0F;
        active = false;
    }

    // --- committing the offset onto the body ---------------------------------------

    /**
     * The offset that a commit would apply right now: the live one if free look is still
     * engaged, otherwise the direction the camera was last pointing.
     */
    private static float pendingYaw()
    {
        return yaw != 0.0F || pitch != 0.0F ? yaw : lastYaw;
    }

    /** The pitch half of {@link #pendingYaw()}. */
    private static float pendingPitch()
    {
        return yaw != 0.0F || pitch != 0.0F ? pitch : lastPitch;
    }

    /** Yaw that {@link #commitTo} would apply, for reporting it before the player commits. */
    public static float getPendingYaw()
    {
        return pendingYaw();
    }

    /** Pitch that {@link #commitTo} would apply. See {@link #getPendingYaw()}. */
    public static float getPendingPitch()
    {
        return pendingPitch();
    }

    /** Whether there is any offset to commit, live or last engaged. */
    public static boolean hasOffset()
    {
        return pendingYaw() != 0.0F || pendingPitch() != 0.0F;
    }

    /**
     * Folds the banked offset into the player's real rotation, so the body turns to face where
     * the free look camera was pointing. The new look vector is the one the player was already
     * looking along; what changes is that the body now agrees with it, which is what walking,
     * sprinting, the rendered player model and the packets sent to the server all read.
     *
     * <p>Every consumer adds the offset on top of a body field, and the camera and
     * {@code EntityLivingBase.getLook} read an interpolation between {@code prevRotation*} and
     * {@code rotation*}. So each current field and the previous one it interpolates from have to
     * move by the same amount. That leaves every interpolated base exactly where it was and the
     * offset dropped from on top of it, so the view does not move at all: the body simply arrives
     * at the place the offset was already describing.</p>
     *
     * <p>Yaw, head yaw and pitch are all shifted. The camera reads {@code rotationYaw} while
     * {@code getLook} reads {@code rotationYawHead}, so leaving either behind would split the two
     * apart by the offset and the crosshair would stop matching the camera.</p>
     *
     * <p>The shape is {@code Entity.setAngles}'s: change the current field, then move the
     * previous one by however much the change actually turned out to be. That is what makes a
     * pitch offset which runs past straight up or down come out right - the body stops at 90
     * degrees, and the view moves by the part of the offset the body could legally hold rather
     * than by all of it.</p>
     *
     * <p>The new rotation is handed to the normal movement packet, so a server learns about it on
     * the next tick; {@code EntityPlayerSP.onUpdateWalkingPlayer} sends the angles whenever they
     * differ at all, however small.</p>
     *
     * @param player the body to turn, normally the local player
     * @return true if an offset was committed
     */
    public static boolean commitTo(EntityLivingBase player)
    {
        if (player == null || !hasOffset())
        {
            return false;
        }

        float commitYaw = pendingYaw();
        float commitPitch = pendingPitch();

        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;
        float head = player.rotationYawHead;

        // Left unwrapped, exactly as vanilla accumulates rotation, so the difference below is the
        // offset and nothing else. Every reader folds it back into a single turn for display.
        player.rotationYaw = yaw + commitYaw;
        player.rotationPitch = MathHelper.clamp_float(pitch + commitPitch, -MAX_PITCH, MAX_PITCH);
        player.rotationYawHead = head + commitYaw;

        player.prevRotationYaw += player.rotationYaw - yaw;
        player.prevRotationPitch += player.rotationPitch - pitch;
        player.prevRotationYawHead += player.rotationYawHead - head;

        clearOffsets();
        return true;
    }

    private static KeyBinding bind()
    {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null || mc.gameSettings == null ? null : mc.gameSettings.keyBindFreeLook;
    }

    /** The live game settings, or null off a real game, which is what the headless tests see. */
    private static GameSettings gameSettings()
    {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null ? null : mc.gameSettings;
    }

    /** Keeps the offset inside a single turn, so the trig in the camera stays well conditioned. */
    private static float wrapDegrees(float value)
    {
        value %= 360.0F;

        if (value >= 180.0F)
        {
            value -= 360.0F;
        }
        else if (value < -180.0F)
        {
            value += 360.0F;
        }

        return value;
    }

    private static float clampPitch(float value)
    {
        return Math.max(-MAX_PITCH, Math.min(MAX_PITCH, value));
    }

    // --- persistence -------------------------------------------------------------

    /**
     * The single config file in the game directory.
     *
     * <p>Kept as a method on each mod so the commands can name the file without knowing that
     * {@link ClientConfig} owns it.</p>
     */
    public static File getConfigFile()
    {
        return ClientConfig.getConfigFile();
    }

    /** Restores every default in memory, including the runtime offsets. */
    static void resetToDefaults()
    {
        mode = Mode.HOLD;
        view = View.BOTH;
        enabled = true;
        // Before the flag is dropped, so a reset taken while free look was holding the camera
        // hands it back rather than stranding it in a view nothing is now tracking.
        releaseAutoF5();
        autoF5 = false;
        autoF5Held = false;
        clearOffsets();
        latched = false;
        keyWasDown = false;
        lastMs = 0L;
    }

    /** Restores every default and rewrites the config file. */
    public static void reset()
    {
        resetToDefaults();
        save();
    }

    /** Writes the config file. Never throws. */
    public static void save()
    {
        ClientConfig.save();
    }
}
