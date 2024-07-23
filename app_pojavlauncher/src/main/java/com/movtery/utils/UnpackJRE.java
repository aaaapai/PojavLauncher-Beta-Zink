package com.movtery.utils;

import static net.kdt.pojavlaunch.Architecture.archAsString;
import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.content.res.AssetManager;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.IOException;

public class UnpackJRE {
    public static void unpackAllJre(AssetManager assetManager) {
        sExecutorService.execute(() -> {
            checkInternalRuntime(assetManager, InternalRuntime.JRE_8);
            checkInternalRuntime(assetManager, InternalRuntime.JRE_11);
            checkInternalRuntime(assetManager, InternalRuntime.JRE_17);
            checkInternalRuntime(assetManager, InternalRuntime.JRE_21);

            LauncherPreferences.reloadRuntime();
        });
    }

    private static void checkInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime) {
        String launcherRuntimeVersion;
        String installedRuntimeVersion = MultiRTUtils.readInternalRuntimeVersion(internalRuntime.name);
        try {
            launcherRuntimeVersion = Tools.read(assetManager.open(internalRuntime.path + "/version"));
        } catch (IOException exc) {
            return;
        }
        if (!launcherRuntimeVersion.equals(installedRuntimeVersion)) {
            unpackInternalRuntime(assetManager, internalRuntime, launcherRuntimeVersion);
        }
    }

    private static void unpackInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime, String version) {
        try {
            MultiRTUtils.installRuntimeNamedBinpack(
                    assetManager.open(internalRuntime.path + "/universal.tar.xz"),
                    assetManager.open(internalRuntime.path + "/bin-" + archAsString(Tools.DEVICE_ARCHITECTURE) + ".tar.xz"),
                    internalRuntime.name, version);
            MultiRTUtils.postPrepare(internalRuntime.name);
        } catch (IOException e) {
            Log.e("UnpackJREAuto", "Internal JRE unpack failed", e);
        }
    }

    public enum InternalRuntime {
        JRE_8(8, "Internal-8", "components/jre-8"),
        JRE_11(11, "Internal-11", "components/jre-11"),
        JRE_17(17, "Internal-17", "components/jre-17"),
        JRE_21(21, "Internal-21", "components/jre-21");
        public final int majorVersion;
        public final String name;
        public final String path;

        InternalRuntime(int majorVersion, String name, String path) {
            this.majorVersion = majorVersion;
            this.name = name;
            this.path = path;
        }
    }
}
