package cz.vitskalicky.lepsirozvrh;

import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import org.joda.time.LocalDateTime;

import java.util.Random;

import cz.vitskalicky.lepsirozvrh.bakaAPI.rozvrh.RozvrhAPI;
import cz.vitskalicky.lepsirozvrh.bakaAPI.rozvrh.RozvrhWrapper;
import cz.vitskalicky.lepsirozvrh.items.Rozvrh;
import cz.vitskalicky.lepsirozvrh.notification.detailed.NotiBroadcastReceiver;
import cz.vitskalicky.lepsirozvrh.notification.detailed.NotificationState;
import cz.vitskalicky.lepsirozvrh.notification.detailed.PermanentNotification;
import io.sentry.Sentry;
import io.sentry.android.AndroidSentryClientFactory;
import io.sentry.event.User;


public class MainApplication extends Application {
    private static final String TAG = MainApplication.class.getSimpleName();

    private NotificationState notificationState = null;
    private LiveData<RozvrhWrapper> notificationLiveData = null;

    // R.string.LEGACY_NOTIFICATION stores if legacy notification (now called detailed notification) was turned on
    // R.string.PERMANENT_NOTIFICATION stores which version of the notification is selected or if any is selected at all
    // 0 = notification turned off (default)
    // 1 = detailed (legacy) notification
    // 2 = progress bar (new) notification
    private Observer<RozvrhWrapper> notificationObserver = rozvrhWrapper -> {
        if (!SharedPrefs.getBooleanPreference(this, R.string.PREFS_LEGACY_NOTIFICATION, false) ||
                !SharedPrefs.getStringPreference(this, R.string.PREFS_PERMANENT_NOTIFICATION, "0").equals("1")){
            return;
        }
        PermanentNotification.update(rozvrhWrapper.getRozvrh(), this);
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize the Sentry (crash report) client
        if (SharedPrefs.getBooleanPreference(this, R.string.PREFS_SEND_CRASH_REPORTS)){
            enableSentry();
        }else{
            diableSentry();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Register notification channel for the permanent notification
            CharSequence name = getString(R.string.notification_channel_name);
            String description = getString(R.string.notification_detials);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(PermanentNotification.PERMANENT_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setSound(Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.raw.silence),new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
            channel.setShowBadge(false);
            channel.setVibrationPattern(null);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        notificationState = new NotificationState(this);

        // R.string.LEGACY_NOTIFICATION stores if legacy notification (now called detailed notification) was turned on
        // R.string.PERMANENT_NOTIFICATION stores which version of the notification is selected or if any is selected at all
        // 0 = notification turned off (default)
        // 1 = detailed (legacy) notification
        // 2 = progress bar (new) notification
        if (SharedPrefs.getBooleanPreference(this, R.string.PREFS_LEGACY_NOTIFICATION, false) ||
                SharedPrefs.getStringPreference(this, R.string.PREFS_PERMANENT_NOTIFICATION, "0").equals("1")){
            enableDetailedNotification();
        }else {
            disableDetailedNotification();
        }
    }

    public LocalDateTime getScheduledNotificationTime() {
        return notificationState.scheduledNotificationTime;
    }

    public NotificationState getNotificationState() {
        return notificationState;
    }

    /**
     * Schedules AlarmManager for notification update
     * @param triggerTime
     */
    public void scheduleDetailedNotificationUpdate(LocalDateTime triggerTime){
        if (triggerTime == null){
            triggerTime = LocalDateTime.now().plusDays(1);
        }
        if (notificationState.getOffsetResetTime() != null && triggerTime.isAfter(notificationState.getOffsetResetTime())){
            triggerTime = notificationState.getOffsetResetTime();
        }
        PendingIntent pendingIntent = getDetailedNotiPendingIntent(this);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerTime.toDate().getTime(),60 * 60000,  pendingIntent);

        Log.d(TAG, "Scheduled a notificatio upadate on " + triggerTime.toString("MM-dd HH:mm:ss"));
        notificationState.scheduledNotificationTime = triggerTime;
    }

    private static PendingIntent getDetailedNotiPendingIntent(Context context){
        Intent intent = new Intent(context, NotiBroadcastReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context.getApplicationContext(), NotiBroadcastReceiver.REQUEST_CODE, intent, 0);
        return pendingIntent;
    }

    /**
     * Schedules notification update accordingly to the give Rozvrh using
     * {@link Rozvrh#getNextCurrentLessonChangeTime()}. If there is already one scheduled, it is
     * overwritten.
     * @return <code>true</code> if successful or <code>false</code> if not
     * ({@link Rozvrh#getNextCurrentLessonChangeTime()} returned error, because this is an
     * old/permanent schedule).
     */
    public boolean scheduleDetailedNotificationUpdate(Rozvrh rozvrh){
        Rozvrh.GetNCLCTreturnValues values = rozvrh.getNextCurrentLessonChangeTime();
        if (values.localDateTime == null){
            return false;
        }
        scheduleDetailedNotificationUpdate(values.localDateTime);
        return true;
    }

    /**
     * Same as {@link #scheduleDetailedNotificationUpdate(Rozvrh)}, but gets the rozvrh for you.
     * @param onFinished
     */
    public void scheduleDetailedNotificationUpdate(onFinishedListener onFinished){
        RozvrhAPI rozvrhAPI = AppSingleton.getInstance(this).getRozvrhAPI();
        rozvrhAPI.getNextNotificationUpdateTime(updateTime -> {
            if (updateTime == null){
                onFinished.onFinished(false);
            }else {
                scheduleDetailedNotificationUpdate(updateTime);
                onFinished.onFinished(true);
            }
        });
    }

    public void enableDetailedNotification(){
        SharedPrefs.setString(this, getString(R.string.PREFS_PERMANENT_NOTIFICATION), "1");
        getPackageManager().setComponentEnabledSetting(new ComponentName(this, NotiBroadcastReceiver.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        RozvrhAPI rozvrhAPI = AppSingleton.getInstance(this).getRozvrhAPI();
        if (notificationLiveData != null)
            notificationLiveData.removeObserver(notificationObserver);
        notificationLiveData = rozvrhAPI.getLiveData(Utils.getDisplayWeekMonday(this));
        notificationLiveData.observeForever(notificationObserver);
        PermanentNotification.update(notificationLiveData.getValue() == null ? null : notificationLiveData.getValue().getRozvrh(), this);
    }

    public void disableDetailedNotification(){
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.cancel(getDetailedNotiPendingIntent(this));
        getPackageManager().setComponentEnabledSetting(new ComponentName(this, NotiBroadcastReceiver.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        // R.string.LEGACY_NOTIFICATION stores if legacy notification (now called detailed notification) was turned on
        // R.string.PERMANENT_NOTIFICATION stores which version of the notification is selected or if any is selected at all
        // 0 = notification turned off (default)
        // 1 = detailed (legacy) notification
        // 2 = progress bar (new) notification
        SharedPrefs.setBoolean(this, getString(R.string.PREFS_LEGACY_NOTIFICATION), false);
        SharedPrefs.setString(this, getString(R.string.PREFS_PERMANENT_NOTIFICATION), "0");

        PermanentNotification.update(null,0, this);
        if (notificationLiveData != null) {
            notificationLiveData.removeObserver(notificationObserver);
            notificationLiveData = null;
        }
    }

    public static interface onFinishedListener {
        public void onFinished(boolean successful);
    }

    /**
     * Starts up sentry crash reporting, but only if it is an official build and crash reporting is
     * allowed (see build.gradle).
     */
    public void enableSentry(){
        /*
         * Only enable sentry on the official release build
         */
        if (BuildConfig.ALLOW_SENTRY) {
            Sentry.init("https://d13d732d380444f5bed7487cfea65814@sentry.io/1820627", new AndroidSentryClientFactory(this));
            Sentry.getContext().addExtra("commit hash",BuildConfig.GitHash);

            if (!SharedPrefs.contains(this, SharedPrefs.SENTRY_ID) || SharedPrefs.getString(this, SharedPrefs.SENTRY_ID).isEmpty()){
                SharedPrefs.setString(this, SharedPrefs.SENTRY_ID, "android:" + Long.toHexString(new Random().nextLong()));
            }
            Sentry.getContext().setUser(new User(SharedPrefs.getString(this, SharedPrefs.SENTRY_ID),null, null, null));
        }else {
            diableSentry();
            SharedPrefs.setBooleanPreference(this, R.string.PREFS_SEND_CRASH_REPORTS, false);
        }
    }

    public void diableSentry(){
        Sentry.close();
    }

    @Override
    public void onTerminate() {
        //prevent leaks
        if (notificationLiveData != null) {
            notificationLiveData.removeObserver(notificationObserver);
            notificationLiveData = null;
        }
        super.onTerminate();
    }

}