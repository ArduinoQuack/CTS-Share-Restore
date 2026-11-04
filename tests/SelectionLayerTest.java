package dev.dov.ctsshare;

public final class SelectionLayerTest {
    static final class Node {
        final Node parent;
        final boolean frame;
        Node(Node parent,boolean frame) {this.parent=parent;this.frame=frame;}
    }
    static final SelectionLayer.Tree<Node> TREE=new SelectionLayer.Tree<Node>() {
        public Node parent(Node node) {return node.parent;}
        public boolean supportsChildren(Node node) {return node.frame;}
    };
    public static void main(String[] args) {
        Node window=new Node(null,true);
        Node content=new Node(window,true);
        Node lens=new Node(content,true);
        Node customOverlay=new Node(lens,false);
        Node selection=new Node(customOverlay,false);
        SelectionLayer.Placement<Node> p=SelectionLayer.find(selection,window,TREE);
        if(p==null||p.parent!=lens||p.branch!=customOverlay)
            throw new AssertionError("Share escaped its native Lens layer");
        // Results in another content branch must remain outside the pill's layer.
        Node results=new Node(content,true);
        if(p.parent==results||p.parent==content||p.parent==window)
            throw new AssertionError("Share was attached above results");
        // A direct child of DecorView must not revive the broken root-level path.
        if(SelectionLayer.find(new Node(window,false),window,TREE)!=null)
            throw new AssertionError("Unsafe root-level fallback");
        // Recreating Lens must bind to the new layer, not a cached old parent.
        Node newLens=new Node(content,true);
        Node newSelection=new Node(newLens,false);
        if(SelectionLayer.find(newSelection,window,TREE).parent!=newLens)
            throw new AssertionError("Stale selection layer");
        // No compatible parent: hide instead of drawing over search results.
        Node unsupported=new Node(window,false);
        if(SelectionLayer.find(new Node(unsupported,false),window,TREE)!=null)
            throw new AssertionError("Unsupported hierarchy used the window root");
        System.out.println("Native layer containment, recreation and root rejection: PASS");
    }
}
