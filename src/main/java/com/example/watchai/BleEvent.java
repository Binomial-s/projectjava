package com.example.watchai;

import org.json.JSONObject;

/**
 * Represents events emitted by the BLE watch connection.
 */
public abstract class BleEvent {

    public static class ButtonPressed extends BleEvent {
        private final String buttonType;

        public ButtonPressed(String buttonType) {
            this.buttonType = buttonType;
        }

        public ButtonPressed() {
            this("play_music");
        }

        public String getButtonType() {
            return buttonType;
        }
    }

    public static class StatusChanged extends BleEvent {
        private final String status;

        public StatusChanged(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }
    }

    public static class Error extends BleEvent {
        private final String message;

        public Error(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
