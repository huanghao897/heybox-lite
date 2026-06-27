package okhttp3;

public interface u {
    c0 intercept(a chain) throws Exception;

    interface a {
        a0 request();
        c0 proceed(a0 request) throws Exception;
        default Object connection() { return null; }
    }
}
