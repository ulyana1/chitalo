package com.tereshchenkoulyana.chitalo.activities;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Window;

import com.tereshchenkoulyana.chitalo.R;
import com.tereshchenkoulyana.chitalo.fragments.AddFeedFragment;

/**
 * Add a feed to be aggregated into the wear-pushed headlines list via the add fragment
 */
public class AddFeedActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_add_feed);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().add(R.id.container, new AddFeedFragment()).commit();
        }
    }
}