package com.example.watchai;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Scans for nearby BLE devices, filtering for Fire-Boltt watches.
 */
public class BleScannerManager {

    public interface ScanListener {
        void onDeviceFound(BleDevice device);
        void onScanFinished();
    }

    private final Context context;
    private final Set<ScanListener> listeners = new CopyOnWriteArraySet<>();
    private ScanCallback scanCallback;
    private BluetoothLeScanner scanner;
    private boolean scanning = false;

    public BleScannerManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void addListener(ScanListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ScanListener listener) {
        listeners.remove(listener);
    }

    @SuppressLint("MissingPermission")
    public void startScan() {
        if (scanning) return;

        // Runtime permission check: BLUETOOTH_SCAN + ACCESS_FINE_LOCATION (required <31)
        boolean hasScanPermission = ContextCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        boolean hasLocationPermission = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!hasScanPermission || (!hasLocationPermission && Build.VERSION.SDK_INT < Build.VERSION_CODES.S)) {
            for (ScanListener listener : listeners) {
                listener.onScanFinished();
            }
            return;
        }

        BluetoothManager bluetoothManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) return;

        BluetoothAdapter adapter = bluetoothManager.getAdapter();
        if (adapter == null) return;

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) return;

        scanning = true;

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                android.bluetooth.BluetoothDevice device = result.getDevice();
                String name = device.getName();
                if (name == null) name = "Unknown Device";
                String address = device.getAddress();
                int rssi = result.getRssi();

                // Filter for Fire-Boltt devices or devices with BLE-related names
                if (name.toLowerCase().contains("fire-boltt") ||
                        name.toLowerCase().contains("fb") ||
                        name.toLowerCase().contains("watch") ||
                        name.toLowerCase().contains("ble")) {
                    BleDevice bleDevice = new BleDevice(name, address, rssi);
                    for (ScanListener listener : listeners) {
                        listener.onDeviceFound(bleDevice);
                    }
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                scanning = false;
                for (ScanListener listener : listeners) {
                    listener.onScanFinished();
                }
            }
        };

        scanner.startScan(scanCallback);
    }

    @SuppressLint("MissingPermission")
    public void stopScan() {
        if (scanner != null && scanCallback != null) {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception ignored) {
            }
        }
        scanning = false;
        scanCallback = null;
        for (ScanListener listener : listeners) {
            listener.onScanFinished();
        }
    }

    public boolean isScanning() {
        return scanning;
    }
}
