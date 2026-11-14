package com.mini.launcher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
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
            assertNull(field("carMaintenance"));
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

    public void testSpdMaintenanceAppearsInSettingsAndUsesSharedBadge() {
        onUi(new Runnable() { public void run() {
            final int year = Calendar.getInstance().get(Calendar.YEAR) + 2;
            final Vehicle real = new Vehicle();
            real.services = Arrays.asList(new Vehicle.Service(1, year, 4, 18000, true),
                    new Vehicle.Service(2, year + 2, 4, 42000, true), new Vehicle.Service(3, year, 4, null, true));
            withCar(real, new Runnable() { public void run() {
                prefs.edit().putBoolean("oil_on", true).putFloat("oil_last_km", 0).putFloat("oil_interval_km", 100)
                        .putString("maint", "[{\"t\":\"air\",\"km\":15000,\"lkm\":4567}]").commit();
                call("rebuild");
                assertTrue((Boolean) call("spdOilAvailable"));
                assertFalse((Boolean) call("oilAlert")); // não duplica o aviso manual de óleo
                List<?> tasks = (List<?>) call("maint");
                assertEquals(4, tasks.size());
                call("saveMaint", List.class, tasks);
                try { assertEquals(1, new org.json.JSONArray(prefs.getString("maint", "")).length()); }
                catch (Exception e) { throw new AssertionError(e); }
                call("showSettings", int.class, Main.C_MAINT);
                assertNotNull(field("carMaintenance"));
                assertSingleOilSection();
                assertTrue(((Map<?, ?>) field("carViews")).isEmpty());
                List<?> rows = maintenanceRows();
                assertEquals(3, rows.size());
                Object oilRow = rows.get(0);
                real.services = Arrays.asList(new Vehicle.Service(1, year, 4, 0, true), real.services.get(1), real.services.get(2));
                call("refreshCarViews");
                assertSame(oilRow, rows.get(0));
                assertEquals("0 km", ((TextView) maintenanceField(0, "distance")).getText().toString());
                assertEquals(I18n.t("¡Ya toca!"), ((TextView) maintenanceField(0, "reminder")).getText().toString());
                call("closeSettings"); call("updateOilBadge");
                assertEquals(I18n.t("Aceite del motor"), ((TextView) field("oilLblTv")).getText().toString());
                assertEquals(I18n.t("¡Ya toca!"), ((TextView) field("oilValTv")).getText().toString());
                assertEquals(View.GONE, ((View) field("oilBarBg")).getVisibility());
            }});
        }});
    }

    public void testSpdMaintenanceAlertSnoozesAndCompletesWithoutChangingCar() {
        onUi(new Runnable() { public void run() {
            final Vehicle real = new Vehicle(); real.services = Arrays.asList(new Vehicle.Service(1, null, null, 0, true));
            withCar(real, new Runnable() { public void run() {
                boolean oldMaint = (Boolean) field("maintLater"), oldOil = (Boolean) field("oilLater");
                try {
                    setMainField("maintLater", false); setMainField("oilLater", false);
                    assertTrue((Boolean) call("maintAlert"));
                    android.app.Dialog alert = (android.app.Dialog) field("oilDlg"); assertTrue(alert.isShowing()); alert.dismiss();
                    org.json.JSONObject task = (org.json.JSONObject) call("spdTask", int.class, 1);
                    call("snoozeSpdTask", org.json.JSONObject.class, task);
                    assertFalse((Boolean) call("maintAlert"));
                    SpdMaintenance.rearm(1, prefs);
                    call("completeSpdTask", org.json.JSONObject.class, task);
                    assertFalse((Boolean) call("maintAlert"));
                    assertEquals(Integer.valueOf(0), real.services.get(0).remainingKm);
                    real.services = Arrays.asList(new Vehicle.Service(1, null, null, 18000, true));
                    task = (org.json.JSONObject) call("spdTask", int.class, 1);
                    assertTrue(SpdMaintenance.active(task));
                    call("updateOilBadge");
                    assertEquals("18\u00a0000 km".replace('\u00a0', ' '), ((TextView) field("oilValTv")).getText().toString().replace('\u00a0', ' '));
                } finally {
                    android.app.Dialog alert = (android.app.Dialog) field("oilDlg"); if (alert != null) alert.dismiss();
                    setMainField("oilDlg", null); setMainField("maintLater", oldMaint); setMainField("oilLater", oldOil);
                }
            }});
        }});
    }

    public void testDemoMaintenanceNeverGeneratesReminders() {
        onUi(new Runnable() { public void run() {
            assertNotNull(field("demoCar"));
            prefs.edit().putBoolean(SpdMaintenance.key(1) + "done", true).putLong(SpdMaintenance.key(1) + "done_date", 1).commit();
            call("showSettings", int.class, Main.C_MAINT);
            assertSingleOilSection();
            assertFalse(((View) maintenanceField(0, "toggleRow")).isEnabled());
            assertTrue(prefs.getBoolean(SpdMaintenance.key(1) + "done", false));
            assertTrue(((List<?>) call("maint")).isEmpty());
            assertFalse((Boolean) call("maintAlert"));
            assertEquals("[]", prefs.getString("maint", "[]"));
            Map<String, ?> saved = prefs.getAll();
            android.app.AlertDialog overview = (android.app.AlertDialog) call("showMaintenanceDialog");
            try {
                View content = overview.getWindow().getDecorView();
                assertEquals(1, countText(content, I18n.t("Cambio de aceite")));
                assertTrue(containsText(content, I18n.t("Inspección del vehículo")));
                assertTrue(containsText(content, I18n.t("Líquido de frenos")));
                assertTrue(containsText(content, I18n.t("Datos simulados")));
                java.util.ArrayList<View> oil = new java.util.ArrayList<View>();
                content.findViewsWithText(oil, I18n.t("Cambio de aceite"), View.FIND_VIEWS_WITH_TEXT);
                assertEquals(1, oil.size());
                assertFalse(((View) oil.get(0).getParent().getParent()).performClick());
                assertEquals(saved, prefs.getAll());
            } finally { overview.dismiss(); }
        }});
    }

    public void testMaintenanceDialogShowsAllServicesAndKeepsActionsLocal() {
        onUi(new Runnable() { public void run() {
            int year = Calendar.getInstance().get(Calendar.YEAR) + 2;
            final Vehicle real = new Vehicle();
            real.services = Arrays.asList(new Vehicle.Service(1, year, 4, 18000, true),
                    new Vehicle.Service(2, year, 8, 42000, true), new Vehicle.Service(3, null, null, null, false));
            withCar(real, new Runnable() { public void run() {
                prefs.edit().putBoolean("oil_on", true).putBoolean(SpdMaintenance.key(2) + "enabled", false)
                        .putString("maint", "[{\"n\":\"Filtro do ar\",\"km\":15000,\"lkm\":4567}]").commit();
                Map<String, ?> saved = prefs.getAll();
                android.app.AlertDialog overview = (android.app.AlertDialog) call("showMaintenanceDialog");
                try {
                    View content = overview.getWindow().getDecorView();
                    assertEquals(1, countText(content, I18n.t("Cambio de aceite")));
                    assertTrue(containsText(content, I18n.t("Inspección del vehículo")));
                    assertTrue(containsText(content, I18n.t("Líquido de frenos")));
                    assertTrue(containsText(content, "Filtro do ar"));
                    assertTrue(containsText(content, I18n.t("Recordatorio desactivado")));
                    assertTrue(containsText(content, I18n.t("No disponible")));
                    assertEquals(saved, prefs.getAll());
                } finally { overview.dismiss(); }
                android.app.AlertDialog actions = (android.app.AlertDialog) call("spdTaskMenu", int.class, 2);
                try {
                    assertEquals(3, actions.getListView().getCount());
                    actions.getListView().performItemClick(null, 0, actions.getListView().getItemIdAtPosition(0));
                    assertTrue(prefs.getBoolean(SpdMaintenance.key(2) + "enabled", false));
                    assertEquals(Integer.valueOf(42000), real.services.get(1).remainingKm);
                } finally { actions.dismiss(); }
            }});
        }});
    }

    public void testMaintenanceTogglesKeepSpdDetailsBelowEachItem() {
        onUi(new Runnable() { public void run() {
            int year = Calendar.getInstance().get(Calendar.YEAR) + 2;
            final Vehicle real = new Vehicle();
            real.services = Arrays.asList(new Vehicle.Service(1, year, 4, 18000, true),
                    new Vehicle.Service(2, year + 2, 4, 42000, true), new Vehicle.Service(3, year, 4, null, true));
            withCar(real, new Runnable() { public void run() {
                call("showSettings", int.class, Main.C_VEHICLE);
                assertNull(field("carMaintenance"));
                assertFalse(containsText((View) field("settingsScroll"), I18n.t("Aceite del motor")));
                assertFalse(containsText((View) field("settingsScroll"), I18n.t("Mantenimientos")));
                call("showSettings", int.class, Main.C_MAINT);
                for (int i = 0; i < real.services.size(); i++) {
                    int code = real.services.get(i).code;
                    View toggle = (View) maintenanceField(i, "toggleRow");
                    assertTrue(toggle.isEnabled());
                    TextView date = (TextView) maintenanceField(i, "date");
                    TextView distance = (TextView) maintenanceField(i, "distance");
                    android.view.ViewGroup card = (android.view.ViewGroup) toggle.getParent();
                    View details = (View) date.getParent().getParent().getParent();
                    assertSame(card, details.getParent());
                    assertEquals(card.indexOfChild(toggle) + 2, card.indexOfChild(details)); // divisor entre o cabeçalho e os dados
                    String shownDate = date.getText().toString(), shownDistance = distance.getText().toString();
                    toggle.performClick();
                    assertFalse(prefs.getBoolean(SpdMaintenance.key(code) + "enabled", true));
                    assertEquals(shownDate, ((TextView) maintenanceField(i, "date")).getText().toString());
                    assertEquals(shownDistance, ((TextView) maintenanceField(i, "distance")).getText().toString());
                    assertEquals(I18n.t("Recordatorio desactivado"), ((TextView) maintenanceField(i, "reminder")).getText().toString());
                    ((View) maintenanceField(i, "toggleRow")).performClick();
                    assertTrue(prefs.getBoolean(SpdMaintenance.key(code) + "enabled", false));
                }
                assertEquals(Integer.valueOf(18000), real.services.get(0).remainingKm);
                assertEquals(Integer.valueOf(42000), real.services.get(1).remainingKm);
                assertNull(real.services.get(2).remainingKm);
            }});
        }});
    }

    private Object maintenanceField(int index, String name) {
        Object row = maintenanceRows().get(index);
        return field("toggleRow".equals(name) ? row : field(row, "details"), name);
    }

    private List<?> maintenanceRows() { return (List<?>) field(field("carMaintenance"), "rows"); }

    private static boolean containsText(View view, String text) {
        return countText(view, text) > 0;
    }

    private static int countText(View view, String text) {
        int count = view instanceof TextView && text.contentEquals(((TextView) view).getText()) ? 1 : 0;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += countText(group.getChildAt(i), text);
        }
        return count;
    }

    private void assertSingleOilSection() {
        View settings = (View) field("settingsScroll");
        assertEquals(1, countText(settings, I18n.t("Cambio de aceite").toUpperCase(I18n.loc)));
        assertEquals(1, countText(settings, I18n.t("Recordatorio de cambio de aceite")));
        assertFalse(containsText(settings, I18n.t("Aceite del motor")));
    }

    public void testMaintenanceSettingsSwitchToCarWhenSpdArrivesLater() {
        onUi(new Runnable() { public void run() {
            final Vehicle real = new Vehicle();
            withCar(real, new Runnable() { public void run() {
                prefs.edit().putBoolean("oil_on", true).putFloat("oil_last_km", 4000).putFloat("oil_interval_km", 7000).commit();
                call("showSettings", int.class, Main.C_MAINT);
                assertSingleOilSection();
                assertTrue(containsText((View) field("settingsScroll"), I18n.t("Cada cuánto")));
                real.services = Arrays.asList(new Vehicle.Service(1, null, null, null, false));
                ((CarInfo.Listener) field("carListener")).onCarInfo();
                assertSingleOilSection();
                assertTrue(containsText((View) field("settingsScroll"), I18n.t("Cada cuánto")));
                real.services = Arrays.asList(new Vehicle.Service(1, Calendar.getInstance().get(Calendar.YEAR) + 2, 4, 18000, true));
                ((CarInfo.Listener) field("carListener")).onCarInfo();
                assertSingleOilSection();
                assertFalse(containsText((View) field("settingsScroll"), I18n.t("Cada cuánto")));
                assertNotNull(field("carMaintenance"));
                assertEquals("18 000 km", ((TextView) maintenanceField(0, "distance")).getText().toString().replace('\u00a0', ' '));
                assertEquals(Main.C_MAINT, ((Integer) field("settingsCat")).intValue());
                real.services = null;
                ((CarInfo.Listener) field("carListener")).onCarInfo();
                assertSingleOilSection();
                assertTrue(containsText((View) field("settingsScroll"), I18n.t("Cada cuánto")));
                assertEquals(4000f, prefs.getFloat("oil_last_km", -1), 0.001f);
                assertEquals(7000f, prefs.getFloat("oil_interval_km", -1), 0.001f);
            }});
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
        Object demo = field("demoCar");
        try {
            setMainField("demoCar", null);
            setCarField("data", vehicle); setCarField("state", CarInfo.CONNECTED);
            test.run();
        } finally { setCarField("data", original); setCarField("state", state); setMainField("demoCar", demo); }
    }

    private void setMainField(String name, Object value) {
        try { Field f = Main.class.getDeclaredField(name); f.setAccessible(true); f.set(activity, value); }
        catch (Exception e) { throw new AssertionError(e); }
    }

    private void onUi(final Runnable test) {
        final Throwable[] error = new Throwable[1];
        getInstrumentation().runOnMainSync(new Runnable() { public void run() {
            try { test.run(); } catch (Throwable e) { error[0] = e; }
        }});
        if (error[0] != null) throw new AssertionError(error[0]);
    }

    private Object field(String name) {
        return field(activity, name);
    }

    private Object field(Object owner, String name) {
        try { Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
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
