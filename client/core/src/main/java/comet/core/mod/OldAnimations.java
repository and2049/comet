package comet.core.mod;

import comet.core.anim.EyeHeight;
import comet.core.anim.ItemTransforms;
import comet.core.anim.OldSwing;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class OldAnimations extends Mod {
    public static final Option BLOCK_HIT = new Option("blockHit", "Block-hitting swing", true);
    public static final Option USE_SWING = new Option("useSwing", "Swing while eating, drinking or drawing", true);
    public static final Option ALWAYS_SWING = new Option("alwaysSwing", "Swing on every attack click", true);
    public static final Option USE_WHILE_DIGGING = new Option("useItemWhileDigging", "Use items while digging", true);
    public static final Option HEALTH = new Option("healthAnimation", "1.7 health animation", true);
    public static final Option GLINT = new Option("glintAnimation", "1.7 glint animation", true);
    public static final Option HURT = new Option("hurtCameraShake", "1.7 hurt camera shake", true);
    public static final Option SNEAK = new Option("sneakAnimation", "Smooth sneak eye height", true);
    public static final NumberOption SNEAK_SPEED = NumberOption.decimal("sneakSpeed", "Sneak speed", 1.0, 0.0, 2.0, 1);
    public static final Option ITEM_TRANSFORMS = new Option("itemTransforms", "1.7 first-person item transforms", true);
    public static final Option THIRD_PERSON = new Option("thirdPersonHeldItems", "1.7 third-person held items", true);
    public static final Option FIRST_PERSON_SWORD = new Option("firstPersonSword", "1.7 sword", true);
    public static final Option FIRST_PERSON_BOW = new Option("firstPersonBow", "1.7 bow", true);
    public static final Option FIRST_PERSON_ROD = new Option("firstPersonFishingRod", "1.7 fishing rod", true);
    public static final Option FIRST_PERSON_CARPET = new Option("firstPersonCarpet", "1.7 blocks", true);
    public static final Option FIRST_PERSON_POTIONS = new Option("firstPersonPotions", "1.7 potions", true);
    public static final Option FIRST_PERSON_FOOD = new Option("firstPersonFood", "1.7 food", true);

    private final OldSwing swing = new OldSwing();
    private final EyeHeight eyeHeight = new EyeHeight();

    @Override
    public String id() {
        return "oldAnimations";
    }

    @Override
    public String name() {
        return "1.7 Visuals";
    }

    @Override
    public String description() {
        return "Brings the 1.7 first-person animations to 1.8.";
    }

    @Override
    public Category category() {
        return Category.VISUAL;
    }

    @Override
    public String glyph() {
        return "1.7";
    }

    @Override
    public List<Option> options() {
        return Arrays.asList(BLOCK_HIT, USE_SWING, ALWAYS_SWING, USE_WHILE_DIGGING, HEALTH, GLINT, HURT, SNEAK,
                ITEM_TRANSFORMS, THIRD_PERSON, FIRST_PERSON_SWORD, FIRST_PERSON_BOW, FIRST_PERSON_ROD,
                FIRST_PERSON_CARPET, FIRST_PERSON_POTIONS, FIRST_PERSON_FOOD);
    }

    @Override
    public List<NumberOption> numbers() {
        return Collections.singletonList(SNEAK_SPEED);
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    public boolean swings(boolean blocking) {
        return option(blocking ? BLOCK_HIT : USE_SWING);
    }

    public boolean alwaysSwing() {
        return option(ALWAYS_SWING);
    }

    public boolean useItemWhileDigging() {
        return option(USE_WHILE_DIGGING);
    }

    public boolean healthAnimation() {
        return option(HEALTH);
    }

    public boolean glintAnimation() {
        return option(GLINT);
    }

    public boolean hurtCameraShake() {
        return option(HURT);
    }

    public boolean sneakAnimation() {
        return option(SNEAK);
    }

    public double sneakSpeed() {
        return decimal(SNEAK_SPEED);
    }

    public boolean itemTransforms() {
        return option(ITEM_TRANSFORMS);
    }

    public boolean thirdPersonHeldItems() {
        return option(THIRD_PERSON);
    }

    public boolean firstPersonCarpet() {
        return option(FIRST_PERSON_CARPET);
    }

    public boolean firstPersonFishingRod() {
        return option(FIRST_PERSON_ROD);
    }

    public boolean firstPerson(ItemTransforms.Action action) {
        return ItemTransforms.allows(action, option(FIRST_PERSON_FOOD), option(FIRST_PERSON_POTIONS),
                option(FIRST_PERSON_SWORD), option(FIRST_PERSON_BOW));
    }

    public void tickSwing(int duration, float swingAtFull, float swingAtZero, boolean usingWhileDigging) {
        if (option(BLOCK_HIT)) {
            swing.tick(duration, swingAtFull, swingAtZero, usingWhileDigging);
        }
    }

    public float swingProgress(float vanilla, float partial) {
        return option(BLOCK_HIT) ? swing.progress(vanilla, partial) : vanilla;
    }

    public void tickEyeHeight() {
        eyeHeight.options(option(SNEAK), decimal(SNEAK_SPEED));
        eyeHeight.tick();
    }

    public float eyeHeight(float incoming, float partial, boolean sneakKeyDown, boolean playerSneaking) {
        eyeHeight.options(option(SNEAK), decimal(SNEAK_SPEED));
        return eyeHeight.eyeHeight(incoming, partial, sneakKeyDown, playerSneaking);
    }

    @Override
    public void reset() {
        swing.reset();
        eyeHeight.reset();
    }
}
