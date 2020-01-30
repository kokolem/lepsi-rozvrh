package cz.vitskalicky.lepsirozvrh.notification.progressBar;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import cz.vitskalicky.lepsirozvrh.AppSingleton;
import cz.vitskalicky.lepsirozvrh.BuildConfig;
import cz.vitskalicky.lepsirozvrh.R;
import cz.vitskalicky.lepsirozvrh.Utils;
import cz.vitskalicky.lepsirozvrh.activity.MainActivity;
import cz.vitskalicky.lepsirozvrh.bakaAPI.rozvrh.RozvrhAPI;
import cz.vitskalicky.lepsirozvrh.items.BreakOrLesson;
import cz.vitskalicky.lepsirozvrh.items.Rozvrh;
import cz.vitskalicky.lepsirozvrh.items.RozvrhHodina;
import cz.vitskalicky.lepsirozvrh.notification.detailed.DetailedPermanentNotification;

public class ProgressBarNotiBroadcastReceiver extends BroadcastReceiver {
    public static final String TAG = ProgressBarNotiBroadcastReceiver.class.getSimpleName();

    public static final int PERMANENT_NOTIFICATION_ID = 535;
    private static final int REQUEST_CODE = 489;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: called");

        RozvrhAPI rozvrhAPI = AppSingleton.getInstance(context).getRozvrhAPI();
        LocalDate weekMonday = Utils.getDisplayWeekMonday(context);

        rozvrhAPI.getRozvrh(weekMonday, rozvrhWrapper -> {
            onScheduleLoaded(rozvrhWrapper.getRozvrh(), context);
        });
    }

    private void onScheduleLoaded(Rozvrh rozvrh, Context context) {
        Log.d(TAG, "onScheduleLoaded: called");

        Rozvrh.Lesson firstLesson = rozvrh.getFirstLessonToday();
        Rozvrh.Lesson lastLesson = rozvrh.getLastLessonToday();

        if (firstLesson == null) { // || lastLesson == null would be redundant, they both return null in the same cases
            // if there aren't any lessons today
            Log.d(TAG, "onScheduleLoaded: There aren't any lessons today");
            scheduleNextTask(rozvrh, true, true, context);
        } else {
            // if there is at least one lesson today

            // get start and end time of the first and the last lesson
            DateTime firstLessonStart = firstLesson.rozvrhHodina.getParsedBegintime().toDateTimeToday();
            DateTime lastLessonEnd = lastLesson.rozvrhHodina.getParsedEndtime().toDateTimeToday();

            if (firstLessonStart.isAfterNow()) {
                // if the first lesson didn't start yet
                Log.d(TAG, "onScheduleLoaded: The first lesson didn't start yet");
                scheduleNextTask(rozvrh, false, false, context);
            } else if (lastLessonEnd.isBeforeNow()) {
                // if the last lesson is over
                Log.d(TAG, "onScheduleLoaded: The last lesson is over");
                scheduleNextTask(rozvrh, true, true, context);
                NotificationManagerCompat.from(context).cancel(PERMANENT_NOTIFICATION_ID); // remove the notification
            } else {
                // if it's school now
                Log.d(TAG, "onScheduleLoaded: It's school now");
                updateNotification(rozvrh, context);
                scheduleNextTask(rozvrh, true, false, context);
            }
        }
    }

    private void scheduleNextTask(Rozvrh rozvrh, boolean firstLessonStarted, boolean lastLessonEnded, Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent permanentNotificationIntent = new Intent(context, ProgressBarNotiBroadcastReceiver.class);
        PendingIntent permanentNotificationPendingIntent = PendingIntent.getBroadcast(context,
                REQUEST_CODE, permanentNotificationIntent, 0);

        if (!firstLessonStarted) {
            // first lesson didn't start yet - schedule an alarm to go off when it starts
            Log.d(TAG, "scheduleNextTask: setting alarm to go off when the first lesson starts");
            DateTime firstLessonStart = rozvrh.getFirstLessonToday().rozvrhHodina.getParsedBegintime().toDateTimeToday();
            alarmManager.setExact(AlarmManager.RTC, firstLessonStart.getMillis(), permanentNotificationPendingIntent);
        } else if (lastLessonEnded) {
            // last lesson ended - schedule an alarm to go off when tomorrow's first lesson starts
            Log.d(TAG, "scheduleNextTask: setting alarm to go off tomorrow");

            // no ones school starts (hopefully) before five AM
            LocalTime sixAm = LocalTime.fromMillisOfDay(1000 * 60 * 60 * 5);
            DateTime sixAmTomorrow = new DateTime().withTimeAtStartOfDay().plusDays(1).withTime(sixAm);

            DateTime tomorrowMidnight = new DateTime().withTimeAtStartOfDay().plusDays(1).withTimeAtStartOfDay();

            // this will work because this method will be run tomorrow before five AM with firstLessonStarted = false and lastLessonEnded = false
            // so it will schedule an alarm to go off when tomorrow's first lesson starts
            alarmManager.setWindow(AlarmManager.RTC, tomorrowMidnight.getMillis(), sixAmTomorrow.getMillis(), permanentNotificationPendingIntent);
        } else {
            // it's school right now - schedule an alarm to go off in 30 seconds
            Log.d(TAG, "scheduleNextTask: setting alarm to go off in 30 seconds");
            alarmManager.setExact(AlarmManager.RTC, System.currentTimeMillis() + 1000 * 30, permanentNotificationPendingIntent);
        }
    }

    private void updateNotification(Rozvrh rozvrh, Context context) {
        Rozvrh.Lesson nextLesson = rozvrh.getNextLesson();
        String nextLessonText;

        if (nextLesson != null) {
            if (nextLesson.rozvrhHodina != null) {
                if (nextLesson.rozvrhHodina.getTyp().equals("X")) {
                    nextLessonText = context.getString(R.string.notification_no_lesson);
                } else {
                    nextLessonText = extractLessonInfo(nextLesson.rozvrhHodina, context);
                }
            } else {
                nextLessonText = context.getString(R.string.notification_no_lesson);
            }
        } else {
            nextLessonText = context.getString(R.string.notification_no_lesson);
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
                    currentLessonText = context.getString(R.string.notification_no_lesson);
                } else {
                    currentLessonText = extractLessonInfo(currentBreakOrLesson.getCurrentLesson(), context);
                }
                progressStart = currentBreakOrLesson.getCurrentLesson().getParsedBegintime();
                progressEnd = currentBreakOrLesson.getCurrentLesson().getParsedEndtime();
            } else {
                currentLessonText = context.getString(R.string.permanent_notification_break);
                progressStart = currentBreakOrLesson.getCurrentBreak().getBeginTime();
                progressEnd = currentBreakOrLesson.getCurrentBreak().getEndTime();
            }
        } else {
            progressIndeterminable = true;
            currentLessonText = context.getString(R.string.notification_no_lesson);
        }

        Intent openAppIntent = new Intent(context, MainActivity.class);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(context,
                0, openAppIntent, 0);

        Notification notification = new NotificationCompat.Builder(context, DetailedPermanentNotification.PERMANENT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_icon)
                .setContentTitle(String.format(context.getString(R.string.notification_lesson_or_break_now_happening), currentLessonText))
                .setContentText(String.format(context.getString(R.string.notification_next_lesson), nextLessonText))
                .setWhen(LocalDate.now().toDateTime(progressEnd).getMillis())
                .setUsesChronometer(!progressIndeterminable)
                .setShowWhen(!progressIndeterminable)
                // .setChronometerCountDown(true) missing from the API for unknown reason, see hack below
                .setProgress(progressEnd.getMillisOfDay() - progressStart.getMillisOfDay(), progressNow.getMillisOfDay() - progressStart.getMillisOfDay(), progressIndeterminable)
                .setContentIntent(openAppPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.raw.silence))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(new long[]{})
                .build();

        // a hack to enable ChronometerCountDown (the thing itself is implemented, the setter method is missing)
        notification.extras.putBoolean("android.chronometerCountDown", true);

        NotificationManagerCompat.from(context).notify(PERMANENT_NOTIFICATION_ID, notification);
    }

    private String extractLessonInfo(RozvrhHodina lesson, Context context) {
        String lessonInfo;

        String classroom = lesson.getZkrmist();
        String subject = lesson.getZkrpr();

        if (subject == null || subject.isEmpty()) {
            if (lesson.getZkratka() != null && !lesson.getZkratka().isEmpty()) {
                subject = lesson.getZkratka();
            } else subject = context.getString(R.string.unknown_lesson);
        }

        if (classroom == null || classroom.isEmpty()) {
            lessonInfo = subject;
        } else {
            lessonInfo = String.format(context.getString(R.string.notification_lesson_in_class), subject, classroom);
        }

        return lessonInfo;
    }
}

