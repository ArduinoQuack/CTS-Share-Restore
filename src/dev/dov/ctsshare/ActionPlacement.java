package dev.dov.ctsshare;

/** Window-local placement, also usable without Android in geometry regression checks. */
final class ActionPlacement {
    static boolean overlaps(float left,float top,float right,float bottom,
        float otherLeft,float otherTop,float otherRight,float otherBottom) {
        return left<otherRight&&right>otherLeft&&top<otherBottom&&bottom>otherTop;
    }
    static boolean fits(float x,float y,float width,float height,
        float left,float top,float right,float bottom) {
        return x>=left&&y>=top&&x+width<=right&&y+height<=bottom;
    }
    static float[] above(float left,float top,float right,float bottom,float width,float height,
        float viewportLeft,float viewportTop,float viewportRight,float viewportBottom,float gap,float margin) {
        float x=clamp((left+right-width)/2,viewportLeft+margin,viewportRight-margin-width);
        float y=top-gap-height;
        if(y<viewportTop+margin)y=bottom+gap;
        y=clamp(y,viewportTop+margin,viewportBottom-margin-height);
        return new float[]{x,y};
    }
    private static float clamp(float value,float low,float high) {
        return Math.max(low,Math.min(value,Math.max(low,high)));
    }
}

