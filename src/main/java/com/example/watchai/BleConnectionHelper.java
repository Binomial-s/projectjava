package com.example.watchai;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.Set;

/**
 * Manages BLE GATT connection to the Fire-Boltt watch.
 * Listens for characteristic notifications to detect button presses.
 */
public class BleConnectionHelper {

    // Fire-Boltt BLE constants
    private static final UUID SERVICE_FEEA_UUID =
            UUID.fromString("0000feea-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_FEE3_UUID =
            UUID.fromString("0000fee3-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final byte PLAY_MUSIC_COMMAND_BYTE = 0x06;

    private static final long MAX_RECONNECT_DELAY_MS = 30_000L;

    public interface BleEventListener {
        void onEvent(BleEvent event);
    }

    private final Context context;
    private final Set<BleEventListener> listeners = new CopyOnWriteArraySet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BluetoothGatt gatt;
    private String lastMacAddress;
    private int reconnectAttempts = 0;
    private boolean connected = false;
    private Runnable reconnectRunnable;

    public BleConnectionHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    public void addListener(BleEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(BleEventListener listener) {
        listeners.remove(listener);
    }

    private void emitEvent(BleEvent event) {
        for (BleEventListener listener : listeners) {
            listener.onEvent(event);
        }
    }

    @SuppressLint("MissingPermission")
    public void connect(String macAddress) {
        this.lastMacAddress = macAddress;
        disconnect();

        // Runtime permission check: BLUETOOTH_CONNECT required for connectGatt
        if (ContextCompat.checkSelfPermission(context, Manifest






            .permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            emitEvent(new BleEvent.StatusChanged("Missing Bluetooth permission"));
            return;
        }

        // Verify the MAC address format before attempting connection
        if (macAddress == null || macAddress.isEmpty() || !macAddress.matches("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}")) {
            emitEvent(new BleEvent.StatusChanged("Invalid MAC address format: " + macAddress));
            return;
        }

        BluetoothManager bluetoothManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            emitEvent(new BleEvent.StatusChanged("Bluetooth not available"));
            return;
        }

        BluetoothAdapter adapter = bluetoothManager.getAdapter();
        if (adapter == null) {
            emitEvent(new BleEvent.StatusChanged("Bluetooth adapter not found"));
            return;
        }

        BluetoothDevice device = adapter.getRemoteDevice(macAddress);
        if (device == null) {
            emitEvent(new BleEvent.StatusChanged("Watch not found (check it's paired)"));
            return;
        }

        emitEvent(new BleEvent.StatusChanged("Connecting to watch..."));
        reconnectAttempts = 0;

        gatt = device.connectGatt(context, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected = true;
                reconnectAttempts = 0;
                emitEvent(new BleEvent.StatusChanged("Watch connected, discovering..."));
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false;
                emitEvent(new BleEvent.StatusChanged("Watch disconnected — retrying..."));
                g.close();
                scheduleReconnect();
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                emitEvent(new BleEvent.StatusChanged("Service discovery failed: " + status));
                return;
            }
            android.bluetooth.BluetoothGattService service = g.getService(SERVICE_FEEA_UUID);
            if (service == null) {
                emitEvent(new BleEvent.StatusChanged("Couldn't find the button service"));
                return;
            }

            BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHAR_FEE3_UUID);
            if (characteristic == null) {
                emitEvent(new BleEvent.StatusChanged("Couldn't find the button characteristic"));
                return;
            }

            g.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                g.writeDescriptor(descriptor);
            }
            emitEvent(new BleEvent.StatusChanged("Watch connected — Play Music triggers assistant"));
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic) {
            if (characteristic == null) return;
            byte[] bytes = characteristic.getValue();
            if (bytes == null || bytes.length < 4) return;

            if (bytes[0] == (byte) 0xFE &&
                    bytes[1] == (byte) 0xEA &&
                    bytes[2] == (byte) 0x20 &&
                    bytes[3] == PLAY_MUSIC_COMMAND_BYTE) {
                emitEvent(new BleEvent.ButtonPressed("play_music"));
            }
        }
    };

    @SuppressLint("MissingPermission")
    private void scheduleReconnect() {
        reconnectAttempts++;
        long delay = Math.min(
                2000L * (1L << (reconnectAttempts - 1)),
                MAX_RECONNECT_DELAY_MS
        );

        reconnectRunnable = new Runnable() {
            @Override
            public void run() {
                if (lastMacAddress != null) {
                    emitEvent(new BleEvent.StatusChanged("Reconnecting..."));
                    connect(lastMacAddress);
                }
            }
        };

        mainHandler.postDelayed(reconnectRunnable, delay);
    }

    /**
     * Reconnect with a specific MAC address.
     */
    @SuppressLint("MissingPermission")
    public void reconnect(String macAddress) {
        disconnect();
        connect(macAddress);
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        // Remove only our reconnect callbacks — do NOT nuke every main-thread
        // callback (that would kill fragment animations, uptime counters, etc.)
        if (reconnectRunnable != null) {
            mainHandler.removeCallbacks(reconnectRunnable);
            reconnectRunnable = null;
        }

        if (gatt != null) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (Exception ignored) {
            }
            gatt = null;
        }
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }
}
