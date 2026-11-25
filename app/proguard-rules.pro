# R8 shrinks and optimizes release builds. The libraries ship their own keep rules
# (Room, coroutines, Compose), and the app itself uses no reflection.

# Keep file names and line numbers so crash reports point at the right code.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
