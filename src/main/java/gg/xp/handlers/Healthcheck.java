package gg.xp.handlers;

import gg.xp.Cache;
import gg.xp.Server;
import gg.xp.Stats;

import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

import static gg.xp.util.ResponseUtils.doResponse;

public class Healthcheck {
	public Healthcheck(Server server, Stats stats, Cache cache) {
		server.registerHandler("/healthcheck", (request) -> {
			doResponse(
					request,
					"Health Check OK, uptime: %s, GETs: %s, POSTs: %s, errors: %s, cache entries: %s\n".formatted(stats.getUptime().truncatedTo(ChronoUnit.SECONDS), stats.getCount.get(), stats.postCount.get(), stats.errCount.get(), cache.cacheSize()),
					"text/plain; charset=utf-8"
			);
		});
	}
}
