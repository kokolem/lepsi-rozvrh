package cz.vitskalicky.lepsirozvrh.permanentNotification;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import java.util.List;

import cz.vitskalicky.lepsirozvrh.AppSingleton;
import cz.vitskalicky.lepsirozvrh.R;
import cz.vitskalicky.lepsirozvrh.Utils;
import cz.vitskalicky.lepsirozvrh.activity.MainActivity;
import cz.vitskalicky.lepsirozvrh.bakaAPI.rozvrh.RozvrhAPI;
import cz.vitskalicky.lepsirozvrh.items.BreakOrLesson;
import cz.vitskalicky.lepsirozvrh.items.Rozvrh;

import static cz.vitskalicky.lepsirozvrh.bakaAPI.ResponseCode.SUCCESS;

public class PermanentNotificationService extends IntentService {
    public static final String TAG = PermanentNotificationService.class.getSimpleName();

    public static final String CHANNEL_ID = "PermanentNotificationChannel";
    public static final int NOTIFICATION_ID = 968;

    private static final int SCHEDULE_TASKS = 665;
    private static final int UPDATE_NOTIFICATION = 748;
    private static final int REMOVE_NOTIFICATION = 540;
    private static final int DISABLE_NOTIFICATION = 497;

    private int netCode;

    public PermanentNotificationService() {
        super("Permanent notification service");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        int requestedAction = intent.getIntExtra("ACTION", SCHEDULE_TASKS);

        RozvrhAPI rozvrhAPI = AppSingleton.getInstance(this).getRozvrhAPI();
        LocalDate week = Utils.getDisplayWeekMonday(this);

/*        // ---------DEBUG----------

        Log.d(TAG, "onHandleIntent: Update notification");
        netCode = -1;
        Rozvrh updateRozvrh2 = rozvrhAPI.get(week, (code, rozvrh) -> {
            //onCacheLoaded
            // if data wasn't already successfully downloaded from the internet, use cached data
            if (netCode != SUCCESS && code == SUCCESS) {
                updateNotification(rozvrh);
            }
        }, (code, rozvrh) -> {
            //onNetLoaded
            netCode = code;
            if (code == SUCCESS) {
                updateNotification(rozvrh);
            }
        });
        if (updateRozvrh2 != null) updateNotification(updateRozvrh2);

        // ---------DEBUG----------*/

        switch (requestedAction) {

            // called every day to schedule tasks for that day
            case SCHEDULE_TASKS:
                Log.d(TAG, "onHandleIntent: Schedule tasks");

                netCode = -1;
                Rozvrh scheduleTasksRozvrh = rozvrhAPI.get(week, (code, rozvrh) -> {
                    //onCacheLoaded
                    // if data wasn't already successfully downloaded from the internet, use cached data
                    if (netCode != SUCCESS && code == SUCCESS) {
                        scheduleTasks(rozvrh);
                    }
                }, (code, rozvrh) -> {
                    //onNetLoaded
                    netCode = code;
                    if (code == SUCCESS) {
                        scheduleTasks(rozvrh);
                    }
                });
                if (scheduleTasksRozvrh != null) scheduleTasks(scheduleTasksRozvrh);
                break;

            // called when the notification should be updated
            case UPDATE_NOTIFICATION:
                Log.d(TAG, "onHandleIntent: Update notification");

                netCode = -1;
                Rozvrh updateNotificationRozvrh = rozvrhAPI.get(week, (code, rozvrh) -> {
                    //onCacheLoaded
                    // if data wasn't already successfully downloaded from the internet, use cached data
                    if (netCode != SUCCESS && code == SUCCESS) {
                        updateNotification(rozvrh);
                    }
                }, (code, rozvrh) -> {
                    //onNetLoaded
                    netCode = code;
                    if (code == SUCCESS) {
                        updateNotification(rozvrh);
                    }
                });
                if (updateNotificationRozvrh != null) updateNotification(updateNotificationRozvrh);
                break;

            // called after the last lesson to remove the notification
            case REMOVE_NOTIFICATION:
                Log.d(TAG, "onHandleIntent: Remove notification");

                // stop updating the notification
                AlarmManager removeNotificationAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
                Intent updateNotificationIntentForRemoveNotification = new Intent(this, PermanentNotificationService.class);
                PendingIntent updateNotificationPendingIntentForRemoveNotification = PendingIntent.getService(this, UPDATE_NOTIFICATION, updateNotificationIntentForRemoveNotification, 0);
                removeNotificationAlarmManager.cancel(updateNotificationPendingIntentForRemoveNotification);

                // remove the notification
                NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
                break;

            // called when the user disables permanent notification in settings  - cancels all scheduled alarms
            // and removes the notification
            case DISABLE_NOTIFICATION:
                Log.d(TAG, "onHandleIntent: Disable notification");

                AlarmManager disableNotificationAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

                // the following code basically reverses what was done in scheduleTasks

                // cancel alarms set for when a lesson begins or ends
                for (int i = 0; i < 24; i++) { // no one has more than 24 lessons a day
                    Intent updateNotificationIntentForDisableNotification = new Intent(this, PermanentNotificationService.class);
                    PendingIntent updateNotificationPendingIntentForDisableNotification = PendingIntent.getService(this, UPDATE_NOTIFICATION + i + 1, updateNotificationIntentForDisableNotification, 0);
                    disableNotificationAlarmManager.cancel(updateNotificationPendingIntentForDisableNotification);
                }

                // stop updating the notification
                Intent updateNotificationIntentForDisableNotification = new Intent(this, PermanentNotificationService.class);
                PendingIntent updateNotificationPendingIntentForDisableNotification = PendingIntent.getService(this, UPDATE_NOTIFICATION, updateNotificationIntentForDisableNotification, 0);
                disableNotificationAlarmManager.cancel(updateNotificationPendingIntentForDisableNotification);

                // cancel alarm for scheduling tasks tomorrow
                Intent scheduleTasksIntentForDisableNotification = new Intent(this, PermanentNotificationService.class);
                PendingIntent scheduleTasksPendingIntentForDisableNotification = PendingIntent.getService(this, SCHEDULE_TASKS, scheduleTasksIntentForDisableNotification, 0);
                disableNotificationAlarmManager.cancel(scheduleTasksPendingIntentForDisableNotification);

                // finally, remove the notification
                NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);

                // theoretically, one of the alarms might go off while this will be running
                // and the intent would be in queue of this service
                // so let's call stopSelf() just to be sure that the notification won't be
                // updated (and thus reappear) after it was disabled in the settings
                stopSelf();

                break;

        }
    }

    private void scheduleTasks(Rozvrh rozvrh) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        // there is no point in scheduling notification updates if the school is already over (.isAfterNow())
        // or there are no lessons today (!= null)
        Rozvrh.Lesson lastLesson = rozvrh.getLastLessonToday();
        if (lastLesson != null && lastLesson.rozvrhHodina.getParsedEndtime().toDateTimeToday().isAfterNow()) {
            Rozvrh.Lesson firstLesson = rozvrh.getFirstLessonToday();

            long firstLessonStart = firstLesson.rozvrhHodina.getParsedBegintime().toDateTimeToday().getMillis();
            long lastLessonEnd = lastLesson.rozvrhHodina.getParsedEndtime().toDateTimeToday().getMillis();

            // update the notification when a lesson begins / ends
            List<DateTime> lessonTimesToday = rozvrh.getLessonDateTimesToday();
            for (int i = 0; i < lessonTimesToday.size(); i++) {
                Intent updateNotificationIntent = new Intent(this, PermanentNotificationService.class);
                updateNotificationIntent.putExtra("ACTION", UPDATE_NOTIFICATION);
                PendingIntent updateNotificationPendingIntent = PendingIntent.getService(this, UPDATE_NOTIFICATION + i + 1, updateNotificationIntent, 0);
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, lessonTimesToday.get(i).getMillis(), updateNotificationPendingIntent);
            }

            // update the notification every two minutes after the first lesson starts to update progress
            Intent updateNotificationIntent = new Intent(this, PermanentNotificationService.class);
            updateNotificationIntent.putExtra("ACTION", UPDATE_NOTIFICATION);
            PendingIntent updateNotificationPendingIntent = PendingIntent.getService(this, UPDATE_NOTIFICATION, updateNotificationIntent, 0);
            alarmManager.setRepeating(AlarmManager.RTC, firstLessonStart, 1000 * 60, updateNotificationPendingIntent);
        }

        // tomorrow, schedule tasks before 6AM (hopefully, no ones school starts before that)
        LocalTime sixam = LocalTime.fromMillisOfDay(1000 * 60 * 60 * 6);

        Intent scheduleTasksIntent = new Intent(this, PermanentNotificationService.class);
        scheduleTasksIntent.putExtra("ACTION", SCHEDULE_TASKS);
        PendingIntent scheduleTasksPendingIntent = PendingIntent.getService(this, SCHEDULE_TASKS, scheduleTasksIntent, 0);
        alarmManager.setWindow(AlarmManager.RTC, new DateTime().withTimeAtStartOfDay().plusDays(1).withTimeAtStartOfDay().getMillis(),
                new DateTime().withTimeAtStartOfDay().plusDays(1).withTime(sixam).getMillis(), scheduleTasksPendingIntent);
    }

    private void updateNotification(Rozvrh rozvrh) {

        // if the first lesson didn't start yet, do nothing
        DateTime firstLessonStart = rozvrh.getFirstLessonToday().rozvrhHodina.getParsedBegintime().toDateTimeToday();
        if (firstLessonStart.isAfterNow()) return;

        // if the last lesson ended, remove and stop updating the notification
        DateTime lastLessonEnd = rozvrh.getLastLessonToday().rozvrhHodina.getParsedEndtime().toDateTimeToday();
        if (lastLessonEnd.isBeforeNow()) {
            Intent removeNotificationIntent = new Intent(this, PermanentNotificationService.class);
            removeNotificationIntent.putExtra("ACTION", REMOVE_NOTIFICATION);
            startService(removeNotificationIntent);
            return;
        }

        Rozvrh.Lesson nextLesson = rozvrh.getNextLesson();
        String nextLessonText;

        if (nextLesson != null) {
            if (nextLesson.rozvrhHodina != null) {
                if (nextLesson.rozvrhHodina.getTyp().equals("X")) {
                    nextLessonText = getString(R.string.notification_no_lesson);
                } else {
                    nextLessonText = String.format(getString(R.string.notification_lesson_in_class), nextLesson.rozvrhHodina.getZkrpr(), nextLesson.rozvrhHodina.getZkrmist());
                }
            } else {
                nextLessonText = getString(R.string.notification_no_lesson);
            }
        } else {
            nextLessonText = getString(R.string.notification_no_lesson);
        }

        BreakOrLesson currentBreakOrLesson = rozvrh.getCurrentBreakOrLesson();
        String currentLessonText;
        LocalTime progressStart = LocalTime.now();
        LocalTime progressNow = LocalTime.now();
        LocalTime progressEnd = LocalTime.now();
        boolean progressIndeterminable = false;

        if (currentBreakOrLesson != null) {
            if (currentBreakOrLesson.getCurrentBreak() == null) {
                if (currentBreakOrLesson.getCurrentLesson().getTyp().equals("X")) {
                    currentLessonText = getString(R.string.notification_no_lesson);
                } else {
                    currentLessonText = String.format(getString(R.string.notification_lesson_in_class), currentBreakOrLesson.getCurrentLesson().getZkrpr(), currentBreakOrLesson.getCurrentLesson().getZkrmist());
                }
                progressStart = currentBreakOrLesson.getCurrentLesson().getParsedBegintime();
                progressEnd = currentBreakOrLesson.getCurrentLesson().getParsedEndtime();
            } else {
                currentLessonText = getString(R.string.permanent_notification_break);
                progressStart = currentBreakOrLesson.getCurrentBreak().getBeginTime();
                progressEnd = currentBreakOrLesson.getCurrentBreak().getEndTime();
            }
        } else {
            progressIndeterminable = true;
            currentLessonText = getString(R.string.notification_no_lesson);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(String.format(getString(R.string.notification_lesson_or_break_now_happening), currentLessonText))
                .setContentText(String.format(getString(R.string.notification_next_lesson), nextLessonText))
                .setWhen(LocalDate.now().toDateTime(progressEnd).getMillis())
                .setUsesChronometer(!progressIndeterminable)
                .setShowWhen(!progressIndeterminable)
                // .setChronometerCountDown(true) missing from the API for unknown reason, see hack below
                .setProgress(progressEnd.getMillisOfDay() - progressStart.getMillisOfDay(), progressNow.getMillisOfDay() - progressStart.getMillisOfDay(), progressIndeterminable)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();

        // a hack to enable ChronometerCountDown (the thing itself is implemented, the setter method is missing)
        notification.extras.putBoolean("android.chronometerCountDown", true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }
}
