package com.mini.launcher;

import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;

import org.json.JSONObject;

import java.util.Arrays;

public class SpdMaintenanceTest extends InstrumentationTestCase {
    private SharedPreferences prefs;

    @Override protected void setUp() throws Exception {
        super.setUp();
        prefs = getInstrumentation().getTargetContext().getSharedPreferences("spd-maintenance-tests", 0);
        prefs.edit().clear().commit();
    }

    public void testCarDatesAndDistanceUseWhicheverIsDueFirst() {
        Vehicle.Service service = new Vehicle.Service(1, 2028, 4, 18000, true);
        JSONObject task = SpdMaintenance.task(service, prefs);
        long due = SpdMaintenance.dueDate(service);
        assertTrue(SpdMaintenance.fraction(task, due - 86400000L) > 0);
        assertEquals(0d, SpdMaintenance.fraction(task, due), 0.001);
        assertTrue(SpdMaintenance.fraction(task, due + 86400000L) < 0);
        JSONObject distanceDue = SpdMaintenance.task(new Vehicle.Service(1, 2028, 4, 0, true), prefs);
        assertEquals(0d, SpdMaintenance.fraction(distanceDue, due - 86400000L), 0.001);
        JSONObject timeOnly = SpdMaintenance.task(new Vehicle.Service(3, 2028, 4, null, true), prefs);
        assertTrue(SpdMaintenance.active(timeOnly));
        assertFalse(timeOnly.has("left"));
        assertEquals(0d, SpdMaintenance.fraction(timeOnly, due), 0.001);
    }

    public void testMissingOrUnknownDataNeverCreatesADueReminder() {
        for (Vehicle.Service service : Arrays.asList(new Vehicle.Service(1, null, null, null, false),
                new Vehicle.Service(3, 2028, null, null, true), new Vehicle.Service(2, 2028, 13, null, false))) {
            JSONObject task = SpdMaintenance.task(service, prefs);
            assertFalse(SpdMaintenance.active(task));
            assertTrue(SpdMaintenance.fraction(task, System.currentTimeMillis()) > 0);
        }
        Vehicle v = new Vehicle();
        v.services = Arrays.asList(new Vehicle.Service(1, 2028, 4, 18000, true), new Vehicle.Service(1, 2028, 4, 18000, true));
        assertEquals(1, SpdMaintenance.tasks(v, prefs).size());
    }

    public void testSnoozeEndsAtDistanceOrTimeAndSurvivesRecreation() {
        Vehicle.Service service = new Vehicle.Service(1, null, null, 0, true);
        long now = 1000000000L;
        JSONObject task = SpdMaintenance.task(service, prefs);
        SpdMaintenance.snooze(task, prefs, 50000, true, false, now);
        task = SpdMaintenance.task(service, prefs);
        assertTrue(SpdMaintenance.snoozed(task, 50050, true, now + 86400000L));
        assertFalse(SpdMaintenance.snoozed(task, 50100, true, now + 86400000L));
        assertFalse(SpdMaintenance.snoozed(task, 50000, true, now + 7 * 86400000L));
        SpdMaintenance.snooze(task, prefs, 50000, true, true, now);
        task = SpdMaintenance.task(service, prefs);
        assertTrue(SpdMaintenance.snoozed(task, 50090, true, now));
        assertFalse(SpdMaintenance.snoozed(task, 50100, true, now));
        JSONObject timeOnly = SpdMaintenance.task(new Vehicle.Service(3, 2028, 4, null, true), prefs);
        SpdMaintenance.snooze(timeOnly, prefs, 50000, true, false, now);
        timeOnly = SpdMaintenance.task(new Vehicle.Service(3, 2028, 4, null, true), prefs);
        assertTrue(SpdMaintenance.snoozed(timeOnly, 99999, true, now));
        assertFalse(SpdMaintenance.snoozed(timeOnly, 50000, true, now + 7 * 86400000L));
    }

    public void testDoneDoesNotChangeCarDataAndRearmsForNextSchedule() {
        Vehicle.Service service = new Vehicle.Service(1, 2028, 4, 0, true);
        SpdMaintenance.done(SpdMaintenance.task(service, prefs), prefs);
        assertEquals(Integer.valueOf(0), service.remainingKm);
        assertEquals(Integer.valueOf(2028), service.year);
        assertFalse(SpdMaintenance.active(SpdMaintenance.task(service, prefs)));
        assertFalse(SpdMaintenance.active(SpdMaintenance.task(new Vehicle.Service(1, 2028, 4, 0, true), prefs)));
        assertTrue(SpdMaintenance.active(SpdMaintenance.task(new Vehicle.Service(1, 2030, 4, 18000, true), prefs)));
        SpdMaintenance.done(SpdMaintenance.task(service, prefs), prefs);
        assertTrue(SpdMaintenance.active(SpdMaintenance.task(new Vehicle.Service(1, 2028, 4, 18000, true), prefs)));
        assertTrue(SpdMaintenance.active(SpdMaintenance.task(new Vehicle.Service(1, 2028, 4, 0, true), prefs)));
        SpdMaintenance.rearm(1, prefs);
        assertTrue(SpdMaintenance.active(SpdMaintenance.task(service, prefs)));
    }

    public void testDisabledRemindersStayDisabledForNewCarSchedule() {
        prefs.edit().putBoolean(SpdMaintenance.key(3) + "enabled", false).commit();
        assertFalse(SpdMaintenance.active(SpdMaintenance.task(new Vehicle.Service(3, 2028, 4, null, true), prefs)));
        assertFalse(SpdMaintenance.active(SpdMaintenance.task(new Vehicle.Service(3, 2030, 4, null, true), prefs)));
    }

    public void testTimeOnlyCustomReminderIsNotSnoozedForever() throws Exception {
        JSONObject task = new JSONObject().put("km", 0).put("sz", 100).put("sd", 10000L);
        assertTrue(SpdMaintenance.snoozed(task, 0, false, 9000L));
        assertFalse(SpdMaintenance.snoozed(task, 0, false, 10000L));
        task.put("km", 1000);
        assertFalse(SpdMaintenance.snoozed(task, 100, true, 9000L));
    }
}
