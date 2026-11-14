package com.mini.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Pista en reproducción en Android 5+ (sesiones multimedia): cualquier app de música, Android Auto/CarPlay nativos y el
 * Bluetooth del sistema. La app Bluetooth de Android (com.android.bluetooth, perfil A2DP sink/AVRCP) publica la canción
 * del celular como una sesión más, igual que Spotify. Solo se miran las sesiones de la entrada elegida en el reproductor:
 * Bluetooth, o las apps de música (todo lo que no es Bluetooth).
 */
@android.annotation.TargetApi(21)
public class MediaWatcher {
    private static MediaWatcher instance;
    private final Context ctx;
    private final NowPlaying.Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private MediaSessionManager msm;
    private MediaController current;
    /** true: solo sesiones Bluetooth; false: solo apps de música. */
    private boolean btMode = true;
    /** Las sesiones activas de la entrada elegida, cada una con su escucha: si otra empieza a sonar (p. ej. un video
     *  que se puso en segundo plano), el reproductor pasa a ella aunque la lista de sesiones no haya cambiado. */
    private final List<MediaController> all = new ArrayList<MediaController>();
    private final List<MediaController.Callback> allCb = new ArrayList<MediaController.Callback>();
    // carátula que la app da solo como dirección (p. ej. el Bluetooth de Android 11+): se carga aparte
    private String artUri;
    private Bitmap artFromUri;

    public MediaWatcher(Context c, NowPlaying.Listener l) {
        ctx = c.getApplicationContext();
        listener = l;
        instance = this;
    }

    static void kick(Context c) { if (instance != null) instance.start(); }

    /** Lector de notificaciones: llegó o se quitó una notificación. Se vuelven a leer las
     *  sesiones (agrupado, máximo una vez cada 300 ms): algunas apps Bluetooth no avisan el cambio de sesión. */
    static void refreshSoon() {
        final MediaWatcher w = instance;
        if (w == null) return;
        w.ui.removeCallbacks(w.refresh);
        w.ui.postDelayed(w.refresh, 300);
    }

    private final Runnable refresh = new Runnable() { public void run() { refreshNow(); } };

    private ComponentName listenerName() { return new ComponentName(ctx, MediaListener.class); }

    private void refreshNow() {
        if (msm == null) return;
        try { pick(msm.getActiveSessions(listenerName())); } catch (Throwable ignored) { }
    }

    /** Entrada elegida en el reproductor (true = Bluetooth). Vuelve a elegir la sesión y avisa. */
    void setBtMode(boolean bt) {
        btMode = bt;
        refreshNow();
    }

    public boolean start() {
        try {
            msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName cn = listenerName();
            msm.removeOnActiveSessionsChangedListener(sessions);
            msm.addOnActiveSessionsChangedListener(sessions, cn);
            pick(msm.getActiveSessions(cn));
            return true;
        } catch (Throwable e) {
            return false;   // sin "Acceso a notificaciones"
        }
    }

    public void stop() {
        ui.removeCallbacks(refresh);
        try { if (msm != null) msm.removeOnActiveSessionsChangedListener(sessions); } catch (Throwable ignored) { }
        if (current != null) try { current.unregisterCallback(cb); } catch (Throwable ignored) { }
        current = null;
        watchAll(null);
    }

    private static boolean playing(MediaController m) {
        try {
            PlaybackState st = m.getPlaybackState();
            return st != null && (st.getState() == PlaybackState.STATE_PLAYING || st.getState() == PlaybackState.STATE_BUFFERING);
        } catch (Throwable e) { return false; }
    }

    private static boolean hasTitle(MediaController m) {
        try {
            MediaMetadata md = m.getMetadata();
            return md != null && (text(md, MediaMetadata.METADATA_KEY_TITLE) != null || text(md, MediaMetadata.METADATA_KEY_DISPLAY_TITLE) != null);
        } catch (Throwable e) { return false; }
    }

    /** ¿La sesión es de la entrada elegida? */
    private boolean wanted(MediaController m) {
        try { return NowPlaying.isBtPkg(m.getPackageName()) == btMode; } catch (Throwable e) { return false; }
    }

    private boolean sameSessions(List<MediaController> list) {
        if (list.size() != all.size()) return false;
        for (int i = 0; i < list.size(); i++)
            if (!list.get(i).getSessionToken().equals(all.get(i).getSessionToken())) return false;
        return true;
    }

    /** Escucha el estado de todas las sesiones de la entrada (no solo la que se muestra). */
    private void watchAll(List<MediaController> list) {
        if (list != null && sameSessions(list)) return;   // mismas sesiones: las escuchas siguen puestas
        for (int i = 0; i < all.size(); i++) try { all.get(i).unregisterCallback(allCb.get(i)); } catch (Throwable ignored) { }
        all.clear();
        allCb.clear();
        if (list == null) return;
        for (final MediaController m : list) {
            MediaController.Callback c = new MediaController.Callback() {
                public void onPlaybackStateChanged(PlaybackState st) {
                    // otra app empezó a sonar y la que se muestra no: pasar a la que suena
                    if (st != null && st.getState() == PlaybackState.STATE_PLAYING
                            && (current == null || (!current.getSessionToken().equals(m.getSessionToken()) && !playing(current)))) {
                        switchTo(m);
                    }
                }
            };
            try { m.registerCallback(c, ui); all.add(m); allCb.add(c); } catch (Throwable ignored) { }
        }
    }

    private void switchTo(MediaController best) {
        if (current != null && best != null && current.getSessionToken().equals(best.getSessionToken())) { emit(); return; }
        if (current != null) try { current.unregisterCallback(cb); } catch (Throwable ignored) { }
        current = best;
        if (current != null) current.registerCallback(cb, ui);
        emit();
    }

    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = new MediaSessionManager.OnActiveSessionsChangedListener() {
        public void onActiveSessionsChanged(List<MediaController> list) { pick(list); }
    };

    private void pick(List<MediaController> found) {
        List<MediaController> list = new ArrayList<MediaController>();
        if (found != null) for (MediaController m : found) if (wanted(m)) list.add(m);
        watchAll(list);
        MediaController best = null;
        // la que suena y trae canción (p. ej. com.android.bluetooth antes que una sesión vacía de la app de fábrica)
        for (MediaController m : list) if (playing(m) && hasTitle(m)) { best = m; break; }
        if (best == null) for (MediaController m : list) if (playing(m)) { best = m; break; }
        // nada suena: se mantiene la que se mostraba si sigue activa; si no, la primera de la lista
        if (best == null && current != null)
            for (MediaController m : list) if (m.getSessionToken().equals(current.getSessionToken())) { best = m; break; }
        if (best == null && !list.isEmpty()) best = list.get(0);
        switchTo(best);
    }

    /** true si hay una sesión activa de la entrada elegida (aunque esté en pausa). */
    public boolean hasSession() { return current != null; }

    private final MediaController.Callback cb = new MediaController.Callback() {
        public void onMetadataChanged(MediaMetadata m) { emit(); }
        public void onPlaybackStateChanged(PlaybackState s) { emit(); }
        public void onSessionDestroyed() { start(); }
    };

    private void emit() {
        final NowPlaying.Track t = new NowPlaying.Track();
        t.origin = "ms";
        MediaController c = current;
        if (c == null) {
            t.clear = true;
            t.artSet = true;
        } else {
            t.pkg = c.getPackageName();
            if (NowPlaying.isBtPkg(t.pkg)) t.source = "Bluetooth";
            else try {
                t.source = String.valueOf(ctx.getPackageManager().getApplicationLabel(
                        ctx.getPackageManager().getApplicationInfo(t.pkg, 0)));
            } catch (Throwable e) { t.source = t.pkg; }
            MediaMetadata m = c.getMetadata();
            if (m != null) {
                readMeta(t, m);
                t.artSet = true;   // sin carátula: se borra la de la canción anterior
                if (t.art == null) {
                    String u = artUriOf(m);
                    if (u != null) {
                        if (u.equals(artUri)) t.art = artFromUri;   // ya cargada (null si aún carga o no se pudo)
                        else loadArt(u);
                    }
                }
            }
            readState(t, c.getPlaybackState());
        }
        listener.onTrack(t);
    }

    /** Título, artista, álbum, duración y la carátula más grande que traiga la canción. */
    private static void readMeta(NowPlaying.Track t, MediaMetadata m) {
        t.title = text(m, MediaMetadata.METADATA_KEY_TITLE);
        if (t.title == null) t.title = text(m, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        t.artist = text(m, MediaMetadata.METADATA_KEY_ARTIST);
        if (t.artist == null) t.artist = text(m, MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (t.artist == null) t.artist = text(m, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        t.album = text(m, MediaMetadata.METADATA_KEY_ALBUM);
        try { long d = m.getLong(MediaMetadata.METADATA_KEY_DURATION); if (d > 0) t.durationMs = d; } catch (Throwable ignored) { }
        Bitmap best = null;
        for (String k : new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
            try {
                Bitmap b = m.getBitmap(k);
                if (b != null && (best == null || b.getWidth() * b.getHeight() > best.getWidth() * best.getHeight())) best = b;
            } catch (Throwable ignored) { }
        }
        t.art = best;
    }

    private static String text(MediaMetadata m, String key) {
        try {
            String s = m.getString(key);
            if (s == null) return null;
            s = s.trim();
            return s.length() == 0 || "Not Provided".equalsIgnoreCase(s) || "unknown".equalsIgnoreCase(s) ? null : s;
        } catch (Throwable e) { return null; }
    }

    private static void readState(NowPlaying.Track t, PlaybackState st) {
        if (st == null) return;
        t.playing = st.getState() == PlaybackState.STATE_PLAYING || st.getState() == PlaybackState.STATE_BUFFERING;
        long pos = st.getPosition();
        if (pos >= 0) {
            if (t.playing && st.getLastPositionUpdateTime() > 0)
                pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
            t.positionMs = pos;
        }
    }

    private static String artUriOf(MediaMetadata m) {
        for (String k : new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}) {
            String u = text(m, k);
            if (u != null) return u;
        }
        return null;
    }

    /** Carga en segundo plano la carátula que llegó como dirección (content:// o file://) y vuelve a avisar. */
    private void loadArt(final String u) {
        artUri = u;
        artFromUri = null;
        new Thread(new Runnable() {
            public void run() {
                Bitmap b = null;
                InputStream in = null;
                try {
                    Uri uri = Uri.parse(u);
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    in = ctx.getContentResolver().openInputStream(uri);
                    BitmapFactory.decodeStream(in, null, o);
                    in.close();
                    int sample = 1;
                    while (o.outWidth / (sample * 2) >= 300 && o.outHeight / (sample * 2) >= 300) sample *= 2;
                    o = new BitmapFactory.Options();
                    o.inSampleSize = sample;
                    in = ctx.getContentResolver().openInputStream(uri);
                    b = BitmapFactory.decodeStream(in, null, o);
                } catch (Throwable ignored) {
                } finally {
                    try { if (in != null) in.close(); } catch (Throwable ignored) { }
                }
                final Bitmap art = b;
                ui.post(new Runnable() {
                    public void run() {
                        if (!u.equals(artUri)) return;   // ya cambió la canción
                        artFromUri = art;
                        if (art != null) emit();
                    }
                });
            }
        }).start();
    }

    /**
     * Aviso AVRCP del Bluetooth de Android 6–9 (android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT): la app
     * Bluetooth lo envía a cada cambio de canción (MediaMetadata) y de estado/tiempo (PlaybackState). Sirve aunque MiniLauncher
     * no tenga «Acceso a notificaciones». Devuelve null si el aviso no trae ninguno de los dos.
     */
    static NowPlaying.Track fromAvrcp(Bundle x) {
        Object md = x.get("android.bluetooth.avrcp-controller.profile.extra.METADATA");
        Object pb = x.get("android.bluetooth.avrcp-controller.profile.extra.PLAYBACK");
        if (!(md instanceof MediaMetadata) && !(pb instanceof PlaybackState)) return null;
        NowPlaying.Track t = new NowPlaying.Track();
        t.origin = "bt";
        t.source = "Bluetooth";
        t.pkg = "com.android.bluetooth";
        if (md instanceof MediaMetadata) {
            readMeta(t, (MediaMetadata) md);
            t.artSet = t.art != null;   // estos avisos casi nunca traen carátula: no borran la que haya
        }
        if (pb instanceof PlaybackState) readState(t, (PlaybackState) pb);
        return t;
    }

    /** Sesiones activas, para el diagnóstico del reproductor. */
    String diag() {
        if (msm == null) return "no iniciado";
        try {
            List<MediaController> l = msm.getActiveSessions(listenerName());
            if (l == null || l.isEmpty()) return "ninguna";
            StringBuilder b = new StringBuilder();
            for (MediaController m : l) {
                PlaybackState st = m.getPlaybackState();
                MediaMetadata md = m.getMetadata();
                b.append(m.getPackageName()).append(NowPlaying.isBtPkg(m.getPackageName()) ? " [Bluetooth]" : " [Reproductor]")
                    .append(" · estado ").append(st == null ? "?" : String.valueOf(st.getState()))
                    .append(" · ").append(md == null ? "sin datos" : String.valueOf(text(md, MediaMetadata.METADATA_KEY_TITLE)))
                    .append(current != null && current.getSessionToken().equals(m.getSessionToken()) ? "  ← en pantalla" : "")
                    .append('\n');
            }
            return b.toString().trim();
        } catch (SecurityException e) {
            return "sin acceso a notificaciones";
        } catch (Throwable e) {
            return "no disponible (" + e.getClass().getSimpleName() + ")";
        }
    }

    /** Controles: 1 siguiente, -1 anterior, 0 play/pausa. Devuelve false si no hay sesión. */
    public boolean command(int which) {
        MediaController c = current;
        if (c == null) return false;
        MediaController.TransportControls tc = c.getTransportControls();
        if (which == 1) tc.skipToNext();
        else if (which == -1) tc.skipToPrevious();
        else {
            PlaybackState st = c.getPlaybackState();
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) tc.pause(); else tc.play();
        }
        return true;
    }
}
