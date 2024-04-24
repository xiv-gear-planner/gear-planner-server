package gg.xp.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import gg.xp.Server;
import gg.xp.util.ResponseUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

public class Share {
	public static final Logger log = LoggerFactory.getLogger(Share.class);
	private final Shortlink sl;
	private final ObjectMapper mapper;

	public static final URI base;

	static {
		try {
			base = new URI("/share/");
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	public Share(Server server, Shortlink sl, ObjectMapper mapper) {
		this.sl = sl;
		this.mapper = mapper;
		server.registerHandler(base.getPath(), this::getShare);
	}

	private static final String DEFAULT_NAME = "XivGear - FFXIV Gear Planner";
	private static final String DEFAULT_DESC = "XivGear is an advanced and easy-to-use FFXIV gear planner/set builder with built-in simulation support.";


	private void getShare(HttpExchange httpExchange) {
		String path = base.relativize(httpExchange.getRequestURI()).getPath().split("/")[0];
		String response = sl.getRaw(path);
		try {
			if (response == null) {
				// TODO: real 404 page
				httpExchange.sendResponseHeaders(HTTP_NOT_FOUND, -1);
			}
			else {
				JsonNode tree = mapper.readTree(response);
				String name = tree.at("/name").textValue();
				String desc = tree.at("/description").textValue();
				// TODO: make the truncate end it with an elipsis
				if (name == null) {
					name = DEFAULT_NAME;
				}
				else {
					name = StringUtils.abbreviate(name, "…", 40) + " - " + DEFAULT_NAME;
				}
				if (desc == null) {
					desc = DEFAULT_DESC;
				}
				else {
					desc = StringUtils.abbreviate(desc, "…", 200) + "\n\n" + DEFAULT_DESC;
				}
				// TODO: parameterize this
				String redir = "https://xivgear.app/#/sl/" + path;
				httpExchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
				ResponseUtils.doResponse(httpExchange, buildShareTemplate(name, desc, redir));
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	@Language("HTML")
	private static String buildShareTemplate(String title, String desc, String redirectUrl) {
		title = StringEscapeUtils.escapeHtml4(title);
		desc = StringEscapeUtils.escapeHtml4(desc);
		return
				"""
						<!DOCTYPE html>
						<html lang="en">
						<head>
						<meta charset="UTF-8">
						<meta property="og:site_name" content="XivGear"/>
						<meta property="og:type" content="website"/>
						<meta property="og:title" content=\"""" + title + """
						"/>
						<meta property="og:description" content=\"""" + desc + """
						"/>
						<meta property="og:url" content=\"""" + redirectUrl + """
						"/>
						<link rel="stylesheet" href="https://xivgear.app/style.css"/>
						<title>XivGear - FFXIV Gear Planner</title>
						<meta http-equiv="Refresh" content="0; url='""" + redirectUrl + """
						'" />
						</head>
						<body>
						<div id="content-area">
						<h1>Loading... </h1>
						</div>
						</body>
						</html>
						""";
	}

}
