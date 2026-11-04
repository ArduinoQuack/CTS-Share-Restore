package dev.dov.ctsshare;

public final class ActionPlacementTest {
    public static void main(String[] args) {
        // Supplied screenshot: action should sit directly above Clock/Contacts.
        check(new float[]{127,416},ActionPlacement.above(83,532,351,779,180,96,0,0,739,1536,20,16));
        // Moving the selected region down moves Share down by the same amount.
        check(new float[]{127,616},ActionPlacement.above(83,732,351,979,180,96,0,0,739,1536,20,16));
        // Reported 0.6 overlap: use the visible expanded handles, not the smaller crop.
        float[] expanded=ActionPlacement.above(224,508,368,684,184,92,0,0,739,1536,23,16);
        check(new float[]{204,393},expanded);
        if(expanded[1]+92>=508)throw new AssertionError("Share overlaps the visible selection");
        // Small selections close to both horizontal edges stay inside the viewport.
        check(new float[]{16,284},ActionPlacement.above(0,400,30,450,180,96,0,24,739,1536,20,16));
        check(new float[]{543,284},ActionPlacement.above(709,400,739,450,180,96,0,24,739,1536,20,16));
        // At the top edge use the space below the selection, respecting the status inset.
        check(new float[]{210,170},ActionPlacement.above(200,30,400,150,180,96,0,24,739,1536,20,16));
        // Landscape and a restricted visible window still use window-local coordinates.
        check(new float[]{210,174},ActionPlacement.above(200,250,400,400,180,56,0,0,1536,650,20,16));
        // Standalone selection from the report stays behind a rising results panel.
        for(int sheetTop=1536;sheetTop>=0;sheetTop-=8) {
            boolean covered=ActionPlacement.overlaps(180,400,340,560,0,sheetTop,739,1536);
            if(covered!=(sheetTop<560))throw new AssertionError("Wrong visibility while dragging sheet: "+sheetTop);
            if(!covered) {
                float[] p=ActionPlacement.above(180,400,340,560,96,48,0,0,739,sheetTop,12,8);
                if(!ActionPlacement.fits(p[0],p[1],96,48,0,0,739,sheetTop))
                    throw new AssertionError("Action outside available space");
                if(ActionPlacement.overlaps(p[0],p[1],p[0]+96,p[1]+48,0,sheetTop,739,1536))
                    throw new AssertionError("Action overlays results");
            }
        }
        // A top-edge fallback cannot be clamped into a fully expanded panel.
        float[] blocked=ActionPlacement.above(200,5,400,30,96,48,0,0,739,40,12,8);
        if(ActionPlacement.fits(blocked[0],blocked[1],96,48,0,0,739,40))
            throw new AssertionError("Expanded sheet left no space for Share");
        // A side panel which does not intersect the selection must not hide it.
        if(ActionPlacement.overlaps(20,400,120,560,370,0,739,1536))
            throw new AssertionError("Disjoint side panel hides selection");
        System.out.println("Placement, panel drag, full expansion and viewport edges: PASS");
    }
    private static void check(float[] expected,float[] actual) {
        for(int i=0;i<2;i++)if(Math.abs(expected[i]-actual[i])>0.01f)
            throw new AssertionError("coordinate "+i+": expected "+expected[i]+", actual "+actual[i]);
    }
}

