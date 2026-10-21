package comet.core.bridge;

public interface MovementBackend {
    MovementBackend NONE = new MovementBackend() {
    };

    default boolean creative() {
        return false;
    }

    default boolean spectator() {
        return false;
    }

    default boolean flying() {
        return false;
    }

    default boolean riding() {
        return false;
    }

    default boolean descending() {
        return false;
    }

    default boolean sneaking() {
        return false;
    }

    default boolean sprintKeyHeld() {
        return false;
    }

    default boolean sneakInputHeld() {
        return false;
    }

    default boolean jumpInputHeld() {
        return false;
    }

    default float flySpeed() {
        return 0F;
    }

    default void setFlySpeed(float speed) {
    }

    default void addMotionY(double amount) {
    }

    default boolean containerOpen() {
        return false;
    }

    default String serverAddress() {
        return null;
    }
}
