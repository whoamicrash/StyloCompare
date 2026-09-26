package com.stylo.core;

public class MetricResult {

    public final String key;
    public final String name;
    public final String val1;
    public final String val2;
    public final double distance;
    public final double weight;
    public final String note;
    public final boolean neutral;

    public MetricResult(String key, String name, String val1, String val2, double distance, double weight, String note) {
        this(key, name, val1, val2, distance, weight, note, false);
    }

    public MetricResult(String key, String name, String val1, String val2, double distance, double weight, String note, boolean neutral) {
        this.key = key;
        this.name = name;
        this.val1 = val1;
        this.val2 = val2;
        this.distance = Math.max(0.0, Math.min(1.0, distance));
        this.weight = weight;
        this.note = note;
        this.neutral = neutral;
    }

    public double contribution() {
        return distance * weight;
    }
}
