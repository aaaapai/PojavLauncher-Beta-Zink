package com.fifthLight.touchController;

import android.system.Os;

import java.util.concurrent.ThreadLocalRandom;

import top.fifthlight.touchcontroller.proxy.client.LauncherSocketProxyClient;
import top.fifthlight.touchcontroller.proxy.client.LauncherSocketProxyClientKt;

public final class ControllerProxy {
    private static LauncherSocketProxyClient proxyClient;
    private static int proxyPort = -1;

    public static void startProxy() throws Throwable {
        if (proxyClient == null) {
            proxyPort = ThreadLocalRandom.current().nextInt(32768) + 32768;
            proxyClient = LauncherSocketProxyClientKt.localhostLauncherSocketProxyClient(proxyPort);
            new Thread(() ->  LauncherSocketProxyClientKt.runProxy(proxyClient)).start();
        }
        if (proxyPort > 0)
            Os.setenv("TOUCH_CONTROLLER_PROXY", String.valueOf(proxyPort), true);
    }

    static LauncherSocketProxyClient getProxyClient() {
        return proxyClient;
    }
}
