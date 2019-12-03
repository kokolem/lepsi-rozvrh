package cz.vitskalicky.lepsirozvrh.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import cz.vitskalicky.lepsirozvrh.AppSingleton;
import cz.vitskalicky.lepsirozvrh.MainApplication;
import cz.vitskalicky.lepsirozvrh.bakaAPI.rozvrh.RozvrhAPI;

/**
 * Stands for <b>Noti</b>fication <b>broadcast reciever</b>
 */
public class NotiBroadcastReciever extends BroadcastReceiver {
    private static final String TAG = NotiBroadcastReciever.class.getSimpleName();
    public static final int REQUEST_CODE = 64857;

    Context context;
    RozvrhAPI rozvrhAPI = null;
    MainApplication application;
    PendingResult pendingResult;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Broadcast recieved");
        this.context = context;
        rozvrhAPI = AppSingleton.getInstance(context).getRozvrhAPI();
        application = (MainApplication) context.getApplicationContext();
        pendingResult = goAsync();

        PermanentNotification.update(application, rozvrhAPI, () -> {
            pendingResult.finish();
        });
    }
}
