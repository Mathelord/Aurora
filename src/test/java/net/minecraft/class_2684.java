package net.minecraft;

/** Minimal intermediary EntityS2CPacket fixture for PacketRelay contract tests. */
public class class_2684 {
    @SuppressWarnings("unused")
    private final int field_12310;
    private final short x;
    private final short y;
    private final short z;

    public class_2684(int entityId, short x, short y, short z) {
        this.field_12310 = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean method_22826() {
        return true;
    }

    public short method_36150() {
        return x;
    }

    public short method_36151() {
        return y;
    }

    public short method_36152() {
        return z;
    }

    public static final class class_2685 extends class_2684 {
        public class_2685(int entityId, short x, short y, short z) {
            super(entityId, x, y, z);
        }
    }
}
