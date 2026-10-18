package probe;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import com.github.xpenatan.gdx.teavm.backends.web.assetloader.AssetInstance;
import com.github.xpenatan.gdx.teavm.backends.web.assetloader.AssetLoader;
import com.github.xpenatan.gdx.teavm.backends.web.assetloader.AssetLoaderListener;

/** Browser entry point for the isolated component experiment. */
public final class WebLauncher {
    private WebLauncher() { }

    public static void main(String[] args) {
        WebApplicationConfiguration config = new WebApplicationConfiguration("canvas");
        config.width = 0;
        config.height = 0;
        config.usePhysicalPixels = true;
        config.useDebugGL = true;
        config.showDownloadLogs = true;
        config.powerPreference = "low-power";
        // This preload listener uses no logo, font, or additional content assets.
        ApplicationAdapter preload = new ApplicationAdapter() {
            private AssetLoader loader;
            private boolean manifestLoaded;

            @Override
            public void create() {
                loader = AssetInstance.getLoaderInstance();
                loader.preload(new AssetLoaderListener<Void>() {
                    @Override
                    public void onSuccess(String url, Void result) {
                        manifestLoaded = true;
                    }

                    @Override
                    public void onFailure(String url) {
                        throw new IllegalStateException("Asset manifest failed: " + url);
                    }
                });
            }

            @Override
            public void render() {
                Gdx.gl.glClearColor(0.04f, 0.06f, 0.10f, 1f);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                if (manifestLoaded && !loader.isDownloading()) {
                    if (!loader.isAssetLoaded(Files.FileType.Internal, "sprites/treasure.atlas")
                            || !loader.isAssetLoaded(Files.FileType.Internal, "sprites/treasure.png")) {
                        throw new IllegalStateException("Required treasure assets failed to preload");
                    }
                    WebApplication.get().setPreloadReady();
                }
            }
        };
        new WebApplication(new SpriteGroupProbe(), preload, config);
    }
}
