package com.ronan.heyboxlite;

import android.media.MediaPlayer;
import android.media.PlaybackParams;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowMediaPlayer;

/** Robolectric omits the native speed getters; keep only that boundary in this test shadow. */
@Implements(MediaPlayer.class)
public class VideoPlayerMediaShadow extends ShadowMediaPlayer {
    private PlaybackParams params = new PlaybackParams().allowDefaults();
    public static boolean speedUnsupported;

    @Implementation(minSdk = 23)
    protected PlaybackParams getPlaybackParams() {
        return this.params;
    }

    @Implementation(minSdk = 23)
    protected void setPlaybackParams(PlaybackParams params) {
        if (speedUnsupported) throw new IllegalArgumentException("Fixture without native speed support");
        this.params = params;
    }
}
