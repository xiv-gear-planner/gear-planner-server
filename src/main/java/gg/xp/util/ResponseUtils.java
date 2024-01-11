package gg.xp.util;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static java.net.HttpURLConnection.HTTP_OK;

public final class ResponseUtils {
	private ResponseUtils() {
	}

	public static void doResponse(HttpExchange httpExchange, String responseString) throws IOException {
		doResponse(httpExchange, responseString.getBytes(StandardCharsets.UTF_8));
	}

	public static void doResponse(HttpExchange httpExchange, byte[] responseBytes) throws IOException {
		httpExchange.sendResponseHeaders(HTTP_OK, responseBytes.length);
		OutputStream body = httpExchange.getResponseBody();
		body.write(responseBytes);
		body.close();
	}

}
