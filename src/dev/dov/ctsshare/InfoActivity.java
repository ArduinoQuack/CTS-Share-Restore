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
        text.setText("CTS Share Restore 1.2\n\nShare joins Google's selection actions or appears above the selection as a pill. The pill belongs to the Lens view layer so Google's results can cover it normally. It also hides in Translate and Live Translate.\n\nInstall over the previous version, then force-stop Google. Enable the module in Vector for Google only.\n\nImages are shared as JPEG attachments, including to Gemini. Temporary images are automatically removed. The module requests no network or storage permissions and has no launcher icon.\n\nGoogle updates can still require a module update.\n\nTo remove the change, disable the module and force-stop Google.");
        setContentView(text);
    }
}

