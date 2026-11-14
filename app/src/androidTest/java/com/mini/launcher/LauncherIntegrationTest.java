package com.mini.launcher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Verificações das funcionalidades do MiniLauncher. Executar num emulador sem SPD. */
public class LauncherIntegrationTest extends ActivityInstrumentationTestCase2<Main> {
    private Main activity;
    private SharedPreferences prefs;

    public LauncherIntegrationTest() { super(Main.class); }

    @Override protected void setUp() throws Exception {
        super.setUp();
        prefs = getInstrumentation().getTargetContext().getSharedPreferences("launcher", 0);
        prefs.edit().clear().putBoolean("setup_done", true).putBoolean("notif_asked", true)
                .putBoolean("auto_update", false).putBoolean("map_live", false).putBoolean("weather_on", false)
                .putString("lang", "pt").putString("speed_unit", "kmh").putFloat("odo_base_km", 4567f).commit();
        setActivityIntent(new Intent(Intent.ACTION_MAIN).putExtra("prueba", "saltar"));
        activity = getActivity();
        getInstrumentation().waitForIdleSync();
    }

    public void testDemoTelemetryNeverChangesMaintenanceOdometer() {
        onUi(new Runnable() { public void run() {
            assertNotNull(field("demoCar"));
            call("syncOdo");
            assertEquals(4567f, prefs.getFloat("odo_base_km", -1), 0.001f);
            assertFalse((Boolean) call("carOdoLive"));
            assertEquals("Dados simulados", ((TextView) field("chipTv")).getText().toString());
            assertTrue(((TextView) field("carVoltageTv")).getText().toString().contains("12,6"));
        }});
    }

    public void testRealCanOdometerReplacesGpsEstimate() {
        onUi(new Runnable() { public void run() {
            CarInfo car = (CarInfo) field("car");
            Vehicle original = car.vehicle();
            Vehicle real = new Vehicle(); real.odometerKm = 123456f;
            try {
                Field data = CarInfo.class.getDeclaredField("data"); data.setAccessible(true);
                try {
                    data.set(car, real);
                    prefs.edit().putFloat("odo_gps_m", 500).commit();
                    call("syncOdo");
                    assertTrue((Boolean) call("carOdoLive"));
                    assertEquals(123456f, prefs.getFloat("odo_base_km", -1), 0.001f);
                    assertEquals(0f, prefs.getFloat("odo_gps_m", -1), 0.001f);
                } finally { data.set(car, original); }
            } catch (Exception e) { throw new AssertionError(e); }
        }});
    }

    public void testSettingsKeepVehicleDiagnosticsAndSystemDebugging() {
        onUi(new Runnable() { public void run() {
            call("showSettings", int.class, Main.C_VEHICLE);
            assertTrue(((Map<?, ?>) field("carViews")).size() > 10);
            assertNotNull(field("carMaintenance"));
            call("showSettings", int.class, Main.C_SYS);
            call("refreshDebuggingSettings");
            assertEquals(Main.C_SYS, ((Integer) field("settingsCat")).intValue());
            assertTrue(((Map<?, ?>) field("carViews")).isEmpty());
            // As categorias reorganizadas, incluindo a manutenção por tempo, continuam acessíveis.
            for (int i = 1; i < Main.CAT_NAMES.length; i++) call("showSettings", int.class, i);
            call("closeSettings");
        }});
    }

    public void testTelemetryFitsWithEveryGaugeAndLargeText() {
        for (final int font : new int[]{100, 150}) {
            for (final int gauge : new int[]{0, 1, 2}) {
                onUi(new Runnable() { public void run() {
                    prefs.edit().putInt("text_size", font).putInt("speedo", gauge).commit();
                    call("loadTextSize");
                    call("rebuild");
                }});
                getInstrumentation().waitForIdleSync();
                onUi(new Runnable() { public void run() {
                    TextView fuel = (TextView) field("carFuelTv");
                    View strip = (View) fuel.getParent().getParent().getParent();
                    TextView chip = (TextView) field("chipTv");
                    int[] stripAt = new int[2], chipAt = new int[2];
                    strip.getLocationOnScreen(stripAt); chip.getLocationOnScreen(chipAt);
                    assertTrue("CAN overlaps label: " + font + "/" + gauge,
                            stripAt[1] >= chipAt[1] + chip.getHeight());
                    assertTrue(strip.getHeight() > 0);
                    TextView consumption = (TextView) field("carConsTv");
                    assertTrue("Consumption unit is clipped at " + font + "%: text="
                                    + consumption.getPaint().measureText(consumption.getText().toString())
                                    + " width=" + consumption.getWidth() + " size=" + consumption.getTextSize()
                                    + " max=" + ((Main.FitText) consumption).max + " min=" + ((Main.FitText) consumption).min
                                    + " lines=" + consumption.getMaxLines(),
                            consumption.getPaint().measureText(consumption.getText().toString()) <= consumption.getWidth() + 1);
                    if (gauge > 0) {
                        View speed = (View) field("carGauge");
                        assertNotNull(speed);
                        int[] speedAt = new int[2]; speed.getLocationOnScreen(speedAt);
                        assertTrue("CAN overlaps gauge", stripAt[1] + strip.getHeight() <= speedAt[1]);
                    }
                }});
            }
        }
    }

    public void testMusicInputFiltersTracksAndClearsPreviousTitle() {
        onUi(new Runnable() { public void run() {
            NowPlaying.Track bluetooth = new NowPlaying.Track();
            bluetooth.title = "Bluetooth track"; bluetooth.pkg = "com.spdbt.music";
            NowPlaying.Track player = new NowPlaying.Track();
            player.title = "Player track"; player.pkg = "com.android.music";
            assertTrue((Boolean) call("acceptInput", NowPlaying.Track.class, bluetooth));
            assertFalse((Boolean) call("acceptInput", NowPlaying.Track.class, player));
            call("showTrack", NowPlaying.Track.class, bluetooth);
            assertEquals("Bluetooth track", field("curTitle"));
            boolean connected = MediaListener.connected;
            MediaListener.connected = true; // O teste não abre a página de permissões do Android.
            try { call("setMusicInput", boolean.class, false); }
            finally { MediaListener.connected = connected; }
            assertNull(field("curTitle"));
            assertEquals("player", prefs.getString("music_input", ""));
            assertFalse((Boolean) call("acceptInput", NowPlaying.Track.class, bluetooth));
            assertTrue((Boolean) call("acceptInput", NowPlaying.Track.class, player));
            call("showTrack", NowPlaying.Track.class, player);
            assertEquals("Player track", field("curTitle"));
        }});
    }

    public void testCustomThemeScheduleRespectsConfiguredHours() {
        onUi(new Runnable() { public void run() {
            prefs.edit().putString("theme", "auto").putInt("dark_start", 0).putInt("dark_end", 0).commit();
            call("applyTheme");
            assertTrue(Main.LIGHT); // Intervalos iguais desativam o período escuro, mesmo durante a noite.
        }});
    }

    public void testSpdSpeedHasPriorityInAllGaugesAndConvertsToMph() {
        onUi(new Runnable() { public void run() {
            final Vehicle real = new Vehicle(); real.speedKmh = 72f; real.speedAt = SystemClock.elapsedRealtime();
            withCar(real, new Runnable() { public void run() {
                setGps(true, 40);
                for (int mode : new int[]{0, 1, 2}) {
                    real.speedAt = SystemClock.elapsedRealtime();
                    prefs.edit().putInt("speedo", mode).commit();
                    call("rebuild");
                    assertSpeed("72", "SPD");
                    if (mode > 0) assertGauge("carGauge", 72f);
                }
                prefs.edit().putString("speed_unit", "mph").commit();
                call("rebuild");
                assertSpeed("45", "SPD");
                assertGauge("carGauge", 72f * 0.621371f);
                prefs.edit().putString("speed_unit", "kmh").putBoolean("show_car", false).commit();
                call("rebuild");
                assertEquals("72 km/h", ((TextView) field("topSpeedTv")).getText().toString());
                prefs.edit().putBoolean("map_gauge", true).commit();
                call("rebuild");
                assertGauge("mapGauge", 72f);
            }});
        }});
    }

    public void testSpdSpeedSurvivesGpsLossAndFallsBackWhenStaleOrDisconnected() {
        onUi(new Runnable() { public void run() {
            final Vehicle real = new Vehicle(); real.speedKmh = 80f; real.speedAt = SystemClock.elapsedRealtime();
            withCar(real, new Runnable() { public void run() {
                setGps(false, 0);
                assertSpeed("80", "SPD");
                setGps(true, 43);
                assertSpeed("80", "SPD");
                real.speedAt = SystemClock.elapsedRealtime() - Vehicle.SPEED_MAX_AGE_MS - 1;
                real.updatedAt = System.currentTimeMillis(); // outras leituras não tornam a velocidade recente
                call("updateCarData");
                assertSpeed("43", "GPS");
                real.speedAt = SystemClock.elapsedRealtime();
                setCarField("state", CarInfo.CONNECTING);
                call("updateCarData");
                assertSpeed("43", "GPS");
                setGps(false, 0);
                assertEquals("--", ((TextView) field("speedTv")).getText().toString());
                setCarField("state", CarInfo.CONNECTED);
                real.speedKmh = 0f;
                call("updateCarData");
                assertSpeed("0", "SPD"); // zero é uma velocidade válida, sem depender do GPS
                real.speedKmh = 1.2f;
                call("updateCarData");
                assertSpeed("1", "SPD"); // não aplica o filtro de oscilação do GPS ao CAN
                real.speedKmh = null;
                setGps(true, 43);
                assertSpeed("43", "GPS");
                try {
                    Field fix = Main.class.getDeclaredField("lastFix"); fix.setAccessible(true);
                    fix.setLong(activity, SystemClock.elapsedRealtime() - 6001);
                } catch (Exception e) { throw new AssertionError(e); }
                call("refreshSpeed");
                assertEquals("--", ((TextView) field("speedTv")).getText().toString());
            }});
        }});
    }

    public void testSimulatedSpeedDoesNotOverrideGpsAndGpsWithoutSpeedIsUnavailable() {
        onUi(new Runnable() { public void run() {
            Vehicle demo = (Vehicle) field("demoCar");
            demo.speedKmh = 99f; demo.speedAt = SystemClock.elapsedRealtime();
            try {
                setGps(true, 30);
                assertSpeed("30", "GPS");
                call("rebuild");
                assertSpeed("30", "GPS");
                setGps(true, Float.NaN);
                assertEquals("--", ((TextView) field("speedTv")).getText().toString());
            } finally { demo.speedKmh = null; demo.speedAt = 0; }
        }});
    }

    private void assertSpeed(String value, String source) {
        assertEquals(value, ((TextView) field("speedTv")).getText().toString());
        assertEquals(source, ((TextView) field("gpsTv")).getText().toString());
    }

    private void assertGauge(String name, float value) {
        Object gauge = field(name); assertNotNull(gauge);
        try {
            Field target = Gauge.class.getDeclaredField("target"); target.setAccessible(true);
            Field ok = Gauge.class.getDeclaredField("ok"); ok.setAccessible(true);
            assertEquals(value, target.getFloat(gauge), 0.001f); assertTrue(ok.getBoolean(gauge));
        } catch (Exception e) { throw new AssertionError(e); }
    }

    private void setGps(boolean ok, float kmh) {
        invoke("setGps", new Class<?>[]{boolean.class, float.class}, new Object[]{ok, kmh});
    }

    private void setCarField(String name, Object value) {
        try {
            Field f = CarInfo.class.getDeclaredField(name); f.setAccessible(true); f.set(field("car"), value);
        } catch (Exception e) { throw new AssertionError(e); }
    }

    private void withCar(Vehicle vehicle, Runnable test) {
        CarInfo car = (CarInfo) field("car");
        Vehicle original = car.vehicle(); int state = car.state();
        try {
            setCarField("data", vehicle); setCarField("state", CarInfo.CONNECTED);
            test.run();
        } finally { setCarField("data", original); setCarField("state", state); }
    }

    private void onUi(final Runnable test) {
        final Throwable[] error = new Throwable[1];
        getInstrumentation().runOnMainSync(new Runnable() { public void run() {
            try { test.run(); } catch (Throwable e) { error[0] = e; }
        }});
        if (error[0] != null) throw new AssertionError(error[0]);
    }

    private Object field(String name) {
        try { Field f = Main.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
        catch (Exception e) { throw new AssertionError(e); }
    }

    private Object call(String name) { return invoke(name, new Class<?>[0], new Object[0]); }
    private Object call(String name, Class<?> type, Object value) {
        return invoke(name, new Class<?>[]{type}, new Object[]{value});
    }
    private Object invoke(String name, Class<?>[] types, Object[] values) {
        try { Method m = Main.class.getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(activity, values); }
        catch (Exception e) { throw new AssertionError(e); }
    }
}
