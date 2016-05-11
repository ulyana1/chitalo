package com.tereshchenkoulyana.chitalo.services;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.tereshchenkoulyana.chitalo.R;
import com.tereshchenkoulyana.chitalo.activities.ViewHeadlinesActivity;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Service to listen for rss headline data updates
 */
public class WearRSSDataListenerService extends WearableListenerService {
    public static String NOTIFICATION_ID_EXTRA = "notificationId";
    private int mNotificationId = 636867;   //We may or may not be able to dictate this

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        storeHeadlineData(new String(messageEvent.getData()));
        generateNotification();
    }

    /**
     * Store the headline json data to prefs so the view activity can load them
     */
    private void storeHeadlineData(String messageData) {
        SharedPreferences prefs = getSharedPreferences(ViewHeadlinesActivity.PREFS_STORE, 0);

        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(ViewHeadlinesActivity.HEADLINES_DATA, messageData);
        edit.commit();
    }

    /**
     * Generate the notification that will allow the user to see their headlines
     */
    private void generateNotification() {
        Intent showHeadlinesIntent = new Intent(this, ViewHeadlinesActivity.class);
        showHeadlinesIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
        showHeadlinesIntent.putExtra(NOTIFICATION_ID_EXTRA, mNotificationId);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.updated_feed_notify_title))
                .setContentText(getString(R.string.updated_feed_notify_desc))
                .setContentIntent(PendingIntent.getActivity(this, 0, showHeadlinesIntent, 0));

        NotificationManagerCompat mgrCompat = NotificationManagerCompat.from(this);
        mgrCompat.notify(mNotificationId, builder.build());
    }
}
