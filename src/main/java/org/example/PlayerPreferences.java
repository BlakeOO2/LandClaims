package org.example;

public class PlayerPreferences {
    private boolean notificationsEnabled = true;

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public void toggleNotifications() {
        this.notificationsEnabled = !this.notificationsEnabled;
    }
}
