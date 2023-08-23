package gg.xp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.picocontainer.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;

import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_ENTITY_TOO_LARGE;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

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

	public Server(Config config, Database db) {
		this.db = db;
		try {
			// TODO: ssl
			server = HttpServer.create(new InetSocketAddress(config.getRequired(Integer.class, "port")), 20);
			server.setExecutor(command -> tf.newThread(command).start());
			server.createContext("/", (request) -> {
				request.sendResponseHeaders(HTTP_NOT_FOUND, -1);
			});
			server.createContext(base.getPath(), this::handle);
			log.info("Server setup done");
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
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
					byte[] bodyBytes = httpExchange.getRequestBody().readAllBytes();
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
			log.error("Error", e);
			try {
				httpExchange.sendResponseHeaders(HTTP_INTERNAL_ERROR, -1);
			}
			catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	private void makeShortLink(HttpExchange httpExchange, JsonNode json) throws IOException {
		UUID uuid = UUID.randomUUID();
		log.info("UUID: {}, data: {}", uuid, json);
		byte[] uuidBytes = uuid.toString().getBytes(StandardCharsets.UTF_8);
		db.putShortLink(uuid, json.toString());
		httpExchange.sendResponseHeaders(201, uuidBytes.length);
		OutputStream body = httpExchange.getResponseBody();
		body.write(uuidBytes);
		body.close();
	}

	private void retrieveShortLink(HttpExchange httpExchange) throws IOException {
		String path = base.relativize(httpExchange.getRequestURI()).getPath();
		String result = db.getShortlink(UUID.fromString(path));
		if (result == null) {
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
		httpExchange.sendResponseHeaders(200, responseBytes.length);
		OutputStream body = httpExchange.getResponseBody();
		body.write(responseBytes);
		body.close();
	}
//
//	private int getLocalPort() {
//		return server.getAddress().getPort();
//	}
//
//	private URL getUrl() {
//		// TODO: allow hostname to be overridden
//		try {
//			return new URL("http", "localhost", getLocalPort(), "/");
//		}
//		catch (MalformedURLException e) {
//			throw new RuntimeException(e);
//		}
//	}
}
