package comet.core.anim;

public final class ItemTransforms {
    public enum Action {
        NONE,
        EAT,
        DRINK,
        BLOCK,
        BOW
    }

    public enum ThirdPerson {
        TOOL,
        BOW,
        FLAT,
        OTHER
    }

    public static final float BOW_DRAW_TICKS = 20.0F;

    private ItemTransforms() {
    }

    public static boolean allows(Action action, boolean food, boolean potions, boolean sword, boolean bow) {
        switch (action) {
            case EAT:
                return food;
            case DRINK:
                return potions;
            case BLOCK:
                return sword;
            case BOW:
                return bow;
            default:
                return true;
        }
    }

    public static float bowPull(int remaining, int maxUse, float partial) {
        float ticks = maxUse - (remaining - partial + 1.0F);
        float pull = ticks / BOW_DRAW_TICKS;
        pull = (pull * pull + pull * 2.0F) / 3.0F;
        return pull > 1.0F ? 1.0F : pull;
    }

    public static void bow(Transform transform, int remaining, int maxUse, float partial) {
        float ticks = maxUse - (remaining - partial + 1.0F);
        float pull = bowPull(remaining, maxUse, partial);
        transform.rotate(-18.0F, 0.0F, 0.0F, 1.0F);
        transform.rotate(-12.0F, 0.0F, 1.0F, 0.0F);
        transform.rotate(-8.0F, 1.0F, 0.0F, 0.0F);
        transform.translate(-0.9F, 0.2F, 0.0F);
        if (pull > 0.1F) {
            transform.translate(0.0F, (float) Math.sin((ticks - 0.1F) * 1.3F) * 0.01F * (pull - 0.1F), 0.0F);
        }
        transform.translate(0.0F, 0.0F, pull * 0.1F);
        transform.rotate(-335.0F, 0.0F, 0.0F, 1.0F);
        transform.rotate(-50.0F, 0.0F, 1.0F, 0.0F);
        transform.translate(0.0F, 0.5F, 0.0F);
        transform.scale(1.0F, 1.0F, 1.0F + pull * 0.2F);
        transform.translate(0.0F, -0.5F, 0.0F);
        transform.rotate(50.0F, 0.0F, 1.0F, 0.0F);
        transform.rotate(335.0F, 0.0F, 0.0F, 1.0F);
    }

    public static void flatItem(Transform transform) {
        transform.translate(0.588F, 0.365F, -0.795F);
        transform.translate(0.0F, -0.3F, 0.0F);
        transform.scale(1.5F, 1.5F, 1.5F);
        transform.rotate(50.0F, 0.0F, 1.0F, 0.0F);
        transform.rotate(335.0F, 0.0F, 0.0F, 1.0F);
        transform.translate(-0.9375F, -0.0625F, 0.0F);
        transform.scale(-1.0F, 1.0F, -1.0F);
    }

    public static void carpet(Transform transform) {
        transform.scale(1.1764705F, 1.1764705F, 1.1764705F);
        transform.rotate(-25.0F, 0.0F, 0.0F, 1.0F);
        transform.rotate(135.0F, 0.0F, 1.0F, 0.0F);
        transform.translate(0.0F, -0.25F, -0.125F);
        transform.scale(0.5F, 0.5F, 0.5F);
    }

    public static void thirdPersonBlock(Transform transform) {
        transform.translate(-0.05F, 0.1F, -0.12F);
        transform.rotate(-45.0F, 0.0F, 1.0F, 0.0F);
        transform.rotate(-30.0F, 1.0F, 0.0F, 0.0F);
        transform.rotate(-60.0F, 0.0F, 0.0F, 1.0F);
    }

    public static void thirdPersonHeld(Transform transform, ThirdPerson kind, boolean blocking, boolean sneaking) {
        switch (kind) {
            case TOOL:
                if (blocking) {
                    transform.rotate(15.0F, 0.0F, 1.0F, 0.0F);
                    transform.translate(0.1F, 0.08F, 0.0F);
                } else {
                    transform.rotate(20.0F, -1.0F, 0.0F, 0.0F);
                    transform.rotate(5.0F, 0.0F, 0.0F, 1.0F);
                    transform.translate(-0.01F, 0.03F, 0.1F);
                }
                break;
            case BOW:
                transform.translate(0.03F, -0.05F, -0.1F);
                transform.rotate(10.0F, -1.5F, -1.0F, 1.0F);
                break;
            case FLAT:
                break;
            default:
                transform.rotate(180.0F, 0.0F, 0.0F, 1.0F);
                transform.translate(0.0F, sneaking ? -0.695F : -0.295F, -0.03F);
                transform.rotate(13.5F, 1.0F, 0.15F, 0.0F);
                transform.rotate(15.0F, 0.0F, 0.9F, 0.8F);
                transform.translate(0.01F, 0.0F, 0.0F);
                break;
        }
    }
}
