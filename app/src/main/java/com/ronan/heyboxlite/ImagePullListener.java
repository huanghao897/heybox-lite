package com.ronan.heyboxlite;

interface ImagePullListener {
    void onInteractionStart();

    void onPull(float dx, float dy, float progress);

    void onPullEnd(boolean dismiss);
}
