package comet.core.anim;

public interface Transform {
    void translate(float x, float y, float z);

    void rotate(float angle, float x, float y, float z);

    void scale(float x, float y, float z);
}
