package gg.xp;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.xp.handlers.Share;
import gg.xp.handlers.Shortlink;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoBuilder;
import org.picocontainer.lifecycle.StartableLifecycleStrategy;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_OK;

public class PersistenceTest {

	private MutablePicoContainer pico;

	@BeforeClass
	void setup() {

		MutablePicoContainer pico = new PicoBuilder().withCaching().withLifecycle(StartableLifecycleStrategy.class).withAutomatic().build();
		Config config = new Config();
		pico.addComponent(config);
		pico.addComponent(Server.class);
		pico.addComponent(new GzipCacheImpl(5000, 10_000));
		Map<UUID, String> backing = new ConcurrentHashMap<>();
		pico.addComponent(new Database() {
			@Override
			public @Nullable String getShortlink(UUID uuid) {
				return backing.get(uuid);
			}

			@Override
			public void putShortLink(UUID uuid, String payload) {
				backing.put(uuid, payload);
			}
		});
		pico.addComponent(Shortlink.class);
		pico.addComponent(Share.class);
		pico.addComponent(Stats.class);
		pico.addComponent(new ObjectMapper());
		HttpClient http = HttpClient.newBuilder().build();
		pico.addComponent(http);

		pico.getComponents();
		pico.start();
		this.pico = pico;
	}

	@AfterClass
	void shutdown() {
		this.pico.stop();
	}

	@Test
	void gearSheetTest() throws URISyntaxException, IOException, InterruptedException {
		HttpClient http = pico.getComponent(HttpClient.class);
		String sheetJson = new String(Objects.requireNonNull(PersistenceTest.class.getResourceAsStream("/test_sheet.json")).readAllBytes());
		var putResponse = http.send(HttpRequest.newBuilder(new URI("http://localhost:8085/shortlink/")).POST(HttpRequest.BodyPublishers.ofString(sheetJson)).build(), HttpResponse.BodyHandlers.ofString());
		Assert.assertEquals(putResponse.statusCode(), HTTP_CREATED);
		String setUuid = putResponse.body();
		// Only using this to validate the form
		//noinspection ResultOfMethodCallIgnored
		UUID.fromString(setUuid);
		{
			var getResponse = http.send(HttpRequest.newBuilder(new URI("http://localhost:8085/shortlink/" + setUuid)).GET().build(), HttpResponse.BodyHandlers.ofString());
			Assert.assertEquals(getResponse.statusCode(), HTTP_OK);
			String getBody = getResponse.body();
			Assert.assertEquals(getBody, sheetJson);
		}
		// Clear cache and try again. Also test trailing slash.
		pico.getComponent(GzipCacheImpl.class).clear();
		{
			var getResponse = http.send(HttpRequest.newBuilder(new URI("http://localhost:8085/shortlink/" + setUuid + '/')).GET().build(), HttpResponse.BodyHandlers.ofString());
			Assert.assertEquals(getResponse.statusCode(), HTTP_OK);
			String getBody = getResponse.body();
			Assert.assertEquals(getBody, sheetJson);
		}
		{
			var shareResponse = http.send(HttpRequest.newBuilder(new URI("http://localhost:8085/share/" + setUuid)).GET().build(), HttpResponse.BodyHandlers.ofString());
			Assert.assertEquals(shareResponse.statusCode(), HTTP_OK);
			String getBody = shareResponse.body();
			MatcherAssert.assertThat(getBody, Matchers.containsString("<meta property=\"og:title\" content=\"6.5 Sage DSR Sets copy - XivGear - FFXIV Gear Planner\"/>"));
			MatcherAssert.assertThat(getBody, Matchers.containsString("<meta property=\"og:description\" content=\"Sage BiS sheet for Dragonsong's Reprise, updated for 6.5\n\nXivGear is an advanced and easy-to-use FFXIV gear planner/set builder with built-in simulation support.\"/>"));
			MatcherAssert.assertThat(getBody, Matchers.containsString("<meta http-equiv=\"Refresh\" content=\"0; url='https://xivgear.app/#/sl/%s'\" />".formatted(setUuid)));
		}
		// Clear cache and try again
		pico.getComponent(GzipCacheImpl.class).clear();
		{
			var shareResponse = http.send(HttpRequest.newBuilder(new URI("http://localhost:8085/share/" + setUuid)).GET().build(), HttpResponse.BodyHandlers.ofString());
			Assert.assertEquals(shareResponse.statusCode(), HTTP_OK);
			String getBody = shareResponse.body();
			MatcherAssert.assertThat(getBody, Matchers.containsString("<meta property=\"og:title\" content=\"6.5 Sage DSR Sets copy - XivGear - FFXIV Gear Planner\"/>"));
			MatcherAssert.assertThat(getBody, Matchers.containsString("<meta property=\"og:description\" content=\"Sage BiS sheet for Dragonsong's Reprise, updated for 6.5\n\nXivGear is an advanced and easy-to-use FFXIV gear planner/set builder with built-in simulation support.\"/>"));
			MatcherAssert.assertThat(getBody, Matchers.containsString("<meta http-equiv=\"Refresh\" content=\"0; url='https://xivgear.app/#/sl/%s'\" />".formatted(setUuid)));
		}
	}

	@Test
	void gearSetTest() throws URISyntaxException, IOException, InterruptedException {
		HttpClient http = pico.getComponent(HttpClient.class);
		String sheetJson = new String(Objects.requireNonNull(PersistenceTest.class.getResourceAsStream("/test_set.json")).readAllBytes());
		var putResponse = http.send(HttpRequest.newBuilder(new URI("http://localhost:8085/shortlink/")).POST(HttpRequest.BodyPublishers.ofString(sheetJson)).build(), HttpResponse.BodyHandlers.ofString());
		Assert.assertEquals(putResponse.statusCode(), HTTP_CREATED);
		String setUuid = putResponse.body();
		// Only using this to validate the form
		//noinspection ResultOfMethodCallIgnored
		UUID.fromString(setUuid);
		{
			var getResponse = http.send(HttpRequest.newBuilder(new URI("http://localhost:8085/shortlink/" + setUuid)).GET().build(), HttpResponse.BodyHandlers.ofString());
			Assert.assertEquals(getResponse.statusCode(), HTTP_OK);
			String getBody = getResponse.body();
			Assert.assertEquals(getBody, sheetJson);
		}
		// Clear cache and try again. Also test trailing slash.
		pico.getComponent(GzipCacheImpl.class).clear();
		{
			var getResponse = http.send(HttpRequest.newBuilder(new URI("http://localhost:8085/shortlink/" + setUuid + '/')).GET().build(), HttpResponse.BodyHandlers.ofString());
			Assert.assertEquals(getResponse.statusCode(), HTTP_OK);
			String getBody = getResponse.body();
			Assert.assertEquals(getBody, sheetJson);
		}
		{
			var shareResponse = http.send(HttpRequest.newBuilder(new URI("http://localhost:8085/share/" + setUuid)).GET().build(), HttpResponse.BodyHandlers.ofString());
			Assert.assertEquals(shareResponse.statusCode(), HTTP_OK);
			String getBody = shareResponse.body();
			MatcherAssert.assertThat(getBody, Matchers.containsString("<meta property=\"og:title\" content=\"6.55 DSR 611 - Crit/Det Relic - XivGear - FFXIV Gear Planner\"/>"));
			MatcherAssert.assertThat(getBody, Matchers.containsString("<meta property=\"og:description\" content=\"This set is intended for you to be doing the 611 strat as it saves a lot of mana requiring little to none. If you want to lose extra Piety you can replace your piety ring with a 660 augmented tome ri&hellip;\n\nXivGear is an advanced and easy-to-use FFXIV gear planner/set builder with built-in simulation support.\"/>"));
			MatcherAssert.assertThat(getBody, Matchers.containsString("<meta http-equiv=\"Refresh\" content=\"0; url='https://xivgear.app/#/sl/%s'\" />".formatted(setUuid)));
		}
		// Clear cache and try again
		pico.getComponent(GzipCacheImpl.class).clear();
		{
			var shareResponse = http.send(HttpRequest.newBuilder(new URI("http://localhost:8085/share/" + setUuid)).GET().build(), HttpResponse.BodyHandlers.ofString());
			Assert.assertEquals(shareResponse.statusCode(), HTTP_OK);
			String getBody = shareResponse.body();
			MatcherAssert.assertThat(getBody, Matchers.containsString("<meta property=\"og:title\" content=\"6.55 DSR 611 - Crit/Det Relic - XivGear - FFXIV Gear Planner\"/>"));
			MatcherAssert.assertThat(getBody, Matchers.containsString("<meta property=\"og:description\" content=\"This set is intended for you to be doing the 611 strat as it saves a lot of mana requiring little to none. If you want to lose extra Piety you can replace your piety ring with a 660 augmented tome ri&hellip;\n\nXivGear is an advanced and easy-to-use FFXIV gear planner/set builder with built-in simulation support.\"/>"));
			MatcherAssert.assertThat(getBody, Matchers.containsString("<meta http-equiv=\"Refresh\" content=\"0; url='https://xivgear.app/#/sl/%s'\" />".formatted(setUuid)));
		}


	}
}
