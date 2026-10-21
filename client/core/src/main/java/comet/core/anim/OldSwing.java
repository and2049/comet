package comet.core.anim;

public final class OldSwing {
    private int counter;
    private boolean active;
    private boolean restartGuard = true;
    private float previous;
    private float current;

    public void tick(int duration, float swingAtFull, float swingAtZero, boolean usingWhileDigging) {
        if (duration <= 0) {
            return;
        }
        int progress = Math.round(swingAtFull * duration);
        if (progress > 0) {
            if (progress < duration / 2 || !usingWhileDigging) {
                counter = progress;
                active = true;
                previous = swingAtZero;
                current = swingAtFull;
                restartGuard = true;
                return;
            }
            if (restartGuard) {
                counter = -1;
                active = true;
            }
            restartGuard = false;
        } else if (usingWhileDigging) {
            restart(duration);
            restartGuard = false;
        }
        if (active) {
            if (++counter >= duration) {
                counter = 0;
                active = false;
            }
        } else {
            counter = 0;
        }
        previous = current;
        current = (float) counter / duration;
    }

    private void restart(int duration) {
        if (restartGuard || !active || counter >= duration / 2 || counter < 0) {
            counter = -1;
            active = true;
        }
    }

    public float progress(float vanilla, float partial) {
        float delta = current - previous;
        if (delta < 0.0F) {
            delta++;
        }
        return previous + delta * partial;
    }

    public void reset() {
        counter = 0;
        active = false;
        restartGuard = true;
        previous = 0.0F;
        current = 0.0F;
    }
}
