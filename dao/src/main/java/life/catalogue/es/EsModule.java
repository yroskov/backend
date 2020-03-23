package life.catalogue.es;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.BareName;
import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.Synonym;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.ddl.IndexDefinition;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.response.EsFacet;
import life.catalogue.es.response.EsMultiResponse;
import life.catalogue.es.response.EsResponse;
import life.catalogue.es.response.SearchHit;

/**
 * Jackson module to configure object mappers required by ES code. Various categories of objects need to be (de)serialized:
 * <p>
 * <ol>
 * <li>In order to create an index (DDL-like actions), we need to <b>write</b> document type mappings and larger structures like
 * {@link IndexDefinition} objects.
 * <li>In order to index data, we need to write name usage documents (modelled by the {@link EsNameUsage} class).
 * <li>The {@code NameUsageDocument} class is a dressed-down and flattened version of the {@link NameUsageWrapper}, but it has a field
 * containing the serialized version of the entire {@code  NameUsageWrapper} object. So in order to index data we also need to <b>write</b>
 * {@code  NameUsageWrapper} objects.
 * <li>We need to <b>write</b> Elasticsearch queries, modelled by {@link EsSearchRequest} and the other classes in
 * {@code life.catalogue.es.query}.
 * <li>We need to <b>read</b> the Elasticsearch response
 * <li>We need to <b>read</b> the {@code NameUsageDocument} instances wrapped into the {@link SearchHit} instances within the response.
 * <li>We need to <b>read</b> the {@code  NameUsageWrapper} objects within the name usage documents in order to pass them up to the higher
 * levels of the backend. (Note though that we do not need to write them out again in order to serve them to client. That is left to the
 * REST layer and the {@link ApiModule}.)
 * </ol>
 * </p>
 * <p>
 * A few things need to be aware of when deciding how to configure the mappers:
 * <ol>
 * <li>Whatever the API and the {@code ApiModule} does, we strongly prefer to save enums as integers for space and performance reasons.
 * <li>However, in queries and DDL-like objects enums must be written as strings. This is not about the objects being saved or retrieved,
 * but about instructing Elasticsearch how to do it, and here Elasticsearch expects strings like "AND" and "OR"; not "1" and "0".
 * <li>Currently, most classes in {@code life.catalogue.es.query} have no getters or setters at all. They are just there to reflect the
 * structure of the Elasticsearch query DSL. So until we fix that, we are basically forced to serialize them using fields rather than
 * getters.
 * <li>The API and the {@code ApiModule} serializes {@code NameUsageWrapper} objects using their getters, so we need to ask ourselves
 * whether this discrepancy could be dangerous. However, until that turns out to be the case, we'll assume that if the ES code simply reads
 * and writes the entire state of a {@code NameUsageWrapper} object, the {@code ApiModule} should have no problems serializing it to the
 * client. In other words we'll assume that it's save to serialize using fields whatever the category of the object being serialized.
 * <li>When querying name usage documents, we get them wrapped into an Elasticsearch response object. The name usage documents need a mapper
 * that maps (and writes) enums to integers. The Elasticsearch response object needs a mapper that maps enums to strings. That looks like a
 * conundrum. However, we are now talking about <i>reading</i> JSON, not writing it, and Jackson will just try everything to infer the
 * intended enum constant. So it doesn't really matter which mapper we use for deserialization. Narrow escape though.
 * </ol>
 * </p>
 */
public class EsModule extends SimpleModule {

  /*
   * We don't expose any of the mappers, readers and writers anymore, because we want full control over which things can be read and/or
   * written, and which reader/writer to use. Before we had code fabricating readers and writers (and even mappers) all over the place.
   */

  private static final ObjectMapper esObjectMapper = configureMapper(new ObjectMapper(), false);
  private static final ObjectMapper contentMapper = configureMapper(new ObjectMapper(), true);

  private static final ObjectWriter ddlWriter = esObjectMapper.writerFor(IndexDefinition.class);
  private static final ObjectWriter queryWriter = esObjectMapper.writerFor(EsSearchRequest.class);

  private static final ObjectReader responseReader = contentMapper.readerFor(new TypeReference<EsResponse<EsNameUsage>>() {});
  private static final ObjectReader multiResponseReader =
      contentMapper.readerFor(new TypeReference<EsMultiResponse<EsNameUsage, EsResponse<EsNameUsage>>>() {});

  private static final ObjectReader documentReader = contentMapper.readerFor(EsNameUsage.class);
  private static final ObjectWriter documentWriter = contentMapper.writerFor(EsNameUsage.class);

  private static final ObjectReader nameUsageReader = contentMapper.readerFor(NameUsageWrapper.class);
  private static final ObjectWriter nameUsageWriter = contentMapper.writerFor(NameUsageWrapper.class);

  private static final TypeReference<Map<String, Object>> mapType = new TypeReference<Map<String, Object>>() {};

  /**
   * Generic read method.
   * 
   * @param is
   * @return
   * @throws IOException
   */
  public static Map<String, Object> readIntoMap(InputStream is) throws IOException {
    // Any mapper will do
    return esObjectMapper.readValue(is, mapType);
  }

  /**
   * Escapes the provided string such that in can be embedded within a json document. Note that you should NOT surround the returned string
   * with double quotes. That's already done by this method; just embed what you get.
   * 
   * @param s
   * @return
   */
  public static String escape(String s) {
    // Any mapper will do
    try {
      return esObjectMapper.writeValueAsString(s);
    } catch (JsonProcessingException e) { // Won't happen
      throw new IllegalArgumentException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T convertValue(Object object, Class<EsFacet> class1) {
    // Any mapper will do
    return (T) esObjectMapper.convertValue(object, class1);
  }

  public static <T> T readDDLObject(InputStream is, Class<T> cls) throws IOException {
    return esObjectMapper.readValue(is, cls);
  }

  public static EsResponse<EsNameUsage> readEsResponse(InputStream is) throws IOException {
    return responseReader.readValue(is);
  }

  public static EsMultiResponse<EsNameUsage, EsResponse<EsNameUsage>> readEsMultiResponse(InputStream is) throws IOException {
    return multiResponseReader.readValue(is);
  }

  public static EsNameUsage readDocument(InputStream is) throws IOException {
    return documentReader.readValue(is);
  }

  public static EsNameUsage readDocument(String json) throws IOException {
    return documentReader.readValue(json);
  }

  public static NameUsageWrapper readNameUsageWrapper(InputStream is) throws IOException {
    return nameUsageReader.readValue(is);
  }

  public static NameUsageWrapper readNameUsageWrapper(String json) throws IOException {
    return nameUsageReader.readValue(json);
  }

  public static String write(IndexDefinition indexDef) throws JsonProcessingException {
    return ddlWriter.writeValueAsString(indexDef);
  }

  public static String write(EsSearchRequest query) throws JsonProcessingException {
    return queryWriter.writeValueAsString(query);
  }

  public static String write(EsNameUsage document) throws JsonProcessingException {
    return documentWriter.writeValueAsString(document);
  }

  public static String write(NameUsageWrapper nuw) throws JsonProcessingException {
    return nameUsageWriter.writeValueAsString(nuw);
  }

  public static void write(OutputStream out, NameUsageWrapper nuw) throws IOException {
    nameUsageWriter.writeValue(out, nuw);
  }

  public static String writeDebug(Object obj) {
    try {
      return DEBUG_WRITER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new EsException(e);
    }
  }

  public static void writeDebug(OutputStream out, Object obj) {
    // Jackson would close the outputstream when done; very undesirable, especially with System.out
    String s = writeDebug(obj);
    if (out instanceof PrintStream) {
      ((PrintStream) out).println(s);
    } else {
      new PrintStream(out).println(s);
    }
  }

  private static final ObjectWriter DEBUG_WRITER = configureMapper(new ObjectMapper(), false)
      .writer()
      .withDefaultPrettyPrinter();

  public EsModule() {
    super("Elasticsearch");
  }

  @Override
  public void setupModule(SetupContext ctxt) {
    // required to properly register serdes
    super.setupModule(ctxt);
    ctxt.setMixInAnnotations(NameUsage.class, NameUsageMixIn.class);
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
  @JsonSubTypes({@JsonSubTypes.Type(value = Taxon.class, name = "T"),
      @JsonSubTypes.Type(value = BareName.class, name = "B"),
      @JsonSubTypes.Type(value = Synonym.class, name = "S")})
  abstract class NameUsageMixIn {
  }

  private static ObjectMapper configureMapper(ObjectMapper mapper, boolean enumToInt) {
    mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    if (enumToInt) {
      mapper.enable(SerializationFeature.WRITE_ENUMS_USING_INDEX);
    } else {
      mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
    }
    mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    mapper.registerModule(new JavaTimeModule());
    mapper.registerModule(new EsModule());
    return mapper;
  }

}
