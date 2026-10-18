package probe;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.viewport.FitViewport;
import forge.adventure.stage.SpriteGroup;
import org.teavm.jso.JSBody;

/** Test scaffolding: original Adventure grouping and assets, with no game/model bootstrap. */
public final class SpriteGroupProbe extends ApplicationAdapter {
    private static final float WIDTH = 320;
    private static final float HEIGHT = 480;
    private static final float SIZE = 96;
    private Stage stage;
    private TextureAtlas atlas;
    private SpriteGroup group;
    private RecordingImage a;
    private RecordingImage b;
    private final StringBuilder drawOrder = new StringBuilder();
    private final Vector2 pointer = new Vector2();
    private int frames;
    private int pointerEvents;
    private String activeActor = "";
    private String lastOrder = "";

    @Override
    public void create() {
        stage = new Stage(new FitViewport(WIDTH, HEIGHT));
        group = new SpriteGroup();
        stage.addActor(group);
        atlas = new TextureAtlas(Gdx.files.internal("sprites/treasure.atlas"));
        a = new RecordingImage("A", 0);
        b = new RecordingImage("B", 1);
        a.setPosition(112, 200);
        b.setPosition(136, 220);
        // Tint only the rendered actor; source PNG and atlas remain untouched.
        a.setColor(0.55f, 1f, 0.85f, 1f);
        b.setColor(1f, 0.75f, 0.50f, 1f);
        group.addActor(a);
        group.addActor(b);
        Gdx.input.setInputProcessor(stage);
        ready(atlas.getRegions().size, atlas.getTextures().size);
        Gdx.app.log("probe", "READY: unchanged Forge SpriteGroup; original treasure atlas and PNG");
    }

    private final class RecordingImage extends Image {
        RecordingImage(String name, int frame) {
            super(atlas.findRegions("Idle").get(frame));
            setName(name);
            setSize(SIZE, SIZE);
            addListener(new InputListener() {
                private float offsetX;
                private float offsetY;

                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointerId, int button) {
                    offsetX = x;
                    offsetY = y;
                    activeActor = getName();
                    recordPointer(event);
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointerId) {
                    recordPointer(event);
                    setPosition(MathUtils.clamp(event.getStageX() - offsetX, 0, WIDTH - SIZE),
                            MathUtils.clamp(event.getStageY() - offsetY, 0, HEIGHT - SIZE));
                    // Let SpriteGroup.drawChildren sort on the next frame; do not implement sorting here.
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointerId, int button) {
                    recordPointer(event);
                    activeActor = "";
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            drawOrder.append(getName());
            super.draw(batch, parentAlpha);
        }
    }

    private void recordPointer(InputEvent event) {
        pointer.set(event.getStageX(), event.getStageY());
        pointerEvents++;
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0.04f, 0.06f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.getViewport().apply();
        stage.act(Math.min(Gdx.graphics.getDeltaTime(), 0.1f));
        drawOrder.setLength(0);
        stage.draw();
        frames++;
        String order = drawOrder.toString();
        StringBuilder children = new StringBuilder();
        for (Actor actor : group.getChildren()) { children.append(actor.getName()); }
        if (!order.equals(lastOrder)) {
            Gdx.app.log("probe", "DRAW_ORDER=" + order + " A.y=" + a.getY() + " B.y=" + b.getY());
            lastOrder = order;
        }
        report(order, children.toString(), a.getX(), a.getY(), b.getX(), b.getY(),
                pointer.x, pointer.y, pointerEvents, activeActor, frames,
                Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) { stage.getViewport().update(width, height, true); }
    }

    @Override
    public void dispose() {
        if (stage != null) { stage.dispose(); }
        if (atlas != null) { atlas.dispose(); }
    }

    @JSBody(params = {"regions", "textures"}, script = ""
            + "const p = window.__spriteGroupProbe; p.ready = true;"
            + "p.readyMs = performance.now() - p.started; p.regions = regions; p.textures = textures;")
    private static native void ready(int regions, int textures);

    @JSBody(params = {"order", "children", "ax", "ay", "bx", "by", "px", "py", "events", "active", "frames", "width", "height"},
            script = ""
            + "const p = window.__spriteGroupProbe; const now = performance.now();"
            + "if (p.lastFrame && !document.hidden && p.intervals.length < 3600) p.intervals.push(now - p.lastFrame);"
            + "p.lastFrame = now; p.drawOrder = order; p.childOrder = children;"
            + "p.actors = { A: {x: ax, y: ay, size: 96}, B: {x: bx, y: by, size: 96} };"
            + "p.pointer = { x: px, y: py, events: events, active: active };"
            + "p.frames = frames; p.canvasSize = { width: width, height: height };"
            + "if (p.previousOrder && p.previousOrder !== order) p.orderChanges++; p.previousOrder = order;"
            + "document.getElementById('status').textContent = 'Draw: ' + order + ' · children: ' + children"
            + " + ' · A.y ' + ay.toFixed(1) + ' · B.y ' + by.toFixed(1) + ' · pointer events ' + events;")
    private static native void report(String order, String children, float ax, float ay, float bx, float by,
            float px, float py, int events, String active, int frames, int width, int height);
}
