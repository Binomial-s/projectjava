package com.example.watchai;

/**
 * Represents a discovered BLE device.
 */
public class BleDevice {

    private final String name;
    private final String address;
    private final int rssi;

    public BleDevice(String name, String address, int rssi) {
        this.name = name;
        this.address = address;
        this.rssi = rssi;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public int getRssi() {
        return rssi;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BleDevice that = (BleDevice) o;
        return address.equals(that.address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }

    @Override
    public String toString() {
        return "BleDevice{name='" + name + "', address='" + address + "', rssi=" + rssi + "}";
    }
}
