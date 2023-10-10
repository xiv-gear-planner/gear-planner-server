package gg.xp;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;

public interface Cache {

	@Nullable String computeIfAbsent(UUID key, Function<UUID, String> getter);

	void set(UUID key, String value);

	int cacheSize();
}
