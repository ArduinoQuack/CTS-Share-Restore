package dev.dov.ctsshare;

/** Chooses the selection's containing layer, never the top-level window. */
final class SelectionLayer {
    interface Tree<T> {
        T parent(T node);
        boolean supportsChildren(T node);
    }
    static final class Placement<T> {
        final T parent,branch;
        Placement(T parent,T branch) {this.parent=parent;this.branch=branch;}
    }
    static <T> Placement<T> find(T selection,T window,Tree<T> tree) {
        T branch=selection;
        for(int depth=0;branch!=null&&branch!=window&&depth<64;depth++) {
            T parent=tree.parent(branch);
            if(parent==null||parent==window)return null;
            if(tree.supportsChildren(parent))return new Placement<>(parent,branch);
            branch=parent;
        }
        return null;
    }
}
