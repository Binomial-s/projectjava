package com.example.watchai;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationMenuView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main activity — fragment navigation, service binding, permissions, and
 * the day/night toggle.
 */
public class MainActivity extends AppCompatActivity implements SettingsFragment.ThemeChangeListener {

    private WatchBleService boundService;
    private boolean isBound = false;

    private ChatFragment chatFragment;
    private DashboardFragment dashboardFragment;
    private SettingsFragment settingsFragment;

    /**
     * Permissions are requested one at a time. Requesting four in a row
     * from onCreate meant Android showed the first dialog and silently
     * dropped the rest, with no callbacks to find out — so the user was
     * told "Missing Bluetooth permission" with no way to grant it.
     */
    private final List<String> pendingPermissions = new ArrayList<>();
    private final Map<String, Integer> permissionRequestCodes = new HashMap<>();
    private ActivityResultLauncher<String> currentPermissionLauncher;
    private int permissionRequestIndex = 0;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            WatchBleService.LocalBinder localBinder = (WatchBleService.LocalBinder) binder;
            boundService = localBinder.getService();
            isBound = true;

            if (chatFragment != null) chatFragment.setService(boundService);
            if (dashboardFragment != null) dashboardFragment.setService(boundService);
            if (settingsFragment != null) settingsFragment.setService(boundService);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            boundService = null;
            isBound = false;
        }
    };

    public WatchBleService getService() {
        return boundService;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Applied before setContentView, and before the service starts, so
        // the first frame is already in the right mode.
        applyTheme(PrefsHelper.loadDarkTheme(this));

        setContentView(R.layout.activity_main);

        chatFragment = new ChatFragment();
        dashboardFragment = new DashboardFragment();
        settingsFragment = new SettingsFragment();

        if (savedInstanceState == null) {
            showChat();
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_chat) {
                showChat();
                return true;
            } else if (id == R.id.nav_dashboard) {
                showDashboard();
                return true;
            } else if (id == R.id.nav_settings) {
                showSettings();
                return true;
            }
            return false;
        });

        // The single listening trigger. Previously chat had its own talk
        // button; two controls for one action is one more thing to keep in
        // sync, and this way listening is reachable from every tab.
        MaterialButton micButton = findViewById(R.id.nav_mic_button);
        micButton.setOnClickListener(v -> {
            if (boundService != null) {
                boundService.manualTrigger();
            } else {
                Toast.makeText(this, R.string.service_not_ready, Toast.LENGTH_SHORT).show();
            }
        });

        Intent serviceIntent = new Intent(this, WatchBleService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        setUpPermissionQueue();
        requestPermissionsSequentially();
    }

    private void showChat() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, chatFragment)
                .commit();
    }

    private void showDashboard() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, dashboardFragment)
                .commit();
    }

    private void showSettings() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, settingsFragment)
                .commit();
    }

    // ---------- Permissions ----------

    private static final int REQUEST_PERMISSION_REQUEST_CODE = 1001;

    private void setUpPermissionQueue() {
        currentPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    // Re‑queue the next permission only after the current one finishes
                    requestNextPermission();
                });
    }

    private void requestPermissionsSequentially() {
        pendingPermissions.clear();

        // Mic is needed for voice input; add it first.
        addIfMissing(Manifest.permission.RECORD_AUDIO);

        // Bluetooth permissions are needed for BLE connectivity.
        addIfMissing(Manifest.permission.BLUETOOTH_CONNECT);
        addIfMissing(Manifest.permission.BLUETOOTH_SCAN);

        // Contacts are needed for shortcut dialing/message.
        addIfMissing(Manifest.permission.READ_CONTACTS);

        // Post‑notifications is only available on Android 13+ (API 33).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(Manifest.permission.POST_NOTIFICATIONS);
        }

        requestNextPermission();
    }

    private void addIfMissing(String permission) {
        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
            pendingPermissions.add(permission);
        }
    }

    private void requestNextPermission() {
        if (permissionRequestIndex >= pendingPermissions.size()) {
            // All requested permissions have been processed – show a summary if anything is still missing.
            boolean allGranted = pendingPermissions.isEmpty();
            if (!allGranted) {
                String missing = pendingPermissions.isEmpty() ? "" : pendingPermissions.get(0);
                Toast.makeText(this, "Some permissions are missing: " + missing, Toast.LENGTH_LONG).show();
            }
            return;
        }
        String perm = pendingPermissions.get(permissionRequestIndex);
        permissionRequestIndex++;
        currentPermissionLauncher.launch(perm);
    }

    // ---------- Theme ----------

    /**
     * Switches day/night via AppCompatDelegate rather than swapping between
     * two app themes. The palette lives in values/ and values-night/, so the
     * system night mode is what selects the colors — setDefaultNightMode
     * also recreates the activity for us.
     */
    private void applyTheme(boolean isDark) {
        AppCompatDelegate.setDefaultNightMode(isDark
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    @Override
    public void onThemeChanged(boolean isDark) {
        applyTheme(isDark);
    }

    @Override
    protected void onDestroy() {
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
        super.onDestroy();
    }
}