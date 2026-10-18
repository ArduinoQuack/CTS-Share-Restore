package dev.dov.ctsshare;

import java.lang.reflect.Field;

/** Android stubs throw on framework calls, modeling a pre-looper module load. */
public final class ConstructorTest {
    public static void main(String[] args) throws Exception {
        ShareHook module=new ShareHook();
        for (String name:new String[]{"main","worker"}) {
            Field field=ShareHook.class.getDeclaredField(name);
            field.setAccessible(true);
            if(field.get(module)!=null)throw new AssertionError(name+" initialized before Android startup");
        }
        System.out.println("Pre-looper module instantiation: PASS");
    }
}
