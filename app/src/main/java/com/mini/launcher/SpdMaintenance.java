package com.mini.launcher;

import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Adapta los servicios del carro a los recordatorios. Solo las preferencias de aviso se guardan en la app. */
final class SpdMaintenance {
    static final int OIL = 1, INSPECTION = 2, BRAKE_FLUID = 3;
    private static final long DAY = 86400000L;
    private static final double DISTANCE_REFERENCE_KM = 10000d, TIME_REFERENCE_MS = 300d * DAY;

    static String key(int code) { return "spd_maint_" + code + "_"; }
    static boolean isSpd(JSONObject task) { return task.has("spd"); }

    static String icon(int code) {
        return code == OIL ? "oil_barrel" : code == BRAKE_FLUID ? "opacity" : "car_repair";
    }

    static boolean hasSchedule(Vehicle.Service service) {
        return service.remainingKm != null || dueDate(service) > 0;
    }

    static boolean hasOilSchedule(List<Vehicle.Service> services) {
        if (services != null) for (Vehicle.Service service : services)
            if (service != null && service.code == OIL) return hasSchedule(service);
        return false;
    }

    /** Servicios ordenados para mostrar; si falta un plazo SPD para el aceite, se conserva el manual. */
    static List<Vehicle.Service> displayServices(List<Vehicle.Service> services) {
        Map<Integer, Vehicle.Service> byCode = new TreeMap<Integer, Vehicle.Service>();
        if (services != null) for (Vehicle.Service service : services) {
            if (service != null && (service.code != OIL || hasSchedule(service))) byCode.put(service.code, service);
        }
        return new ArrayList<Vehicle.Service>(byCode.values());
    }

    static long dueDate(Vehicle.Service service) {
        if (service.year == null || service.month == null || service.year < 2000 || service.year > 2099
                || service.month < 1 || service.month > 12) return 0;
        Calendar date = Calendar.getInstance();
        date.clear(); date.set(service.year, service.month - 1, 1);
        return date.getTimeInMillis();
    }

    static List<JSONObject> tasks(Vehicle vehicle, SharedPreferences prefs) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        if (vehicle == null || vehicle.services == null) return out;
        Set<Integer> codes = new HashSet<Integer>();
        for (Vehicle.Service service : vehicle.services) {
            if (service == null || !codes.add(service.code)) continue;
            out.add(task(service, prefs));
        }
        return out;
    }

    static JSONObject task(Vehicle.Service service, SharedPreferences prefs) {
        JSONObject task = new JSONObject();
        String key = key(service.code);
        long due = dueDate(service);
        boolean done = prefs.getBoolean(key + "done", false);
        // Una fecha posterior o un aumento claro de distancia indica el siguiente servicio del carro.
        if (done && (due > prefs.getLong(key + "done_date", 0) && due > 0
                || service.remainingKm != null && service.remainingKm > Math.max(0, prefs.getInt(key + "done_left", 0)) + 100)) {
            rearm(service.code, prefs); // no recuperar la conclusión antigua al volver a cero en el nuevo ciclo
            done = false;
        }
        try {
            task.put("spd", service.code).put("n", service.label())
                    .put("ic", icon(service.code))
                    .put("due", due).put("left", service.remainingKm).put("ok", service.ok)
                    .put("enabled", prefs.getBoolean(key + "enabled", true)).put("done", done)
                    .put("sz", prefs.getFloat(key + "sz", 0)).put("sd", prefs.getLong(key + "sd", 0));
        } catch (Exception ignored) { }
        return task;
    }

    static boolean hasSchedule(JSONObject task) { return task.has("left") || task.optLong("due", 0) > 0; }
    static boolean active(JSONObject task) { return task.optBoolean("enabled", true) && !task.optBoolean("done", false) && hasSchedule(task); }

    /** Urgencia comparable con las tareas propias: aviso cercano a 1000 km o 30 días. No es un intervalo del carro. */
    static double fraction(JSONObject task, long now) {
        double fraction = 9;
        if (task.has("left")) fraction = task.optDouble("left", 0) / DISTANCE_REFERENCE_KM;
        if (task.optLong("due", 0) > 0)
            fraction = Math.min(fraction, (task.optLong("due", 0) - now) / TIME_REFERENCE_MS);
        return fraction;
    }

    static boolean distanceFirst(JSONObject task, long now) {
        long due = task.optLong("due", 0);
        return task.has("left") && (due == 0 || task.optDouble("left", 0) / DISTANCE_REFERENCE_KM <= (due - now) / TIME_REFERENCE_MS);
    }

    /** «Aún no»: lo que ocurra primero, 100 km (60 mi) o siete días. Sin distancia, solo se usa la fecha. */
    static boolean snoozed(JSONObject task, float odoKm, boolean hasOdo, long now) {
        boolean distance = hasOdo && (isSpd(task) ? task.has("left") : task.optDouble("km", 0) > 0)
                && task.optDouble("sz", 0) > 0;
        boolean time = task.optLong("sd", 0) > 0;
        return (distance || time) && (!distance || odoKm < task.optDouble("sz", 0))
                && (!time || now < task.optLong("sd", 0));
    }

    static void snooze(JSONObject task, SharedPreferences prefs, float odoKm, boolean hasOdo, boolean mph, long now) {
        String key = key(task.optInt("spd"));
        prefs.edit().putFloat(key + "sz", hasOdo && task.has("left") ? odoKm + (mph ? 60 / 0.621371f : 100) : 0)
                .putLong(key + "sd", now + 7 * DAY).apply();
    }

    static void done(JSONObject task, SharedPreferences prefs) {
        String key = key(task.optInt("spd"));
        prefs.edit().putBoolean(key + "done", true).putLong(key + "done_date", task.optLong("due", 0))
                .putInt(key + "done_left", task.optInt("left", 0)).remove(key + "sz").remove(key + "sd").apply();
    }

    static void rearm(int code, SharedPreferences prefs) {
        String key = key(code);
        prefs.edit().remove(key + "done").remove(key + "done_date").remove(key + "done_left")
                .remove(key + "sz").remove(key + "sd").apply();
    }
}
