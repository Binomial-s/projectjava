package com.example.watchai;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.res.ColorStateList;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings screen fragment — API key, theme toggle, watch MAC, voice shortcuts, clear history.
 */
public class SettingsFragment extends Fragment implements ServiceStateListener {

    private SwitchMaterial themeSwitch;
    private TextView macAddressText;
    private MaterialButton scanButton;
    private MaterialCardView scannerCard;
    private LinearProgressIndicator scannerProgress;
    private TextView scannerStatusText;
    private LinearLayout scannedDevicesList;
    private MaterialButton startScanButton;
    private TextInputEditText apiKeyEdit;
    private MaterialButton saveKeyButton;
    private LinearLayout shortcutsList;
    private TextInputEditText shortcutPhraseEdit;
    private ChipGroup actionTypeChips;
    private TextInputEditText shortcutTargetEdit;
    private LinearLayout messageBodyLayout;
    private TextInputEditText shortcutMessageEdit;
    private MaterialButton addShortcutButton;
    private MaterialButton clearHistoryButton;

    private WatchBleService service;
    private BleScannerManager bleScannerManager;
    private List<VoiceShortcut> currentShortcuts = new ArrayList<>();
    private boolean isDarkTheme = true;

    // Callback to tell the activity to switch themes
    public interface ThemeChangeListener {
        void onThemeChanged(boolean isDark);
    }

    private ThemeChangeListener themeChangeListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof MainActivity) {
            service = ((MainActivity) context).getService();
            themeChangeListener = (ThemeChangeListener) context;
        }
        if (context instanceof ThemeChangeListener) {
            themeChangeListener = (ThemeChangeListener) context;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Find views
        themeSwitch = view.findViewById(R.id.theme_switch);
        macAddressText = view.findViewById(R.id.mac_address_text);
        scanButton = view.findViewById(R.id.scan_button);
        scannerCard = view.findViewById(R.id.scanner_card);
        scannerProgress = view.findViewById(R.id.scanner_progress);
        scannerStatusText = view.findViewById(R.id.scanner_status_text);
        scannedDevicesList = view.findViewById(R.id.scanned_devices_list);
        startScanButton = view.findViewById(R.id.start_scan_button);
        apiKeyEdit = view.findViewById(R.id.api_key_edit);
        saveKeyButton = view.findViewById(R.id.save_key_button);
        shortcutsList = view.findViewById(R.id.shortcuts_list);
        shortcutPhraseEdit = view.findViewById(R.id.shortcut_phrase_edit);
        actionTypeChips = view.findViewById(R.id.action_type_chips);
        shortcutTargetEdit = view.findViewById(R.id.shortcut_target_edit);
        messageBodyLayout = view.findViewById(R.id.message_body_layout);
        shortcutMessageEdit = view.findViewById(R.id.shortcut_message_edit);
        addShortcutButton = view.findViewById(R.id.add_shortcut_button);
        clearHistoryButton = view.findViewById(R.id.clear_history_button);

        // Load saved data
        Context ctx = requireContext();
        isDarkTheme = PrefsHelper.loadDarkTheme(ctx);
        themeSwitch.setOnCheckedChangeListener(null);
        themeSwitch.setChecked(isDarkTheme);
        macAddressText.setText(PrefsHelper.loadWatchMacAddress(ctx));
        apiKeyEdit.setText(PrefsHelper.loadApiKey(ctx));
        currentShortcuts = PrefsHelper.loadShortcuts(ctx);
        renderShortcuts();

        // Initialize BLE scanner
        bleScannerManager = new BleScannerManager(ctx);
        bleScannerManager.addListener(new BleScannerManager.ScanListener() {
            @Override
            public void onDeviceFound(BleDevice device) {
                if (!isAdded()) return;
                addDeviceToScannedList(device);
            }

            @Override
            public void onScanFinished() {
                if (!isAdded()) return;
                scannerProgress.setVisibility(View.GONE);
                startScanButton.setEnabled(true);
                if (scannedDevicesList.getChildCount() == 0) {
                    scannerStatusText.setText(R.string.no_devices);
                }
            }
        });

        // Theme toggle — immediately save preference and ask activity to switch.
        // AppCompatDelegate.setDefaultNightMode will recreate the activity, after
        // which fragments' onViewCreated will restore the switch state from prefs.
        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isDarkTheme = isChecked;
            PrefsHelper.saveDarkTheme(ctx, isChecked);
            if (themeChangeListener != null) {
                themeChangeListener.onThemeChanged(isChecked);
            }
        });

        // Scan button (show/hide scanner)
        scanButton.setOnClickListener(v -> {
            boolean visible = scannerCard.getVisibility() == View.VISIBLE;
            scannerCard.setVisibility(visible ? View.GONE : View.VISIBLE);
            scanButton.setText(visible ? R.string.scan_for_watch : R.string.hide_scanner);
        });

        // Start BLE scan
        startScanButton.setOnClickListener(v -> {
            startScan();
        });

        // Save API key
        saveKeyButton.setOnClickListener(v -> {
            String key = apiKeyEdit.getText().toString().trim();
            PrefsHelper.saveApiKey(ctx, key);
            // Restart service with new key
            if (service != null) {
                requireContext().stopService(new Intent(requireContext(), WatchBleService.class));
                ContextCompat.startForegroundService(requireContext(),
                        new Intent(requireContext(), WatchBleService.class));
            }
            Toast.makeText(ctx, R.string.api_key_saved, Toast.LENGTH_SHORT).show();
        });

        // Message type chip visibility — default to "Call" so message body starts hidden
        actionTypeChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            boolean isMessage = checkedId == R.id.chip_message;
            messageBodyLayout.setVisibility(isMessage ? View.VISIBLE : View.GONE);
            // Update hint
            if (isMessage) {
                shortcutTargetEdit.setHint(R.string.contact_or_number_hint);
            } else {
                shortcutTargetEdit.setHint(R.string.search_query_hint);
            }
        });
        // Ensure call chip is selected by default if nothing is selected yet
        if (actionTypeChips.getCheckedChipId() == View.NO_ID) {
            actionTypeChips.check(R.id.chip_call);
        }

        // Add shortcut
        addShortcutButton.setOnClickListener(v -> {
            addShortcut();
        });

        // Clear history
        clearHistoryButton.setOnClickListener(v -> {
            new AlertDialog.Builder(ctx)
                    .setTitle(R.string.clear_history_title)
                    .setMessage(R.string.clear_history_msg)
                    .setPositiveButton(R.string.clear, (dialog, which) -> {
                        if (service != null) {
                            service.clearConversationHistory();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    @Override
    public void onDestroyView() {
        if (bleScannerManager != null) {
            bleScannerManager.stopScan();
        }
        themeSwitch = null;
        macAddressText = null;
        scanButton = null;
        scannerCard = null;
        scannerProgress = null;
        scannerStatusText = null;
        scannedDevicesList = null;
        startScanButton = null;
        apiKeyEdit = null;
        saveKeyButton = null;
        shortcutsList = null;
        shortcutPhraseEdit = null;
        actionTypeChips = null;
        shortcutTargetEdit = null;
        messageBodyLayout = null;
        shortcutMessageEdit = null;
        addShortcutButton = null;
        clearHistoryButton = null;
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        if (service != null) {
            service.removeListener(this);
        }
        if (bleScannerManager != null) {
            bleScannerManager.stopScan();
        }
        service = null;
        super.onDetach();
    }

    public void setService(WatchBleService svc) {
        this.service = svc;
        if (svc != null) {
            svc.addListener(this);
        }
    }

    /** Resolves a ?attr/wai_* theme attribute to its concrete color. */
    private int resolveAttr(int attr) {
        TypedValue tv = new TypedValue();
        requireContext().getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private void startScan() {
        scannerProgress.setVisibility(View.VISIBLE);
        startScanButton.setEnabled(false);
        scannerStatusText.setText(R.string.scanning);
        scannedDevicesList.removeAllViews();
        bleScannerManager.startScan();

        // Auto-stop after 15 seconds
        startScanButton.postDelayed(() -> {
            if (bleScannerManager != null) {
                bleScannerManager.stopScan();
            }
        }, 15000);
    }

    private void addDeviceToScannedList(BleDevice device) {
        // Check if already added
        for (int i = 0; i < scannedDevicesList.getChildCount(); i++) {
            View child = scannedDevicesList.getChildAt(i);
            TextView addrText = child.findViewById(R.id.device_address);
            if (addrText != null && device.getAddress().equals(addrText.getText().toString())) {
                return;
            }
        }

        scannerStatusText.setText("");

        MaterialCardView card = new MaterialCardView(requireContext());
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        card.setCardBackgroundColor(resolveAttr(R.attr.waiSurface));
        card.setRadius(8f);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad, pad, pad);

        TextView nameText = new TextView(requireContext());
        nameText.setText(device.getName());
        nameText.setTextColor(resolveAttr(R.attr.waiOnSurface));
        nameText.setTextSize(14f);
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView addrText = new TextView(requireContext());
        addrText.setId(R.id.device_address);
        addrText.setText(device.getAddress());
        addrText.setTextColor(resolveAttr(R.attr.waiTextSecondary));
        addrText.setTextSize(12f);

        TextView rssiText = new TextView(requireContext());
        rssiText.setText(device.getRssi() + " dBm");
        rssiText.setTextColor(resolveAttr(R.attr.waiTextSecondary));
        rssiText.setTextSize(12f);

        row.addView(nameText);
        row.addView(addrText);
        row.addView(rssiText);
        card.addView(row);

        card.setOnClickListener(v -> {
            // Select this device
            PrefsHelper.saveWatchMacAddress(requireContext(), device.getAddress());
            macAddressText.setText(device.getAddress());
            scannerCard.setVisibility(View.GONE);
            scanButton.setText(R.string.scan_for_watch);
            bleScannerManager.stopScan();

            // Restart service
            if (service != null) {
                requireContext().stopService(new Intent(requireContext(), WatchBleService.class));
                ContextCompat.startForegroundService(requireContext(),
                        new Intent(requireContext(), WatchBleService.class));
            }
        });

        scannedDevicesList.addView(card);
    }

    private void renderShortcuts() {
        if (shortcutsList == null) return;
        shortcutsList.removeAllViews();

        for (VoiceShortcut shortcut : currentShortcuts) {
            MaterialCardView card = new MaterialCardView(requireContext());
            card.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            card.setCardBackgroundColor(resolveAttr(R.attr.waiSurface));
            card.setRadius(12f);

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            int pad = (int) (12 * getResources().getDisplayMetrics().density);
            row.setPadding(pad, pad, pad, pad);

            LinearLayout info = new LinearLayout(requireContext());
            info.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            info.setLayoutParams(infoLp);

            TextView phraseText = new TextView(requireContext());
            phraseText.setText("\"" + shortcut.getPhrase() + "\"");
            phraseText.setTextColor(resolveAttr(R.attr.waiOnSurface));
            phraseText.setTypeface(null, android.graphics.Typeface.BOLD);
            phraseText.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

            TextView actionText = new TextView(requireContext());
            actionText.setText(shortcut.getActionType() + " → " + shortcut.getTarget());
            actionText.setTextColor(resolveAttr(R.attr.waiTextSecondary));
            actionText.setTextSize(12f);
            actionText.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

            info.addView(phraseText);
            info.addView(actionText);

            MaterialButton removeBtn = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            removeBtn.setText(R.string.remove);
            removeBtn.setTextSize(12f);
            removeBtn.setOnClickListener(v -> {
                currentShortcuts.remove(shortcut);
                PrefsHelper.saveShortcuts(requireContext(), currentShortcuts);
                renderShortcuts();
            });

            row.addView(info);
            row.addView(removeBtn);
            card.addView(row);
            shortcutsList.addView(card);
        }
    }

    private void addShortcut() {
        String phrase = shortcutPhraseEdit.getText().toString().trim();
        String target = shortcutTargetEdit.getText().toString().trim();

        if (phrase.isEmpty() || target.isEmpty()) {
            Toast.makeText(getContext(), R.string.shortcut_fields_required, Toast.LENGTH_SHORT).show();
            return;
        }

        // Get selected action type
        int checkedId = actionTypeChips.getCheckedChipId();
        String actionType = "call";
        if (checkedId == R.id.chip_message) actionType = "message";
        else if (checkedId == R.id.chip_spotify) actionType = "spotify";
        else if (checkedId == R.id.chip_google) actionType = "google";

        String extra = "";
        if (actionType.equals("message") && shortcutMessageEdit != null) {
            extra = shortcutMessageEdit.getText().toString().trim();
        }

        currentShortcuts.add(new VoiceShortcut(phrase, actionType, target, extra));
        PrefsHelper.saveShortcuts(requireContext(), currentShortcuts);
        renderShortcuts();

        // Clear form
        shortcutPhraseEdit.setText("");
        shortcutTargetEdit.setText("");
        if (shortcutMessageEdit != null) shortcutMessageEdit.setText("");
    }

    // ---------- ServiceStateListener (minimal for settings) ----------

    @Override
    public void onStatusChanged(String status) { }

    @Override
    public void onHistoryChanged(List<ConversationTurn> history) { }

    @Override
    public void onWaveformLevelChanged(float level) { }

    @Override
    public void onUptimeChanged(long seconds) { }

    @Override
    public void onStreamingTextChanged(String text) { }
}
