package com.mini.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Clima actual, previsión (Open-Meteo, sin clave) y nombre de la ciudad. */
public class Weather {
    private static final int FORECAST_HOURS = 12, FORECAST_DAYS = 5;

    public static class Now {
        public double temp;
        public int code;
        public boolean day = true;
        public String city;
        public String unit = "C";
        public double feels = Double.NaN, wind = Double.NaN;
        public int humidity = -1;
        public String windUnit = "km/h", date = "";
        public final List<Hour> hours = new ArrayList<Hour>();
        public final List<Day> days = new ArrayList<Day>();
    }

    public static class Hour {
        public int hour, code;
        public double temp;
        public int rain = -1;
        public boolean day = true, now;
    }

    public static class Day {
        public String date;
        public double hi, lo;
        public int code, rain = -1;
    }

    public interface Listener { void onWeather(Now n, String error); }

    static String lastError = "—";

    /** Se ejecuta en un hilo aparte; el resultado llega por el listener (en ese mismo hilo). */
    public static void fetch(final Context c, final double lat, final double lon, final Listener l) {
        new Thread(new Runnable() {
            public void run() {
                SharedPreferences p = c.getSharedPreferences("launcher", 0);
                boolean f = "F".equals(p.getString("temp_unit", "C"));
                Now n = null;
                for (String scheme : new String[]{"https://", "http://"}) {
                    // sin cifrar (solo si falla https, en Android 4.x) se envía la posición redondeada a ~10 km
                    boolean plain = scheme.startsWith("http:");
                    String q = String.format(Locale.US,
                            "api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&current=temperature_2m,weather_code,is_day,apparent_temperature,relative_humidity_2m,wind_speed_10m"
                            + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day"
                            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=" + FORECAST_DAYS + "&timezone=auto%s",
                            coord(lat, plain), coord(lon, plain), f ? "&temperature_unit=fahrenheit&wind_speed_unit=mph" : "");
                    try {
                        JSONObject root = new JSONObject(new String(Net.get(scheme + q, 10000), "UTF-8"));
                        n = parse(root, f);
                        break;
                    } catch (Throwable e) {
                        lastError = e.toString();
                    }
                }
                if (n == null) { l.onWeather(null, lastError); return; }
                n.city = city(c, lat, lon);
                l.onWeather(n, null);
            }
        }).start();
    }

    /** Solo entrega un resultado cuando el clima actual se ha leído por completo. */
    static Now parse(JSONObject root, boolean fahrenheit) throws JSONException {
        JSONObject current = root.getJSONObject("current");
        Now n = new Now();
        n.temp = current.getDouble("temperature_2m");
        n.code = current.optInt("weather_code", 0);
        n.day = current.optInt("is_day", 1) == 1;
        n.unit = fahrenheit ? "F" : "C";
        n.windUnit = fahrenheit ? "mph" : "km/h";
        parseDetail(root, current, n);
        return n;
    }

    /** Datos opcionales: una previsión incompleta no impide mostrar el tiempo actual ni las otras secciones. */
    static void parseDetail(JSONObject root, JSONObject cur, Now n) {
        n.feels = cur.optDouble("apparent_temperature", Double.NaN);
        n.humidity = percent(cur.optInt("relative_humidity_2m", -1));
        n.wind = cur.optDouble("wind_speed_10m", Double.NaN);
        if (n.wind < 0) n.wind = Double.NaN;
        String now = cur.optString("time", "");
        String hourKey = now.length() >= 13 ? now.substring(0, 13) : "";
        n.date = now.length() >= 10 ? now.substring(0, 10) : "";
        parseHours(root.optJSONObject("hourly"), hourKey, n.hours);
        parseDays(root.optJSONObject("daily"), n.days);
    }

    private static void parseHours(JSONObject hourly, String hourKey, List<Hour> hours) {
        hours.clear();
        if (hourly == null) return;
        JSONArray times = hourly.optJSONArray("time"), temps = hourly.optJSONArray("temperature_2m");
        if (times == null || temps == null) return;
        JSONArray codes = hourly.optJSONArray("weather_code"), rain = hourly.optJSONArray("precipitation_probability"), day = hourly.optJSONArray("is_day");
        for (int i = 0; i < times.length() && hours.size() < FORECAST_HOURS; i++) {
            String ts = times.optString(i, "");
            double temp = temps.optDouble(i, Double.NaN);
            if (ts.length() < 13 || ts.compareTo(hourKey) < 0 || Double.isNaN(temp)) continue;
            int hour;
            try { hour = Integer.parseInt(ts.substring(11, 13)); } catch (NumberFormatException e) { continue; }
            if (hour < 0 || hour > 23) continue;
            Hour h = new Hour();
            h.hour = hour;
            h.temp = temp;
            h.code = codes != null ? codes.optInt(i, 3) : 3;
            h.rain = probability(rain, i);
            h.day = day == null || day.optInt(i, 1) == 1;
            h.now = hourKey.length() > 0 && ts.startsWith(hourKey);
            hours.add(h);
        }
    }

    private static void parseDays(JSONObject daily, List<Day> days) {
        days.clear();
        if (daily == null) return;
        JSONArray times = daily.optJSONArray("time"), hi = daily.optJSONArray("temperature_2m_max"), lo = daily.optJSONArray("temperature_2m_min");
        if (times == null || hi == null || lo == null) return;
        JSONArray codes = daily.optJSONArray("weather_code"), rain = daily.optJSONArray("precipitation_probability_max");
        for (int i = 0; i < times.length() && days.size() < FORECAST_DAYS; i++) {
            String date = times.optString(i, "");
            double max = hi.optDouble(i, Double.NaN), min = lo.optDouble(i, Double.NaN);
            if (date.length() != 10 || Double.isNaN(max) || Double.isNaN(min)) continue;
            Day d = new Day();
            d.date = date;
            d.hi = max;
            d.lo = min;
            d.code = codes != null ? codes.optInt(i, 3) : 3;
            d.rain = probability(rain, i);
            days.add(d);
        }
    }

    private static int probability(JSONArray values, int i) {
        return percent(values != null ? values.optInt(i, -1) : -1);
    }

    private static int percent(int value) { return value >= 0 && value <= 100 ? value : -1; }

    /** Coordenada para la URL: 4 decimales por https; 1 decimal (~10 km) si la conexión va sin cifrar. */
    static String coord(double v, boolean plain) {
        return String.format(Locale.US, plain ? "%.1f" : "%.4f", v);
    }

    /** Ciudad: la escrita por el usuario, la guardada para esa zona, Geocoder o BigDataCloud. */
    static String city(Context c, double lat, double lon) {
        SharedPreferences p = c.getSharedPreferences("launcher", 0);
        String manual = p.getString("city_manual", "").trim();
        if (manual.length() > 0) return manual;
        String key = String.format(Locale.US, "%.2f,%.2f", lat, lon);
        if (key.equals(p.getString("city_key", ""))) return p.getString("city_name", null);
        String name = null;
        try {
            if (Geocoder.isPresent()) {
                List<Address> a = new Geocoder(c, new Locale(I18n.lang)).getFromLocation(lat, lon, 1);
                if (a != null && !a.isEmpty()) {
                    name = a.get(0).getLocality();
                    if (name == null) name = a.get(0).getSubAdminArea();
                }
            }
        } catch (Throwable ignored) { }
        if (name == null) {
            for (String scheme : new String[]{"https://", "http://"}) {
                boolean plain = scheme.startsWith("http:");
                String q = String.format(Locale.US,
                        "api.bigdatacloud.net/data/reverse-geocode-client?latitude=%s&longitude=%s&localityLanguage=%s",
                        coord(lat, plain), coord(lon, plain), I18n.lang);
                try {
                    JSONObject o = new JSONObject(new String(Net.get(scheme + q, 8000), "UTF-8"));
                    name = o.optString("city", "");
                    if (name.length() == 0) name = o.optString("locality", "");
                    if (name.length() == 0) name = null;
                    if (name != null) break;
                } catch (Throwable ignored) { }
            }
        }
        if (name != null) p.edit().putString("city_key", key).putString("city_name", name).apply();
        return name;
    }

    /** Código WMO → ícono. */
    public static String icon(int code, boolean day) {
        if (code == 0) return day ? "wb_sunny" : "nights_stay";
        if (code <= 2) return day ? "filter_drama" : "nights_stay";
        if (code == 3) return "cloud";
        if (code == 45 || code == 48) return "dehaze";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "umbrella";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "ac_unit";
        if (code >= 95) return "thunderstorm";
        return "cloud";
    }

    public static String describe(int code) {
        if (code == 0) return "Despejado";
        if (code <= 2) return "Parcialmente nublado";
        if (code == 3) return "Nublado";
        if (code == 45 || code == 48) return "Niebla";
        if (code >= 51 && code <= 57) return "Llovizna";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "Lluvia";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Nieve";
        if (code >= 95) return "Tormenta";
        return "";
    }
}
