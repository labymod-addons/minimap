package net.labymod.addons.minimap.map.v2;

import java.util.function.Supplier;
import net.labymod.addons.minimap.MinimapRenderStates;
import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.config.MinimapConfiguration;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.addons.minimap.data.ChunkDataStorage;
import net.labymod.addons.minimap.debug.MinimapDebugger;
import net.labymod.addons.minimap.debug.MinimapDebugger.TextureInfo;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.gfx.pipeline.post.CustomPostPassProcessor;
import net.labymod.api.client.gfx.pipeline.post.PostPassData;
import net.labymod.api.client.gfx.pipeline.post.PostProcessor;
import net.labymod.api.client.gfx.pipeline.post.PostProcessorLoader;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.state.states.GuiTextureSet;
import net.labymod.api.client.resources.texture.GameImage;
import net.labymod.api.client.world.ClientWorld;
import net.labymod.api.laby3d.Laby3D;
import net.labymod.api.laby3d.shaders.block.CustomPostProcessorUniformBlock;
import net.labymod.api.util.color.format.ColorFormat;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.position.Position;
import net.labymod.api.util.math.vector.FloatVector2;
import net.labymod.laby3d.api.pipeline.pass.DrawRenderCommand;
import net.labymod.laby3d.api.pipeline.target.RenderTarget;
import net.labymod.laby3d.api.pipeline.target.RenderTargetDescription;
import net.labymod.laby3d.api.pipeline.target.attachment.AttachmentType;
import net.labymod.laby3d.api.pipeline.target.attachment.ClearValue;
import net.labymod.laby3d.api.pipeline.target.attachment.RenderTargetAttachmentDescription;
import net.labymod.laby3d.api.textures.DeviceTexture.Format;
import net.labymod.laby3d.api.textures.SamplerDescription.Filter;
import org.joml.Vector3f;

public class MinimapTexture extends DynamicTexture {

  private static final int CHUNK_X = 16;
  private static final int CHUNK_Z = 16;

  private static final FloatVector2 SUN_POSITION = new FloatVector2(0.9F, -1.0F);
  private static final int SKY_COLOR = ColorFormat.ARGB32.pack(142, 163, 255, 255);

  private final Supplier<MinimapConfiguration> config;
  private final MinimapBounds minimapBounds;
  private final ChunkDataStorage storage;
  private final HeightmapTexture heightmapTexture;
  private final LightmapTexture lightmapTexture;
  private final Laby3D laby3D;

  private final RenderTarget renderTarget;
  private PostProcessor postProcessor;

  private DaylightPeriod currentPeriod = DaylightPeriod.DAYTIME;

  private int highestBlockY;

  private boolean lastUnderground = false;
  private int lastMidChunkX;
  private int lastMidChunkZ;

  public MinimapTexture(
      Supplier<MinimapConfiguration> config,
      ChunkDataStorage storage,
      MinimapBounds minimapBounds
  ) {
    super("minimap/level");
    this.config = config;
    this.storage = storage;
    this.minimapBounds = minimapBounds;
    this.heightmapTexture = new HeightmapTexture();
    this.lightmapTexture = new LightmapTexture();
    this.laby3D = Laby.references().laby3D();

    this.renderTarget = this.laby3D.renderDevice().createTarget(
        () -> "Minimap Target",
        RenderTargetDescription.builder()
            .setSize(this.getWidth(), this.getHeight())
            .addColorAttachment(RenderTargetAttachmentDescription.builder()
                .setType(AttachmentType.COLOR)
                .setFormat(Format.R8G8B8A8_UNORM)
                .setClearValue(ClearValue.color(0.0F, 0.0F, 0.0F, 0.0F))
                .setSamplerDescription(builder -> builder.setFilter(Filter.NEAREST))
                .build())
            .build()
    );
    PostProcessorLoader.loadDynamic(this.renderTarget, Util.newDefaultNamespace("post/shadow.json"),
        processor -> {
          this.postProcessor = processor;

          this.postProcessor.setCustomPostPassProcessor(new CustomPostPassProcessor() {
            @Override
            public void process(PostPassData data, DrawRenderCommand command, float time) {
              MinimapTexture minimapTexture = MinimapTexture.this;
              command.setTexture(1, minimapTexture.texture().deviceTextureView());
              command.setTexture(2, minimapTexture.heightmapTexture.texture().deviceTextureView());
              command.setTexture(3, minimapTexture.lightmapTexture.texture().deviceTextureView());

              CustomPostProcessorUniformBlock minimap = data.getBlock("Minimap");
              minimap.getProperty("SunPosition").set(new Vector3f(SUN_POSITION.getX(), SUN_POSITION.getY(), 1F));
              minimap.getProperty("PixelSize").set(
                  new Vector3f(
                      1.0F / minimapTexture.getWidth(),
                      1.0F / minimapTexture.getHeight(),
                      0F
                  )
              );

              float timeOfDay = MinimapTexture.this.getTimeOfDay();
              float normalizedDayTime = (float) (1.0F - (
                  Math.cos(timeOfDay * (float) (Math.PI * 2)) * 2.0F + 0.2F));
              normalizedDayTime = MathHelper.clamp(normalizedDayTime, 0.0F, 1.0F);
              normalizedDayTime = 1.0F - normalizedDayTime;

              minimap.getProperty("DayTime").set(normalizedDayTime);
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

    Position position = player.position();
    int midX = MathHelper.floor(position.getX());
    int midZ = MathHelper.floor(position.getZ());

    int zoom = this.config.get().zoom().get() * 10;
    int minX = midX - zoom;
    int minZ = midZ - zoom;
    int maxX = midX + zoom;
    int maxZ = midZ + zoom;

    int midChunkX = midX >> 4;
    int midChunkZ = midZ >> 4;

    boolean underground = (this.highestBlockY - (int) (position.getY())) > 10;

    boolean changed = false;
    if ((this.lastMidChunkX != midChunkX && this.lastMidChunkZ != midChunkZ)
        || this.lastUnderground != underground) {
      this.lastMidChunkX = midChunkX;
      this.lastMidChunkZ = midChunkZ;

      this.lastUnderground = underground;
      this.highestBlockY = minBuildHeight;
      changed = true;
    }

    int minChunkX = minX >> 4;
    int minChunkZ = minZ >> 4;

    int maxChunkX = maxX >> 4;
    int maxChunkZ = maxZ >> 4;

    int midInChunkX = midX & 15;
    int midInChunkZ = midZ & 15;

    ColorFormat format = ColorFormat.ARGB32;
    if (changed || this.storage.shouldProcess()) {
      this.resize(maxX - minX, maxZ - minZ);

      this.clearImage(SKY_COLOR);
      this.heightmapTexture.clearImage(0xFF000000);
      this.lightmapTexture.clearImage(0xFF000000);

      this.forEach(minChunkX, minChunkZ, maxChunkX, maxChunkZ, (chunkX, chunkZ, chunk) -> {
        this.storage.compile(chunk);

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
      });

      this.minimapBounds.update(minX, minZ, maxX, maxZ, 0);
      this.storage.processed();

      this.updateTexture();
    }

    this.forEach(minChunkX, minChunkZ, maxChunkX, maxChunkZ, (chunkX, chunkZ, chunk) -> {
      if (chunkX == midChunkX && chunkZ == midChunkZ) {
        this.highestBlockY = chunk.getHeight(midInChunkX, midInChunkZ);
      }
    });
  }

  private void forEach(int minX, int minZ, int maxX, int maxZ, ChunkConsumer consumer) {
    for (int chunkX = minX; chunkX <= maxX; chunkX++) {
      for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
        ChunkData chunk = this.storage.getChunk(chunkX, chunkZ);
        if (chunk == null) {
          continue;
        }

        consumer.accept(chunkX, chunkZ, chunk);
      }
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
  public void render(ScreenContext context, float x, float y, float width, float height) {
    this.postProcessor.process(context.getTickDelta());
    context.canvas().submitGuiBlit(
        MinimapRenderStates.GUI_TEXTURED,
        GuiTextureSet.single(this.renderTarget.findColorTexture(0)),
        x, y, width, height,
        0.0F, 0.0F, 1.0F, 1.0F,
        -1
    );
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

    TextureInfo texture = MinimapDebugger.COLOR_MAP_TEXTURE;
    //texture.setId(this.getId());
    texture.setSize(this.getWidth(), this.getHeight());
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

  @FunctionalInterface
  public interface ChunkConsumer {

    void accept(int chunkX, int chunkZ, ChunkData chunk);

  }
}
