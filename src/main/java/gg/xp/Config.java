package gg.xp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class Config {

	private static final ObjectMapper mapper = new ObjectMapper();
	private static final Properties customProps = new Properties();
	private static final Logger log = LoggerFactory.getLogger(Config.class);

	static {
		try {
			customProps.load(Config.class.getResourceAsStream("/gear-planner.properties"));
		}
		catch (Throwable e) {
			log.error("Error loading properties", e);
		}
	}

	public <X> X getRequired(Class<X> type, String key) {
		String effectiveValue = System.getenv(key);
		if (effectiveValue == null) {
			effectiveValue = System.getProperty(key);
		}
		if (effectiveValue == null) {
			effectiveValue = customProps.getProperty(key);
		}
		if (effectiveValue == null) {
			throw new RuntimeException("Property not found: " + key);
		}
		return mapper.convertValue(effectiveValue, type);
	}
}
