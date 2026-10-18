package dev.dov.ctsshare;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.Enumeration;
import java.util.Properties;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** Discovers class owners from semantic string constants, independent of R8 names. */
final class DexAnchors {
    static final String[] KEYS = {"peer", "selection", "rendered", "text", "config", "region", "regionRendering"};
    static final int REQUIRED = 6;
    static final String[] ANCHORS = {
        "Frozen image bitmap is null, deferring thumbnail update",
        "SelectionData(region=", "RenderedResultImage(image=",
        "TextSelection(selectedText=", "LensViewConfig{accountName=", "Region(rect=",
        "RegionRenderingData(region="
    };

    static Map<String,String> discoverCached(String apk,File cache) throws Exception {
        File source=new File(apk);
        String fingerprint=apk+":"+source.length()+":"+source.lastModified()+":rendered-geometry-v2";
        try (InputStream input=new FileInputStream(cache)) {
            Properties props=new Properties();props.load(input);
            if (fingerprint.equals(props.getProperty("source"))) {
                Map<String,String> result=new HashMap<>();
                for (int i=0;i<KEYS.length;i++) {
                    String key=KEYS[i];
                    String name=props.getProperty(key);
                    if(name==null&&i<REQUIRED)throw new IllegalStateException("Incomplete cached profile");
                    if(name!=null)result.put(key,name);
                }
                return result;
            }
        } catch (Exception ignored) { }
        Map<String,String> result=discover(apk);
        Properties props=new Properties();props.setProperty("source",fingerprint);props.putAll(result);
        File parent=cache.getParentFile();
        if (parent.isDirectory() || parent.mkdirs()) {
            File temp=File.createTempFile("cts-profile-",".tmp",parent);
            try (FileOutputStream output=new FileOutputStream(temp)) {props.store(output,"CTS Share semantic adapter cache");}
            temp.renameTo(cache);temp.delete();
        }
        return result;
    }

    static Map<String, String> discover(String apk) throws Exception {
        Map<String, Set<String>> hits = new HashMap<>();
        for (String key : KEYS) hits.put(key, new HashSet<>());
        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().matches("classes[0-9]*\\.dex")) continue;
                try (InputStream input = zip.getInputStream(entry)) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buf = new byte[65536]; int count;
                    while ((count = input.read(buf)) >= 0) output.write(buf, 0, count);
                    scan(output.toByteArray(), hits);
                }
            }
        }
        Map<String, String> result = new HashMap<>();
        for (int i=0;i<KEYS.length;i++) {
            String key=KEYS[i];
            Set<String> found = hits.get(key);
            if(i>=REQUIRED) {
                if(!found.isEmpty())result.put(key,String.join(",",found));
                continue;
            }
            if (found.size() != 1) throw new IllegalStateException("Anchor " + key + " matches: " + found);
            result.put(key, found.iterator().next());
        }
        return result;
    }

    private static void scan(byte[] b, Map<String, Set<String>> hits) throws Exception {
        if (b.length < 112 || b[0] != 'd' || b[1] != 'e' || b[2] != 'x') throw new IllegalArgumentException("Unsupported DEX");
        int stringsSize = u32(b, 56), stringsOff = u32(b, 60);
        String[] strings = new String[stringsSize];
        Map<Integer, String> targets = new HashMap<>();
        for (int i = 0; i < stringsSize; i++) {
            int[] cursor = {u32(b, stringsOff + 4*i)};
            leb(b, cursor); int end = cursor[0];
            while (end < b.length && b[end] != 0) end++;
            strings[i] = new String(b, cursor[0], end-cursor[0], "UTF-8");
            for (int j = 0; j < ANCHORS.length; j++) if (strings[i].startsWith(ANCHORS[j])) targets.put(i, KEYS[j]);
        }
        if (targets.isEmpty()) return;
        int typesOff = u32(b, 68), methodsOff = u32(b, 92);
        int classesSize = u32(b, 96), classesOff = u32(b, 100);
        for (int i = 0; i < classesSize; i++) {
            int data = u32(b, classesOff + 32*i + 24);
            if (data == 0) continue;
            int[] p = {data};
            int statics = leb(b,p), instances = leb(b,p), direct = leb(b,p), virtual = leb(b,p);
            for (int j = 0; j < statics + instances; j++) { leb(b,p); leb(b,p); }
            for (int count : new int[]{direct, virtual}) {
                int method = 0;
                for (int j = 0; j < count; j++) {
                    method += leb(b,p); leb(b,p); int code = leb(b,p);
                    if (code == 0) continue;
                    int units = u32(b, code+12), start = code+16;
                    for (int pc = 0; pc < units; ) {
                        int word = u16(b,start+pc*2), op = word & 255;
                        int string = -1;
                        if (op == 0x1a) string = u16(b,start+(pc+1)*2);
                        if (op == 0x1b) string = u32(b,start+(pc+1)*2);
                        String key = targets.get(string);
                        if (key != null) {
                            int type = u16(b,methodsOff+8*method);
                            String descriptor = strings[u32(b,typesOff+type*4)];
                            String name=descriptor.substring(1,descriptor.length()-1).replace('/','.');
                            if (!key.equals("region") || name.startsWith("com.google.android.libraries.lens.view.region.")) hits.get(key).add(name);
                        }
                        int width = width(b,start,pc,word);
                        if (width <= 0 || pc+width > units) throw new IllegalStateException("Invalid instruction boundary");
                        pc += width;
                    }
                }
            }
        }
    }
    private static int width(byte[] b, int start, int pc, int word) {
        int op = word & 255;
        if (op == 0) {
            int payload = word >>> 8;
            if (payload == 1) return 4 + 2*u16(b,start+(pc+1)*2);
            if (payload == 2) return 2 + 4*u16(b,start+(pc+1)*2);
            if (payload == 3) return 4 + (u16(b,start+(pc+1)*2)*u32(b,start+(pc+2)*2)+1)/2;
            return 1;
        }
        if (op == 0x18) return 5;
        if (op == 0xfa || op == 0xfb) return 4;
        if (op == 3 || op == 6 || op == 9 || op == 0x14 || op == 0x17 || op == 0x1b
            || (op >= 0x24 && op <= 0x26) || (op >= 0x2a && op <= 0x2c)
            || (op >= 0x6e && op <= 0x72) || (op >= 0x74 && op <= 0x78) || op == 0xfc || op == 0xfd) return 3;
        if (op == 2 || op == 5 || op == 8 || (op >= 0x13 && op <= 0x19)
            || op == 0x1a || op == 0x1c || op == 0x1f || op == 0x20 || op == 0x22 || op == 0x23
            || op == 0x29 || (op >= 0x2d && op <= 0x3d) || (op >= 0x44 && op <= 0x6d)
            || (op >= 0x90 && op <= 0xaf) || (op >= 0xd0 && op <= 0xe2) || op == 0xfe || op == 0xff) return 2;
        return 1;
    }
    private static int u16(byte[] b,int o) { return (b[o]&255)|((b[o+1]&255)<<8); }
    private static int u32(byte[] b,int o) { return u16(b,o)|(u16(b,o+2)<<16); }
    private static int leb(byte[] b,int[] p) {
        int v=0, shift=0, c;
        do { c=b[p[0]++]&255; v|=(c&127)<<shift; shift+=7; } while ((c&128)!=0);
        return v;
    }
    public static void main(String[] args) throws Exception { System.out.println(discover(args[0])); }
}
