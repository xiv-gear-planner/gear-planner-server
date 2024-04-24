package gg.xp.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import gg.xp.Cache;
import gg.xp.Database;
import gg.xp.Server;
import gg.xp.Stats;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static gg.xp.util.ResponseUtils.doResponse;
import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_ENTITY_TOO_LARGE;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

public class Shortlink {

	private static final Logger log = LoggerFactory.getLogger(Shortlink.class);
	private final Database db;
	private final Cache cache;
	private final Stats stats;
	private final ObjectMapper mapper;

	public Shortlink(Server server, Database db, Cache cache, Stats stats, ObjectMapper mapper) {
		this.db = db;
		this.cache = cache;
		this.stats = stats;
		this.mapper = mapper;
		server.registerHandler(base.getPath(), this::handle);

	}

	public static final int MAX_SHORTLINK_BYTES = 50_000;
	public static final URI base;

	static {
		try {
			base = new URI("/shortlink/");
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	private void handle(HttpExchange httpExchange) {
		httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
		try {
			switch (httpExchange.getRequestMethod()) {
				case "POST" -> {
					String clStr = httpExchange.getRequestHeaders().getFirst("Content-Length");
					try {
						if (clStr != null && Integer.parseInt(clStr) > MAX_SHORTLINK_BYTES) {
							httpExchange.sendResponseHeaders(HTTP_ENTITY_TOO_LARGE, -1);
							return;
						}
					}
					catch (NumberFormatException ignored) {
					}
					InputStream body = httpExchange.getRequestBody();
					byte[] bodyBytes = body.readNBytes(MAX_SHORTLINK_BYTES + 1000);
					body.close();
					if (bodyBytes.length > MAX_SHORTLINK_BYTES) {
						httpExchange.sendResponseHeaders(HTTP_ENTITY_TOO_LARGE, -1);
					}
					else {
						JsonNode json = mapper.readTree(bodyBytes);
						makeShortLink(httpExchange, json);
					}
				}
				case "GET" -> {
					retrieveShortLink(httpExchange);
				}
				default -> httpExchange.sendResponseHeaders(HTTP_BAD_METHOD, -1);
			}
		}
		catch (Throwable e) {
			log.error("Error on {}", httpExchange.getRequestURI(), e);
			stats.errCount.incrementAndGet();
			try {
				httpExchange.sendResponseHeaders(HTTP_INTERNAL_ERROR, -1);
			}
			catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	private void makeShortLink(HttpExchange httpExchange, JsonNode json) throws IOException {
		stats.postCount.incrementAndGet();
		UUID uuid = UUID.randomUUID();
		byte[] uuidBytes = uuid.toString().getBytes(StandardCharsets.UTF_8);
		String stringed = json.toString();
		log.info("CREATED UUID: {}, data: {}", uuid, StringUtils.truncate(stringed, 100));
		db.putShortLink(uuid, stringed);
		cache.set(uuid, stringed);
		httpExchange.sendResponseHeaders(201, uuidBytes.length);
		OutputStream body = httpExchange.getResponseBody();
		body.write(uuidBytes);
		body.close();
	}

	private void retrieveShortLink(HttpExchange httpExchange) throws IOException {
		stats.getCount.incrementAndGet();
		String path = base.relativize(httpExchange.getRequestURI()).getPath().split("/")[0];
		UUID uuid = UUID.fromString(path);
		String result = getRaw(uuid);
//		log.info("GET UUID: {}", );
		if (result == null) {
			httpExchange.sendResponseHeaders(HTTP_NOT_FOUND, -1);
		}
		else {
			doResponse(httpExchange, result);
		}
	}

	public @Nullable String getRaw(UUID uuid) {
		String result = cache.computeIfAbsent(uuid, db::getShortlink);
		if (result == null) {
			log.info("UUID not found: {}", uuid);
		}
		return result;
	}

	public @Nullable String getRaw(String uuidRaw) {
		UUID uuid = UUID.fromString(uuidRaw);
		return getRaw(uuid);
	}
}
