package dev.dov.ctsshare;

import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Joins Google's existing action row without replacing its native actions. */
final class SelectionUi {
    final TextView button;
    final TextView standaloneButton;
    private final FrameLayout host;
    private final View overlay;
    private final ClassMap map;
    private final LinearLayout standalone;
    private final int rowId;
    private final ViewTreeObserver.OnPreDrawListener preDraw;
    private boolean disposed;

    SelectionUi(FrameLayout host,View overlay,ClassMap map,Runnable refresh) {
        this.host=host;this.overlay=overlay;this.map=map;
        rowId=id("lens_action_menu_buttons","id");
        View nativeRow=rowId==0?null:host.findViewById(rowId);
        android.content.Context context=nativeRow==null?overlay.getContext():nativeRow.getContext();
        standalone=new LinearLayout(context);
        standalone.setGravity(Gravity.CENTER_VERTICAL);
        standalone.setOrientation(LinearLayout.HORIZONTAL);
        standalone.setVisibility(View.GONE);
        TextView inflated=null;
        try {
            int layout=id("lens_action_menu_item","layout");
            if(layout!=0) {
                View item=LayoutInflater.from(context).inflate(layout,standalone,false);
                if(item instanceof TextView)inflated=(TextView)item;
            }
        } catch(Throwable ignored) { }
        button=inflated==null?new TextView(context):inflated;
        button.setTag("CTSShareRestore");
        button.setText("Share");
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription("Share Circle to Search selection");
        button.setVisibility(View.GONE);
        // A separate TextView avoids MaterialButton's narrow minimum-width and inset background.
        // Keep the native inflated item unchanged for the working grouped menu.
        standaloneButton=new TextView(context);
        standaloneButton.setTag("CTSShareRestore");
        standaloneButton.setText("Share");
        standaloneButton.setSingleLine(true);
        standaloneButton.setGravity(Gravity.CENTER);
        standaloneButton.setContentDescription("Share Circle to Search selection");
        standaloneButton.setTextSize(14);
        standaloneButton.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        standaloneButton.setTextColor(button.getTextColors());
        standaloneButton.setPadding(dp(24),0,dp(24),0);
        standaloneButton.setMinWidth(dp(96));
        standaloneButton.setMinHeight(dp(48));
        standaloneButton.setClickable(true);
        standaloneButton.setFocusable(true);
        int background=id("lens_action_menu_background","drawable");
        Drawable nativeBackground=nativeRow==null?null:nativeRow.getBackground();
        if(nativeBackground!=null&&nativeBackground.getConstantState()!=null) {
            setStandaloneBackground(nativeBackground);
        } else if(background!=0)try {
            // Theme-aware loading on the actual device; our compile-only stub predates API 21.
            Drawable drawable=(Drawable)android.content.Context.class.getMethod("getDrawable",int.class).invoke(context,background);
            setStandaloneBackground(drawable);
        } catch(Throwable ignored) { }
        if(standaloneButton.getBackground()==null)setStandaloneBackground(null);
        standalone.addView(standaloneButton,new LinearLayout.LayoutParams(-2,dp(48)));
        host.addView(standalone,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT));
        preDraw=()->{if(!disposed)refresh.run();return true;};
        host.getViewTreeObserver().addOnPreDrawListener(preDraw);
    }

    boolean nativeStyle() { return button instanceof android.widget.Button; }

    void update(Object peer,boolean translating) {
        if(disposed)return;
        if(translating||peer==null) { hide();return; }
        View candidate=rowId==0?null:host.findViewById(rowId);
        ViewGroup row=candidate instanceof LinearLayout&&((LinearLayout)candidate).getOrientation()==LinearLayout.HORIZONTAL
            ?(ViewGroup)candidate:null;
        TextView reference=row==null?null:visibleAction(row);
        if(row!=null&&row.isShown()&&row.getAlpha()>0.01f&&reference!=null) {
            copyTextStyle(reference);
            // Google uses the final child for overflow. Keep that invariant intact.
            if(button.getParent()!=row) {
                removeButton();
                row.addView(button,Math.max(0,row.getChildCount()-1),new LinearLayout.LayoutParams(-2,
                    reference.getHeight()>0?reference.getHeight():-2));
            }
            standalone.setVisibility(View.GONE);
            button.setVisibility(View.VISIBLE);
            return;
        }
        RectF bounds=drawnBounds(overlay);
        if(bounds==null||bounds.isEmpty()) {hide();return;}
        // RegionRenderingData is the rectangle used by RegionView.onDraw, including
        // minimum-size expansion and animation. Sharing still uses the original crop.
        button.setVisibility(View.GONE);
        if(button.getParent()!=null)removeButton();
        standalone.measure(View.MeasureSpec.makeMeasureSpec(host.getWidth(),View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(host.getHeight(),View.MeasureSpec.AT_MOST));
        int width=standalone.getMeasuredWidth(),height=standalone.getMeasuredHeight();
        if(width<=0||height<=0) {hide();return;}
        int[] origin=new int[2];host.getLocationInWindow(origin);
        int[] screen=new int[2];host.getLocationOnScreen(screen);
        Rect visible=new Rect();host.getWindowVisibleDisplayFrame(visible);
        // Android reports the visible frame in screen coordinates, the selection in window coordinates.
        float left=Math.max(0,visible.left-screen[0]),top=Math.max(0,visible.top-screen[1]);
        float right=Math.min(host.getWidth(),visible.right-screen[0]);
        float bottom=Math.min(host.getHeight(),visible.bottom-screen[1]);
        float density=host.getResources().getDisplayMetrics().density;
        float[] position=ActionPlacement.above(bounds.left-origin[0],bounds.top-origin[1],
            bounds.right-origin[0],bounds.bottom-origin[1],width,height,
            left,top,right,bottom,12*density,8*density);
        // Position against the actual laid-out origin, including DecorView padding.
        standalone.setTranslationX(position[0]-standalone.getLeft());
        standalone.setTranslationY(position[1]-standalone.getTop());
        standalone.setVisibility(View.VISIBLE);standalone.bringToFront();
    }

    private TextView visibleAction(ViewGroup row) {
        for(int i=0;i<row.getChildCount();i++) {
            View child=row.getChildAt(i);
            if(child!=button&&child instanceof TextView&&child.getVisibility()==View.VISIBLE
                &&((TextView)child).getText().length()>0)return (TextView)child;
        }
        return null;
    }
    private RectF drawnBounds(View view) {
        if(view.getVisibility()!=View.VISIBLE||!view.isShown()||view.getAlpha()<=0.01f)return null;
        if(view.getClass().getName().equals("com.google.android.libraries.lens.view.region.RegionView"))
            try {return map.drawnSelectionInWindow(view);}catch(Throwable ignored) {return null;}
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) {
                RectF bounds=drawnBounds(group.getChildAt(i));if(bounds!=null)return bounds;
            }
        }
        return null;
    }
    private void copyTextStyle(TextView reference) {
        if(button.getTextSize()!=reference.getTextSize())
            button.setTextSize(TypedValue.COMPLEX_UNIT_PX,reference.getTextSize());
        if(button.getTypeface()!=reference.getTypeface())button.setTypeface(reference.getTypeface());
        if(button.getTextColors()!=reference.getTextColors())button.setTextColor(reference.getTextColors());
        if(standaloneButton.getTextSize()!=reference.getTextSize())
            standaloneButton.setTextSize(TypedValue.COMPLEX_UNIT_PX,reference.getTextSize());
        if(standaloneButton.getTypeface()!=reference.getTypeface())standaloneButton.setTypeface(reference.getTypeface());
        if(standaloneButton.getTextColors()!=reference.getTextColors())standaloneButton.setTextColor(reference.getTextColors());
        int height=reference.getHeight();
        if(height>0&&button.getLayoutParams().height!=height) {
            ViewGroup.LayoutParams params=button.getLayoutParams();params.height=height;button.setLayoutParams(params);
        }
        Drawable background=((View)reference.getParent()).getBackground();
        if(background!=null&&standalone.getTag()!=background)setStandaloneBackground(background);
    }
    private void setStandaloneBackground(Drawable nativeBackground) {
        boolean dark=(host.getResources().getConfiguration().uiMode&0x30)==0x20;
        Drawable normal=nativeBackground!=null&&nativeBackground.getConstantState()!=null
            ?nativeBackground.getConstantState().newDrawable().mutate():rounded(dark?0xff202124:0xffffffff);
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},rounded(dark?0xff303134:0xffe8eaed));
        states.addState(new int[]{},normal);
        standaloneButton.setBackground(states);
        standalone.setTag(nativeBackground);
    }
    private Drawable rounded(int color) {
        GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(24));return drawable;
    }
    private int dp(int value) {return Math.round(value*host.getResources().getDisplayMetrics().density);}
    private void hide() {button.setVisibility(View.GONE);standalone.setVisibility(View.GONE);}
    private void removeButton() {
        ViewGroup parent=(ViewGroup)button.getParent();if(parent!=null)parent.removeView(button);
    }
    void dispose() {
        if(disposed)return;disposed=true;
        ViewTreeObserver observer=host.getViewTreeObserver();
        if(observer.isAlive())observer.removeOnPreDrawListener(preDraw);
        removeButton();host.removeView(standalone);
    }
    private int id(String name,String type) {
        return host.getResources().getIdentifier(name,type,"com.google.android.googlequicksearchbox");
    }
}
