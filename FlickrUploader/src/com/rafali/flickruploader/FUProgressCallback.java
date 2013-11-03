package com.rafali.flickruploader;

import com.googlecode.flickrjandroid.uploader.ProgressCallback;

public class FUProgressCallback implements ProgressCallback {

    private final Media media;

    FUProgressCallback(Media media) {
        this.media = media;
    }

    @Override
    public void reportProgress(int progress) {
        UploadService.onProgress(media, progress);
    }
}
