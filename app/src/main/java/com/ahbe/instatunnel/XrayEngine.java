package com.ahbe.instatunnel;

import android.content.Context;

import go.Seq;
import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;

public final class XrayEngine {
    private static CoreController controller;
    private static boolean initialized;

    private XrayEngine() {}

    public static synchronized void start(Context context, String configJson) throws Exception {
        stop();
        if (!initialized) {
            Seq.setContext(context.getApplicationContext());
            Libv2ray.initCoreEnv(context.getFilesDir().getAbsolutePath(), "instatunnel-xudp");
            initialized = true;
        }

        controller = Libv2ray.newCoreController(new CoreCallbackHandler() {
            @Override
            public long startup() {
                return 0;
            }

            @Override
            public long shutdown() {
                return 0;
            }

            @Override
            public long onEmitStatus(long code, String message) {
                return 0;
            }
        });
        controller.startLoop(configJson, 0);
        if (!controller.getIsRunning()) {
            throw new Exception("هسته Xray شروع نشد.");
        }
    }

    public static synchronized boolean isRunning() {
        try {
            return controller != null && controller.getIsRunning();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static synchronized void stop() {
        if (controller == null) return;
        try {
            controller.stopLoop();
        } catch (Throwable ignored) {
        }
        controller = null;
    }

    public static String version() {
        try {
            return Libv2ray.checkVersionX();
        } catch (Throwable e) {
            return "Xray";
        }
    }
}
