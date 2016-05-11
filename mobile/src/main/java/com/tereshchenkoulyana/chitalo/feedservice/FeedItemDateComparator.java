package com.tereshchenkoulyana.chitalo.feedservice;

import android.util.Log;

import com.axelby.riasel.FeedItem;

import java.util.Comparator;

/**
 * Compares two RssItem objects by their publication date
 */
public class FeedItemDateComparator implements Comparator<FeedItem> {
    @Override
    public int compare(FeedItem firstItem, FeedItem secondItem) {
        try {
            return secondItem.getPublicationDate().compareTo(firstItem.getPublicationDate());
        } catch (Exception e) {
            Log.e("Chitalo", "::: Error when sorting RSS items :::", e);
            return 0;
        }
    }
}
