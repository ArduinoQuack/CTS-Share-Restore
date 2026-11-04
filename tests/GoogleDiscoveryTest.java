package dev.dov.ctsshare;

import java.util.Map;

public final class GoogleDiscoveryTest {
    public static void main(String[] args) throws Exception {
        Map<String,String> classes=DexAnchors.discover(args[0]);
        for(String key:DexAnchors.KEYS)
            if(!classes.containsKey(key))throw new AssertionError("Missing Google anchor: "+key);
        System.out.println("Installed Google APK: all selection and geometry anchors discovered: PASS");
    }
}
