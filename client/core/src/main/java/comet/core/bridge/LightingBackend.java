package comet.core.bridge;

public interface LightingBackend {
    LightingBackend NONE = new LightingBackend() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public float gamma() {
            return 0F;
        }

        @Override
        public void setGammaOverride(float gamma) {
        }

        @Override
        public void removeGammaOverride() {
        }

        @Override
        public boolean consumeUserGammaEdit() {
            return false;
        }
    };

    default boolean available() {
        return true;
    }

    default boolean shadersActive() {
        return false;
    }

    float gamma();

    void setGammaOverride(float gamma);

    void removeGammaOverride();

    boolean consumeUserGammaEdit();

    final class Optifine {
        private Optifine() {
        }

        public static boolean shadersActive() {
            if (Boolean.TRUE.equals(probe("Config", "isShaders"))) {
                return true;
            }
            Object pack = probe("Shaders", "getShaderPackName");
            return pack != null && !"OFF".equalsIgnoreCase(String.valueOf(pack));
        }

        private static Object probe(String type, String method) {
            try {
                return Class.forName(type).getMethod(method).invoke(null);
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
    }
}
