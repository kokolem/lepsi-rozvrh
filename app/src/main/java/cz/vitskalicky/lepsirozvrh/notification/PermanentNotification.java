package cz.vitskalicky.lepsirozvrh.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.text.HtmlCompat;

import cz.vitskalicky.lepsirozvrh.BuildConfig;
import cz.vitskalicky.lepsirozvrh.MainApplication;
import cz.vitskalicky.lepsirozvrh.R;
import cz.vitskalicky.lepsirozvrh.Utils;
import cz.vitskalicky.lepsirozvrh.activity.MainActivity;
import cz.vitskalicky.lepsirozvrh.bakaAPI.rozvrh.RozvrhAPI;
import cz.vitskalicky.lepsirozvrh.items.Rozvrh;
import cz.vitskalicky.lepsirozvrh.items.RozvrhHodina;

public class PermanentNotification {
    public static final int PERMANENT_NOTIFICATION_ID = 7055713;
    public static final String PERMANENT_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".permanentNotificationChannel";

    /**
     * Same as {@link #update(RozvrhHodina, Context)}, but gets the RozvrhHodina for you.
     * @param onFinished called when finished (Rozvrh may be fetched from the internet).
     */
    public static void update(MainApplication application, RozvrhAPI rozvrhAPI, Utils.Listener onFinished){
        Context context = application;
        rozvrhAPI.justGet(Utils.getDisplayWeekMonday(context), (code, rozvrh) -> {
            if (rozvrh != null){
                Rozvrh.GetNLreturnValues nextLessonInfo = rozvrh.getHighlightLesson(true);
                RozvrhHodina rozvrhHodina = nextLessonInfo == null ? null : nextLessonInfo.rozvrhHodina;
                update(rozvrhHodina, context);
                application.scheduleNotificationUpdate(successful -> {
                    onFinished.method();
                });
            } else {
                onFinished.method();
            }
        });
    }

    /**
     * Updates the notification with the data of supplied RozvrhHodina. (Notification is hidden if
     * RozvrhHodina is null)
     */
    public static void update(RozvrhHodina hodina, Context context) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        if (hodina == null) {
            notificationManager.cancel(PERMANENT_NOTIFICATION_ID);
            return;
        }

        String predmet = hodina.getPr();
        if (predmet == null || predmet.isEmpty())
            predmet = hodina.getZkrpr();
        if (predmet == null || predmet.isEmpty())
            predmet = hodina.getZkratka();
        if (predmet == null || predmet.isEmpty())
            predmet = hodina.getNazev();
        if (predmet == null || predmet.isEmpty())
            predmet = "";

        String mistnost = hodina.getMist();
        if (mistnost == null || mistnost.isEmpty())
            mistnost = hodina.getZkrmist();
        if (mistnost == null || mistnost.isEmpty())
            mistnost = "";

        String ucitel = hodina.getUc();
        if (ucitel == null || ucitel.isEmpty())
            ucitel = hodina.getZkruc();
        if (ucitel == null || ucitel.isEmpty())
            ucitel = "";

        String skupina = hodina.getSkup();
        if (skupina == null || skupina.isEmpty())
            skupina = hodina.getZkrskup();
        if (skupina == null || skupina.isEmpty())
            skupina = "";
        else //skupina is not empty
            skupina = context.getString(R.string.group_in_notification) + " " + skupina;

        CharSequence title = "";
        if (!predmet.isEmpty() && !mistnost.isEmpty()) {
            title = HtmlCompat.fromHtml(predmet + " - <b>" + mistnost + "</b>", HtmlCompat.FROM_HTML_MODE_COMPACT);
        } else {
            title = HtmlCompat.fromHtml(predmet + "<b>" + mistnost + "</b>", HtmlCompat.FROM_HTML_MODE_COMPACT);
        }

        CharSequence content = "";
        if (!ucitel.isEmpty() && !skupina.isEmpty()) {
            content = ucitel + ", " + skupina;
        } else {
            content = ucitel + skupina;
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_JUMP_TO_TODAY, true);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntentWithParentStack(intent);

        PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        //create notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, PERMANENT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_icon)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setWhen(Long.MAX_VALUE)
                .setShowWhen(false)
                .setSound(Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.raw.silence))
                .setVibrate(new long[]{})
                .setOnlyAlertOnce(true);
        Notification ntf = builder.build();

        // notificationId is a unique int for each notification that you must
        notificationManager.notify(PERMANENT_NOTIFICATION_ID, ntf);
    }
}
