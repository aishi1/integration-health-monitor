package com.brightline.monitor.report;

public record StatusBucket(String label, int red, int amber, int green, int noData) {
    public int total() { return red + amber + green + noData; }
}
