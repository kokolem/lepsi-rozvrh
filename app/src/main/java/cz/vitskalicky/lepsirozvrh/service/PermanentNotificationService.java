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
import org.joda.time.LocalTime;

import cz.vitskalicky.lepsirozvrh.AppSingleton;
import cz.vitskalicky.lepsirozvrh.R;
import cz.vitskalicky.lepsirozvrh.Utils;
import cz.vitskalicky.lepsirozvrh.activity.MainActivity;
import cz.vitskalicky.lepsirozvrh.bakaAPI.rozvrh.RozvrhAPI;
import cz.vitskalicky.lepsirozvrh.items.BreakOrLesson;
import cz.vitskalicky.lepsirozvrh.items.Rozvrh;

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
        Rozvrh.GetAnyLessonReturnValues nextLesson = rozvrh.getNextLesson();
        String nextLessonText;

        if (nextLesson != null) {
            if (nextLesson.rozvrhHodina != null) {
                if (nextLesson.rozvrhHodina.getTyp().equals("X")) {
                    nextLessonText = "volná hodina";
                } else {
                    nextLessonText = nextLesson.rozvrhHodina.getZkrpr() + " v " + nextLesson.rozvrhHodina.getZkrmist();
                }
            } else {
                nextLessonText = "volná hodina";
            }
        } else {
            nextLessonText = "volná hodina";
        }

        Log.d("PERMANOT", "další hodina: " + nextLessonText);

        BreakOrLesson currentBreakOrLesson = rozvrh.getCurrentBreakOrLesson();
        String currentLessonText;
        LocalTime progressStart = LocalTime.now();
        LocalTime progressNow = LocalTime.now();
        LocalTime progressEnd = LocalTime.now();
        boolean progressIndeterminable = false;

        if (currentBreakOrLesson != null) {
            if (currentBreakOrLesson.getCurrentBreak() == null) {
                if (currentBreakOrLesson.getCurrentLesson().getTyp().equals("X")) {
                    currentLessonText = "volná hodina";
                } else {
                    currentLessonText = currentBreakOrLesson.getCurrentLesson().getZkrpr() + " v " + currentBreakOrLesson.getCurrentLesson().getZkrmist();
                }
                progressStart = currentBreakOrLesson.getCurrentLesson().getParsedBegintime();
                progressEnd = currentBreakOrLesson.getCurrentLesson().getParsedEndtime();
            } else {
                currentLessonText = "přestávka";
                progressStart = currentBreakOrLesson.getCurrentBreak().getBeginTime();
                progressEnd = currentBreakOrLesson.getCurrentBreak().getEndTime();
            }
        } else {
            progressIndeterminable = true;
            currentLessonText = "volná hodina";
        }

        Log.d("PERMANOT", "tato hodina: " + currentLessonText);
        Log.d("PERMANOT", "progress start: " + progressStart);
        Log.d("PERMANOT", "progress now: " + progressNow);
        Log.d("PERMANOT", "progress end: " + progressEnd);
        Log.d("PERMANOT", "nov: " + (progressNow.getMillisOfDay() - progressStart.getMillisOfDay()));
        Log.d("PERMANOT", "van: " + (progressEnd.getMillisOfDay() - progressStart.getMillisOfDay()));
        Log.d("PERMANOT", "progress indeterminable: " + progressIndeterminable);
        Log.d("PERMANOT", "-----------------------------------");


        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Právě probíhá: " + currentLessonText)
                .setContentText("Následuje: " + nextLessonText)
                .setWhen(LocalDate.now().toDateTime(progressEnd).getMillis())
                .setUsesChronometer(!progressIndeterminable)
                .setShowWhen(!progressIndeterminable)
                // .setChronometerCountDown(true) missing from the API for unknown reason, see hack below
                .setProgress(progressEnd.getMillisOfDay() - progressStart.getMillisOfDay(), progressNow.getMillisOfDay() - progressStart.getMillisOfDay(), progressIndeterminable)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .build();

        // a hack to enable ChronometerCountDown (the thing itself is implemented, the setter method is missing)
        notification.extras.putBoolean("android.chronometerCountDown", true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(NOTIFICATION_ID, notification);
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

