# The app has no reflection-based production dependencies. Keep rules intentionally
# narrow so R8 can optimize and obfuscate the complete release application.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
