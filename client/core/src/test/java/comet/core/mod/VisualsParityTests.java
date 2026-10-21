package comet.core.mod;

import comet.core.anim.EyeHeight;
import comet.core.anim.Glint;
import comet.core.anim.HealthBar;
import comet.core.anim.HurtShake;
import comet.core.anim.ItemTransforms;
import comet.core.anim.OldSwing;
import comet.core.anim.Transform;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class VisualsParityTests {
    private static int assertions;

    public static void run(Path root) throws Exception {
        Files.createDirectories(root);
        options(root.resolve("options"));
        swing();
        eyeHeight();
        hurt();
        health();
        glint();
        transforms();
        System.out.println("1.7 Visuals parity: " + assertions + " assertions passed");
    }

    public static void main(String[] args) throws Exception {
        Path base = new File(System.getProperty("java.io.tmpdir"), "redsun").toPath();
        Files.createDirectories(base);
        Path root = Files.createTempDirectory(base, "comet-visuals-");
        try {
            run(root);
        } finally {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator) {
                    Files.delete(file);
                }
            }
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void options(Path root) throws Exception {
        Files.createDirectories(root.resolve("comet"));
        Files.write(root.resolve("comet/settings.json"),
                "{\"mods\":{\"oldAnimations\":false},\"options\":{\"oldAnimations\":{\"blockHit\":false}}}"
                        .getBytes(StandardCharsets.UTF_8));
        ModSettings settings = new ModSettings(root.toFile());
        ModRegistry registry = new ModRegistry(settings);
        OldAnimations visuals = new OldAnimations();
        registry.register(visuals);
        check(!registry.isEnabled(visuals), "The stored 1.7 Visuals switch survives");
        check(!visuals.swings(true) && visuals.swings(false), "Legacy blockHit and useSwing keys are preserved");
        check(visuals.alwaysSwing() && visuals.useItemWhileDigging() && visuals.healthAnimation()
                && visuals.glintAnimation() && visuals.hurtCameraShake() && visuals.sneakAnimation()
                && visuals.itemTransforms() && visuals.thirdPersonHeldItems() && visuals.firstPersonCarpet()
                && visuals.firstPersonFishingRod(), "Every new 1.7 Visuals option defaults to on");
        check(visuals.options().size() == 16 && visuals.numbers().size() == 1,
                "The option set matches the documented legacy keys");
        check(visuals.sneakSpeed() == 1.0, "Sneak speed defaults to 1.0");
        visuals.setNumber(OldAnimations.SNEAK_SPEED, 9999);
        check(visuals.sneakSpeed() == 2.0, "Sneak speed is bounded to 2");
        visuals.setNumber(OldAnimations.SNEAK_SPEED, -9999);
        check(visuals.sneakSpeed() == 0.0, "Sneak speed is bounded to 0");
        visuals.setNumber(OldAnimations.SNEAK_SPEED, 10);
        ModSettings reloaded = new ModSettings(root.toFile());
        ModRegistry reloadedRegistry = new ModRegistry(reloaded);
        OldAnimations restored = new OldAnimations();
        reloadedRegistry.register(restored);
        check(restored.sneakSpeed() == visuals.sneakSpeed(), "Sneak speed survives a restart");
        check(restored.sneakSpeed() > 0.0 && restored.sneakSpeed() < 2.0, "The stored sneak speed is interpolated");
        check(visuals.firstPerson(ItemTransforms.Action.NONE), "Idle items are never filtered out");
        visuals.setOption(OldAnimations.FIRST_PERSON_FOOD, false);
        check(!visuals.firstPerson(ItemTransforms.Action.EAT) && visuals.firstPerson(ItemTransforms.Action.DRINK),
                "Eating and drinking have independent switches");
        check(visuals.firstPerson(ItemTransforms.Action.BLOCK) && visuals.firstPerson(ItemTransforms.Action.BOW),
                "Disabling food does not disable swords or bows");
        visuals.setOption(OldAnimations.BLOCK_HIT, false);
        check(visuals.swingProgress(0.42F, 0.5F) == 0.42F, "Disabled block-hit animation keeps the vanilla swing");
        visuals.setOption(OldAnimations.BLOCK_HIT, true);
    }

    private static void swing() {
        OldSwing timeline = new OldSwing();
        timeline.tick(6, 0.0F, 0.0F, false);
        check(timeline.progress(0.0F, 0.5F) == 0.0F, "An idle timeline reports no swing");

        timeline.reset();
        timeline.tick(6, 0.5F, 0.25F, false);
        check(Math.abs(timeline.progress(0.0F, 0.5F) - 0.375F) < 1e-6,
                "A mid-swing tick mirrors the vanilla counter and interpolates it");
        check(Math.abs(timeline.progress(0.0F, 0.0F) - 0.25F) < 1e-6, "Partial 0 is the previous progress");
        check(Math.abs(timeline.progress(0.0F, 1.0F) - 0.5F) < 1e-6, "Partial 1 is the current progress");
        check(Math.abs(timeline.progress(0.0F, 0.25F) - 0.3125F) < 1e-6, "Partial 0.25 interpolates forward");

        timeline.reset();
        timeline.tick(6, 0.1F, 0.9F, false);
        check(Math.abs(timeline.progress(0.0F, 0.5F) - 1.0F) < 1e-6,
                "A counter wrap is corrected instead of interpolating backwards");

        timeline.reset();
        timeline.tick(6, 0.5F, 0.25F, true);
        check(timeline.progress(0.0F, 1.0F) == 0.0F,
                "The latter half of a swing while digging seeds a fresh counter");
        timeline.tick(6, 0.5F, 0.25F, true);
        check(Math.abs(timeline.progress(0.0F, 1.0F) - 1.0F / 6.0F) < 1e-6,
                "The restarted counter advances one step per tick");
        for (int tick = 0; tick < 5; tick++) {
            timeline.tick(6, 0.5F, 0.25F, true);
        }
        check(Math.abs(timeline.progress(0.0F, 1.0F) - 1.0F) < 1e-6,
                "The end of a swing wraps forward to the cycle boundary");
        timeline.tick(6, 0.5F, 0.25F, false);
        check(Math.abs(timeline.progress(0.0F, 1.0F) - 0.5F) < 1e-6,
                "A finished swing mirrors the vanilla counter again");

        timeline.reset();
        timeline.tick(8, 0.25F, 0.0F, true);
        check(Math.abs(timeline.progress(0.0F, 1.0F) - 0.25F) < 1e-6,
                "A longer potion-altered swing mirrors the first half");
        timeline.tick(8, 0.5F, 0.0F, true);
        timeline.tick(8, 0.5F, 0.0F, true);
        check(Math.abs(timeline.progress(0.0F, 1.0F) - 1.0F / 8.0F) < 1e-6,
                "The same half-way restart applies to a longer swing");
        timeline.reset();
        timeline.tick(4, 0.5F, 0.0F, true);
        check(timeline.progress(0.0F, 1.0F) == 0.0F, "A haste-shortened swing still restarts at half way");

        timeline.reset();
        timeline.tick(6, 0.5F, 0.25F, false);
        check(Math.abs(timeline.progress(0.0F, 1.0F) - 0.5F) < 1e-6,
                "Attack and use over air never restarts the swing");
    }

    private static void eyeHeight() {
        EyeHeight eye = new EyeHeight();
        eye.options(true, 1.0);
        float standing = 1.62F;
        float crouching = standing - EyeHeight.CROUCH_OFFSET;
        check(eye.eyeHeight(standing, 0.5F, false, false) == standing,
                "The first sample seeds the standing eye height");
        check(eye.eyeHeight(standing, 1.0F, true, true) == standing,
                "Crouching does not jump before the smoother ticks");
        eye.tick();
        check(Math.abs(eye.eyeHeight(crouching, 0.5F, true, true) - (standing + crouching) / 2.0F) < 1e-6,
                "A held sneak key drops the eye height and the render pass interpolates it");
        eye.tick();
        check(eye.eyeHeight(crouching, 1.0F, false, true) == crouching,
                "Releasing keeps the last height until the smoother ticks");
        for (int tick = 0; tick < 40; tick++) {
            eye.tick();
        }
        check(eye.eyeHeight(standing, 1.0F, false, false) == standing,
                "The released height converges on the standing baseline");

        EyeHeight moved = new EyeHeight();
        moved.options(true, 1.0);
        moved.eyeHeight(standing, 1.0F, false, false);
        for (int sample = 0; sample < 4; sample++) {
            moved.eyeHeight(1.5F, 1.0F, false, false);
        }
        for (int tick = 0; tick < 40; tick++) {
            moved.tick();
        }
        check(moved.eyeHeight(1.5F, 1.0F, false, false) == standing,
                "Fewer than five identical samples do not move the standing baseline");
        for (int sample = 0; sample < 5; sample++) {
            moved.eyeHeight(1.5F, 1.0F, false, false);
        }
        for (int tick = 0; tick < 40; tick++) {
            moved.tick();
        }
        check(moved.eyeHeight(1.5F, 1.0F, false, false) == 1.5F,
                "Five identical samples adopt a new standing baseline");

        EyeHeight fast = new EyeHeight();
        fast.options(true, 2.0);
        fast.eyeHeight(standing, 1.0F, false, false);
        fast.eyeHeight(crouching, 1.0F, true, true);
        fast.tick();
        fast.eyeHeight(crouching, 1.0F, false, true);
        fast.tick();
        check(fast.eyeHeight(standing, 1.0F, false, false) == standing,
                "A high sneak speed clamps instead of overshooting the baseline");

        EyeHeight slow = new EyeHeight();
        slow.options(true, 1.0);
        slow.eyeHeight(standing, 1.0F, false, false);
        slow.eyeHeight(crouching, 1.0F, true, true);
        slow.tick();
        slow.options(true, 0.0);
        slow.eyeHeight(crouching, 1.0F, false, true);
        slow.tick();
        float slowHeight = slow.eyeHeight(standing, 1.0F, false, false);
        check(slowHeight > crouching && slowHeight < crouching + (standing - crouching) * 0.6F,
                "A zero slider still moves at the minimum speed");

        EyeHeight off = new EyeHeight();
        off.options(false, 1.0);
        check(off.eyeHeight(1.23F, 0.5F, true, true) == 1.23F,
                "Disabled sneak animation leaves the vanilla eye height alone");

        eye.reset();
        check(eye.eyeHeight(1.5F, 1.0F, false, false) == 1.5F, "Leaving a world reseeds the eye height");
        EyeHeight crouchedJoin = new EyeHeight();
        crouchedJoin.eyeHeight(crouching, 1.0F, true, true);
        crouchedJoin.eyeHeight(crouching, 1.0F, true, true);
        crouchedJoin.tick();
        check(Math.abs(crouchedJoin.eyeHeight(crouching, 1.0F, true, true) - crouching) < 1e-6,
                "Enabling while crouched does not subtract the crouch offset twice");
        crouchedJoin.options(false, 1.0);
        crouchedJoin.eyeHeight(standing, 1.0F, false, false);
        crouchedJoin.options(true, 1.0);
        check(crouchedJoin.eyeHeight(standing, 1.0F, false, false) == standing,
                "Re-enabling smoothing seeds the current pose without a stale camera jump");
    }

    private static void hurt() {
        check(HurtShake.remap(0.0F) == 0.0F, "A fresh hit has no shake");
        check(Math.abs(HurtShake.remap((float) Math.PI) - 1.7962156F) < 1e-5,
                "The quartic hurt remap matches the documented formula");
        check(HurtShake.remap((float) Math.PI) < (float) Math.PI, "A full hit shakes less than vanilla");
        float previous = -1.0F;
        for (float angle = 0.0F; angle <= (float) Math.PI; angle += 0.1F) {
            float remapped = HurtShake.remap(angle);
            check(remapped >= previous, "The hurt remap stays monotonic at " + angle);
            previous = remapped;
        }
    }

    private static void health() {
        check(HealthBar.threshold(19, true) == 0 && HealthBar.threshold(19, false) == 19,
                "Health animation only rewrites the display threshold while enabled");
    }

    private static void glint() {
        check(Glint.scale(true) == 8.0F && Glint.scale(false) == 4.0F, "The glint pass scales by context");
        check(Glint.color(true) == 0xFF9078C8 && Glint.color(false) == 0xFF57327B,
                "The glint colours match the documented values");
        check(Glint.firstScroll(0L, false) == 0.0F && Glint.firstScroll(3000L, false) == 0.0F,
                "The first glint pass restarts every 3000 ms");
        check(Math.abs(Glint.firstScroll(1500L, false) - 2.0F * Glint.firstScroll(1500L, true)) < 1e-6,
                "The world glint scroll uses half the GUI scale");
        check(Glint.firstScroll(1500L, false) < 0.25F && Glint.firstScroll(1500L, true) < 0.125F,
                "The glint scroll stays inside one texture tile");
        check(Glint.secondScroll(0L, true) == 0.0F && Glint.secondScroll(4873L, true) == 0.0F,
                "The second glint pass restarts every 4873 ms");
        check(Math.abs(Glint.secondScroll(1218L, false) - 2.0F * Glint.secondScroll(1218L, true)) < 1e-6,
                "The second world glint scroll uses half the GUI scale");
        check(Glint.secondAngle(true) == Glint.FIRST_ANGLE && Glint.secondAngle(false) == 10.0F,
                "The second pass rotates differently in the GUI");
        check(Glint.secondOffset(0.3F, true) == 0.3F && Glint.secondOffset(0.3F, false) == -0.3F,
                "The second pass scrolls backwards outside the GUI");
    }

    private static void transforms() {
        check(ItemTransforms.bowPull(19, 20, 0.0F) == 0.0F, "A bow draw starts at pull 0");
        check(ItemTransforms.bowPull(0, 20, 1.0F) == 1.0F, "A fully drawn bow reaches pull 1");
        check(Math.abs(ItemTransforms.bowPull(10, 20, 0.0F) - 0.3675F) < 1e-6, "Bow pull follows the documented curve");
        check(ItemTransforms.bowPull(9, 20, 0.0F) > ItemTransforms.bowPull(10, 20, 0.0F),
                "A longer bow draw pulls further");

        Ops bow = new Ops();
        ItemTransforms.bow(bow, 17, 20, 0.0F);
        check(bow.ops.size() == 12, "A bow pull below 0.1 skips the bobbing offset");
        Ops bobbing = new Ops();
        ItemTransforms.bow(bobbing, 16, 20, 0.0F);
        check(bobbing.ops.size() == 13, "A bow pull above 0.1 adds the bobbing offset");
        check(bow.ops.get(0).equals("r-18.0,0.0,0.0,1.0") && bow.ops.get(1).equals("r-12.0,0.0,1.0,0.0")
                && bow.ops.get(2).equals("r-8.0,1.0,0.0,0.0") && bow.ops.get(3).equals("t-0.9,0.2,0.0"),
                "The bow recipe starts with the documented rotations and translation");
        check(bow.ops.get(bow.ops.size() - 1).equals("r335.0,0.0,0.0,1.0")
                && bow.ops.get(bow.ops.size() - 2).equals("r50.0,0.0,1.0,0.0"),
                "The bow recipe ends with the mirrored rotations");

        Ops flat = new Ops();
        ItemTransforms.flatItem(flat);
        check(flat.ops.toString().equals("[t0.588,0.365,-0.795, t0.0,-0.3,0.0, s1.5,1.5,1.5, r50.0,0.0,1.0,0.0,"
                + " r335.0,0.0,0.0,1.0, t-0.9375,-0.0625,0.0, s-1.0,1.0,-1.0]"),
                "The flat item conversion matches the documented order");

        Ops carpet = new Ops();
        ItemTransforms.carpet(carpet);
        check(carpet.ops.toString().equals("[s1.1764705,1.1764705,1.1764705, r-25.0,0.0,0.0,1.0, r135.0,0.0,1.0,0.0,"
                + " t0.0,-0.25,-0.125, s0.5,0.5,0.5]"), "The carpet conversion matches the documented order");

        Ops block = new Ops();
        ItemTransforms.thirdPersonBlock(block);
        check(block.ops.toString().equals("[t-0.05,0.1,-0.12, r-45.0,0.0,1.0,0.0, r-30.0,1.0,0.0,0.0,"
                + " r-60.0,0.0,0.0,1.0]"), "The third-person blocking adjustment matches the documented order");

        Ops tool = new Ops();
        ItemTransforms.thirdPersonHeld(tool, ItemTransforms.ThirdPerson.TOOL, false, false);
        check(tool.ops.toString().equals("[r20.0,-1.0,0.0,0.0, r5.0,0.0,0.0,1.0, t-0.01,0.03,0.1]"),
                "An idle tool gets the documented third-person adjustment");
        Ops toolBlocking = new Ops();
        ItemTransforms.thirdPersonHeld(toolBlocking, ItemTransforms.ThirdPerson.TOOL, true, false);
        check(toolBlocking.ops.toString().equals("[r15.0,0.0,1.0,0.0, t0.1,0.08,0.0]"),
                "A blocking tool gets the documented third-person adjustment");
        Ops bowThird = new Ops();
        ItemTransforms.thirdPersonHeld(bowThird, ItemTransforms.ThirdPerson.BOW, false, false);
        check(bowThird.ops.toString().equals("[t0.03,-0.05,-0.1, r10.0,-1.5,-1.0,1.0]"),
                "A third-person bow gets the documented adjustment");
        Ops standing = new Ops();
        ItemTransforms.thirdPersonHeld(standing, ItemTransforms.ThirdPerson.OTHER, false, false);
        Ops sneaking = new Ops();
        ItemTransforms.thirdPersonHeld(sneaking, ItemTransforms.ThirdPerson.OTHER, false, true);
        check(standing.ops.get(1).equals("t0.0,-0.295,-0.03") && sneaking.ops.get(1).equals("t0.0,-0.695,-0.03"),
                "A sneaking entity lowers its third-person held item");
        Ops flatThird = new Ops();
        ItemTransforms.thirdPersonHeld(flatThird, ItemTransforms.ThirdPerson.FLAT, false, false);
        check(flatThird.ops.isEmpty(), "Flat third-person items keep the vanilla transform");

        check(ItemTransforms.allows(ItemTransforms.Action.EAT, true, false, false, false)
                && !ItemTransforms.allows(ItemTransforms.Action.EAT, false, true, true, true)
                && !ItemTransforms.allows(ItemTransforms.Action.DRINK, true, false, false, false)
                && ItemTransforms.allows(ItemTransforms.Action.DRINK, false, true, false, false)
                && ItemTransforms.allows(ItemTransforms.Action.BLOCK, false, false, true, false)
                && !ItemTransforms.allows(ItemTransforms.Action.BOW, true, true, true, false)
                && ItemTransforms.allows(ItemTransforms.Action.NONE, false, false, false, false),
                "The item filter follows the per-item toggles");
    }

    private static final class Ops implements Transform {
        final List<String> ops = new ArrayList<String>();

        @Override
        public void translate(float x, float y, float z) {
            ops.add("t" + x + "," + y + "," + z);
        }

        @Override
        public void rotate(float angle, float x, float y, float z) {
            ops.add("r" + angle + "," + x + "," + y + "," + z);
        }

        @Override
        public void scale(float x, float y, float z) {
            ops.add("s" + x + "," + y + "," + z);
        }
    }
}
