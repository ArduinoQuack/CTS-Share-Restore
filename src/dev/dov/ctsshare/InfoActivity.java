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
        text.setText("CTS Share Restore 1.0\n\nShare joins Google's selection action row when available. Otherwise a wide rounded pill appears above the visible selection handles, moving below only when there is no space above. It hides in Translate and Live Translate. There is no home-screen or app-drawer icon; open this screen through the module manager.\n\n1. Enable this module in Vector.\n2. Scope it to Google only.\n3. Force-stop Google and Gemini, then reopen Google.\n4. Invoke Circle to Search and select a region or text.\n5. Tap Share.\n\nVersion 1.0 publishes image crops as ordinary seekable MediaStore attachments. This supplies the descriptor and metadata Gemini's importer expects instead of relying on Google's private cache provider. Crops remain standard 8-bit JPEGs and are automatically deleted.\n\nThe module rediscovers selection classes and rendered-region data after Google updates and validates their structure. Major internal changes can still require an update.\n\nIt uses Google's captured image and recognized text. It requests no network or storage permissions.\n\nRollback: disable this module in Vector and force-stop Google.\n\nIf needed, send the CTSShare entries from Vector's logs.");
        setContentView(text);
    }
}
