package cz.vitskalicky.lepsirozvrh.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.joda.time.LocalDate;

import cz.vitskalicky.lepsirozvrh.AppSingleton;
import cz.vitskalicky.lepsirozvrh.R;
import cz.vitskalicky.lepsirozvrh.Utils;
import cz.vitskalicky.lepsirozvrh.activity.MainActivity;
import cz.vitskalicky.lepsirozvrh.bakaAPI.rozvrh.RozvrhAPI;
import cz.vitskalicky.lepsirozvrh.items.Rozvrh;
import cz.vitskalicky.lepsirozvrh.items.RozvrhHodina;

import static cz.vitskalicky.lepsirozvrh.bakaAPI.ResponseCode.SUCCESS;

public class PermanentNotificationService extends Service {
    public static final String CHANNEL_ID = "PermanentNotificationChannel";
    private static final int NOTIFICATION_ID = 968;

    private int netCode;
    private PendingIntent pendingIntent;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);


        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Právě probíhá: načítání rozvrhu")
                .setContentText("Může to chvíli trvat...")
                .setShowWhen(false)
                .setProgress(0, 0, true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        RozvrhAPI rozvrhAPI = AppSingleton.getInstance(this).getRozvrhAPI();

        netCode = -1;
        LocalDate week = Utils.getDisplayWeekMonday(this);

        Rozvrh item = rozvrhAPI.get(week, (code, rozvrh) -> {
            //onCacheLoaded
            // if data wasn't already successfully downloaded from the internet, use cached data
            if (netCode != SUCCESS && code == SUCCESS) rerenderNotification(rozvrh);
        }, (code, rozvrh) -> {
            //onNetLoaded
            netCode = code;
            if (code == SUCCESS) rerenderNotification(rozvrh);
        });

        if (item != null) rerenderNotification(item);

        return START_STICKY;
    }

    public void rerenderNotification(Rozvrh rozvrh) {
        Log.d("PERMANOT", "rerenderNotification: my rozvrh: " + rozvrh.toString());
        Log.d("PERMANOT", "rerenderNotification: next lesson: " + rozvrh.getRelevantLesson());

        if (rozvrh.getRelevantLesson() != null && rozvrh.getRelevantLesson().rozvrhHodina != null) {
            RozvrhHodina rozvrhHodina = rozvrh.getRelevantLesson().rozvrhHodina;

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Právě probíhá: " + rozvrhHodina.getZkrpr() + " v " + rozvrhHodina.getZkrmist())
                    .setContentText("Končí v: " + rozvrhHodina.getEndtime())
                    // .setWhen(System.currentTimeMillis() + rozvrh.getRelevantLesson().rozvrhHodina.)
                    // .setUsesChronometer(true)
                    // .setChronometerCountDown(true) missing from the API for unknown reason, see hack below
                    // .setProgress(45, 45 - 34, false)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .build();

            // a hack to enable ChronometerCountDown (the thing itself is implemented, the setter method is missing)
            // notification.extras.putBoolean("android.chronometerCountDown", true);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

