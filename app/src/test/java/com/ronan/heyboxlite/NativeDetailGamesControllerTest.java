package com.ronan.heyboxlite;

import static org.junit.Assert.*;

import android.app.Application;
import android.widget.LinearLayout;

import androidx.activity.ComponentActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, manifest = Config.NONE,
        qualifiers = "w240dp-h320dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeDetailGamesControllerTest {
    @Test public void actualActivityPauseHoldsPublicationAndTheNextBatchUntilResume() throws Exception {
        try (Fixture f = new Fixture()) {
            f.show(new Object(), blocks(1, 25));
            assertEquals(1, f.requests.size());
            assertEquals(20, f.requests.get(0).ids.size());
            f.activity.pause().stop();
            int publications = f.published.size();

            f.requests.get(0).success();

            assertEquals(publications, f.published.size());
            assertEquals(1, f.requests.size());
            f.activity.restart().start().resume();
            assertEquals(2, f.requests.size());
            assertEquals(5, f.requests.get(1).ids.size());
            assertEquals(20, f.last().data.size());
            f.requests.get(1).success();
            assertEquals(25, f.last().data.size());
            assertEquals(25, f.last().finished.size());
        }
    }

    @Test public void suspendedParentCollectsOnlyDataAndRestoresWithoutLosingItsCards() throws Exception {
        try (Fixture f = new Fixture()) {
            Object parent = new Object();
            f.show(parent, blocks(1, 25));
            f.owner.suspend();
            Object child = new Object();
            f.show(child, blocks(100, 100));
            int publications = f.published.size();

            f.requests.get(0).success();

            assertEquals(publications, f.published.size());
            assertEquals(2, f.requests.size());
            f.active = parent;
            f.owner.restore(parent);
            assertEquals(20, f.last().data.size());
            assertEquals(3, f.requests.size());
            f.requests.get(2).success();
            assertEquals(25, f.last().data.size());
            publications = f.published.size();
            f.requests.get(1).success();
            assertEquals(publications, f.published.size());
            assertFalse(f.last().data.containsKey("100"));
        }
    }

    @Test public void returningBeforeTheParentResponseDoesNotDispatchADuplicateRequest() throws Exception {
        try (Fixture f = new Fixture()) {
            Object parent = new Object();
            f.show(parent, blocks(1, 1));
            f.owner.suspend();
            f.active = parent;
            f.owner.restore(parent);
            assertEquals(1, f.requests.size());
            f.requests.get(0).success();
            assertEquals(1, f.last().data.size());
        }
    }

    @Test public void discardedEntryAndDestroyedActivityCannotPublishLateData() throws Exception {
        try (Fixture f = new Fixture()) {
            Object old = new Object();
            f.show(old, blocks(1, 25));
            f.owner.discard(old);
            f.show(new Object(), blocks(100, 100));
            int publications = f.published.size();
            f.requests.get(0).success();
            assertEquals(publications, f.published.size());
            assertEquals(2, f.requests.size());
            f.activity.pause().stop().destroy();
            publications = f.published.size();
            f.requests.get(1).success();
            assertEquals(publications, f.published.size());
            assertEquals(2, f.requests.size());
        }
    }

    @Test public void replacementBindsExistingResultsToTheNewViewTreeWithoutARequest() throws Exception {
        try (Fixture f = new Fixture()) {
            Object entry = new Object();
            List<RichContent.Block> blocks = blocks(1, 1);
            f.show(entry, blocks);
            f.requests.get(0).success();
            LinearLayout replacement = f.render(blocks);
            f.root = replacement;
            f.owner.show(entry, blocks);
            assertNotNull(NativeDetailTestFixture.text(replacement, "Game 1"));
            assertEquals(1, f.requests.size());
            assertEquals(1, f.last().data.size());
        }
    }

    @Test public void synchronousCacheHitBindsBeforeAttachmentAndDoesNotGetLost() throws Exception {
        try (Fixture f = new Fixture()) {
            f.show(new Object(), blocks(1, 1));
            f.requests.get(0).success();
            f.owner.discard(f.active);
            f.show(new Object(), blocks(1, 1));
            assertNotNull(NativeDetailTestFixture.text(f.root, "Game 1"));
            assertEquals(1, f.requests.size());
        }
    }

    @Test public void renderingAloneDoesNotStartBusinessLoadingAndEmbeddedCardsNeedNoRequest() throws Exception {
        try (Fixture f = new Fixture()) {
            f.render(blocks(1, 1));
            assertTrue(f.requests.isEmpty());
            JSONObject embedded = new JSONObject().put("appid", "7").put("name", "Embedded")
                    .put("price", new JSONObject().put("current", "10"));
            f.show(new Object(), Collections.singletonList(RichContent.Block.gameCard(embedded, "7")));
            assertTrue(f.requests.isEmpty());
            assertNotNull(NativeDetailTestFixture.text(f.root, "Embedded"));
        }
    }

    @Test public void failedMetadataPublishesUnavailableOnlyForTheActiveEntry() throws Exception {
        try (Fixture f = new Fixture()) {
            f.show(new Object(), blocks(1, 1));
            f.requests.get(0).callback.onError("fixture unavailable");
            assertTrue(f.last().finished.contains("1"));
            assertTrue(f.last().data.isEmpty());
            assertNotNull(NativeDetailTestFixture.find(f.root, GameCardView.class));
            assertEquals(1, f.requests.size());
        }
    }

    private static List<RichContent.Block> blocks(int start, int end) {
        List<RichContent.Block> blocks = new ArrayList<>();
        for (int id = start; id <= end; id++) blocks.add(RichContent.Block.gameCard(null, String.valueOf(id)));
        return blocks;
    }

    private static final class Fixture implements AutoCloseable {
        final ActivityController<ComponentActivity> activity = Robolectric.buildActivity(ComponentActivity.class).setup();
        final SessionStore session = new SessionStore(activity.get());
        final DetailContentRenderer renderer = new DetailContentRenderer(activity.get(), session,
                ThemeTokens.of(false, 0xff111111, 0xff555555), false, (source, urls, index) -> { });
        final List<Request> requests = new ArrayList<>();
        final List<NativeDetailGamesController.Snapshot> published = new ArrayList<>();
        final NativeDetailGamesController owner = new NativeDetailGamesController(
                new GameDetailClient((path, query, algorithm, profile, callback) ->
                        requests.add(new Request(query, callback)), () -> 0L),
                activity.get().getLifecycle(), new NativeDetailGamesController.Host() {
                    @Override public boolean active(Object entry) { return entry == active; }
                    @Override public void changed(NativeDetailGamesController.Snapshot snapshot) {
                        published.add(snapshot);
                        renderer.bindGames(root, snapshot);
                    }
                });
        Object active;
        LinearLayout root;

        void show(Object entry, List<RichContent.Block> blocks) {
            active = entry;
            root = render(blocks);
            owner.show(entry, blocks);
        }
        LinearLayout render(List<RichContent.Block> blocks) {
            LinearLayout root = new LinearLayout(activity.get());
            root.setOrientation(LinearLayout.VERTICAL);
            renderer.add(root, new DetailContentParser.Result(blocks, false, Collections.emptyList()));
            return root;
        }
        NativeDetailGamesController.Snapshot last() { return published.get(published.size() - 1); }
        @Override public void close() {
            owner.close();
            if (!activity.get().isDestroyed()) activity.pause().stop().destroy();
        }
    }

    private static final class Request {
        final List<String> ids;
        final ApiClient.Callback callback;
        Request(Map<String, String> query, ApiClient.Callback callback) {
            ids = java.util.Arrays.asList(query.get("appids").split(","));
            this.callback = callback;
        }
        void success() throws Exception {
            JSONArray games = new JSONArray();
            for (String id : ids) games.put(new JSONObject().put("appid", id).put("name", "Game " + id));
            callback.onSuccess(new JSONObject().put("result", new JSONObject().put("base_infos", games)));
        }
    }
}
