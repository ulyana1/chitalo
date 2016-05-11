package com.tereshchenkoulyana.chitalo.feedservice;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.axelby.riasel.Feed;
import com.axelby.riasel.FeedItem;
import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.tereshchenkoulyana.chitalo.R;
import com.tereshchenkoulyana.chitalo.models.WearFeedEntry;
import com.tereshchenkoulyana.chitalo.wearable.SendHeadline;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.gson.Gson;

import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

/**
 * Service to refresh the feed headlines and send to the wearable
 */
public class RefreshHeadlinesService extends WakefulIntentService
        implements Action0, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    public static String COMPLETION_SUCCESS_EXTRA = "COMPLETION_SUCCESS_EXTRA";

    private Map<String, List<FeedItem>> mSyncedFeedData = new HashMap<String, List<FeedItem>>();
    private GoogleApiClient mApiClient;
    private Node mConnectedNode;

    /**
     * Constructor
     */
    public RefreshHeadlinesService() {
        super("RefreshHeadlinesService");
    }

    /**
     * Refresh the RSS headlines as a wakeful work method
     */
    @Override
    protected void doWakefulWork(Intent intent) {
        List<WearFeedEntry> wearFeedEntries = WearFeedEntry.listAll(WearFeedEntry.class);
        if (wearFeedEntries.size() > 0) {
            wearableConnect();

            List<Observable<Feed>> feedObservables = createRssSyncObservables(wearFeedEntries);
            rx.Observable.merge(feedObservables)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(feed -> {
                        if (feed != null) {
                            List<FeedItem> rssItemList = feed.getFeedItems();
                            mSyncedFeedData.put(feed.getTitle(), rssItemList);
                        }
                    }, throwable -> { }, this);
        }
    }

    /**
     * Connect to an associated wearable
     */
    private void wearableConnect() {
        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        if (!mApiClient.isConnected()) {
            mApiClient.connect();
        }
    }

    /**
     * Create the set of observables to wrap the RSS Client calls, so that they can all be synchronized
     */
    private List<rx.Observable<Feed>> createRssSyncObservables(List<WearFeedEntry> wearFeedEntries) {
        List<rx.Observable<Feed>> feedObservables = new ArrayList<>();

        for (final WearFeedEntry entry : wearFeedEntries) {
            feedObservables.add(Observable.create((Subscriber<? super Feed> subscriber) -> {
                FeedProcessingService processSvc = new FeedProcessingService(RefreshHeadlinesService.this, new FeedProcessedSubscriber(subscriber));
                processSvc.getAndProcessFeed(entry.getFeedUrl());
            }));
        }

        return feedObservables;
    }

    /**
     * The onComplete method for all the chained observables
     * 1. Generate the list of headlines to send to the wearable
     * 2. Sort the list of headlines
     * 3. Serialize the headlines
     * 4. Send to the json string to the wearable
     */
    @Override
    public void call() {
        Intent finishIntent = new Intent(getString(R.string.action_local_sync_finished));
        finishIntent.putExtra(COMPLETION_SUCCESS_EXTRA, false);

        try {
            ArrayList<SendHeadline> sendHeadlines = generateSendHeadlines();
            if (sendHeadlines.size() > 0) {
                Collections.sort(sendHeadlines, new SendHeadlineComparator());

                String serializedItems = new Gson().toJson(sendHeadlines);
                if (mApiClient.isConnected() && mConnectedNode != null) {
                    PendingResult<MessageApi.SendMessageResult> msgResult = Wearable.MessageApi.sendMessage(mApiClient, mConnectedNode.getId(), "/feed", serializedItems.getBytes());
                    finishIntent.putExtra(COMPLETION_SUCCESS_EXTRA, true);
                }
            }
        } catch (Exception e) {
            Log.e(getString(R.string.app_name), "::: General error during headline sync :::", e);
        }

        sendBroadcast(finishIntent);
    }

    /**
     * Generate the SendHeadline POJOs that will be sent to the wearable
     */
    private ArrayList<SendHeadline> generateSendHeadlines() {
        ArrayList<SendHeadline> sendHeadlines = new ArrayList<SendHeadline>();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String countStr = prefs.getString(getString(R.string.key_prefs_headlines_count), "5");
        int headlineCount = Integer.parseInt(countStr);

        for (String sourceHostKey : mSyncedFeedData.keySet()) {
            List<FeedItem> rssItems = mSyncedFeedData.get(sourceHostKey);

            try {
                Collections.sort(rssItems, new FeedItemDateComparator());
                int sublistSize = rssItems.size() >= headlineCount ? headlineCount : rssItems.size();
                rssItems = rssItems.subList(0, sublistSize);

                for (FeedItem feedItem : rssItems) {
                    String title = Jsoup.parse(feedItem.getTitle()).text();
                    String rawText = feedItem.getDescription() != null ? Jsoup.parse(feedItem.getDescription()).text() : "";
                    sendHeadlines.add(new SendHeadline(feedItem.getTitle(), sourceHostKey, feedItem.getPublicationDate(), rawText, feedItem.getLink().toString()));
                }
            } catch (Exception ex) {
                Log.e(getString(R.string.app_name), "::: Error generating one set of headlines :::", ex);
            }
        }

        return sendHeadlines;
    }

    /**
     * OnConnected callback for wearable API interface
     */
    @Override
    public void onConnected(Bundle bundle) {
        PendingResult<NodeApi.GetConnectedNodesResult> connectedNodes = Wearable.NodeApi.getConnectedNodes(mApiClient);
        connectedNodes.setResultCallback(result -> {
            if (result.getNodes().size() > 0) {
                mConnectedNode = result.getNodes().get(0);
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) { }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) { }
}
