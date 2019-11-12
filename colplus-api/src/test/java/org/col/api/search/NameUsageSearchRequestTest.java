package org.col.api.search;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.col.api.jackson.ApiModule;
import org.col.api.jackson.SerdeTestBase;
import org.col.api.search.NameUsageSearchRequest.SearchContent;
import org.col.api.vocab.NomStatus;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NameUsageSearchRequestTest extends SerdeTestBase<NameUsageSearchRequest> {

  public NameUsageSearchRequestTest() {
    super(NameUsageSearchRequest.class);
  }

  @Override
  public NameUsageSearchRequest genTestValue() throws Exception {
    NameUsageSearchRequest s = new NameUsageSearchRequest();
    s.setQ("Abies");
    s.setContent(new HashSet<>(Arrays.asList(NameUsageSearchRequest.SearchContent.AUTHORSHIP)));
    s.setSortBy(NameUsageSearchRequest.SortBy.NATIVE);
    s.addFilter(NameUsageSearchParameter.NOM_STATUS, NomStatus.MANUSCRIPT);
    s.addFilter(NameUsageSearchParameter.NOM_STATUS, NomStatus.CHRESONYM);
    return s;
  }

  @Test(expected = IllegalArgumentException.class)
  public void badInt() {
    NameUsageSearchRequest r = new NameUsageSearchRequest();
    r.addFilter(NameUsageSearchParameter.DATASET_KEY, "fgh");
  }

  @Test(expected = IllegalArgumentException.class)
  public void badEnum() {
    NameUsageSearchRequest r = new NameUsageSearchRequest();
    r.addFilter(NameUsageSearchParameter.RANK, "spezi");
  }

  @Test
  public void addFilterGood() {
    NameUsageSearchRequest r = new NameUsageSearchRequest();
    r.addFilter(NameUsageSearchParameter.DATASET_KEY, "123");
    r.addFilter(NameUsageSearchParameter.DATASET_KEY, 1234);
    assertEquals(ImmutableList.of(123, 1234), r.getFilterValues(NameUsageSearchParameter.DATASET_KEY));
    r.addFilter(NameUsageSearchParameter.DATASET_KEY, Lists.newArrayList(1234, 12, 13, 14));
    assertEquals(ImmutableList.of(123, 1234, 1234, 12, 13, 14), r.getFilterValues(NameUsageSearchParameter.DATASET_KEY));

    r.addFilter(NameUsageSearchParameter.DATASET_KEY, Lists.newArrayList("1", "2"));
    assertEquals(ImmutableList.of(123, 1234, 1234, 12, 13, 14, 1, 2), r.getFilterValues(NameUsageSearchParameter.DATASET_KEY));
  }

  @Test
  public void copy01() {
    NameUsageSearchRequest r = new NameUsageSearchRequest();
    r.addFilter(NameUsageSearchParameter.DATASET_KEY, "123");
    r.addFilter(NameUsageSearchParameter.DATASET_KEY, 1234);
    assertEquals(r, r.copy());

    r.setContent(null);
    assertEquals(r, r.copy());

    r.setContent(Sets.newHashSet(NameUsageSearchRequest.SearchContent.AUTHORSHIP));
    assertEquals(r, r.copy());

    r.setContent(Sets.newHashSet());
    r.copy();
  }

  @Test // Tests #510 (bad behaviour if filters/facets/content is empty).
  public void copy02() {
    NameUsageSearchRequest r0 = new NameUsageSearchRequest();
    NameUsageSearchRequest r1 = r0.copy();
    assertFalse(r1.hasFilters());
    assertTrue(r1.getFacets().isEmpty());
    assertEquals(EnumSet.allOf(SearchContent.class),r1.getContent());  
  }

  protected void debug(String json, Wrapper<NameUsageSearchRequest> wrapper, Wrapper<NameUsageSearchRequest> wrapper2) {
    try {
      System.out.println(ApiModule.MAPPER.writeValueAsString(wrapper.value));
      System.out.println(ApiModule.MAPPER.writeValueAsString(wrapper2.value));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }
}