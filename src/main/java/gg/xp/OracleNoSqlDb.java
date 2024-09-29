package gg.xp;

import oracle.nosql.driver.NoSQLHandle;
import oracle.nosql.driver.NoSQLHandleConfig;
import oracle.nosql.driver.NoSQLHandleFactory;
import oracle.nosql.driver.iam.SignatureProvider;
import oracle.nosql.driver.ops.GetRequest;
import oracle.nosql.driver.ops.GetResult;
import oracle.nosql.driver.ops.PutRequest;
import oracle.nosql.driver.ops.PutResult;
import oracle.nosql.driver.ops.QueryRequest;
import oracle.nosql.driver.values.JsonOptions;
import oracle.nosql.driver.values.MapValue;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.UUID;

public class OracleNoSqlDb implements Database {


	private static final Logger log = LoggerFactory.getLogger(OracleNoSqlDb.class);
	private final NoSQLHandle handle;
	private static final String tableName = "shortlinks";

	public OracleNoSqlDb(Config config) {
		// TODO: add timestamps and IP
		// CREATE TABLE shortlinks ( linkuuid string, datavalue json, hash long DEFAULT -1, PRIMARY KEY ( linkuuid ) )
		String endpoint = config.getRequired(String.class, "endpoint");
		String tenant = config.getRequired(String.class, "tenant");
		String user = config.getRequired(String.class, "user");
		String fingerprint = config.getRequired(String.class, "fingerprint");
		File pemPath = config.getRequired(File.class, "pemPath");
		SignatureProvider authProvider = new SignatureProvider(tenant, user, fingerprint, pemPath, null);
		NoSQLHandleConfig handleConfig = new NoSQLHandleConfig(endpoint)
				.setRequestTimeout(5_000)
				.setAuthorizationProvider(authProvider)
				.configureDefaultRetryHandler(5, 1_000);
		handle = NoSQLHandleFactory.createNoSQLHandle(handleConfig);
	}

	public static void main(String[] args) throws InterruptedException {
		OracleNoSqlDb oracleNoSqlDb = new OracleNoSqlDb(new Config());
		oracleNoSqlDb.countRows();
		// Test the throttling
		while (true) {
			Thread.ofVirtual().start(() -> {

				try {
					String shortlink = oracleNoSqlDb.getShortlink(UUID.fromString("58cd500c-3566-4416-9523-9d3c05430921"));
					log.info(shortlink);
				} catch (Throwable t) {
					log.error("Error", t);
				}
			});
			Thread.sleep(5);
		}
	}

	public void countRows() {
		List<MapValue> results;
		try (QueryRequest queryRequest = new QueryRequest()) {
			queryRequest.setStatement("select count(*) from shortlinks");
			results = handle.query(queryRequest).getResults();
		}
		log.info("Results: {}", results);
	}

	@Override
	public @Nullable String getShortlink(UUID uuid) {
		GetResult linkuuid = handle.get(new GetRequest().setTableName("shortlinks").setKey(new MapValue().put("linkuuid", uuid.toString())));
		MapValue value = linkuuid.getValue();
		if (value == null) {
			return null;
		}
		else {
			MapValue dataMap = value.get("datavalue").asMap();
			dataMap.put("timestamp", linkuuid.getModificationTime());
			return dataMap.toString();
		}
	}

	private static void sleep(int ms) {
		try {
			Thread.sleep(ms);
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void putShortLink(UUID uuid, String payload) {
		long hash = payload.hashCode();
		PutRequest pr = new PutRequest().setTableName(tableName).setValue(
				new MapValue()
						.put("linkuuid", uuid.toString())
						.put("hash", hash)
						.putFromJson("datavalue", payload, new JsonOptions()));
		PutResult put = handle.put(pr);
		// TODO: error handling here?
	}


}
