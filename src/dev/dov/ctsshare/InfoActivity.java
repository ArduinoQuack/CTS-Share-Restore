package dev.dov.ctsshare;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class InfoActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setPadding(32, 64, 32, 32);
        text.setTextSize(18);
        text.setText("CTS Share Restore 0.7\n\nShare joins Google's selection action row when available. Otherwise a wide rounded pill appears above the visible selection handles, moving below only when there is no space above. It hides in Translate and Live Translate. There is no home-screen or app-drawer icon; open this screen through the module manager.\n\n1. Enable this module in Vector.\n2. Scope it to Google only.\n3. Force-stop Google and reopen it.\n4. Invoke Circle to Search and select a region or text.\n5. Tap Share.\n\nThe module rediscovers selection classes and rendered-region data after Google updates and validates their structure. Major internal changes can still require an update.\n\nIt uses Google's captured image and recognized text. It requests no network or storage permissions. Shared images use Google's private cache.\n\nRollback: disable this module in Vector and force-stop Google.\n\nThe previous sharing behavior was confirmed working by the user. The new placement uses the native drawn rectangle, including minimum-size expansion and animation. It needs verification on your phone. If needed, send the CTSShare entries from Vector's logs.");
        setContentView(text);
    }
}
