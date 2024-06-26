package net.labymod.addons.minimap.map.v2;

import net.labymod.addons.minimap.api.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.gfx.GFXBridge;
import net.labymod.api.client.gfx.pipeline.GFXRenderPipeline;
import net.labymod.api.client.gfx.pipeline.post.CustomPostPassProcessor;
import net.labymod.api.client.gfx.pipeline.post.PostProcessor;
import net.labymod.api.client.gfx.pipeline.post.PostProcessorLoader;
import net.labymod.api.client.gfx.shader.ShaderProgram;
import net.labymod.api.client.gfx.shader.ShaderTextures;
import net.labymod.api.client.gfx.shader.uniform.Uniform1F;
import net.labymod.api.client.gfx.shader.uniform.Uniform3F;
import net.labymod.api.client.gfx.shader.uniform.UniformSampler;
import net.labymod.api.client.gfx.target.RenderTarget;
import net.labymod.api.client.gfx.texture.GFXTextureFilter;
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
  private final LightmapTexture lightmapTexture;

  private final RenderTarget renderTarget;
  private PostProcessor postProcessor;

  private DaylightPeriod currentPeriod = DaylightPeriod.DAYTIME;

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
    this.lightmapTexture = new LightmapTexture();

    this.renderTarget = new RenderTarget();
    this.renderTarget.addColorAttachment(0, GFXTextureFilter.NEAREST);
    this.renderTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
    this.renderTarget.resize(this.getWidth(), this.getHeight());
    PostProcessorLoader.loadDynamic(this.renderTarget, Util.newDefaultNamespace("post/shadow.json"),
        processor -> {
          this.postProcessor = processor;

          this.postProcessor.setCustomPostPassProcessor(new CustomPostPassProcessor() {
            @Override
            public void process(String name, ShaderProgram program, float time) {
              UniformSampler diffuseSampler = program.getUniform("DiffuseSampler");
              UniformSampler heightmapSampler = program.getUniform("HeightmapSampler");
              UniformSampler lightmapSampler = program.getUniform("LightmapSampler");

              GFXBridge gfx = Laby.gfx();
              gfx.setActiveTexture(0);
              MinimapTexture minimapTexture = MinimapTexture.this;
              gfx.bindResourceLocation(minimapTexture.location());
              gfx.setActiveTexture(1);
              gfx.bindResourceLocation(minimapTexture.heightmapTexture.location());

              gfx.setActiveTexture(2);
              gfx.bindResourceLocation(minimapTexture.lightmapTexture.location());

              diffuseSampler.set(ShaderTextures.getShaderTexture(0));
              heightmapSampler.set(ShaderTextures.getShaderTexture(1));
              lightmapSampler.set(ShaderTextures.getShaderTexture(2));

              float scale = 10000.0F;
              float x = (float) Math.cos((TimeUtil.getMillis() / scale) * 0.75F + 0.5F);
              float y = (float) Math.sin((TimeUtil.getMillis() / scale) * 0.75F + 0.5F);

              Uniform3F sunPosition = program.getUniform("SunPosition");
              sunPosition.set(x, y, 1F);

              Uniform3F pixelSize = program.getUniform("PixelSize");
              pixelSize.set(1.0F / minimapTexture.getWidth(), 1.0F / minimapTexture.getHeight(),
                  0F);

              float timeOfDay = MinimapTexture.this.getTimeOfDay();
              float normalizedDayTime = (float) (1.0F - (Math.cos(timeOfDay * (float) (Math.PI * 2)) * 2.0F + 0.2F));
              normalizedDayTime = Math.clamp(normalizedDayTime, 0.0F, 1.0F);
              normalizedDayTime = 1.0F - normalizedDayTime;

              Uniform1F dayTime = program.getUniform("DayTime");
              dayTime.set(normalizedDayTime);
            }
          });
        });
  }

  public long getDayTime() {
    return Laby.references().clientWorld().getDayTime() % 24000L;
  }


  @Override
  public void tick() {
    ClientPlayer player = Laby.labyAPI().minecraft().getClientPlayer();
    if (player == null) {
      return;
    }

    long dayTime = this.getDayTime();
    this.setDaylightPeriod(DaylightPeriod.findByTime(dayTime));

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

    boolean changed = false;
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
      this.lightmapTexture.image().fillRect(0, 0, this.getWidth(), this.getHeight(), 0xFF000000);
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

                ColorFormat format = ColorFormat.ARGB32;

                float normalized =
                    (height - minBuildHeight) * (1.0F - 0.0F) / (maxBuildHeight - minBuildHeight)
                        + 0.0F;

                int heightmapColor = format.pack(normalized, normalized, normalized, 1.0F);
                this.image().setARGB(destX, destZ, tileColor);
                this.heightmapTexture.image().setARGB(destX, destZ, heightmapColor);

                int blockLightLevel = chunk.getBlockLightLevel(pixelX, pixelZ);

                int normalizedLightLevel = this.normalize(blockLightLevel);

                boolean noBlockLighting = normalizedLightLevel == 150;

                this.lightmapTexture.image().setARGB(
                    destX, destZ,
                    format.pack(
                        noBlockLighting ? 0 : normalizedLightLevel,
                        noBlockLighting ? 0 : normalizedLightLevel,
                        noBlockLighting ? 0 : normalizedLightLevel,
                        noBlockLighting ? 0 : 255
                    )
                );
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

  private int normalize(int value) {
    return this.normalize(value, 0, 15, 10, 255);
  }

  private int normalize(int value, int oldMin, int oldMax, int newMin, int newMax) {
    return (value - oldMin) * (newMax - newMin) / (oldMax - oldMin) + newMin;
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
    GFXRenderPipeline renderPipeline = Laby.references().gfxRenderPipeline();

    GFXBridge gfx = Laby.gfx();
    gfx.glPushDebugGroup(0, "PostProcessor");
    this.postProcessor.process(1.0F);
    gfx.glPopDebugGroup();

    this.renderTarget.setProjectionSetter((projectionMatrix, width1, height1, near, far) -> {
      Window window = Laby.labyAPI().minecraft().minecraftWindow();

      stack.push();
      stack.translate(0, 0, -2000);
      FloatMatrix4 position = stack.getProvider().getPosition();

      renderPipeline.matrixStorage().setModelViewMatrix(position, 4);
      stack.pop();

      projectionMatrix.setOrthographic(
          window.getScaledWidth(),
          -window.getScaledHeight(),
          near,
          far
      );
    });

    renderPipeline.renderToActivityTarget(target -> this.renderTarget.render((int) width, (int) height, false));

    // TODO(Christian)
    RenderTarget target = renderPipeline.getActivityRenderTarget();
    target.bind(true);
  }

  @Override
  public void initialize() {
    this.heightmapTexture.initialize();
    this.lightmapTexture.initialize();
    super.initialize();
  }

  @Override
  public void resize(int newWidth, int newHeight) {
    this.postProcessor.resize(newWidth, newHeight);
    this.renderTarget.resize(newWidth, newHeight);
    super.resize(newWidth, newHeight);
    this.heightmapTexture.resize(newWidth, newHeight);
    this.lightmapTexture.resize(newWidth, newHeight);
  }

  @Override
  public void updateTexture() {
    super.updateTexture();
    this.heightmapTexture.updateTexture();
    this.lightmapTexture.updateTexture();
  }

  private float getTimeOfDay() {
    long dayTime = this.getDayTime();
    double timeFraction = this.frac(dayTime / 24000.0 - 0.25D);
    double timeOfDay = 0.5D - Math.cos(timeFraction * Math.PI) / 2.0D;
    return (float) (timeFraction * 2.0D + timeOfDay) / 3.0F;
  }

  private double frac(double value) {
    return value - Math.floor(value);
  }

  private void setDaylightPeriod(DaylightPeriod period) {
    if (this.currentPeriod == period) {
      return;
    }

    System.out.println("Changed period from " + this.currentPeriod + " to " + period);
    this.currentPeriod = period;
  }
}
