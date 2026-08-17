package etl;

// AI magic algo
public class TokenHasher {
    private static final double JUMP_CONSTANT = 2147483648.0d; // (double) (1L << 31)

    // FNV-1a 64-bit constants
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    public static int hash(String key, int len) {
        long keyHash = fastHash64(key);
        return jumpConsistentHash(keyHash, len);
    }

    private static int jumpConsistentHash(long key, int numBuckets) {
        long b = -1;
        long j = 0;

        while (j < numBuckets) {
            b = j;
            key = key * 2862933555777941757L + 1L;
            j = (long) ((b + 1) * JUMP_CONSTANT / (double) ((key >>> 33) + 1));
        }
        return (int) b;
    }

    private static long fastHash64(String text) {
        long hash = FNV_OFFSET_BASIS;

        for (int i = 0, len = text.length(); i < len; i++) {
            hash ^= text.charAt(i);
            hash *= FNV_PRIME;
        }

        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= hash >>> 33;

        return hash;
    }

}