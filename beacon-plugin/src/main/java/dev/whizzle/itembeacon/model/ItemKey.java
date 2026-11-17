package dev.whizzle.itembeacon.model;

import java.util.Arrays;

/**
 * One exact item (same item and components), as the bytes of a single item (Paper's {@code ItemStack#serializeAsBytes}
 * of a count-1 copy). Two stacks group together exactly when their keys are equal. The client mod reads the same bytes
 * (docs/PROTOCOL.md).
 */
public final class ItemKey {
    /** A single item's bytes are far smaller; guards against corrupt data. */
    public static final int MAX_BYTES = 256 * 1024;
    private final byte[] bytes;
    private final int hash;

    public ItemKey(byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Item too large: " + bytes.length + " bytes");
        this.bytes = bytes.clone();
        this.hash = Arrays.hashCode(bytes);
    }

    public byte[] bytes() { return bytes.clone(); }
    public int length() { return bytes.length; }
    void write(Wire.Writer out) { out.bytes(bytes); }
    static ItemKey read(Wire.Reader in) { return new ItemKey(in.bytes(MAX_BYTES)); }

    @Override public boolean equals(Object o) { return o instanceof ItemKey k && k.hash == hash && Arrays.equals(k.bytes, bytes); }
    @Override public int hashCode() { return hash; }
    @Override public String toString() { return "ItemKey(" + bytes.length + " bytes, " + Integer.toHexString(hash) + ")"; }
}
