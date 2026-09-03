package hev.htproxy;

/**
 * JNI bridge expected by the upstream hev-socks5-tunnel Android native wrapper.
 * The native library registers these exact methods against hev/htproxy/TProxyService.
 */
public final class TProxyService {
    private TProxyService() {}

    static {
        System.loadLibrary("hev-socks5-tunnel-jni");
    }

    public static native void TProxyStartService(String configPath, int tunFd);
    public static native void TProxyStopService();
    public static native long[] TProxyGetStats();
}
