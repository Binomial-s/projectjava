package com.example.watchai;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.util.TypedValue;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;
import java.util.Locale;

import static com.example.watchai.R.attr.waiNegative;
import static com.example.watchai.R.attr.waiNegativeContainer;
import static com.example.watchai.R.attr.waiPositive;
import static com.example.watchai.R.attr.waiPositiveContainer;
import static com.example.watchai.R.attr.waiTextSecondary;

/**
 * Dashboard screen fragment — shows BLE connection status, uptime, battery.
 */
public class DashboardFragment extends Fragment implements ServiceStateListener {

    private FrameLayout bleStatusChip;
    private ImageView bleStatusIcon;
    private TextView bleStatusText;
    private TextView bleStatusDesc;
    private TextView uptimeText;
    private TextView batteryText;
    private TextView rawStatusText;

    private WatchBleService service;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bleStatusChip = view.findViewById(R.id.ble_status_chip);
        bleStatusIcon = view.findViewById(R.id.ble_status_icon);
        bleStatusText = view.findViewById(R.id.ble_status_text);
        bleStatusDesc = view.findViewById(R.id.ble_status_desc);
        uptimeText = view.findViewById(R.id.uptime_text);
        batteryText = view.findViewById(R.id.battery_text);
        rawStatusText = view.findViewById(R.id.raw_status_text);
    }

    @Override
    public void onDestroyView() {
        // Views must not outlive their hierarchy; the fragment object itself
        // is retained across configuration changes.
        bleStatusChip = null;
        bleStatusIcon = null;
        bleStatusText = null;
        bleStatusDesc = null;
        uptimeText = null;
        batteryText = null;
        rawStatusText = null;
        super.onDestroyView();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof MainActivity) {
            // May still be null: binding completes asynchronously. setService
            // covers the other ordering, and the listener Set makes a
            // double registration harmless rather than a duplicate entry.
            service = ((MainActivity) context).getService();
            registerListener();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshFromService();
    }

    @Override
    public void onDetach() {
        unregisterListener();
        service = null;
        super.onDetach();
    }

    public void setService(WatchBleService svc) {
        this.service = svc;
        registerListener();
        refreshFromService();
    }

    private void registerListener() {
        if (service != null) {
            service.addListener(this);
        }
    }

    private void unregisterListener() {
        if (service != null) {
            service.removeListener(this);
        }
    }

    /** Pulls current state, so a re-entered tab is not showing stale values. */
    private void refreshFromService() {
        if (service == null) return;
        onStatusChanged(service.getStatus());
        onUptimeChanged(service.getUptimeSeconds());
        updateBattery();
    }

    private void updateBattery() {
        if (batteryText == null || service == null || !isAdded()) return;
        int battery = service.getPhoneBatteryPercent();
        batteryText.setText(battery >= 0 ? battery + "%" : "Unknown");
    }

    // ---------- ServiceStateListener ----------

    @Override
    public void onStatusChanged(String status) {
        if (!isAdded()) return;

        boolean connected = status != null && status.startsWith("Watch connected");

        if (bleStatusText != null) {
            bleStatusText.setText(connected ? R.string.connected : R.string.disconnected);
            bleStatusText.setTextColor(connected
                    ? resolveAttr(waiPositive)
                    : resolveAttr(waiTextSecondary));
        }

        // The chip carries the state as a color AND a spoken description,
        // since color alone is invisible to a screen reader.
        if (bleStatusChip != null) {
            int containerAttr = connected
                    ? waiPositiveContainer
                    : waiNegativeContainer;
            bleStatusChip.setBackgroundTintList(ColorStateList.valueOf(resolveAttr(containerAttr)));
            bleStatusChip.setContentDescription(getString(connected
                    ? R.string.ble_connected_desc
                    : R.string.ble_disconnected_desc));
        }
        if (bleStatusIcon != null) {
            bleStatusIcon.setImageTintList(ColorStateList.valueOf(resolveAttr(connected
                    ? waiPositive
                    : waiNegative)));
        }
        if (bleStatusDesc != null) {
            String mac = PrefsHelper.loadWatchMacAddress(requireContext());
            if (mac == null || mac.isEmpty() || mac.equals("E1:6F:15:37:99:08")) {
                bleStatusDesc.setText(R.string.no_watch_paired);
            } else {
                bleStatusDesc.setText(mac);
            }
        }

        if (rawStatusText != null) {
            rawStatusText.setText(status != null ? status : getString(R.string.status_starting));
        }
    }

    /** Resolves a ?attr/wai_* theme attribute to its concrete color. */
    private int resolveAttr(int attr) {
        TypedValue tv = new TypedValue();
        requireContext().getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    @Override
    public void onHistoryChanged(List<ConversationTurn> history) {
        // Not used in dashboard
    }

    @Override
    public void onWaveformLevelChanged(float level) {
        // Not used in dashboard
    }

    @Override
    public void onUptimeChanged(long seconds) {
        if (uptimeText == null || !isAdded()) return;
        long minutes = seconds / 60;
        long secs = seconds % 60;
        uptimeText.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, secs));
    }

    @Override
    public void onStreamingTextChanged(String text) {
        // Not used in dashboard
    }
}
