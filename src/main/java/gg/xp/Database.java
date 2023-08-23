package gg.xp;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface Database {
	@Nullable String getShortlink(UUID uuid);

	void putShortLink(UUID uuid, String payload);
}
