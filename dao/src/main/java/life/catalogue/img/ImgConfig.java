package life.catalogue.img;

import java.nio.file.Path;
import javax.validation.constraints.NotNull;

import life.catalogue.db.PgDbConfig;

/**
 * A configuration for the postgres database connection pool as used by the mybatis layer.
 */
@SuppressWarnings("PublicField")
public class ImgConfig extends PgDbConfig {
  
  public enum Scale {
    ORIGINAL,
    LARGE,
    MEDIUM,
    SMALL
  }
  
  @NotNull
  public Path repo;
  
  @NotNull
  public Size small = new Size(30, 90);
  
  @NotNull
  public Size medium = new Size(100, 300);

  @NotNull
  public Size large = new Size(200, 600);
  
  public Size size(Scale scale) {
    switch (scale) {
      case LARGE:
        return large;
      case MEDIUM:
        return medium;
      case SMALL:
        return small;
    }
    throw new IllegalArgumentException("No raw size supported");
  }
  
  public Path datasetLogo(int datasetKey, Scale scale) {
    return repo.resolve("dataset").resolve(filename(datasetKey + "-logo", scale));
  }
  
  private String filename(String prefix, Scale scale) {
    return String.format("%s-%s.%s", prefix, scale.name().toLowerCase(), ImageServiceFS.IMAGE_FORMAT);
  }
  
}
