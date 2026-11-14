package com.mini.launcher;

import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcel;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Verifica o contrato observado na central com Binder/Bundle reais, sem um carro ligado. */
public class CarInfoTest extends InstrumentationTestCase {
    private CarInfo car;
    private Spd service;

    @Override protected void setUp() throws Exception {
        super.setUp();
        Constructor<CarInfo> ctor = CarInfo.class.getDeclaredConstructor(Context.class);
        ctor.setAccessible(true);
        car = ctor.newInstance(getInstrumentation().getTargetContext());
        field("codeInt", 1);
        field("codeBundle", 2);
        field("codeFloat", 3);
        service = new Spd();
        service.ints.put(170004, 135725059);
        service.ints.put(100045, 19);
        service.bundles.put(100011, distance(227));
        service.bundles.put(100013, distance(123456));
        Bundle units = new Bundle(); units.putInt("mSetEnergyUnit", 0);
        service.bundles.put(120283, units);
        Bundle trip = new Bundle();
        trip.putFloat("mCarArriveMile", 227); trip.putFloat("mCarEnergy", 6.5f); trip.putFloat("mCarAverageSpeed", 38.5f);
        service.bundles.put(120287, trip);
        Bundle doors = new Bundle(); doors.putInt("frontLeft", 1); doors.putInt("frontRight", 0);
        service.bundles.put(50001, doors);
        Bundle outside = new Bundle(); outside.putInt("unit", 0); outside.putFloat("value", 16.5f);
        service.bundles.put(30023, outside);
    }

    public void testConfirmedReadingsAndPollingCadence() throws Exception {
        read(0);
        Vehicle v = car.vehicle();
        assertEquals(19f, v.fuelLitres, 0.001f);
        assertEquals(227f, v.rangeKm, 0.001f);
        assertEquals(123456f, v.odometerKm, 0.001f);
        assertEquals(6.5f, v.consumption, 0.001f);
        assertEquals(16.5f, v.outsideC, 0.001f);
        assertTrue(v.doors.frontLeft);
        int fuelReads = service.fuelReads;
        read(1);
        assertEquals(fuelReads, service.fuelReads);
        assertEquals(2, service.doorReads);
        read(10);
        assertEquals(fuelReads + 1, service.fuelReads);
        assertEquals(0, service.unexpectedTransactions);
    }

    public void testFuelAndConsumptionRequireKnownProfileAndUnits() throws Exception {
        service.ints.put(170004, 123);
        service.bundles.get(100011).putInt("unit", 1);
        service.bundles.get(100013).putInt("unit", 1);
        service.bundles.get(120283).putInt("mSetEnergyUnit", 1);
        read(0);
        assertNull(car.vehicle().fuelLitres);
        assertNull(car.vehicle().rangeKm);
        assertNull(car.vehicle().odometerKm);
        assertNull(car.vehicle().consumption);
    }

    public void testRawDiagnosticsAreNotConvertedAndMissingValuesAreCleared() throws Exception {
        service.ints.put(140080, 4);
        service.ints.put(140030, 0);
        service.floats.put(140062, 42.5f);
        read(0);
        assertEquals("4", car.vehicle().spdDiagnostics.get("spd_gear"));
        assertEquals("0", car.vehicle().spdDiagnostics.get("spd_brake"));
        assertEquals("42.5", car.vehicle().spdDiagnostics.get("spd_speed"));
        assertTrue(car.vehicle().spdDiagnosticsAt > 0);
        Map<String, String> previous = car.vehicle().spdDiagnostics;
        service.ints.remove(140080); service.floats.clear();
        read(10);
        assertNull(car.vehicle().spdDiagnostics.get("spd_gear"));
        assertNull(car.vehicle().spdDiagnostics.get("spd_speed"));
        assertEquals("4", previous.get("spd_gear"));
    }

    public void testOptionalFloatGetterDoesNotBlockOtherReadings() throws Exception {
        field("codeFloat", 0);
        service.floats.put(140062, 30f);
        read(0);
        assertNull(car.vehicle().spdDiagnostics.get("spd_speed"));
        assertNotNull(car.vehicle().rangeKm);
        assertEquals(0, service.floatReads);
    }

    public void testSpeedIsPolledEverySecondAndClearedWhenUnavailable() throws Exception {
        service.floats.put(140062, 72.5f);
        read(0);
        assertEquals(72.5f, car.vehicle().speedKmh, 0.001f);
        assertTrue(car.vehicle().speedAt > 0);
        assertEquals(1, service.floatReads);
        service.floats.put(140062, 0f);
        read(1);
        assertEquals(0f, car.vehicle().speedKmh, 0.001f);
        assertEquals(2, service.floatReads);
        service.floats.put(140062, 1.2f);
        read(2);
        assertEquals(1.2f, car.vehicle().speedKmh, 0.001f);
        service.floats.clear();
        read(3);
        assertNull(car.vehicle().speedKmh);
        assertEquals(0L, car.vehicle().speedAt);
        assertEquals(0, service.unexpectedTransactions);
    }

    public void testInvalidSpeedAndUnknownProfileAreRejected() throws Exception {
        for (float invalid : new float[]{-1, 401, Float.NaN, Float.POSITIVE_INFINITY}) {
            service.floats.put(140062, invalid);
            read(0);
            assertNull(car.vehicle().speedKmh);
        }
        service.floats.put(140062, 72f);
        service.ints.put(170004, 123);
        read(0);
        assertNull(car.vehicle().speedKmh);
        service.ints.put(170004, 135725059);
        read(0);
        assertNotNull(car.vehicle().speedKmh);
        Method read = CarInfo.class.getDeclaredMethod("read", IBinder.class, int.class);
        read.setAccessible(true); read.invoke(car, null, 1);
        assertNull(car.vehicle().speedKmh);
        assertEquals(0L, car.vehicle().speedAt);
    }

    public void testSpeedCallbackWinsOverDefaultGetterAndIgnoresOtherFields() throws Exception {
        HandlerThread thread = new HandlerThread("SpdSpeedTest"); thread.start();
        Handler worker = new Handler(thread.getLooper());
        objectField("worker", worker); objectField("binder", service); objectField("running", true);
        field("model", 135725059); field("codeChanged", 4); field("codeSpeedChanged", 2);
        Field callbackField = CarInfo.class.getDeclaredField("callback"); callbackField.setAccessible(true);
        IBinder callback = (IBinder) callbackField.get(car);
        try {
            sendSpeed(callback, 140062, 65.5f, 0);
            awaitWorker(worker);
            assertEquals(65.5f, car.vehicle().speedKmh, 0.001f);
            service.floats.put(140062, 0f); // este firmware só atualiza a velocidade nos avisos
            read(1);
            assertEquals(65.5f, car.vehicle().speedKmh, 0.001f);
            sendSpeed(callback, 140057, 99f, 0); // ângulo, não velocidade
            sendSpeed(callback, 140062, 99f, 2); // argumento não utilizado
            awaitWorker(worker);
            assertEquals(65.5f, car.vehicle().speedKmh, 0.001f);
            objectField("speedPushAt", SystemClock.elapsedRealtime() - Vehicle.SPEED_MAX_AGE_MS - 1);
            read(2);
            assertEquals(0f, car.vehicle().speedKmh, 0.001f);
            sendSpeed(callback, 140062, Float.NaN, 0);
            awaitWorker(worker);
            assertNull(car.vehicle().speedKmh);
            objectField("running", false);
            sendSpeed(callback, 140062, 80f, 0);
            awaitWorker(worker);
            assertNull(car.vehicle().speedKmh);
        } finally { objectField("worker", null); thread.quit(); thread.join(2000); }
    }

    private static void sendSpeed(IBinder callback, int id, float speed, int arg) throws Exception {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("com.spd.carinfo.ICarInfoAidlCallback");
            data.writeInt(id); data.writeFloat(speed); data.writeInt(arg);
            assertTrue(callback.transact(2, data, reply, 0)); reply.readException();
        } finally { data.recycle(); reply.recycle(); }
    }

    private static void awaitWorker(Handler worker) throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        worker.post(new Runnable() { public void run() { done.countDown(); } });
        assertTrue("SPD callback did not complete", done.await(5, TimeUnit.SECONDS));
    }

    private void objectField(String name, Object value) throws Exception {
        Field f = CarInfo.class.getDeclaredField(name); f.setAccessible(true); f.set(car, value);
    }

    public void testVoltageScaleAndInvalidReadings() throws Exception {
        Method voltage = CarInfo.class.getDeclaredMethod("voltage", int.class);
        voltage.setAccessible(true);
        assertEquals(12.3f, (Float) voltage.invoke(null, 123), 0.001f);
        assertEquals(11.7f, (Float) voltage.invoke(null, 117), 0.001f);
        assertNull(voltage.invoke(null, -1));
        assertNull(voltage.invoke(null, 0));
        assertNull(voltage.invoke(null, 321));
    }

    public void testTyreSentinelsAndMaintenanceValidity() throws Exception {
        Bundle tyres = new Bundle();
        tyres.putInt("mTyrePressValid", 1); tyres.putInt("mTyrePressDataValid", 1); tyres.putInt("mTyrePressUnit", 1);
        tyres.putIntArray("mTyrePressData", new int[]{24, -1, 255, 25});
        service.bundles.put(120288, tyres);
        Bundle maintenance = new Bundle();
        maintenance.putIntArray("mMaintainProjectNumber", new int[]{1, 3});
        maintenance.putIntArray("mMaintainDateYear", new int[]{28, 28});
        maintenance.putIntArray("mMaintainDateMonth", new int[]{4, 4});
        maintenance.putIntArray("mMaintainDateValid", new int[]{1, 1});
        maintenance.putIntArray("mMaintainDistance", new int[]{18000, -1});
        maintenance.putIntArray("mMaintainDistanceValid", new int[]{1, 0});
        maintenance.putIntArray("mMaintainDistanceUnit", new int[]{1, 1});
        maintenance.putIntArray("mMaintainDistanceType", new int[]{0, 0});
        maintenance.putIntArray("mMaintainProjectState", new int[]{0});
        service.bundles.put(120290, maintenance);
        read(0);
        assertEquals(2.4f, car.vehicle().tyresBar[0], 0.001f);
        assertNull(car.vehicle().tyresBar[1]);
        assertNull(car.vehicle().tyresBar[2]);
        assertEquals(Integer.valueOf(2028), car.vehicle().services.get(0).year);
        assertEquals(Integer.valueOf(18000), car.vehicle().services.get(0).remainingKm);
        assertTrue(car.vehicle().services.get(0).ok);
        assertNull(car.vehicle().services.get(1).remainingKm);
        assertFalse(car.vehicle().services.get(1).ok);
    }

    private void field(String name, int value) throws Exception {
        Field f = CarInfo.class.getDeclaredField(name); f.setAccessible(true); f.setInt(car, value);
    }

    private void read(int tick) throws Exception {
        Method m = CarInfo.class.getDeclaredMethod("read", IBinder.class, int.class);
        m.setAccessible(true); m.invoke(car, service, tick);
    }

    private static Bundle distance(float km) {
        Bundle b = new Bundle(); b.putInt("unit", 0); b.putFloat("value", km); return b;
    }

    private static final class Spd extends Binder {
        final Map<Integer, Integer> ints = new HashMap<Integer, Integer>();
        final Map<Integer, Float> floats = new HashMap<Integer, Float>();
        final Map<Integer, Bundle> bundles = new HashMap<Integer, Bundle>();
        int fuelReads, doorReads, floatReads, unexpectedTransactions;

        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            data.enforceInterface("com.spd.carinfo.ICarInfoAidlInterface");
            int id = data.readInt(); assertEquals(0, data.readInt());
            if (code == 1) {
                if (id == 100045) fuelReads++;
                if (!ints.containsKey(id)) return false;
                reply.writeNoException(); reply.writeInt(ints.get(id)); return true;
            }
            if (code == 2) {
                if (id == 50001) doorReads++;
                Bundle b = bundles.get(id);
                reply.writeNoException(); reply.writeInt(b == null ? 0 : 1);
                if (b != null) b.writeToParcel(reply, 0);
                return true;
            }
            if (code == 3) {
                floatReads++;
                if (!floats.containsKey(id)) return false;
                reply.writeNoException(); reply.writeFloat(floats.get(id)); return true;
            }
            unexpectedTransactions++;
            return false;
        }
    }
}
