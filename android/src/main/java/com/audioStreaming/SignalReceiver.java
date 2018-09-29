package com.audioStreaming;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

class SignalReceiver extends BroadcastReceiver {
    private Signal signal;

    public SignalReceiver(Signal signal) {
        super();
        this.signal = signal;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(Signal.BROADCAST_PLAYBACK_PLAY)) {
            if (!this.signal.isPlaying) {
                if(this.signal!=null) {
                    this.signal.play();
                }
            } else {
                if(this.signal!=null) {
                    this.signal.stop();
                }
            }
        } else if (action.equals(Signal.BROADCAST_EXIT)) {
            if(this.signal!=null) {
                this.signal.stopForeground(true);
                this.signal.getNotifyManager().cancelAll();
                this.signal.stop();
                this.signal.exitNotification();
            }
            else {
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancelAll();
            }
        }
    }
}
