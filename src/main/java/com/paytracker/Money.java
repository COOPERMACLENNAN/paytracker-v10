package com.paytracker;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Money {
    private static final Pattern INPUT =
            Pattern.compile("^\\$?\\s*([\\d,]+(?:\\.\\d+)?)\\s*([kmbt])?$", Pattern.CASE_INSENSITIVE);

    private Money() {}

    /** Turns a number string + optional k/m/b/t suffix into a value. */
    public static double toValue(String number, String suffix) {
        double v = Double.parseDouble(number.replace(",", ""));
        if (suffix != null && !suffix.isEmpty()) {
            switch (suffix.toLowerCase(Locale.ROOT).charAt(0)) {
                case 'k' -> v *= 1_000d;
                case 'm' -> v *= 1_000_000d;
                case 'b' -> v *= 1_000_000_000d;
                case 't' -> v *= 1_000_000_000_000d;
            }
        }
        return v;
    }

    /** Parses user input such as "25m", "1.5b", "$25,000,000". Returns null if invalid. */
    public static Double parse(String text) {
        Matcher m = INPUT.matcher(text.trim());
        if (!m.matches()) return null;
        try {
            return toValue(m.group(1), m.group(2));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String format(double v) {
        if (v >= 1e12) return trim(v / 1e12) + "T";
        if (v >= 1e9) return trim(v / 1e9) + "B";
        if (v >= 1e6) return trim(v / 1e6) + "M";
        if (v >= 1e3) return trim(v / 1e3) + "K";
        return trim(v);
    }

    private static String trim(double d) {
        String s = String.format(Locale.ROOT, "%.2f", d);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    public static String ago(long millis) {
        long s = Math.max(0, (System.currentTimeMillis() - millis) / 1000);
        if (s < 60) return s + "s ago";
        long m = s / 60;
        if (m < 60) return m + "m " + (s % 60) + "s ago";
        long h = m / 60;
        if (h < 24) return h + "h " + (m % 60) + "m ago";
        long d = h / 24;
        return d + "d " + (h % 24) + "h ago";
    }
}
