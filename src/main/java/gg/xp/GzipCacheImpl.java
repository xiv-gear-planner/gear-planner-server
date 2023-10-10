package gg.xp;

import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.IntSummaryStatistics;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipCacheImpl implements Cache, Startable {

	private static final Logger log = LoggerFactory.getLogger(GzipCacheImpl.class);
	private final Map<UUID, CacheEntry> map = new ConcurrentHashMap<>();
	private static final float WORST_COMPRESS_RATIO = 0.80f;
	private boolean stop;
	private final Thread pruner;


	public GzipCacheImpl(int maxCacheSize, int wakeupInterval) {
		pruner = Thread.ofVirtual().unstarted(() -> {
			while (!stop) {
				try {
					int sizeBefore = map.size();
					if (sizeBefore > maxCacheSize) {
						log.info("Pruning cache");
						IntSummaryStatistics lfuStats = map.values().stream().mapToInt(e -> e.usedCount).summaryStatistics();
						LongSummaryStatistics lruStats = map.values().stream().mapToLong(CacheEntry::lastUsed).filter(l -> l > 0).summaryStatistics();
						long lruCutoff = (long) lruStats.getAverage();
						int lfuCutoff = (int) lfuStats.getAverage();
						map.entrySet().removeIf(e -> {
							CacheEntry val = e.getValue();
							long lastUsed = val.lastUsed();
							return (lastUsed > 0 && lastUsed < lruCutoff) || val.usedCount < lfuCutoff;
						});
						int sizeAfter = map.size();
						log.info("Pruning cache pruned ({} => {})", sizeBefore, sizeAfter);
					}
				}
				catch (Throwable t) {
					log.error("Error pruning cache");
				}
				finally {
					try {
						Thread.sleep(wakeupInterval);
					}
					catch (InterruptedException e) {
						log.error("Interrupted!");
					}
				}
			}
		});
	}

	@Override
	public void start() {
		pruner.start();
	}

	@Override
	public void stop() {
		stop = true;
		pruner.interrupt();
	}

	private static final class CacheEntry {
		// it's okay if these lose some updates due to concurrency. Approximate is good enough.
		int usedCount;
		Instant lastUsed;
		final byte[] compressed;
		final String uncompressed;

		private CacheEntry(String value) {
			byte[] compressed = compressStringToBytes(value);
			// Only compress if actually worth it
			if (compressed.length < (value.length() * WORST_COMPRESS_RATIO)) {
				this.compressed = compressed;
				this.uncompressed = null;
			}
			else {
				this.compressed = null;
				this.uncompressed = value;
			}
			update();
		}

		private void update() {
			usedCount++;
			lastUsed = Instant.now();
		}

		private String getValue() {
			if (uncompressed != null) {
				return uncompressed;
			}
			else {
				return uncompressBytesToString(this.compressed);
			}
		}

		private int size() {
			if (uncompressed != null) {
				return uncompressed.length();
			}
			else {
				return this.compressed.length;
			}
		}

		private long lastUsed() {
			Instant lu = lastUsed;
			if (lu == null) {
				return 0;
			}
			else {
				return lu.toEpochMilli();
			}
		}
	}

	private static byte[] compressStringToBytes(String inStr) {
		byte[] inBytes = inStr.getBytes(StandardCharsets.UTF_8);
		byte[] outBytes;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (OutputStream gzip = new GZIPOutputStream(baos)) {
			gzip.write(inBytes);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		outBytes = baos.toByteArray();
		return outBytes;
	}

	private static String uncompressBytesToString(byte[] compressed) {
		ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
		try (InputStream gzip = new GZIPInputStream(bais)) {
			return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public @Nullable String computeIfAbsent(UUID key, Function<UUID, String> getter) {
		MutableObject<String> out = new MutableObject<>();
		map.compute(key, (uuid, current) -> {
			if (current == null) {
				String newValue = getter.apply(uuid);
				// This avoids having to re-compress
				out.setValue(newValue);
				// TODO: look into ways of having this not block the request
				return new CacheEntry(newValue);
			}
			else {
				current.update();
				out.setValue(current.getValue());
				return current;
			}
		});
		return out.getValue();
	}

	@Override
	public void set(UUID key, String value) {
		map.put(key, new CacheEntry(value));
	}

	@Override
	public int cacheSize() {
		return map.size();
	}

}
