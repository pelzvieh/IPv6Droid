package de.flyingsnail.ipv6droid.android.subscription;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.SettingsActivity;

public class InitialSetup extends Activity {

    private WebView subscriptionWebView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_initial_setup);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        subscriptionWebView = (WebView) findViewById(R.id.subscribeButtonWebView);
        subscriptionWebView.loadData("<form action=\"https://www.paypal.com/cgi-bin/webscr\" method=\"post\" target=\"_top\">\n" +
                "<input type=\"hidden\" name=\"cmd\" value=\"_s-xclick\">\n" +
                "<input type=\"hidden\" name=\"encrypted\" value=\"-----BEGIN PKCS7-----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-----END PKCS7-----\n" +
                "\">\n" +
                "<input type=\"image\" src=\"https://www.paypalobjects.com/en_US/i/btn/btn_subscribeCC_LG.gif\" border=\"0\" name=\"submit\" alt=\"PayPal - The safer, easier way to pay online!\">\n" +
                "<img alt=\"\" border=\"0\" src=\"https://www.paypalobjects.com/en_US/i/scr/pixel.gif\" width=\"1\" height=\"1\">\n" +
                "</form>\n" +
                "\n", "text/html", "UTF-8");
    }


    /**
     * Open Settings activity via intent .
     *
     * @param view the View that was clicked to call this method.
     */
    public void openSettings (View view) {
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        startActivity(settingsIntent);
    }
}
