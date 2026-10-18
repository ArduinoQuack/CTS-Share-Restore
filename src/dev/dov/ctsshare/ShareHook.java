package dev.dov.ctsshare;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Runtime-discovered hooks. No Google code is shipped. */
public final class ShareHook implements IXposedHookLoadPackage {
    private static final String GOOGLE = "com.google.android.googlequicksearchbox";
    private static final String PROVIDER = GOOGLE + ".provider.fileprovider";
    private static final String OVERLAY = "com.google.android.libraries.lens.view.main.LensOverlayLayout";
    private static final String PREFIX = "CTSShare: ";
    // Vector can instantiate the module before Looper.prepareMainLooper().
    // Do not create Android handlers or workers in the module constructor.
    private volatile Handler main;
    private volatile ExecutorService worker;
    private final List<WeakReference<Object>> peers = new ArrayList<>();
    private final AtomicBoolean sharing = new AtomicBoolean();
    private ClassMap map;
    private final List<TextSnapshot> texts = new ArrayList<>();

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) {
        if (!GOOGLE.equals(load.packageName)) return;
        try {
            map = new ClassMap(DexAnchors.discoverCached(load.appInfo.sourceDir,
                new File(load.appInfo.dataDir, "cache/cts-share-adapter.properties")), load.classLoader);
            Class<?> peerClass = map.peerClass;
            Class<?> overlayClass = XposedHelpers.findClass(OVERLAY, load.classLoader);
            overlayClass.getDeclaredMethod("onAttachedToWindow");

            XposedBridge.hookAllConstructors(peerClass, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    synchronized (peers) {
                        peers.removeIf(ref -> ref.get() == null);
                        if (peers.size() >= 8) peers.remove(0);
                        peers.add(new WeakReference<>(p.thisObject));
                    }
                    log("native selection controller captured");
                }
            });
            XposedBridge.hookAllConstructors(map.textClass, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Object range = map.textRange.get(p.thisObject);
                        String text = (String) map.text.get(p.thisObject);
                        if (range != null && text != null && !text.isEmpty()) synchronized (texts) {
                            if (texts.size() >= 16) texts.remove(0);
                            texts.add(new TextSnapshot(range, text));
                        }
                    } catch (Throwable e) { log("text capture failed: " + e); }
                }
            });
            XposedHelpers.findAndHookMethod(overlayClass, "onAttachedToWindow", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    View overlay = (View) p.thisObject;
                    Handler handler = mainHandler();
                    if (handler != null) handler.post(() -> attach(overlay));
                    else log("view attached before the UI looper was available");
                }
            });
            log("validated adaptive hooks registered in " + load.processName);
        } catch (Throwable error) { log("incompatible code shape; disabled: " + error); }
    }

    private Handler mainHandler() {
        Handler handler=main;
        if (handler==null) {
            Looper looper=Looper.getMainLooper();
            if(looper==null)return null;
            synchronized(this) {
                if(main==null)main=new Handler(looper);
                handler=main;
            }
        }
        return handler;
    }

    private synchronized ExecutorService shareWorker() {
        if(worker==null)worker=Executors.newSingleThreadExecutor();
        return worker;
    }

    private static final class TextSnapshot {
        final WeakReference<Object> range;
        final String text;
        TextSnapshot(Object range, String text) { this.range=new WeakReference<>(range);this.text=text; }
    }
    private String selectedText(Object range) {
        synchronized (texts) {
            for (int i=texts.size()-1;i>=0;i--) {
                TextSnapshot snapshot=texts.get(i);
                if (snapshot.range.get()==range) return snapshot.text;
            }
        }
        return null;
    }

    private void attach(View overlay) {
        try {
            View root = overlay.getRootView();
            // Avoid adding a child to LensOverlayLayout: it requires custom LayoutParams.
            if (!(root instanceof FrameLayout) || root.getClass().getName().equals(OVERLAY)) {
                log("unsupported root layout " + root.getClass().getName());
                return;
            }
            FrameLayout host = (FrameLayout) root;
            if (host.findViewWithTag("CTSShareRestore") != null) return;
            final SelectionUi[] holder=new SelectionUi[1];
            final boolean[] layoutWarning={false};
            SelectionUi ui=new SelectionUi(host,overlay,map,()->{
                if(holder[0]==null)return;
                try {
                    Object peer=peerFor(overlay);
                    holder[0].update(peer,translating(peer,root));
                } catch(Throwable e) {
                    if(!layoutWarning[0]) {log("selection action layout failed: "+e);layoutWarning[0]=true;}
                }
            });
            holder[0]=ui;
            TextView button = ui.button;
            if(!ui.nativeStyle()) {
            button.setTextSize(14);
            button.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            boolean dark=(host.getResources().getConfiguration().uiMode & 0x30)==0x20;
            int foreground=dark?0xffe8eaed:0xff202124;
            button.setTextColor(foreground);
            button.setGravity(Gravity.CENTER);
            button.setContentDescription("Share Circle to Search selection");
            int pad = dp(host.getContext(), 16);
            button.setPadding(pad,dp(host.getContext(),8),pad,dp(host.getContext(),8));
            button.setMinHeight(dp(host.getContext(),40));
            Drawable icon=new ShareIcon(foreground);
            int iconSize=dp(host.getContext(),18);
            icon.setBounds(0,0,iconSize,iconSize);
            button.setCompoundDrawables(icon,null,null,null);
            button.setCompoundDrawablePadding(dp(host.getContext(),8));
            StateListDrawable background=new StateListDrawable();
            background.addState(new int[]{android.R.attr.state_pressed},pill(host.getContext(),dark?0xff484b50:0xffdfe1e5));
            background.addState(new int[]{},pill(host.getContext(),dark?0xff303134:0xfff1f3f4));
            button.setBackground(background);
            }
            button.setOnClickListener(v -> choose(host, button, overlay));
            ui.standaloneButton.setOnClickListener(v -> choose(host, ui.standaloneButton, overlay));
            WeakReference<View> overlayRef = new WeakReference<>(overlay);
            WeakReference<TextView> buttonRef = new WeakReference<>(button);
            main.post(new Runnable() {
                private String previousState;
                @Override public void run() {
                    View lens = overlayRef.get();
                    TextView control = buttonRef.get();
                    if (lens == null || control == null) return;
                    if (lens.getWindowToken() == null) {
                        log("selection window detached; removing Share control");
                        ui.dispose();
                        return;
                    }
                    Object peer = peerFor(lens);
                    boolean selected = peer != null && hasSelection(peer);
                    boolean translation=translating(peer,lens.getRootView());
                    String state = translation ? "translation active; Share hidden" : peer == null ? "waiting for matching native controller"
                        : selected ? "selection available" : "native controller ready; waiting for selection";
                    if (!state.equals(previousState)) { log(state); previousState=state; }
                    try {ui.update(peer,translation);}
                    catch(Throwable e) {if(!layoutWarning[0]) {log("selection action layout failed: "+e);layoutWarning[0]=true;}}
                    main.postDelayed(this, 500);
                }
            });
            log("selection-anchored Share attached; native style="+ui.nativeStyle());
        } catch (Throwable e) { log("attach failed: " + e); }
    }

    private Object peerFor(View overlay) {
        // Google's own gesture bridge holds the exact controller for this window.
        // This also works if ART bypasses an already-inlined constructor hook.
        try {
            Object owner=map.owner(overlay);
            if(owner!=null)return owner;
        } catch(Throwable e) { log("native overlay-owner lookup failed: " + e); }
        View root=overlay.getRootView();
        synchronized (peers) {
            for (int i = peers.size() - 1; i >= 0; i--) {
                Object peer = peers.get(i).get();
                if (peer == null) continue;
                try {
                    // Match the actual view root, not the descriptive caller-package string.
                    // Android's contextual-search path can use a different caller tag.
                    Object fragment = map.fragment.get(peer);
                    Object view = call(fragment, "getView");
                    if (view instanceof View && ((View) view).getRootView() == root) return peer;
                } catch (Throwable ignored) { }
            }
        }
        return null;
    }
    private boolean hasSelection(Object peer) {
        try {
            Object selection = map.selection(peer);
            return map.region.get(selection) != null || map.range.get(selection) != null;
        } catch (Throwable ignored) { return false; }
    }

    private void choose(FrameLayout host, TextView button, View overlay) {
        Object peer = peerFor(overlay);
        if(translating(peer,overlay.getRootView()))return;
        if (peer == null) {
            log("Share clicked without a controller matching this window");
            toast(host.getContext(), "Selection controller is not ready; check CTSShare in Vector logs");
            return;
        }
        try {
            Object selection = map.selection(peer);
            RectF crop = map.crop(selection);
            Object textRange = map.range.get(selection);
            String text = textRange==null?null:selectedText(textRange);
            final String selectedText = text;
            if (selectedText != null && !selectedText.isEmpty()) {
                PopupMenu menu = new PopupMenu(button.getContext(), button);
                menu.getMenu().add(Menu.NONE, 1, 0, "Share text");
                if (valid(crop)) menu.getMenu().add(Menu.NONE, 2, 1, "Share image");
                menu.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == 1) shareText(host.getContext(), selectedText);
                    else shareImage(host.getContext(), peer, crop);
                    return true;
                });
                menu.show();
            } else if (valid(crop)) {
                shareImage(host.getContext(), peer, crop);
            } else {
                log("Share clicked but native selection has no image region or captured text");
                toast(host.getContext(), "Google has not provided a shareable selection yet");
            }
        } catch (Throwable e) {
            log("selection read failed: " + e);
            toast(host.getContext(), "Cannot read this selection; check CTSShare in Vector logs");
        }
    }

    private void shareText(Context context, String text) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        launch(context, send);
    }
    private void shareImage(Context context, Object peer, RectF region) {
        if (!sharing.compareAndSet(false, true)) return;
        final Bitmap source;
        try {
            // The native rendered-image record retains the original source bitmap.
            source = map.image(peer);
            if (source==null || source.isRecycled()) throw new IllegalStateException("native screenshot is unavailable");
        } catch (Throwable e) {
            sharing.set(false);
            log("bitmap read failed: " + e);
            toast(context, "Google's screenshot is not available yet");
            return;
        }
        Context app = context.getApplicationContext();
        RectF crop = new RectF(region);
        shareWorker().execute(() -> {
            Bitmap output = null;
            try {
                int left = clamp(Math.round(crop.left * source.getWidth()), 0, source.getWidth() - 1);
                int top = clamp(Math.round(crop.top * source.getHeight()), 0, source.getHeight() - 1);
                int right = clamp(Math.round(crop.right * source.getWidth()), left + 1, source.getWidth());
                int bottom = clamp(Math.round(crop.bottom * source.getHeight()), top + 1, source.getHeight());
                output = Bitmap.createBitmap(source, left, top, right - left, bottom - top);
                File dir = new File(app.getCacheDir(), "cts-share");
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Cannot create temporary share directory");
                File[] old = dir.listFiles();
                if (old != null) for (File file : old) {
                    if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000L) file.delete();
                }
                String name = "selection-" + System.currentTimeMillis() + ".png";
                File file = new File(dir, name);
                try (FileOutputStream stream = new FileOutputStream(file)) {
                    if (!output.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IllegalStateException("PNG encoding failed");
                }
                Uri uri = new Uri.Builder().scheme("content").authority(PROVIDER)
                    .appendPath("image_share").appendPath("cts-share").appendPath(name).build();
                // Provider and cache-path are verified from this exact APK's manifest/resources.
                try (java.io.InputStream probe = app.getContentResolver().openInputStream(uri)) {
                    if (probe == null) throw new IllegalStateException("Image provider could not open the crop");
                }
                Intent send = new Intent(Intent.ACTION_SEND).setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                send.setClipData(ClipData.newRawUri("Circle to Search selection", uri));
                main.post(() -> launch(context, send));
                log("crop ready " + (right - left) + "x" + (bottom - top));
            } catch (Throwable e) {
                log("share failed: " + e);
                main.post(() -> toast(context, "Share failed; check CTSShare in Vector logs"));
            } finally {
                if (output != null && output != source) output.recycle();
                sharing.set(false);
            }
        });
    }
    private void launch(Context context, Intent send) {
        try { context.startActivity(Intent.createChooser(send, "Share selection").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Throwable e) { log("chooser failed: " + e); toast(context, "Could not open the share sheet"); }
    }
    private static boolean valid(RectF r) {
        return r != null && !r.isEmpty() && Float.isFinite(r.left) && Float.isFinite(r.top)
            && Float.isFinite(r.right) && Float.isFinite(r.bottom)
            && r.left >= -0.001f && r.top >= -0.001f && r.right <= 1.001f && r.bottom <= 1.001f;
    }
    private boolean translating(Object peer,View root) {
        try {if(map.translating(peer))return true;}
        catch(Throwable e) {log("translate-mode check failed: "+e);}
        return liveTranslateVisible(root);
    }
    private static boolean liveTranslateVisible(View view) {
        if(view.getVisibility()!=View.VISIBLE||!view.isShown())return false;
        String type=view.getClass().getName();
        if(type.startsWith("com.google.android.libraries.lens.view.")&&type.contains(".livetranslate."))return true;
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)if(liveTranslateVisible(group.getChildAt(i)))return true;
        }
        return false;
    }
    private static GradientDrawable pill(Context context,int color) {
        GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(color);drawable.setCornerRadius(dp(context,20));return drawable;
    }
    private static final class ShareIcon extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        ShareIcon(int color) {paint.setColor(color);paint.setStrokeWidth(1.8f);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);}
        @Override public void draw(Canvas canvas) {
            canvas.save();canvas.translate(getBounds().left,getBounds().top);
            canvas.scale(getBounds().width()/24f,getBounds().height()/24f);
            canvas.drawLine(8.2f,10.7f,15.8f,6.3f,paint);
            canvas.drawLine(8.2f,13.3f,15.8f,17.7f,paint);
            canvas.drawCircle(6,12,2.5f,paint);canvas.drawCircle(18,5,2.5f,paint);canvas.drawCircle(18,19,2.5f,paint);
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) {paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter) {paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity() {return PixelFormat.TRANSLUCENT;}
    }
    private static int clamp(int x, int low, int high) { return Math.max(low, Math.min(x, high)); }
    private static Object call(Object object, String method) { return XposedHelpers.callMethod(object, method); }
    private static int dp(Context c, int n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    private static void toast(Context c, String message) { Toast.makeText(c, message, Toast.LENGTH_LONG).show(); }
    private static void log(String message) { XposedBridge.log(PREFIX + message); }
}
