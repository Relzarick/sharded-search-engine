package redis;

import etl.TokenHasher;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenHasherTest {
    @Test
    void testConsistency() {
        String key = "consistent_routing_key";
        int buckets = 15;

        int expectedBucket = TokenHasher.hash(key, buckets);

        // Hashing the same key with the same bucket count must always yield the same result
        for (int i = 0; i < 100; i++)
            assertEquals(expectedBucket, TokenHasher.hash(key, buckets));
    }

    @Test
    void testBucketBounds() {
        int buckets = 5;

        // Test a large number of random keys to ensure they never fall outside [0, buckets - 1]
        for (int i = 0; i < 1000; i++) {
            String key = UUID.randomUUID().toString();
            int bucket = TokenHasher.hash(key, buckets);

            assertTrue(bucket >= 0 && bucket < buckets, "Bucket assignment " + bucket + " is out of bounds for " + buckets + " buckets");
        }
    }

    @Test
    void testJumpConsistencyContract() {
        String key = "stable_session_id";

        int currentBucket = TokenHasher.hash(key, 10);

        // Jump Consistent Hash Contract:
        // When the number of buckets increases, a key must either stay in its current bucket
        // OR move to the newly added bucket (newBuckets - 1). It must NEVER move to another old bucket.
        for (int newBuckets = 11; newBuckets <= 100; newBuckets++) {
            int newBucket = TokenHasher.hash(key, newBuckets);

            assertTrue(newBucket == currentBucket || newBucket == newBuckets - 1,
                    String.format("Key moved to invalid bucket. Was: %d, Now: %d (Buckets: %d)",
                            currentBucket, newBucket, newBuckets));

            currentBucket = newBucket; // Update tracking for the next iteration
        }
    }

    @Test
    void testSingleBucket() {
        // If there is only 1 bucket, everything must route to bucket 0
        assertEquals(0, TokenHasher.hash("user1", 1));
        assertEquals(0, TokenHasher.hash("user2", 1));
        assertEquals(0, TokenHasher.hash("", 1));
        assertEquals(0, TokenHasher.hash("a_much_longer_string_for_testing", 1));
    }

    @Test
    void testEmptyString() {
        int buckets = 10;
        int bucket = TokenHasher.hash("", buckets);

        // Empty strings should hash cleanly without throwing exceptions
        assertTrue(bucket >= 0 && bucket < buckets);
    }

    @Test
    void testDistribution() {
        int buckets = 10;
        Set<Integer> hitBuckets = new HashSet<>();

        // With 1000 distinct strings into 10 buckets, a good hashing algorithm
        // will naturally populate every single bucket.
        for (int i = 0; i < 1000; i++) {
            hitBuckets.add(TokenHasher.hash("user_id_" + i, buckets));
        }

        assertEquals(buckets, hitBuckets.size(), "The hash function failed to distribute keys across all available buckets");
    }

}