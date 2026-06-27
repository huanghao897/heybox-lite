package okhttp3;

public final class c0 {
    private final a0 request;
    private final int code;
    private final String message;

    public c0() {
        this(null, 200, "");
    }

    public c0(a0 request, int code, String message) {
        this.request = request;
        this.code = code;
        this.message = message == null ? "" : message;
    }

    public a0 h0() { return request == null ? new a0() : request; }
    public String Q() { return message; }
    public int E() { return code; }
    public int code() { return code; }
    public String message() { return message; }
}
