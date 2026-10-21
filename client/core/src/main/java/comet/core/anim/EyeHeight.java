package comet.core.anim;

public final class EyeHeight {
    public static final float CROUCH_OFFSET = 0.08F;
    private static final float RELEASE = 0.6F;
    private static final float SNEAK_KEY_RELEASE = 1.0F;
    private static final float MIN_SPEED = 0.1F;
    private static final int STABLE_TICKS = 5;

    private boolean animation = true;
    private float speed = 1.0F;
    private boolean sneaking;
    private boolean seeded;
    private float baseline;
    private float lastIncoming;
    private int stableTicks;
    private float height;
    private float previousHeight;
    private float target;

    public void options(boolean animation, double speed) {
        if (this.animation != animation) reset();
        this.animation = animation;
        this.speed = (float) speed;
    }

    public void tick() {
        previousHeight = height;
        if (height == target) {
            return;
        }
        float factor = sneaking ? SNEAK_KEY_RELEASE : RELEASE;
        height += (target - height) * factor * speed();
        if (previousHeight > height && height < target || height > previousHeight && height > target) {
            height = target;
        }
    }

    public float eyeHeight(float incoming, float partial, boolean sneakKeyDown, boolean playerSneaking) {
        if (!animation) {
            return incoming;
        }
        sneaking = sneakKeyDown;
        if (!seeded) {
            seeded = true;
            baseline = incoming + (playerSneaking ? CROUCH_OFFSET : 0.0F);
            lastIncoming = incoming;
            height = incoming;
            previousHeight = incoming;
            target = incoming;
            return incoming;
        }
        if (stableTicks >= STABLE_TICKS && !sneaking && !playerSneaking) {
            baseline = incoming;
        }
        if (lastIncoming == incoming) {
            stableTicks++;
        } else {
            lastIncoming = incoming;
            stableTicks = 0;
        }
        target = sneaking ? baseline - CROUCH_OFFSET : baseline;
        return previousHeight + (height - previousHeight) * partial;
    }

    private float speed() {
        return Math.max(MIN_SPEED, speed);
    }

    public void reset() {
        seeded = false;
        sneaking = false;
        baseline = 0.0F;
        lastIncoming = 0.0F;
        stableTicks = 0;
        height = 0.0F;
        previousHeight = 0.0F;
        target = 0.0F;
    }
}
