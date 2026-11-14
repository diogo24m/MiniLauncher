package com.mini.launcher;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Necesario para leer las sesiones multimedia en Android 5+ (el usuario lo activa en "Acceso a notificaciones"). */
@android.annotation.TargetApi(18)
public class MediaListener extends NotificationListenerService {
    static boolean connected;
    @Override public void onListenerConnected() { connected = true; MediaWatcher.kick(this); }
    @Override public void onListenerDisconnected() { connected = false; }
    // cada notificación nueva o quitada: volver a leer las sesiones
    @Override public void onNotificationPosted(StatusBarNotification sbn) { refresh(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { refresh(); }

    private static void refresh() {
        if (android.os.Build.VERSION.SDK_INT < 21) return;   // MediaWatcher es de Android 5+
        try { MediaWatcher.refreshSoon(); } catch (Throwable ignored) { }
    }
}
