package hev.htproxy;

/**
 * JNI bridge expected by the official hev-socks5-tunnel Android core.
 */
public final class TProxyService {
    private TProxyService() {}

    static {
        System.loadLibrary("hev-socks5-tunnel");
    }

    public static native boolean TProxyStartService(String configPath, int tunFd);
    public static native boolean TProxyStopService();
    public static native boolean TProxyIsRunning();
    public static native long[] TProxyGetStats();
}
