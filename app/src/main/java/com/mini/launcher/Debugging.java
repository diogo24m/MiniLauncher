package com.mini.launcher;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

import java.io.InputStream;

/** Estado real de ADB. Nunca guarda interruptores locales ni cambia la autenticación de Android. */
final class Debugging {
    static final int UNKNOWN = -1, OFF = 0, ON = 1;
    static final int OK = 0, DENIED = 1, TIMEOUT = 2, FAILED = 3, NEED_USB = 4;
    private static final int SDK = Build.VERSION.SDK_INT;
    private static final int STATE_TIMEOUT_MS = 1800, ROOT_TIMEOUT_MS = 20000, POLL_INTERVAL_MS = 100;
    private static final String USB = "adb_enabled", WIFI = "adb_wifi_enabled";

    static boolean wirelessNative() { return SDK >= 30; }

    static Uri uri(boolean wifi) {
        return SDK >= 17 ? Global.uri(key(wifi)) : Settings.Secure.getUriFor(USB);
    }

    static int state(Context context, boolean wifi) {
        if (wifi && !wirelessNative()) return legacyWifiState();
        // Android 17 redacta adb_enabled a 0 para apps: no mostrar un apagado ficticio.
        if (!wifi && SDK >= 37) return UNKNOWN;
        try {
            ContentResolver resolver = context.getContentResolver();
            return (SDK >= 17 ? Global.read(resolver, key(wifi))
                    : Settings.Secure.getInt(resolver, USB, 0)) != 0 ? ON : OFF;
        } catch (RuntimeException e) { return UNKNOWN; }
    }

    private static int legacyWifiState() {
        String daemon = property("init.svc.adbd");
        if ("stopped".equals(daemon)) return OFF;
        if (!"running".equals(daemon)) return UNKNOWN;
        String port = property("service.adb.tcp.port");
        // Un -1 explícito anula el puerto persistente, igual que en adbd.
        if ("".equals(port)) port = property("persist.adb.tcp.port");
        if (port == null) return UNKNOWN;
        try {
            if (Integer.parseInt(port) > 0) return ON;
        } catch (NumberFormatException ignored) { }
        // Sin USB disponible, adbd puede escuchar en 5555 incluso con puerto -1 o vacío.
        return UNKNOWN;
    }

    /** Se llama en un hilo de trabajo. Root solo se solicita tras un toque explícito. */
    static synchronized int set(Context context, boolean wifi, boolean enabled) {
        if (!wirelessNative()) {
            if (!enabled) return stopLegacy(context, wifi);
            if (wifi) {
                if (state(context, false) != ON) return NEED_USB;
                int result = root("setprop service.adb.tcp.port 5555 && setprop ctl.restart adbd");
                return result == OK ? awaitState(context, true, true) : result;
            }
        }
        if (!write(context, wifi, enabled)) {
            // La app no se concede permisos a sí misma. Se conserva el control de acceso de su/Android.
            if (SDK < 17) return DENIED;
            int result = root(settingsCommand(wifi, enabled));
            if (result != OK) return result;
        }
        return awaitState(context, wifi, enabled);
    }

    private static int stopLegacy(Context context, boolean wifi) {
        // Un reinicio con puerto -1 NO garantiza que TCP se apague en equipos sin USB de dispositivo.
        // Con permiso especial, apagar USB suele detener adbd sin necesitar root.
        if (!wifi && write(context, false, false) && awaitState(context, true, false) == OK) return OK;
        int result = root(settingsCommand(false, false)
                + " && setprop service.adb.tcp.port -1 && setprop ctl.stop adbd");
        if (result != OK) return result;
        return awaitState(context, true, false) == OK && state(context, false) == OFF ? OK : FAILED;
    }

    private static String key(boolean wifi) { return wifi ? WIFI : USB; }

    private static String settingsCommand(boolean wifi, boolean enabled) {
        return "settings put " + (SDK >= 17 ? "global" : "secure") + " " + key(wifi) + (enabled ? " 1" : " 0");
    }

    private static boolean write(Context context, boolean wifi, boolean enabled) {
        try {
            ContentResolver resolver = context.getContentResolver();
            return SDK >= 17 ? Global.write(resolver, key(wifi), enabled)
                    : Settings.Secure.putInt(resolver, USB, enabled ? 1 : 0);
        } catch (RuntimeException e) { return false; }
    }

    private static int awaitState(Context context, boolean wifi, boolean enabled) {
        long until = SystemClock.elapsedRealtime() + STATE_TIMEOUT_MS;
        do {
            if (state(context, wifi) == (enabled ? ON : OFF)) return OK;
            SystemClock.sleep(POLL_INTERVAL_MS);
        } while (SystemClock.elapsedRealtime() < until);
        return FAILED;
    }

    private static String property(String key) {
        try {
            return (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class).invoke(null, key, "");
        } catch (Exception e) { return null; }
    }

    /** Comandos fijos, sin entradas del usuario, salida drenada y espera acotada incluso si su pide permiso. */
    private static int root(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            InputStream output = process.getInputStream();
            byte[] buffer = new byte[1024];
            long until = SystemClock.elapsedRealtime() + ROOT_TIMEOUT_MS;
            while (SystemClock.elapsedRealtime() < until) {
                if (output.available() > 0) output.read(buffer, 0, Math.min(buffer.length, output.available()));
                try { return process.exitValue() == 0 ? OK : DENIED; }
                catch (IllegalThreadStateException running) { SystemClock.sleep(POLL_INTERVAL_MS); }
            }
            return TIMEOUT;
        } catch (Exception e) { return DENIED; }
        finally {
            if (process != null) {
                try { process.destroy(); } catch (RuntimeException ignored) { }
                try { process.getInputStream().close(); } catch (Exception ignored) { }
                try { process.getErrorStream().close(); } catch (Exception ignored) { }
            }
        }
    }

    /** Aísla Settings.Global (API 17) del verificador de Android 4.1. */
    @android.annotation.TargetApi(17)
    private static final class Global {
        static int read(ContentResolver resolver, String key) { return Settings.Global.getInt(resolver, key, 0); }
        static boolean write(ContentResolver resolver, String key, boolean value) {
            return Settings.Global.putInt(resolver, key, value ? 1 : 0);
        }
        static Uri uri(String key) { return Settings.Global.getUriFor(key); }
    }
}
