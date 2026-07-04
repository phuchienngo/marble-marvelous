# Manifest components and dependency consumer rules cover the release build.

# commons-suncalc references SpotBugs/FindBugs annotations that are compile-only
# and absent at runtime; they are safe to ignore.
-dontwarn edu.umd.cs.findbugs.annotations.**
