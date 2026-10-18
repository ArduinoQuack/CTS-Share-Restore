package dev.dov.ctsshare;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Rejects ambiguous shapes instead of guessing after Google updates. */
final class ClassMap {
    final Class<?> peerClass, textClass;
    final Field fragment, config, state, region, range, rect, manager, rendered, bitmap, text, textRange;
    final Field overlayContainer, overlayBridge, overlayController;
    final Method selection;
    private Field filter;
    private Method currentFilter;
    private Field regionViewPeer, regionDrawing, drawnRect;

    ClassMap(Map<String,String> names,ClassLoader loader) throws Exception {
        peerClass = loader.loadClass(names.get("peer"));
        Class<?> selectionClass = loader.loadClass(names.get("selection"));
        Class<?> regionClass = loader.loadClass(names.get("region"));
        Class<?> renderedClass = loader.loadClass(names.get("rendered"));
        Class<?> configClass = loader.loadClass(names.get("config"));
        textClass = loader.loadClass(names.get("text"));
        region = fieldOf(selectionClass,regionClass);
        rect = fieldOf(regionClass,RectF.class);
        bitmap = fieldOf(renderedClass,Bitmap.class);
        List<Field> rangeCandidates = new ArrayList<>();
        for (Field f: selectionClass.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && !f.getType().isPrimitive()
                && f.getType()!=String.class && f.getType()!=regionClass && !f.getType().isEnum()) rangeCandidates.add(f);
        }
        range=unique(rangeCandidates,"text range");
        text=fieldOf(textClass,String.class);
        textRange=fieldOf(textClass,range.getType());
        List<Field> states=new ArrayList<>(), fragments=new ArrayList<>(), configs=new ArrayList<>(), managers=new ArrayList<>();
        Method selectionMethod=null;
        for (Field f:peerClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            Class<?> type=f.getType();
            for (Method m:type.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) && m.getParameterTypes().length==0 && m.getReturnType()==selectionClass) {
                    states.add(f);selectionMethod=m;
                }
            }
            try { if (View.class.isAssignableFrom(type.getMethod("getView").getReturnType())) fragments.add(f); }
            catch (NoSuchMethodException ignored) { }
            if (type!=Object.class && type.isAssignableFrom(configClass)) configs.add(f);
            for (Field sub:type.getDeclaredFields()) {
                if (!Modifier.isStatic(sub.getModifiers()) && sub.getType()==renderedClass) managers.add(f);
            }
        }
        state=unique(states,"selection state"); selection=selectionMethod;selection.setAccessible(true);
        fragment=unique(fragments,"fragment"); config=unique(configs,"configuration");
        manager=unique(managers,"rendered image manager");rendered=fieldOf(manager.getType(),renderedClass);
        Class<?> overlayClass=loader.loadClass("com.google.android.libraries.lens.view.main.LensOverlayLayout");
        List<Field> containers=new ArrayList<>();
        for(Field f:overlayClass.getDeclaredFields()) {
            if(Modifier.isStatic(f.getModifiers())||f.getType().isPrimitive())continue;
            for(Field nested:f.getType().getDeclaredFields()) {
                if(!Modifier.isStatic(nested.getModifiers())&&nested.getType()==overlayClass)containers.add(f);
            }
        }
        overlayContainer=unique(containers,"overlay view peer");
        List<Field> bridges=new ArrayList<>();
        for(Field f:overlayContainer.getType().getDeclaredFields()) {
            if(Modifier.isStatic(f.getModifiers())||f.getType().isPrimitive())continue;
            for(Field nested:f.getType().getDeclaredFields()) {
                if(!Modifier.isStatic(nested.getModifiers())&&nested.getType()==peerClass)bridges.add(f);
            }
        }
        overlayBridge=unique(bridges,"overlay controller bridge");
        overlayController=fieldOf(overlayBridge.getType(),peerClass);
        try {
            Class<?> renderingClass=loader.loadClass(names.get("regionRendering"));
            Class<?> regionViewClass=loader.loadClass("com.google.android.libraries.lens.view.region.RegionView");
            List<Field> viewPeers=new ArrayList<>();
            for(Field f:regionViewClass.getDeclaredFields()) {
                if(Modifier.isStatic(f.getModifiers())||f.getType().isPrimitive())continue;
                for(Field nested:f.getType().getDeclaredFields())
                    if(!Modifier.isStatic(nested.getModifiers())&&nested.getType()==renderingClass)viewPeers.add(f);
            }
            regionViewPeer=unique(viewPeers,"region drawing peer");
            regionDrawing=fieldOf(regionViewPeer.getType(),renderingClass);
            drawnRect=fieldOf(renderingClass,RectF.class);
        } catch(Exception ignored) {regionViewPeer=null;regionDrawing=null;drawnRect=null;}
        // Optional refinement: failure to resolve a mode must not break working sharing.
        List<Field> filters=new ArrayList<>();
        List<Method> filterGetters=new ArrayList<>();
        for(Field f:peerClass.getDeclaredFields()) {
            if(Modifier.isStatic(f.getModifiers())||f.getType().isPrimitive())continue;
            for(Method m:f.getType().getDeclaredMethods()) {
                Class<?> result=m.getReturnType();
                if(Modifier.isStatic(m.getModifiers())||m.getParameterTypes().length!=0||!result.isEnum())continue;
                boolean auto=false,translate=false;
                for(Object value:result.getEnumConstants()) {
                    String name=((Enum<?>)value).name();
                    auto|=name.equals("AUTO_FILTER");translate|=name.equals("TRANSLATE");
                }
                if(auto&&translate) {filters.add(f);filterGetters.add(m);}
            }
        }
        if(filters.size()==1) {
            filter=filters.get(0);filter.setAccessible(true);
            currentFilter=filterGetters.get(0);currentFilter.setAccessible(true);
        }
    }
    private static Field fieldOf(Class<?> type,Class<?> value) throws Exception {
        List<Field> fields=new ArrayList<>();
        for (Field f:type.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers()) && f.getType()==value) fields.add(f);
        return unique(fields,type.getName()+" -> "+value.getName());
    }
    private static Field unique(List<Field> fields,String role) throws Exception {
        if(fields.size()!=1)throw new IllegalStateException(role+": "+fields.size()+" candidates");
        Field field=fields.get(0);field.setAccessible(true);return field;
    }
    Object selection(Object peer) throws Exception { return selection.invoke(state.get(peer)); }
    RectF crop(Object selection) throws Exception {
        Object value=region.get(selection);return value==null?null:new RectF((RectF)rect.get(value));
    }
    Bitmap image(Object peer) throws Exception {
        Object image=rendered.get(manager.get(peer));return image==null?null:(Bitmap)bitmap.get(image);
    }
    Object owner(View overlay) throws Exception {
        Object container=overlayContainer.get(overlay);
        Object bridge=container==null?null:overlayBridge.get(container);
        return bridge==null?null:overlayController.get(bridge);
    }
    boolean translating(Object peer) throws Exception {
        if(peer==null||filter==null||currentFilter==null)return false;
        Object mode=currentFilter.invoke(filter.get(peer));
        return mode instanceof Enum && ((Enum<?>)mode).name().equals("TRANSLATE");
    }
    RectF drawnSelectionInWindow(View view) throws Exception {
        if(regionViewPeer==null||!regionViewPeer.getDeclaringClass().isInstance(view))return null;
        Object drawingPeer=regionViewPeer.get(view);
        Object drawing=drawingPeer==null?null:regionDrawing.get(drawingPeer);
        if(drawing==null)return null;
        RectF bounds=new RectF((RectF)drawnRect.get(drawing));
        if(bounds.isEmpty()||!Float.isFinite(bounds.top)||!Float.isFinite(bounds.left)
            ||!Float.isFinite(bounds.right)||!Float.isFinite(bounds.bottom))return null;
        View current=view;
        while(current.getParent() instanceof View) {
            View parent=(View)current.getParent();
            current.getMatrix().mapRect(bounds);
            bounds.offset(current.getLeft()-parent.getScrollX(),current.getTop()-parent.getScrollY());
            current=parent;
        }
        int[] origin=new int[2];current.getLocationInWindow(origin);
        bounds.offset(origin[0],origin[1]);
        return bounds;
    }
}
