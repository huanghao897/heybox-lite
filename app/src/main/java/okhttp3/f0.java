package okhttp3;

public final class f0 {
    private final a0 a;
    private final g0 b;

    public f0() {
        this(null, null);
    }

    public f0(a0 request, g0 body) {
        this.a = request;
        this.b = body;
    }

    public a0 a() { return a; }
    public g0 b() { return b; }
    public boolean send(String text) { return true; }
    public boolean send(okio.ByteString bytes) { return true; }
    public boolean close(int code, String reason) { return true; }
    public void cancel() {}
}
