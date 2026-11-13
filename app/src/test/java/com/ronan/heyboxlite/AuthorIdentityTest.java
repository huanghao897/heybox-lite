package com.ronan.heyboxlite;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AuthorIdentityTest {
    @Test public void nestedAuthorWinsOverBothPostIdAndRootUserAliases() throws Exception {
        JSONObject user = new JSONObject().put("userid", "author-7");
        JSONObject post = new JSONObject().put("id", "post-42")
                .put("userid", "root-user").put("user", user);
        assertEquals("author-7", AuthorIdentity.authorId(post));
        assertEquals("author-7", AuthorIdentity.authorId(post, user));
    }

    @Test public void genericIdIsOnlyAllowedOnAKnownUserObject() throws Exception {
        assertEquals("", AuthorIdentity.authorId(new JSONObject().put("id", "post-42")));
        JSONObject user = new JSONObject().put("id", "author-7");
        assertEquals("author-7", AuthorIdentity.userId(user));
        assertEquals("author-7", AuthorIdentity.authorId(
                new JSONObject().put("id", "post-42").put("user", user)));
    }

    @Test public void supportsExplicitRootAliasesWithoutUsingPostId() throws Exception {
        for (String alias : new String[] {"userid", "user_id", "heybox_id", "heyboxid", "uid", "account_id"}) {
            assertEquals(alias, "17", AuthorIdentity.authorId(new JSONObject()
                    .put("id", "post-42").put(alias, 17)));
        }
    }

    @Test public void missingNullBlankAndStructuredValuesAreNotIds() throws Exception {
        assertEquals("", AuthorIdentity.authorId(null));
        JSONObject post = new JSONObject().put("id", "post-42").put("userid", JSONObject.NULL)
                .put("user_id", "  " ).put("uid", new JSONObject());
        assertEquals("", AuthorIdentity.authorId(post));
    }

    @Test public void listItemsUseTheSameAuthorRulesAsNativeDetail() throws Exception {
        JSONObject post = new JSONObject().put("id", "post-42").put("linkid", "post-42")
                .put("title", "fixture");
        assertEquals("", FeedItem.from(post).authorId);
        post.put("user", new JSONObject().put("id", "author-7"));
        assertEquals("author-7", FeedItem.from(post).authorId);
        assertEquals(AuthorIdentity.authorId(post), FeedItem.from(post).authorId);
    }
}
