package okio;

public final class ByteString {
    private final byte[] data;

    public static final ByteString EMPTY = new ByteString(new byte[0]);

    public ByteString(byte[] data) {
        this.data = data == null ? new byte[0] : data.clone();
    }

    public static ByteString of(byte... data) {
        return new ByteString(data);
    }

    public int size() {
        return data.length;
    }

    public byte[] toByteArray() {
        return data.clone();
    }

    @Override public String toString() {
        return "ByteString[size=" + data.length + "]";
    }
}
