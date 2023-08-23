package gg.xp;

import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoBuilder;
import org.picocontainer.lifecycle.StartableLifecycleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GearPlanServerMain {

	private static final Logger log = LoggerFactory.getLogger(GearPlanServerMain.class);

	public static void main(String[] args) {
		MutablePicoContainer pico = new PicoBuilder()
				.withCaching()
				.withLifecycle(StartableLifecycleStrategy.class)
				.build();
		Config config = new Config();
		pico.addComponent(config);
		pico.addComponent(Server.class);
		pico.addComponent(OracleNoSqlDb.class);
		try {
			pico.getComponent(Server.class);
			pico.start();
		} catch (Throwable t) {
			log.error("Startup failure", t);
			System.exit(1);
		}
	}
}