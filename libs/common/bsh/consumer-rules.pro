-keep class bsh.This { *; }
-keep class bsh.This$* { *; }
-keep class bsh.Primitive { *; }
-keep class bsh.Primitive$* { *; }
-keep class bsh.commands.** {
    public static *** invoke(...);
}
-keepclassmembers class bsh.BshLambda {
    public final *** invoke(...);
}

-keep class bsh.BSH** { *; }
-keep class bsh.SimpleNode { *; }
-keep class bsh.Node { *; }
-keep class bsh.Modifiers { *; }
-keep class bsh.snapshot.BshSnapshot { *; }
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
