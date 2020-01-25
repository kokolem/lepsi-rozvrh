package cz.vitskalicky.lepsirozvrh.notification.detailed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import cz.vitskalicky.lepsirozvrh.AppSingleton;
import cz.vitskalicky.lepsirozvrh.BuildConfig;
import cz.vitskalicky.lepsirozvrh.MainApplication;
import cz.vitskalicky.lepsirozvrh.bakaAPI.rozvrh.RozvrhAPI;

/**
 * Stands for <b>Noti</b>fication <b>broadcast reciever</b>
 */
public class DetailedNotiBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = DetailedNotiBroadcastReceiver.class.getSimpleName();
    public static final int REQUEST_CODE = 64857;
    /**
     * +1 for next, -1 for prev
     */
    public static final String EXTRA_NEXT_PREV = BuildConfig.APPLICATION_ID + ".extra-next-or-prev-lesson";
    public static final String ACTION_NEXT_PREV = BuildConfig.APPLICATION_ID + ".action-next-or-prev-lesson";

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

        if (intent != null && intent.getAction() != null && intent.getAction().equals(ACTION_NEXT_PREV) && intent.hasExtra(EXTRA_NEXT_PREV)){
            int offset = intent.getIntExtra(EXTRA_NEXT_PREV, 0);
            application.getNotificationState().setOffset(application.getNotificationState().getOffset() + offset);
            application.scheduleDetailedNotificationUpdate(application.getNotificationState().getOffsetResetTime());
        }

        DetailedPermanentNotification.update(rozvrhAPI, application, () -> pendingResult.finish());
    }
}
