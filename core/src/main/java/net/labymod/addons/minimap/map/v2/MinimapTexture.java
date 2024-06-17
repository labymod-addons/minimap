package net.labymod.addons.minimap.map.v2;

import net.labymod.addons.minimap.api.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.util.Util;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.gfx.GFXBridge;
import net.labymod.api.client.gfx.pipeline.GFXRenderPipeline;
import net.labymod.api.client.gfx.pipeline.post.CustomPostPassProcessor;
import net.labymod.api.client.gfx.pipeline.post.PostProcessor;
import net.labymod.api.client.gfx.pipeline.post.PostProcessorLoader;
import net.labymod.api.client.gfx.shader.ShaderProgram;
import net.labymod.api.client.gfx.shader.ShaderTextures;
import net.labymod.api.client.gfx.shader.uniform.Uniform3F;
import net.labymod.api.client.gfx.shader.uniform.UniformSampler;
import net.labymod.api.client.gfx.target.RenderTarget;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.window.Window;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.client.resources.texture.GameImage;
import net.labymod.api.client.world.ClientWorld;
import net.labymod.api.util.color.format.ColorFormat;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.vector.FloatMatrix4;
import net.labymod.api.util.time.TimeUtil;
import java.util.function.Supplier;

public class MinimapTexture extends DynamicTexture {

  private static final int CHUNK_X = 16;
  private static final int CHUNK_Z = 16;

  private static final int SKY_COLOR = ColorFormat.ARGB32.pack(142, 163, 255, 255);

  private final Supplier<MinimapHudWidgetConfig> config;
  private final MinimapBounds minimapBounds;
  private final MinimapChunkStorage storage;
  private final HeightmapTexture heightmapTexture;

  private final RenderTarget renderTarget;
  private PostProcessor postProcessor;

  private int lastMidChunkX;
  private int lastMidChunkZ;

  public MinimapTexture(
      Supplier<MinimapHudWidgetConfig> config,
      MinimapChunkStorage storage,
      MinimapBounds minimapBounds
  ) {
    super("minimap/level");
    this.config = config;
    this.storage = storage;
    this.minimapBounds = minimapBounds;
    this.heightmapTexture = new HeightmapTexture();

    this.renderTarget = new RenderTarget();
    this.renderTarget.addColorAttachment(0);
    this.renderTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
    this.renderTarget.resize(this.getWidth(), this.getHeight());
    PostProcessorLoader.loadDynamic(this.renderTarget, Util.newDefaultNamespace("post/shadow.json"), processor -> {
      this.postProcessor = processor;

      this.postProcessor.setCustomPostPassProcessor(new CustomPostPassProcessor() {
        @Override
        public void process(String name, ShaderProgram program, float time) {
          UniformSampler diffuseSampler = program.getUniform("DiffuseSampler");
          UniformSampler heightmapSampler = program.getUniform("HeightmapSampler");

          GFXBridge gfx = Laby.gfx();
          gfx.setActiveTexture(0);
          MinimapTexture minimapTexture = MinimapTexture.this;
          gfx.bindResourceLocation(minimapTexture.location());
          gfx.setActiveTexture(1);
          gfx.bindResourceLocation(minimapTexture.heightmapTexture.location());

          diffuseSampler.set(ShaderTextures.getShaderTexture(0));
          heightmapSampler.set(ShaderTextures.getShaderTexture(1));

          float scale = 10000.0F;
          float x = (float) Math.cos((TimeUtil.getMillis() / scale) * 0.75F + 0.5F);
          float y = (float) Math.sin((TimeUtil.getMillis() / scale) * 0.75F + 0.5F);

          Uniform3F sunPosition = program.getUniform("SunPosition");
          sunPosition.set(x, y, 1F);

          Uniform3F pixelSize = program.getUniform("PixelSize");
          pixelSize.set(1.0F / minimapTexture.getWidth(), 1.0F / minimapTexture.getHeight(), 0F);
        }
      });
    });
  }

  @Override
  public void tick() {
    ClientPlayer player = Laby.labyAPI().minecraft().getClientPlayer();
    if (player == null) {
      return;
    }

    ClientWorld level = Laby.labyAPI().minecraft().clientWorld();
    int minBuildHeight = level.getMinBuildHeight();
    int maxBuildHeight = level.getMaxBuildHeight();

    int zoom = this.config.get().zoom().get() * 10;
    int minX = (int) (player.getPosX() - zoom);
    int minZ = (int) (player.getPosZ() - zoom);
    int maxX = (int) (player.getPosX() + zoom);
    int maxZ = (int) (player.getPosZ() + zoom);

    int midChunkX = MathHelper.floor(player.getPosX()) >> 4;
    int midChunkZ = MathHelper.floor(player.getPosZ()) >> 4;

    boolean changed = true;
    if (this.lastMidChunkX != midChunkX && this.lastMidChunkZ != midChunkZ) {
      this.lastMidChunkX = midChunkX;
      this.lastMidChunkZ = midChunkZ;
      changed = true;
    }

    int minChunkX = minX >> 4;
    int minChunkZ = minZ >> 4;

    int maxChunkX = maxX >> 4;
    int maxChunkZ = maxZ >> 4;

    if (changed || this.storage.shouldProcess()) {
      this.resize(maxX - minX, maxZ - minZ);

      this.image().fillRect(0, 0, this.getWidth(), this.getHeight(), SKY_COLOR);
      this.heightmapTexture.image().fillRect(0, 0, this.getWidth(), this.getHeight(), 0xFF000000);
      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
          MinimapChunk chunk = this.storage.getChunk(chunkX, chunkZ);
          if (chunk == null) {
            continue;
          }

          chunk.compile();

          int offsetX = chunkX << 4;
          int offsetZ = chunkZ << 4;

          for (int pixelX = 0; pixelX < CHUNK_X; pixelX++) {
            for (int pixelZ = 0; pixelZ < CHUNK_Z; pixelZ++) {
              int destX = (offsetX + pixelX) - minX;
              int destZ = (offsetZ + pixelZ) - minZ;

              if (this.isWithinBounds(destX, destZ)) {
                int tileColor = chunk.getColor(pixelX, pixelZ);
                int height = chunk.getHeight(pixelX, pixelZ);


                float normalized =
                    (height - minBuildHeight) * (1.0F - 0.0F) / (maxBuildHeight - minBuildHeight)
                        + 0.0F;

                int heightmapColor = ColorFormat.ARGB32.pack(normalized, normalized, normalized,
                    1.0F);
                this.image().setARGB(destX, destZ, tileColor);
                this.heightmapTexture.image().setARGB(destX, destZ, heightmapColor);
              }
            }
          }
        }
      }

      this.minimapBounds.update(minX, minZ, maxX, maxZ, 0);
      this.storage.processed();

      this.updateTexture();
    }
  }

  @Override
  public void reInitialize() {
    this.lastMidChunkX = Integer.MIN_VALUE;
    this.lastMidChunkZ = Integer.MIN_VALUE;
  }

  private boolean isWithinBounds(int destX, int destZ) {
    GameImage image = this.image();
    return destX >= 0 && destX < image.getWidth() && destZ >= 0 && destZ < image.getHeight();
  }

  @Override
  public void render(Stack stack, float x, float y, float width, float height) {
    GFXBridge gfx = Laby.gfx();
    gfx.glPushDebugGroup(0, "PostProcessor");
    this.postProcessor.process(1.0F);
    gfx.glPopDebugGroup();

    this.renderTarget.setProjectionSetter((projectionMatrix, width1, height1, near, far) -> {
      Window window = Laby.labyAPI().minecraft().minecraftWindow();

      stack.push();
      stack.translate(0, 0,-2000);
      FloatMatrix4 position = stack.getProvider().getPosition();

      Laby.references().gfxRenderPipeline().matrixStorage().setModelViewMatrix(position, 4);
      stack.pop();

      projectionMatrix.setOrthographic(window.getScaledWidth(), -window.getScaledHeight(), near, far);
    });
    GFXRenderPipeline renderPipeline = Laby.references().gfxRenderPipeline();
    renderPipeline.renderToActivityTarget(a -> {
      if(true) {
        this.renderTarget.render((int) width, (int) height, false);
      } else {
        this.renderTarget.render(false);
      }
    });

    /*
    if (Laby.labyAPI().minecraft().isKeyPressed(Key.H)) {
      this.heightmapTexture.render(stack, x, y, width, height);
    } else {
      super.render(stack, x, y, width, height);
    }*/
  }

  @Override
  public void initialize() {
    this.heightmapTexture.initialize();
    super.initialize();
  }

  @Override
  public void resize(int newWidth, int newHeight) {
    this.postProcessor.resize(newWidth, newHeight);
    this.renderTarget.resize(newWidth, newHeight);
    super.resize(newWidth, newHeight);
    this.heightmapTexture.resize(newWidth, newHeight);
  }

  @Override
  public void updateTexture() {
    super.updateTexture();
    this.heightmapTexture.updateTexture();
  }
}
