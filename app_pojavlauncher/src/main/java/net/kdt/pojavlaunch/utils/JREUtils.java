package net.kdt.pojavlaunch.utils;

import static net.kdt.pojavlaunch.Architecture.ARCH_X86;
import static net.kdt.pojavlaunch.Architecture.is64BitsDevice;
import static net.kdt.pojavlaunch.Tools.CONFIG_BRIDGE;
import static net.kdt.pojavlaunch.Tools.DRIVER_MODEL;
import static net.kdt.pojavlaunch.Tools.LOADER_OVERRIDE;
import static net.kdt.pojavlaunch.Tools.LOCAL_RENDERER;
import static net.kdt.pojavlaunch.Tools.MESA_LIBS;
import static net.kdt.pojavlaunch.Tools.TURNIP_LIBS;
import static net.kdt.pojavlaunch.Tools.NATIVE_LIB_DIR;
import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;
import static net.kdt.pojavlaunch.Tools.shareLog;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.util.ArrayMap;
import android.util.Log;
import android.widget.Toast;

import com.firefly.utils.MesaUtils;
import com.firefly.utils.PGWTools;
import com.firefly.utils.TurnipUtils;

import com.movtery.ui.subassembly.customprofilepath.ProfilePathHome;
import com.movtery.ui.subassembly.customprofilepath.ProfilePathManager;
import com.oracle.dalvik.VMLauncher;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.MainActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.plugins.FFmpegPlugin;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

public class JREUtils {

    private JREUtils() {}

    public static String LD_LIBRARY_PATH;
    public static String jvmLibraryPath;
    private static String glVersion = PREF_MESA_GL_VERSION;
    private static String glslVersion = PREF_MESA_GLSL_VERSION;

    public static String findInLdLibPath(String libName) {
        if (Os.getenv("LD_LIBRARY_PATH") == null) {
            try {
                if (LD_LIBRARY_PATH != null) {
                    Os.setenv("LD_LIBRARY_PATH", LD_LIBRARY_PATH, true);
                }
            } catch (ErrnoException e) {
                e.printStackTrace();
            }
            return libName;
        }
        for (String libPath : Os.getenv("LD_LIBRARY_PATH").split(":")) {
            File f = new File(libPath, libName);
            if (f.exists() && f.isFile()) {
                return f.getAbsolutePath();
            }
        }
        return libName;
    }

    public static ArrayList<File> locateLibs(File path) {
        ArrayList<File> returnValue = new ArrayList<>();
        File[] list = path.listFiles();
        if (list != null) {
            for (File f : list) {
                if (f.isFile() && f.getName().endsWith(".so")) {
                    returnValue.add(f);
                } else if (f.isDirectory()) {
                    returnValue.addAll(locateLibs(f));
                }
            }
        }
        return returnValue;
    }

    public static void initJavaRuntime(String jreHome) {
        dlopen(findInLdLibPath("libjli.so"));
        if (!dlopen("libjvm.so")) {
            Log.w("DynamicLoader", "Failed to load with no path, trying with full path");
            dlopen(jvmLibraryPath + "/libjvm.so");
        }
        dlopen(findInLdLibPath("libverify.so"));
        dlopen(findInLdLibPath("libjava.so"));
        // dlopen(findInLdLibPath("libjsig.so"));
        dlopen(findInLdLibPath("libnet.so"));
        dlopen(findInLdLibPath("libnio.so"));
        dlopen(findInLdLibPath("libawt.so"));
        dlopen(findInLdLibPath("libawt_headless.so"));
        dlopen(findInLdLibPath("libfreetype.so"));
        dlopen(findInLdLibPath("libfontmanager.so"));
        for (File f : locateLibs(new File(jreHome, Tools.DIRNAME_HOME_JRE))) {
            dlopen(f.getAbsolutePath());
        }
        dlopen(NATIVE_LIB_DIR + "/libopenal.so");
    }

    public static void redirectAndPrintJRELog() {

        Log.v("jrelog", "Log starts here");
        new Thread(new Runnable() {
            int failTime = 0;
            ProcessBuilder logcatPb;

            @Override
            public void run() {
                try {
                    if (logcatPb == null) {
                        logcatPb = new ProcessBuilder().command("logcat", /* "-G", "1mb", */ "-v", "brief", "-s", "jrelog:I", "LIBGL:I", "NativeInput").redirectErrorStream(true);
                    }

                    Log.i("jrelog-logcat", "Clearing logcat");
                    new ProcessBuilder().command("logcat", "-c").redirectErrorStream(true).start();
                    Log.i("jrelog-logcat", "Starting logcat");
                    java.lang.Process p = logcatPb.start();

                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = p.getInputStream().read(buf)) != -1) {
                        String currStr = new String(buf, 0, len);
                        Logger.appendToLog(currStr);
                    }

                    if (p.waitFor() != 0) {
                        Log.e("jrelog-logcat", "Logcat exited with code " + p.exitValue());
                        failTime++;
                        Log.i("jrelog-logcat", (failTime <= 10 ? "Restarting logcat" : "Too many restart fails") + " (attempt " + failTime + "/10");
                        if (failTime <= 10) {
                            run();
                        } else {
                            Logger.appendToLog("ERROR: Unable to get more log.");
                        }
                    }
                } catch (Throwable e) {
                    Log.e("jrelog-logcat", "Exception on logging thread", e);
                    Logger.appendToLog("Exception on logging thread:\n" + Log.getStackTraceString(e));
                }
            }
        }).start();
        Log.i("jrelog-logcat", "Logcat thread started");

    }

    public static void relocateLibPath(Runtime runtime, String jreHome) {
        String JRE_ARCHITECTURE = runtime.arch;
        if (Architecture.archAsInt(JRE_ARCHITECTURE) == ARCH_X86) {
            JRE_ARCHITECTURE = "i386/i486/i586";
        }

        for (String arch : JRE_ARCHITECTURE.split("/")) {
            File f = new File(jreHome, "lib/" + arch);
            if (f.exists() && f.isDirectory()) {
                Tools.DIRNAME_HOME_JRE = "lib/" + arch;
            }
        }

        String libName = is64BitsDevice() ? "lib64" : "lib";
        StringBuilder ldLibraryPath = new StringBuilder();
        if (FFmpegPlugin.isAvailable) {
            ldLibraryPath.append(FFmpegPlugin.libraryPath).append(":");
        }
        ldLibraryPath.append(jreHome)
                .append("/").append(Tools.DIRNAME_HOME_JRE)
                .append("/jli:").append(jreHome).append("/").append(Tools.DIRNAME_HOME_JRE)
                .append(":");
        ldLibraryPath.append("/system/").append(libName).append(":")
                .append("/vendor/").append(libName).append(":")
                .append("/vendor/").append(libName).append("/hw:")
                .append(NATIVE_LIB_DIR);
        LD_LIBRARY_PATH = ldLibraryPath.toString();
    }

    private static void setJavaEnv(String jreHome) throws Throwable {
        Map<String, String> envMap = new ArrayMap<>();

        envMap.put("POJAV_NATIVEDIR", NATIVE_LIB_DIR);
        envMap.put("JAVA_HOME", jreHome);
        envMap.put("HOME", ProfilePathManager.getCurrentPath());
        envMap.put("TMPDIR", Tools.DIR_CACHE.getAbsolutePath());
        envMap.put("PATH", jreHome + "/bin:" + Os.getenv("PATH"));
        envMap.put("LD_LIBRARY_PATH", LD_LIBRARY_PATH);
        envMap.put("FORCE_VSYNC", String.valueOf(LauncherPreferences.PREF_FORCE_VSYNC));
        envMap.put("AWTSTUB_WIDTH", Integer.toString(CallbackBridge.windowWidth > 0 ? CallbackBridge.windowWidth : CallbackBridge.physicalWidth));
        envMap.put("AWTSTUB_HEIGHT", Integer.toString(CallbackBridge.windowHeight > 0 ? CallbackBridge.windowHeight : CallbackBridge.physicalHeight));

        if (Tools.CONFIG_BRIDGE != null)
            envMap.put("POJAV_CONFIG_BRIDGE", Tools.CONFIG_BRIDGE);
        if (PREF_BIG_CORE_AFFINITY)
            envMap.put("POJAV_BIG_CORE_AFFINITY", "1");
        if (PREF_DUMP_SHADERS)
            envMap.put("LIBGL_VGPU_DUMP", "1");
        if (PREF_ZINK_PREFER_SYSTEM_DRIVER)
            envMap.put("POJAV_ZINK_PREFER_SYSTEM_DRIVER", "1");
        if (PREF_VSYNC_IN_ZINK)
            envMap.put("POJAV_VSYNC_IN_ZINK", "1");
        if (PREF_EXP_SETUP)
            envMap.put("POJAV_EXP_SETUP", "1");
        if (PREF_SPARE_FRAME_BUFFER)
            envMap.put("POJAV_SPARE_FRAME_BUFFER", "1");
        if (FIX_Q3_BEHAVIOR)
            envMap.put("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1");
        if (Tools.deviceHasHangingLinker())
            envMap.put("POJAV_EMUI_ITERATOR_MITIGATE", "1");
        if (FFmpegPlugin.isAvailable)
            envMap.put("PATH", FFmpegPlugin.libraryPath + ":" + envMap.get("PATH"));

        for (Map.Entry<String, String> env : envMap.entrySet()) {
            Logger.appendToLog("Added custom env: " + env.getKey() + "=" + env.getValue());
            try {
                Os.setenv(env.getKey(), env.getValue(), true);
            } catch (NullPointerException exception) {
                Log.e("JREUtils", exception.toString());
            }
        }

        File serverFile = new File(jreHome + "/" + Tools.DIRNAME_HOME_JRE + "/server/libjvm.so");
        jvmLibraryPath = jreHome + "/" + Tools.DIRNAME_HOME_JRE + "/" + (serverFile.exists() ? "server" : "client");
        Log.d("DynamicLoader", "Base LD_LIBRARY_PATH: " + LD_LIBRARY_PATH);
        Log.d("DynamicLoader", "Internal LD_LIBRARY_PATH: " + jvmLibraryPath + ":" + LD_LIBRARY_PATH);
        setLdLibraryPath(jvmLibraryPath + ":" + LD_LIBRARY_PATH);
    }

    private static void setRendererEnv() throws Throwable {
        Map<String, String> envMap = new ArrayMap<>();

        if (LOCAL_RENDERER.startsWith("opengles3")) {
            envMap.put("LIBGL_ES", "3");
            envMap.put("LIBGL_FB", "3");
            envMap.put("LIBGL_MIPMAP", "3");
            envMap.put("LIBGL_NOERROR", "1");
            envMap.put("LIBGL_NOINTOVLHACK", "1");
            envMap.put("LIBGL_NORMALIZE", "1");
        }

        if (LOCAL_RENDERER.equals("opengles3_ltw")) {
            envMap.put("LIBGL_ES", "3");
            envMap.put("POJAVEXEC_EGL", "libltw.so");
        }

        if (!LOCAL_RENDERER.startsWith("opengles")) {
            envMap.put("MESA_GLSL_CACHE_DIR", Tools.DIR_CACHE.getAbsolutePath());
            envMap.put("force_glsl_extensions_warn", "true");
            envMap.put("allow_higher_compat_version", "true");
            envMap.put("allow_glsl_extension_directive_midshader", "true");
        } else envMap.put("POJAV_BETA_RENDERER", LOCAL_RENDERER);

        if (!LOCAL_RENDERER.startsWith("opengles") && !PREF_EXP_SETUP) {
            switch (LOCAL_RENDERER) {
                case "vulkan_zink": {
                    envMap.put("POJAV_BETA_RENDERER", "mesa_3d");
                    envMap.put("LOCAL_DRIVER_MODEL", "driver_zink");
                    envMap.put("mesa_glthread", "true");
                }
                break;
                case "virglrenderer": {
                    envMap.put("POJAV_BETA_RENDERER", "mesa_3d");
                    envMap.put("LOCAL_DRIVER_MODEL", "driver_virgl");
                    envMap.put("MESA_GL_VERSION_OVERRIDE", "4.3");
                    envMap.put("MESA_GLSL_VERSION_OVERRIDE", "430");
                    envMap.put("mesa_glthread", "true");
                    envMap.put("VTEST_SOCKET_NAME", new File(Tools.DIR_CACHE, ".virgl_test").getAbsolutePath());
                }
                break;
                case "freedreno": {
                    envMap.put("POJAV_BETA_RENDERER", "mesa_3d");
                    envMap.put("LOCAL_DRIVER_MODEL", "driver_freedreno");
                    envMap.put("LOCAL_LOADER_OVERRIDE", "kgsl");
                }
                break;
                case "panfrost": {
                    envMap.put("POJAV_BETA_RENDERER", "mesa_3d");
                    envMap.put("LOCAL_DRIVER_MODEL", "driver_panfrost");
                    envMap.put("MESA_DISK_CACHE_SINGLE_FILE", "1");
                    envMap.put("MESA_DISK_CACHE_SINGLE_FILE", "true");
                }
                break;
                default:
                    // Nothing to do here
                    break;
            }
            envMap.put("MESA_LIBRARY", loadGraphicsLibrary());
        }

        if (LOCAL_RENDERER.equals("mesa_3d")) {
            if (PREF_EXP_ENABLE_SPECIFIC) {
                switch (DRIVER_MODEL) {
                    case "driver_zink":
                    case "driver_freedreno":
                    case "driver_softpipe":
                    case "driver_llvmpipe": {
                        envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                        envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                    }
                    break;
                    case "driver_virgl": {
                        envMap.put("MESA_GL_VERSION_OVERRIDE", "4.3");
                        envMap.put("MESA_GLSL_VERSION_OVERRIDE", "430");
                    }
                    break;
                    case "driver_panfrost": {
                        envMap.put("MESA_GL_VERSION_OVERRIDE", "3.3");
                        envMap.put("MESA_GLSL_VERSION_OVERRIDE", "330");
                    }
                    break;
                }
            } else if (PREF_EXP_ENABLE_CUSTOM) {
                envMap.put("MESA_GL_VERSION_OVERRIDE", glVersion);
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", glslVersion);
            }

            if (PREF_LOADER_OVERRIDE && DRIVER_MODEL.equals("driver_freedreno")) {
                switch (LOADER_OVERRIDE) {
                    case "kgsl":
                        envMap.put("LOCAL_LOADER_OVERRIDE", "kgsl");
                        break;
                    case "msm":
                        envMap.put("LOCAL_LOADER_OVERRIDE", "msm");
                        break;
                    case "virtio_gpu":
                        envMap.put("LOCAL_LOADER_OVERRIDE", "virtio_gpu");
                        break;
                    default:
                        envMap.put("LOCAL_LOADER_OVERRIDE", "kgsl");
                        break;
                }
            }

            if (PREF_USE_DRM_SHIM)
                envMap.put("LD_PRELOAD", NATIVE_LIB_DIR + getDrmShimPath(PGWTools.isAdrenoGPU()));

            if (DRIVER_MODEL.equals("driver_virgl")) {
                envMap.put("DCLAT_FRAMEBUFFER", "1");
                envMap.put("VTEST_SOCKET_NAME", new File(Tools.DIR_CACHE, ".virgl_test").getAbsolutePath());
            }

            if (DRIVER_MODEL.equals("driver_panfrost")) {
                envMap.put("MESA_DISK_CACHE_SINGLE_FILE", "1");
                if (MESA_LIBS.equals("default"))
                    envMap.put("PAN_MESA_DEBUG", "trace");
            }

            envMap.put("MESA_LIBRARY", loadGraphicsLibrary());
            envMap.put("LOCAL_DRIVER_MODEL", DRIVER_MODEL);
            envMap.put("POJAV_BETA_RENDERER", "mesa_3d");
        }

        if (!envMap.containsKey("LIBGL_ES")) {
            int glesMajor = getDetectedVersion();
            Log.i("glesDetect", "GLES version detected: " + glesMajor);

            if (glesMajor < 3) {
                // fallback to 2 since it's the minimum for the entire app
                envMap.put("LIBGL_ES", "2");
            } else if (LOCAL_RENDERER.startsWith("opengles")) {
                envMap.put("LIBGL_ES", LOCAL_RENDERER.replace("opengles", "").replace("_5", ""));
            } else {
                // TODO if can: other backends such as Vulkan.
                // Sure, they should provide GLES 3 support.
                envMap.put("LIBGL_ES", "3");
            }
        }

        for (Map.Entry<String, String> env : envMap.entrySet()) {
            Logger.appendToLog("Added custom env: " + env.getKey() + "=" + env.getValue());
            try {
                Os.setenv(env.getKey(), env.getValue(), true);
            } catch (NullPointerException exception) {
                Log.e("JREUtils", exception.toString());
            }
        }

    }

    private static void setCustomEnv() throws Throwable {
        Map<String, String> envMap = new ArrayMap<>();
        File customEnvFile = new File(ProfilePathManager.getCurrentPath(), "custom_env.txt");
        if (customEnvFile.exists() && customEnvFile.isFile()) {
            BufferedReader reader = new BufferedReader(new FileReader(customEnvFile));
            String line;
            while ((line = reader.readLine()) != null) {
                // Not use split() as only split first one
                int index = line.indexOf("=");
                envMap.put(line.substring(0, index), line.substring(index + 1));
            }
            reader.close();
        } else return;

        for (Map.Entry<String, String> env : envMap.entrySet()) {
            Logger.appendToLog("Added custom env: " + env.getKey() + "=" + env.getValue());
            try {
                Os.setenv(env.getKey(), env.getValue(), true);
            } catch (NullPointerException exception) {
                Log.e("JREUtils", exception.toString());
            }
        }
    }

    private static void checkAndUsedJSPH(final Runtime runtime) throws Throwable {
        boolean onUseJSPH = runtime.javaVersion > 11;
        if (!onUseJSPH) return;
        File dir = new File(NATIVE_LIB_DIR);
        if (!dir.isDirectory()) return;
        String jsphName = runtime.javaVersion == 17 ? "libjsph17" : "libjsph21";
        File[] files = dir.listFiles((dir1, name) -> name.startsWith(jsphName));
        if (files != null && files.length > 0) {
            String libName = NATIVE_LIB_DIR + "/" + jsphName + ".so";
            Logger.appendToLog("Added custom env: JSP=" + libName);
            try {
                Os.setenv("JSP", libName, true);
            } catch (Exception e) {
                System.err.println("Error setting environment variable: " + e.getMessage());
            }
        } else {
            System.out.println("Native: Library " + jsphName + ".so not found, some mod cannot used");
        }
    }

    private static void loadCustomTurnip() throws Throwable {
        if (TURNIP_LIBS.equals("default") || PREF_ZINK_PREFER_SYSTEM_DRIVER) return;
        String folder = TurnipUtils.INSTANCE.getTurnipDriver(TURNIP_LIBS);
        if (folder == null) return;
        Logger.appendToLog("Added custom env: TURNIP_DIR=" + folder);
        try {
            Os.setenv("TURNIP_DIR", folder, true);
        } catch (Exception e) {
            System.err.println("Error setting environment variable: " + e.getMessage());
        }
    }

    public static int launchJavaVM(final Activity activity, final Runtime runtime, File gameDirectory, final List<String> JVMArgs, final String userArgsString) throws Throwable {
        String runtimeHome = MultiRTUtils.getRuntimeHome(runtime.name).getAbsolutePath();
        JREUtils.relocateLibPath(runtime, runtimeHome);

        setJavaEnv(runtimeHome);
        setCustomEnv();
        checkAndUsedJSPH(runtime);
        if (PGWTools.isAdrenoGPU() && TURNIP_LIBS != null) loadCustomTurnip();
        if (LOCAL_RENDERER != null) setRendererEnv();

        List<String> userArgs = getJavaArgs(activity, runtimeHome, userArgsString);

        //Add automatically generated args
        userArgs.add("-Xms" + LauncherPreferences.PREF_RAM_ALLOCATION + "M");
        userArgs.add("-Xmx" + LauncherPreferences.PREF_RAM_ALLOCATION + "M");
        if (LOCAL_RENDERER != null) userArgs.add("-Dorg.lwjgl.opengl.libname=" + loadGraphicsLibrary());

        userArgs.addAll(JVMArgs);
        activity.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.autoram_info_msg, LauncherPreferences.PREF_RAM_ALLOCATION), Toast.LENGTH_SHORT).show());
        System.out.println(JVMArgs);

        initJavaRuntime(runtimeHome);
        setupExitTrap(activity.getApplication());
        chdir(gameDirectory == null ? ProfilePathHome.getGameHome() : gameDirectory.getAbsolutePath());
        userArgs.add(0, "java"); //argv[0] is the program name according to C standard.

        int exitCode = VMLauncher.launchJVM(userArgs.toArray(new String[0]));
        Logger.appendToLog("Java Exit code: " + exitCode);
        if (exitCode != 0) {
            activity.runOnUiThread(() -> {
                AlertDialog.Builder dialog = new AlertDialog.Builder(activity);
                dialog.setMessage(activity.getString(R.string.mcn_exit_title, exitCode));

                dialog.setPositiveButton(R.string.main_share_logs, (p1, p2) -> {
                    shareLog(activity);
                    MainActivity.fullyExit();
                });
                dialog.show();
            });
        }
        return exitCode;
    }

    /**
     * Gives an argument list filled with both the user args
     * and the auto-generated ones (eg. the window resolution).
     *
     * @param ctx The application context
     * @return A list filled with args.
     */
    public static List<String> getJavaArgs(Context ctx, String runtimeHome, String userArgumentsString) {
        List<String> userArguments = parseJavaArguments(userArgumentsString);
        String resolvFile;
        resolvFile = new File(Tools.DIR_DATA, "resolv.conf").getAbsolutePath();

        ArrayList<String> overridableArguments = new ArrayList<>(Arrays.asList(
                "-Djava.home=" + runtimeHome,
                "-Djava.io.tmpdir=" + Tools.DIR_CACHE.getAbsolutePath(),
                "-Djna.boot.library.path=" + NATIVE_LIB_DIR,
                "-Duser.home=" + ProfilePathManager.getCurrentPath(),
                "-Duser.language=" + System.getProperty("user.language"),
                "-Dos.name=Linux",
                "-Dos.version=Android-" + Build.VERSION.RELEASE,
                "-Dpojav.path.minecraft=" + ProfilePathHome.getGameHome(),
                "-Dpojav.path.private.account=" + Tools.DIR_ACCOUNT_NEW,
                "-Duser.timezone=" + TimeZone.getDefault().getID(),

                "-Dorg.lwjgl.vulkan.libname=libvulkan.so",
                //LWJGL 3 DEBUG FLAGS
                //"-Dorg.lwjgl.util.Debug=true",
                //"-Dorg.lwjgl.util.DebugFunctions=true",
                //"-Dorg.lwjgl.util.DebugLoader=true",
                // GLFW Stub width height
                "-Dglfwstub.windowWidth=" + Tools.getDisplayFriendlyRes(currentDisplayMetrics.widthPixels, LauncherPreferences.PREF_SCALE_FACTOR / 100F),
                "-Dglfwstub.windowHeight=" + Tools.getDisplayFriendlyRes(currentDisplayMetrics.heightPixels, LauncherPreferences.PREF_SCALE_FACTOR / 100F),
                "-Dglfwstub.initEgl=false",
                "-Dext.net.resolvPath=" + resolvFile,
                "-Dlog4j2.formatMsgNoLookups=true", //Log4j RCE mitigation

                "-Dnet.minecraft.clientmodname=" + Tools.APP_NAME,
                "-Dfml.earlyprogresswindow=false", //Forge 1.14+ workaround
                "-Dloader.disable_forked_guis=true",
                "-Dsodium.checks.issue2561=false"
        ));
        if (LauncherPreferences.PREF_ARC_CAPES) {
            overridableArguments.add("-javaagent:" + new File(Tools.DIR_DATA, "arc_dns_injector/arc_dns_injector.jar").getAbsolutePath() + "=23.95.137.176");
        }
        List<String> additionalArguments = new ArrayList<>();
        for (String arg : overridableArguments) {
            String strippedArg = arg.substring(0, arg.indexOf('='));
            boolean add = true;
            for (String uarg : userArguments) {
                if (uarg.startsWith(strippedArg)) {
                    add = false;
                    break;
                }
            }
            if (add)
                additionalArguments.add(arg);
            else
                Log.i("ArgProcessor", "Arg skipped: " + arg);
        }

        //Add all the arguments
        userArguments.addAll(additionalArguments);
        return userArguments;
    }

    /**
     * Parse and separate java arguments in a user friendly fashion
     * It supports multi line and absence of spaces between arguments
     * The function also supports auto-removal of improper arguments, although it may miss some.
     *
     * @param args The un-parsed argument list.
     * @return Parsed args as an ArrayList
     */
    public static ArrayList<String> parseJavaArguments(String args) {
        ArrayList<String> parsedArguments = new ArrayList<>(0);
        args = args.trim().replace(" ", "");
        //For each prefixes, we separate args.
        String[] separators = new String[]{"-XX:-", "-XX:+", "-XX:", "--", "-D", "-X", "-javaagent:", "-verbose"};
        for (String prefix : separators) {
            while (true) {
                int start = args.indexOf(prefix);
                if (start == -1) break;
                //Get the end of the current argument by checking the nearest separator
                int end = -1;
                for (String separator : separators) {
                    int tempEnd = args.indexOf(separator, start + prefix.length());
                    if (tempEnd == -1) continue;
                    if (end == -1) {
                        end = tempEnd;
                        continue;
                    }
                    end = Math.min(end, tempEnd);
                }
                //Fallback
                if (end == -1) end = args.length();

                //Extract it
                String parsedSubString = args.substring(start, end);
                args = args.replace(parsedSubString, "");

                //Check if two args aren't bundled together by mistake
                if (parsedSubString.indexOf('=') == parsedSubString.lastIndexOf('=')) {
                    int arraySize = parsedArguments.size();
                    if (arraySize > 0) {
                        String lastString = parsedArguments.get(arraySize - 1);
                        // Looking for list elements
                        if (lastString.charAt(lastString.length() - 1) == ',' ||
                                parsedSubString.contains(",")) {
                            parsedArguments.set(arraySize - 1, lastString + parsedSubString);
                            continue;
                        }
                    }
                    parsedArguments.add(parsedSubString);
                } else Log.w("JAVA ARGS PARSER", "Removed improper arguments: " + parsedSubString);
            }
        }
        return parsedArguments;
    }

    /**
     * Open the render library in accordance to the settings.
     * It will fallback if it fails to load the library.
     *
     * @return The name of the loaded library
     */
    public static String loadGraphicsLibrary() {
        if (LOCAL_RENDERER == null) return null;
        String renderLibrary;
        if (LOCAL_RENDERER.equals("mesa_3d")) {
            switch (MESA_LIBS) {
                case "default":
                    renderLibrary = "libOSMesa_8.so";
                    break;
                case "mesa2320d":
                    renderLibrary = "libOSMesa_2320d.so";
                    break;
                case "mesa2304":
                    renderLibrary = "libOSMesa_2304.so";
                    break;
                case "mesa2300d":
                    renderLibrary = "libOSMesa_2300d.so";
                    break;
                case "mesa2205":
                    renderLibrary = "libOSMesa_2205.so";
                    break;
                default:
                    renderLibrary = MesaUtils.INSTANCE.getMesaLib(MESA_LIBS);
                    break;
            }
        } else {
            switch (LOCAL_RENDERER) {
                case "opengles3":
                    renderLibrary = "libgl4es_114.so";
                    break;
                case "opengles3_ptitseb":
                    renderLibrary = "libgl4es_ptitseb.so";
                    break;
                case "opengles2_vgpu":
                    renderLibrary = "libvgpu.so";
                    break;
                case "opengles2_vgpu_1":
                    renderLibrary = "libvgpu_1368.so";
                    break;
                case "vulkan_zink":
                case "freedreno":
                    renderLibrary = "libOSMesa_2304.so";
                    break;
                case "virglrenderer":
                    renderLibrary = "libOSMesa_2205.so";
                    break;
                case "panfrost":
                    renderLibrary = "libOSMesa_2300d.so";
                    break;
                case "opengles3_ltw":
                    renderLibrary = "libltw.so";
                    break;
                default:
                    Log.w("RENDER_LIBRARY", "No renderer selected, defaulting to opengles2");
                    renderLibrary = "libgl4es_114.so";
                    break;
            }
        }

        if (!dlopen(renderLibrary) && !dlopen(findInLdLibPath(renderLibrary))) {
            Log.e("RENDER_LIBRARY", "Failed to load renderer ");
        }
        return renderLibrary;
    }

    private static String getDrmShimPath(boolean isAdreno) {
        return isAdreno ? "/libfreedreno_noop_drm_shim.so" : "/libpanfrost_noop_drm_shim.so";
    }

    /**
     * Remove the argument from the list, if it exists
     * If the argument exists multiple times, they will all be removed.
     *
     * @param argList  The argument list to purge
     * @param argStart The argument to purge from the list.
     */
    private static void purgeArg(List<String> argList, String argStart) {
        Iterator<String> args = argList.iterator();
        while (args.hasNext()) {
            String arg = args.next();
            if (arg.startsWith(argStart)) args.remove();
        }
    }

    private static final int EGL_OPENGL_ES_BIT = 0x0001;
    private static final int EGL_OPENGL_ES2_BIT = 0x0004;
    private static final int EGL_OPENGL_ES3_BIT_KHR = 0x0040;

    @SuppressWarnings("SameParameterValue")
    private static boolean hasExtension(String extensions, String name) {
        int start = extensions.indexOf(name);
        while (start >= 0) {
            // check that we didn't find a prefix of a longer extension name
            int end = start + name.length();
            if (end == extensions.length() || extensions.charAt(end) == ' ') {
                return true;
            }
            start = extensions.indexOf(name, end);
        }
        return false;
    }

    public static int getDetectedVersion() {
        /*
         * Get all the device configurations and check the EGL_RENDERABLE_TYPE attribute
         * to determine the highest ES version supported by any config. The
         * EGL_KHR_create_context extension is required to check for ES3 support; if the
         * extension is not present this test will fail to detect ES3 support. This
         * effectively makes the extension mandatory for ES3-capable devices.
         */
        EGL10 egl = (EGL10) EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        int[] numConfigs = new int[1];
        if (egl.eglInitialize(display, null)) {
            try {
                boolean checkES3 = hasExtension(egl.eglQueryString(display, EGL10.EGL_EXTENSIONS),
                        "EGL_KHR_create_context");
                if (egl.eglGetConfigs(display, null, 0, numConfigs)) {
                    EGLConfig[] configs = new EGLConfig[numConfigs[0]];
                    if (egl.eglGetConfigs(display, configs, numConfigs[0], numConfigs)) {
                        int highestEsVersion = 0;
                        int[] value = new int[1];
                        for (int i = 0; i < numConfigs[0]; i++) {
                            if (egl.eglGetConfigAttrib(display, configs[i],
                                    EGL10.EGL_RENDERABLE_TYPE, value)) {
                                if (checkES3 && ((value[0] & EGL_OPENGL_ES3_BIT_KHR) ==
                                        EGL_OPENGL_ES3_BIT_KHR)) {
                                    if (highestEsVersion < 3) highestEsVersion = 3;
                                } else if ((value[0] & EGL_OPENGL_ES2_BIT) == EGL_OPENGL_ES2_BIT) {
                                    if (highestEsVersion < 2) highestEsVersion = 2;
                                } else if ((value[0] & EGL_OPENGL_ES_BIT) == EGL_OPENGL_ES_BIT) {
                                    if (highestEsVersion < 1) highestEsVersion = 1;
                                }
                            } else {
                                Log.w("glesDetect", "Getting config attribute with "
                                        + "EGL10#eglGetConfigAttrib failed "
                                        + "(" + i + "/" + numConfigs[0] + "): "
                                        + egl.eglGetError());
                            }
                        }
                        return highestEsVersion;
                    } else {
                        Log.e("glesDetect", "Getting configs with EGL10#eglGetConfigs failed: "
                                + egl.eglGetError());
                        return -1;
                    }
                } else {
                    Log.e("glesDetect", "Getting number of configs with EGL10#eglGetConfigs failed: "
                            + egl.eglGetError());
                    return -2;
                }
            } finally {
                egl.eglTerminate(display);
            }
        } else {
            Log.e("glesDetect", "Couldn't initialize EGL.");
            return -3;
        }
    }

    public static native int chdir(String path);

    public static native boolean dlopen(String libPath);

    public static native void setLdLibraryPath(String ldLibraryPath);

    public static native void setupBridgeWindow(Object surface);

    public static native void releaseBridgeWindow();

    public static native void setupExitTrap(Context context);

    // Obtain AWT screen pixels to render on Android SurfaceView
    public static native int[] renderAWTScreenFrame(/* Object canvas, int width, int height */);

    static {
        System.loadLibrary("pojavexec");
        System.loadLibrary("pojavexec_awt");
    }
}
