package gg.xp;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.picocontainer.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ThreadFactory;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

public class Server implements Startable {

	private static final Logger log = LoggerFactory.getLogger(Server.class);

	private static final ThreadFactory tf = new BasicThreadFactory.Builder()
			.wrappedFactory(Thread.ofVirtual().factory())
			.namingPattern("serve-%d")
			.daemon(true)
			.build();

	private final HttpServer server;

	public Server(Config config) {
		try {
			server = HttpServer.create(new InetSocketAddress(config.getRequired(Integer.class, "port")), 20);
			server.setExecutor(command -> tf.newThread(command).start());
			registerHandler("/", (request) -> {
				log.info("Not found: {}", request.getRequestURI());
				request.sendResponseHeaders(HTTP_NOT_FOUND, -1);
			});
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

	private static void before(HttpExchange exchange) {
		List<String> header = exchange.getRequestHeaders().get("CF-Connecting-IP");
		String ip;
		if (header == null || header.isEmpty()) {
			ip = exchange.getRemoteAddress().getAddress().getHostAddress();
		}
		else {
			ip = header.get(0);
		}
		MDC.put("ip", ip);
	}

	private static void after() {
		MDC.remove("ip");
	}


	public HttpContext registerHandler(String path, HttpHandler handler) {
		log.info("Registered handler: {}", path);
		return server.createContext(path, h -> {
			try {
				before(h);
				handler.handle(h);
			}
			catch (Throwable t) {
				log.error("Error in handler {}", path, t);
				h.sendResponseHeaders(HTTP_INTERNAL_ERROR, -1);
			}
			finally {
				after();
			}
		});
	}

}
