package cz.vitskalicky.lepsirozvrh.notification.progressBar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ProgressBarNotiBroadcastReceiver extends BroadcastReceiver {
    public static final String TAG = ProgressBarNotiBroadcastReceiver.class.getSimpleName();
    public static final int NOTIFICATION_ID = 535;
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: called");
    }
}
