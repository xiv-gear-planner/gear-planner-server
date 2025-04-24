package gg.xp;

import com.fasterxml.jackson.databind.ObjectMapper;
import oracle.nosql.driver.values.StringValue;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoBuilder;
import org.picocontainer.lifecycle.StartableLifecycleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class Backup {
	private static final Logger log = LoggerFactory.getLogger(Backup.class);

	private final OracleNoSqlDb db;

	public Backup(OracleNoSqlDb db) {
		this.db = db;
	}

	private void doBackup(File outdir) {
		if (outdir.exists()) {
			if (!outdir.isDirectory()) {
				throw new IllegalArgumentException(outdir.getAbsolutePath() + " is not a directory");
			}
		}
		else {
			boolean dirsMade = outdir.mkdirs();
			if (!dirsMade) {
				throw new IllegalStateException("Could not create directory " + outdir.getAbsolutePath());
			}
		}
		log.info("Row count: {}", db.countRows());

		Path outPath = outdir.toPath();
		db.iterateAll((v) -> {
			UUID uuid = UUID.fromString(v.get("linkuuid").getString());
			Path out = outPath.resolve(uuid + ".json");
			try {
				Files.writeString(out, v.toJson(), StandardCharsets.UTF_8);
				// TODO: possibly redundant now that it has proper unit throttling
				Thread.sleep(10);
			}
			catch (IOException | InterruptedException e) {
				throw new RuntimeException(e);
			}
		});
	}

	public static void main(String[] args) {
		log.info("Backup starting");
		MutablePicoContainer pico = new PicoBuilder()
				.withCaching()
				.withLifecycle(StartableLifecycleStrategy.class)
				.withAutomatic()
				.build();
		Config config = new Config();
		pico.addComponent(config);
		pico.addComponent(OracleNoSqlDb.class);
		pico.addComponent(Backup.class);
		pico.addComponent(new ObjectMapper());
		try {
			Backup backup = pico.getComponent(Backup.class);
			backup.doBackup(new File("backup"));
		}
		catch (Throwable t) {
			log.error("Backup failure", t);
			System.exit(1);
		}

	}
}
