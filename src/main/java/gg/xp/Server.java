package gg.xp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.picocontainer.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_ENTITY_TOO_LARGE;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

public class Server implements Startable {

	private static final Logger log = LoggerFactory.getLogger(Server.class);

	private static final ThreadFactory tf = new BasicThreadFactory.Builder()
			.wrappedFactory(Thread.ofVirtual().factory())
			.namingPattern("serve-%d")
			.daemon(true)
			.build();
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

	private final HttpServer server;
	private final ObjectMapper mapper = new ObjectMapper();
	private final Database db;
	private final AtomicInteger errCount = new AtomicInteger(0);
	private final AtomicInteger getCount = new AtomicInteger(0);
	private final AtomicInteger postCount = new AtomicInteger(0);
	private final Instant startedAt = Instant.now();
	private final Cache cache;

	public Server(Config config, Database db, Cache cache) {
		this.db = db;
		this.cache = cache;
		try {
			// TODO: is https needed? public-facing https is handled by the load balancer
			server = HttpServer.create(new InetSocketAddress(config.getRequired(Integer.class, "port")), 20);
			server.setExecutor(command -> tf.newThread(command).start());
			server.createContext("/", (request) -> {
				request.sendResponseHeaders(HTTP_NOT_FOUND, -1);
			});
			server.createContext("/healthcheck", (request) -> {
				doResponse(request, "Health Check OK, uptime %s, GETs %s, POSTs %s, errors: %s".formatted(getUptime(), getCount.get(), postCount.get(), errCount.get()));
			});
			server.createContext(base.getPath(), this::handle);
			log.info("Server setup done");
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Duration getUptime() {
		return Duration.between(startedAt, Instant.now());
	}

	@Override
	public void start() {
		server.start();
		log.info("Server started on port {}", server.getAddress().getPort());
	}

	@Override
	public void stop() {
		server.stop(5);
		log.info("Server stopped");
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
			errCount.incrementAndGet();
			try {
				httpExchange.sendResponseHeaders(HTTP_INTERNAL_ERROR, -1);
			}
			catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	private void makeShortLink(HttpExchange httpExchange, JsonNode json) throws IOException {
		postCount.incrementAndGet();
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
		getCount.incrementAndGet();
		String path = base.relativize(httpExchange.getRequestURI()).getPath();
		UUID uuid = UUID.fromString(path);
		String result = cache.computeIfAbsent(uuid, db::getShortlink);
//		log.info("GET UUID: {}", );
		if (result == null) {
			log.info("404: {}", path);
			httpExchange.sendResponseHeaders(HTTP_NOT_FOUND, -1);
		}
		else {
			doResponse(httpExchange, result);
		}
	}

	private void doResponse(HttpExchange httpExchange, String responseString) throws IOException {
		doResponse(httpExchange, responseString.getBytes(StandardCharsets.UTF_8));
	}

	private void doResponse(HttpExchange httpExchange, byte[] responseBytes) throws IOException {
		httpExchange.sendResponseHeaders(HTTP_OK, responseBytes.length);
		OutputStream body = httpExchange.getResponseBody();
		body.write(responseBytes);
		body.close();
	}
}
