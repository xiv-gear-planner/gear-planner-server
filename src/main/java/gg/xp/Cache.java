package gg.xp;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;

public interface Cache {

	@Nullable String computeIfAbsent(UUID key, Function<UUID, @Nullable String> getter);

	void set(UUID key, @NotNull String value);

	int cacheSize();
}
