package life.catalogue.resources;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Joiner;
import com.google.common.collect.Streams;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.*;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.NameUsageWrapperMapper;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.es.InvalidQueryException;
import life.catalogue.es.NameUsageSearchService;
import life.catalogue.es.NameUsageSuggestionService;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.stream.Stream;

@Produces(MediaType.APPLICATION_JSON)
@Path("/dataset/{datasetKey}/nameusage")
public class NameUsageResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageResource.class);
  private static final Joiner COMMA_CAT = Joiner.on(';').skipNulls();
  private static final Object[][] NAME_HEADER = new Object[1][];
  static {
    NAME_HEADER[0] = new Object[]{"ID", "parentID", "status", "rank", "scientificName", "authorship", "issues"};
  }
  private final NameUsageSearchService searchService;
  private final NameUsageSuggestionService suggestService;

  public NameUsageResource(NameUsageSearchService search, NameUsageSuggestionService suggest) {
    this.searchService = search;
    this.suggestService = suggest;
  }

  @GET
  public ResultPage<NameUsageBase> list(@PathParam("datasetKey") int datasetKey, @Valid Page page, @Context SqlSession session) {
    Page p = page == null ? new Page() : page;
    NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
    List<NameUsageBase> result = mapper.list(datasetKey, p);
    return new ResultPage<>(p, result, () -> mapper.count(datasetKey));
  }

  @GET
  @Produces({MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV})
  public Stream<Object[]> exportCsv(@PathParam("datasetKey") int datasetKey,
                                  @QueryParam("issue") boolean withIssueOnly,
                                  @Context SqlSession session) {
    NameUsageWrapperMapper nuwm = session.getMapper(NameUsageWrapperMapper.class);
    return Stream.concat(
            Stream.of(NAME_HEADER),
            Streams.stream(nuwm.processDatasetUsageOnly(datasetKey, withIssueOnly))
              .map(nu -> new Object[]{
                  nu.getId(),
                  ((NameUsageBase) nu.getUsage()).getParentId(),
                  nu.getUsage().getStatus(),
                  nu.getUsage().getName().getRank(),
                  nu.getUsage().getName().getScientificName(),
                  nu.getUsage().getName().getAuthorship(),
                  COMMA_CAT.join(nu.getIssues())
            })
    );
  }

  @GET
  @Path("{id}")
  public NameUsageWrapper getByID(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id) {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(NameUsageSearchParameter.DATASET_KEY, datasetKey);
    req.addFilter(NameUsageSearchParameter.USAGE_ID, id);
    ResultPage<NameUsageWrapper> results = searchService.search(req, new Page());
    if (results.size()==1) {
      return results.getResult().get(0);
    }
    throw NotFoundException.idNotFound(NameUsage.class, datasetKey, id);
  }

  @GET
  @Timed
  @Path("search")
  public ResultPage<NameUsageWrapper> searchDataset(@PathParam("datasetKey") int datasetKey,
                                                    @BeanParam NameUsageSearchRequest query,
                                                    @Valid @BeanParam Page page,
                                                    @Context UriInfo uri) throws InvalidQueryException {
    query.addFilters(uri.getQueryParameters());
    if (query.hasFilter(NameUsageSearchParameter.DATASET_KEY)) {
      throw new IllegalArgumentException("No further datasetKey parameter allowed, search already scoped to datasetKey=" + datasetKey);
    }
    query.addFilter(NameUsageSearchParameter.DATASET_KEY, datasetKey);
    return searchService.search(query, page);
  }

  @POST
  @Path("search")
  public ResultPage<NameUsageWrapper> searchPOST(@PathParam("datasetKey") int datasetKey,
                                                 @Valid NameUsageSearchResource.SearchRequestBody req,
                                                 @Context UriInfo uri) throws InvalidQueryException {
    return searchDataset(datasetKey, req.request, req.page, uri);
  }

  @GET
  @Timed
  @Path("suggest")
  public NameUsageSuggestResponse suggestDataset(@PathParam("datasetKey") int datasetKey,
                                                 @BeanParam NameUsageSuggestRequest query) throws InvalidQueryException {
    if (query.getDatasetKey() != null && !query.getDatasetKey().equals(datasetKey)) {
      throw new IllegalArgumentException("No further datasetKey parameter allowed, suggest already scoped to datasetKey=" + datasetKey);
    }
    query.setDatasetKey(datasetKey);
    return suggestService.suggest(query);
  }
}
