import gg.xp.GzipCacheImpl;
import org.apache.commons.lang3.StringUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class CacheTest {

	/**
	 * Test that the cache will be pruned above a certain size after waiting
	 *
	 * @throws InterruptedException
	 */
	@Test
	void testCachePruning() throws InterruptedException {
		GzipCacheImpl cache = new GzipCacheImpl(100, 1000);
		cache.start();
		AtomicInteger counter = new AtomicInteger();
		Function<UUID, String> conv = uuid -> {
			counter.incrementAndGet();
			return uuid.toString();
		};
		// One specific instance we can use to track evictions
		UUID first = new UUID(0, 0);
		String firstResultBefore = cache.computeIfAbsent(first, conv);
		// Put a total of 75 entries into the cache
		for (int i = 1; i < 75; i++) {
			UUID uuid = new UUID(0, i);
			cache.computeIfAbsent(uuid, conv);
		}
		Assert.assertEquals(cache.cacheSize(), 75);
		Assert.assertEquals(counter.get(), 75);
		// Put 75 more into the cache. It is now overfull.
		for (int i = 0; i < 75; i++) {
			UUID uuid = new UUID(1, i);
			cache.computeIfAbsent(uuid, conv);
		}
		String firstResultMid = cache.computeIfAbsent(first, conv);
		// This should be 150, rather than 151, because the above calc is cached.
		Assert.assertEquals(counter.get(), 150);
		// Wait a bit so LRU can do its thing
		Thread.sleep(50);
		for (int i = 0; i < 75; i++) {
			UUID uuid = new UUID(1, i);
			// Also access thrice so that LFU can also do its thing
			cache.computeIfAbsent(uuid, conv);
		}
		// Still 150
		Assert.assertEquals(counter.get(), 150);
		// However, it does not get pruned until the right time.
		Assert.assertEquals(cache.cacheSize(), 150);
		Thread.sleep(1100);
		// Now it should be pruned
		Assert.assertEquals(cache.cacheSize(), 75);

		String firstResultAfter = cache.computeIfAbsent(first, conv);
		// Value should be same
		Assert.assertEquals(firstResultBefore, firstResultMid);
		Assert.assertEquals(firstResultBefore, firstResultAfter);
		// Instance should be different for all 3. It's different at mid because we're pulling a value from cache.
		// It's different at end because the cache has been evicted at that point.
		Assert.assertNotSame(firstResultBefore, firstResultMid);
		Assert.assertNotSame(firstResultBefore, firstResultAfter);
		Assert.assertNotSame(firstResultMid, firstResultAfter);

	}

	/**
	 * Test that the cache will not be pruned if it does not hit the size threshold
	 *
	 * @throws InterruptedException
	 */
	@Test
	void testCacheNoPrune() throws InterruptedException {
		GzipCacheImpl cache = new GzipCacheImpl(100, 1000);
		cache.start();
		AtomicInteger counter = new AtomicInteger();
		Function<UUID, String> conv = uuid -> {
			counter.incrementAndGet();
			return uuid.toString();
		};
		for (int i = 0; i < 75; i++) {
			UUID uuid = new UUID(0, i);
			cache.computeIfAbsent(uuid, conv);
		}
		Assert.assertEquals(cache.cacheSize(), 75);
		Assert.assertEquals(counter.get(), 75);
		Thread.sleep(1100);
		Assert.assertEquals(cache.cacheSize(), 75);
		for (int i = 0; i < 75; i++) {
			UUID uuid = new UUID(0, i);
			cache.computeIfAbsent(uuid, conv);
		}
		Assert.assertEquals(counter.get(), 75);
	}


	@Test
	void testCacheAfterPassiveSet() {
		GzipCacheImpl cache = new GzipCacheImpl(100, 1000);
		AtomicInteger counter = new AtomicInteger();
		Function<UUID, String> conv = uuid -> {
			counter.incrementAndGet();
			return uuid.toString();
		};
		String s1 = cache.computeIfAbsent(new UUID(1234567890, 1234567890), conv);
		Assert.assertEquals(counter.get(), 1);
		String s2 = cache.computeIfAbsent(new UUID(1234567890, 1234567890), conv);
		Assert.assertEquals(counter.get(), 1);
		Assert.assertEquals(s1, s2);
		// Made it non-compressible on purpose so this should work
		Assert.assertSame(s2, s1);
	}

	@Test
	void testCacheAfterActiveSet() {
		GzipCacheImpl cache = new GzipCacheImpl(100, 1000);
		AtomicInteger counter = new AtomicInteger();
		Function<UUID, String> conv = uuid -> {
			counter.incrementAndGet();
			return uuid.toString();
		};
		String val = "foo";
		cache.set(new UUID(0, 0), val);
		Assert.assertEquals(counter.get(), 0);
		String s2 = cache.computeIfAbsent(new UUID(0, 0), conv);
		Assert.assertEquals(counter.get(), 0);
		Assert.assertEquals(s2, val);
		// 'foo' isn't long enough to compress, so don't
		Assert.assertSame(s2, val);
	}

	/**
	 * Test that large entries are compressed
	 */
	@Test
	void testCacheCompression() {
		String input = StringUtils.repeat('a', 20_000);
		GzipCacheImpl cache = new GzipCacheImpl(100, 1000);
		UUID uuid = new UUID(0, 0);
		AtomicInteger counter = new AtomicInteger();
		Function<UUID, String> func = ignored -> {
			counter.incrementAndGet();
			return input;
		};
		String result1 = cache.computeIfAbsent(uuid, func);
		// The first time, the result should be returned as-is, and the counter should be increased
		Assert.assertEquals(counter.get(), 1);
		Assert.assertEquals(result1, input);
		Assert.assertSame(result1, input);

		// This should have been compressed and decompressed. Thus, the result should be an identical string, but not
		// the exact same string instance.
		String result2 = cache.computeIfAbsent(uuid, func);
		Assert.assertEquals(counter.get(), 1);
		Assert.assertEquals(result2, input);
		Assert.assertNotSame(result2, input);
	}

	/**
	 * Test that small entries are not compressed
	 */
	@Test
	void testCacheCompressionBypass() {
		// Short string, not worth compressing
		String input = "foo";
		GzipCacheImpl cache = new GzipCacheImpl(100, 1000);
		UUID uuid = new UUID(0, 0);
		String result = cache.computeIfAbsent(uuid, ignored -> input);
		// This should not get compressed. It should be the exact same string instance.
		Assert.assertEquals(result, input);
		Assert.assertSame(result, input);
	}

}
