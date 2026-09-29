# NewMyBook currently relies only on AndroidX consumer rules.

# Commons Compress is used only for ZIP metadata validation. Its optional XZ/Zstd
# adapters are unreachable and those codecs are intentionally not packaged.
-dontwarn com.github.luben.zstd.ZstdInputStream
-dontwarn org.tukaani.xz.MemoryLimitException
-dontwarn org.tukaani.xz.SingleXZInputStream
-dontwarn org.tukaani.xz.XZInputStream
