/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.search.suggest;

import com.carrotsearch.randomizedtesting.generators.RandomStrings;

import org.apache.lucene.spatial.util.GeoHashUtils;
import org.apache.lucene.util.LuceneTestCase.SuppressCodecs;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.suggest.CompletionSuggestSearchIT.CompletionMappingBuilder;
import org.elasticsearch.search.suggest.completion.CompletionSuggestionBuilder;
import org.elasticsearch.search.suggest.completion.context.CategoryContextMapping;
import org.elasticsearch.search.suggest.completion.context.CategoryQueryContext;
import org.elasticsearch.search.suggest.completion.context.ContextBuilder;
import org.elasticsearch.search.suggest.completion.context.ContextMapping;
import org.elasticsearch.search.suggest.completion.context.GeoContextMapping;
import org.elasticsearch.search.suggest.completion.context.GeoQueryContext;
import org.elasticsearch.search.suggest.completion.context.QueryContext;
import org.elasticsearch.test.ESIntegTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

@SuppressCodecs("*") // requires custom completion format
public class ContextCompletionSuggestSearchIT extends ESIntegTestCase {

    private final String INDEX = RandomStrings.randomAsciiOfLength(getRandom(), 10).toLowerCase(Locale.ROOT);
    private final String TYPE = RandomStrings.randomAsciiOfLength(getRandom(), 10).toLowerCase(Locale.ROOT);
    private final String FIELD = RandomStrings.randomAsciiOfLength(getRandom(), 10).toLowerCase(Locale.ROOT);

    @Override
    protected int numberOfReplicas() {
        return 0;
    }

    public void testContextPrefix() throws Exception {
        LinkedHashMap<String, ContextMapping> map = new LinkedHashMap<>();
        map.put("cat", ContextBuilder.category("cat").field("cat").build());
        boolean addAnotherContext = randomBoolean();
        if (addAnotherContext) {
            map.put("type", ContextBuilder.category("type").field("type").build());
        }
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder().context(map);
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 0; i < numDocs; i++) {
            XContentBuilder source = jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", "suggestion" + i)
                    .field("weight", i + 1)
                    .endObject()
                    .field("cat", "cat" + i % 2);
                    if (addAnotherContext) {
                        source.field("type", "type" + i % 3);
                    }
                    source.endObject();
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(source));
        }
        indexRandom(true, indexRequestBuilders);
        ensureYellow(INDEX);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg");
        assertSuggestions("foo", prefix, "suggestion9", "suggestion8", "suggestion7", "suggestion6", "suggestion5");
    }

    public void testContextRegex() throws Exception {
        LinkedHashMap<String, ContextMapping> map = new LinkedHashMap<>();
        map.put("cat", ContextBuilder.category("cat").field("cat").build());
        boolean addAnotherContext = randomBoolean();
        if (addAnotherContext) {
            map.put("type", ContextBuilder.category("type").field("type").build());
        }
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder().context(map);
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 0; i < numDocs; i++) {
            XContentBuilder source = jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", "sugg" + i + "estion")
                    .field("weight", i + 1)
                    .endObject()
                    .field("cat", "cat" + i % 2);
            if (addAnotherContext) {
                source.field("type", "type" + i % 3);
            }
            source.endObject();
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(source));
        }
        indexRandom(true, indexRequestBuilders);
        ensureYellow(INDEX);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).regex("sugg.*es");
        assertSuggestions("foo", prefix, "sugg9estion", "sugg8estion", "sugg7estion", "sugg6estion", "sugg5estion");
    }

    public void testContextFuzzy() throws Exception {
        LinkedHashMap<String, ContextMapping> map = new LinkedHashMap<>();
        map.put("cat", ContextBuilder.category("cat").field("cat").build());
        boolean addAnotherContext = randomBoolean();
        if (addAnotherContext) {
            map.put("type", ContextBuilder.category("type").field("type").build());
        }
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder().context(map);
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 0; i < numDocs; i++) {
            XContentBuilder source = jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", "sugxgestion" + i)
                    .field("weight", i + 1)
                    .endObject()
                    .field("cat", "cat" + i % 2);
            if (addAnotherContext) {
                source.field("type", "type" + i % 3);
            }
            source.endObject();
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(source));
        }
        indexRandom(true, indexRequestBuilders);
        ensureYellow(INDEX);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg", Fuzziness.ONE);
        assertSuggestions("foo", prefix, "sugxgestion9", "sugxgestion8", "sugxgestion7", "sugxgestion6", "sugxgestion5");
    }

    public void testSingleContextFiltering() throws Exception {
        CategoryContextMapping contextMapping = ContextBuilder.category("cat").field("cat").build();
        LinkedHashMap<String, ContextMapping> map = new LinkedHashMap<String, ContextMapping>(Collections.singletonMap("cat", contextMapping));
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder().context(map);
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 0; i < numDocs; i++) {
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(jsonBuilder()
                                    .startObject()
                                    .startObject(FIELD)
                                    .field("input", "suggestion" + i)
                                    .field("weight", i + 1)
                                    .endObject()
                                    .field("cat", "cat" + i % 2)
                                    .endObject()
                    ));
        }
        indexRandom(true, indexRequestBuilders);
        ensureYellow(INDEX);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg")
                .contexts(Collections.singletonMap("cat", Collections.singletonList(CategoryQueryContext.builder().setCategory("cat0").build())));

        assertSuggestions("foo", prefix, "suggestion8", "suggestion6", "suggestion4", "suggestion2", "suggestion0");
    }

    public void testSingleContextBoosting() throws Exception {
        CategoryContextMapping contextMapping = ContextBuilder.category("cat").field("cat").build();
        LinkedHashMap<String, ContextMapping> map = new LinkedHashMap<String, ContextMapping>(Collections.singletonMap("cat", contextMapping));
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder().context(map);
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 0; i < numDocs; i++) {
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(jsonBuilder()
                                    .startObject()
                                    .startObject(FIELD)
                                    .field("input", "suggestion" + i)
                                    .field("weight", i + 1)
                                    .endObject()
                                    .field("cat", "cat" + i % 2)
                                    .endObject()
                    ));
        }
        indexRandom(true, indexRequestBuilders);
        ensureYellow(INDEX);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg")
                .contexts(Collections.singletonMap("cat",
                        Arrays.asList(CategoryQueryContext.builder().setCategory("cat0").setBoost(3).build(),
                        CategoryQueryContext.builder().setCategory("cat1").build()))
                );
        assertSuggestions("foo", prefix, "suggestion8", "suggestion6", "suggestion4", "suggestion9", "suggestion2");
    }

    public void testSingleContextMultipleContexts() throws Exception {
        CategoryContextMapping contextMapping = ContextBuilder.category("cat").field("cat").build();
        LinkedHashMap<String, ContextMapping> map = new LinkedHashMap<String, ContextMapping>(Collections.singletonMap("cat", contextMapping));
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder().context(map);
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<String> contexts = Arrays.asList("type1", "type2", "type3", "type4");
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 0; i < numDocs; i++) {
            XContentBuilder source = jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", "suggestion" + i)
                    .field("weight", i + 1)
                    .endObject()
                    .field("cat", contexts)
                    .endObject();
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(source));
        }
        indexRandom(true, indexRequestBuilders);
        ensureYellow(INDEX);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg");

        assertSuggestions("foo", prefix, "suggestion9", "suggestion8", "suggestion7", "suggestion6", "suggestion5");
    }

    public void testMultiContextFiltering() throws Exception {
        LinkedHashMap<String, ContextMapping> map = new LinkedHashMap<>();
        map.put("cat", ContextBuilder.category("cat").field("cat").build());
        map.put("type", ContextBuilder.category("type").field("type").build());
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder().context(map);
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 0; i < numDocs; i++) {
            XContentBuilder source = jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", "suggestion" + i)
                    .field("weight", i + 1)
                    .endObject()
                    .field("cat", "cat" + i % 2)
                    .field("type", "type" + i % 4)
                    .endObject();
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(source));
        }
        indexRandom(true, indexRequestBuilders);
        ensureYellow(INDEX);

        // filter only on context cat
        CompletionSuggestionBuilder catFilterSuggest = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg");
        catFilterSuggest.contexts(Collections.singletonMap("cat", Collections.singletonList(CategoryQueryContext.builder().setCategory("cat0").build())));
        assertSuggestions("foo", catFilterSuggest, "suggestion8", "suggestion6", "suggestion4", "suggestion2", "suggestion0");

        // filter only on context type
        CompletionSuggestionBuilder typeFilterSuggest = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg");
        typeFilterSuggest.contexts(Collections.singletonMap("type", Arrays.asList(CategoryQueryContext.builder().setCategory("type2").build(),
                CategoryQueryContext.builder().setCategory("type1").build())));
        assertSuggestions("foo", typeFilterSuggest, "suggestion9", "suggestion6", "suggestion5", "suggestion2", "suggestion1");

        CompletionSuggestionBuilder multiContextFilterSuggest = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg");
        // query context order should never matter
        Map<String, List<? extends QueryContext>> contextMap = new HashMap<>();
        contextMap.put("type", Collections.singletonList(CategoryQueryContext.builder().setCategory("type2").build()));
        contextMap.put("cat", Collections.singletonList(CategoryQueryContext.builder().setCategory("cat2").build()));
        multiContextFilterSuggest.contexts(contextMap);
        assertSuggestions("foo", multiContextFilterSuggest, "suggestion6", "suggestion2");
    }

    @AwaitsFix(bugUrl = "multiple context boosting is broken, as a suggestion, contexts pair is treated as (num(context) entries)")
    public void testMultiContextBoosting() throws Exception {
        LinkedHashMap<String, ContextMapping> map = new LinkedHashMap<>();
        map.put("cat", ContextBuilder.category("cat").field("cat").build());
        map.put("type", ContextBuilder.category("type").field("type").build());
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder().context(map);
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 0; i < numDocs; i++) {
            XContentBuilder source = jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", "suggestion" + i)
                    .field("weight", i + 1)
                    .endObject()
                    .field("cat", "cat" + i % 2)
                    .field("type", "type" + i % 4)
                    .endObject();
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(source));
        }
        indexRandom(true, indexRequestBuilders);
        ensureYellow(INDEX);

        // boost only on context cat
        CompletionSuggestionBuilder catBoostSuggest = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg");
        catBoostSuggest.contexts(Collections.singletonMap("cat",
                Arrays.asList(
                    CategoryQueryContext.builder().setCategory("cat0").setBoost(3).build(),
                    CategoryQueryContext.builder().setCategory("cat1").build())));
        assertSuggestions("foo", catBoostSuggest, "suggestion8", "suggestion6", "suggestion4", "suggestion9", "suggestion2");

        // boost only on context type
        CompletionSuggestionBuilder typeBoostSuggest = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg");
        typeBoostSuggest.contexts(Collections.singletonMap("type",
                Arrays.asList(
                    CategoryQueryContext.builder().setCategory("type2").setBoost(2).build(),
                    CategoryQueryContext.builder().setCategory("type1").setBoost(4).build())));
        assertSuggestions("foo", typeBoostSuggest, "suggestion9", "suggestion5", "suggestion6", "suggestion1", "suggestion2");

        // boost on both contexts
        CompletionSuggestionBuilder multiContextBoostSuggest = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg");
        // query context order should never matter
        Map<String, List<? extends QueryContext>> contextMap = new HashMap<>();
        contextMap.put("type", Arrays.asList(
            CategoryQueryContext.builder().setCategory("type2").setBoost(2).build(),
            CategoryQueryContext.builder().setCategory("type1").setBoost(4).build())
        );
        contextMap.put("cat", Arrays.asList(
            CategoryQueryContext.builder().setCategory("cat0").setBoost(3).build(),
            CategoryQueryContext.builder().setCategory("cat1").build())
        );
        multiContextBoostSuggest.contexts(contextMap);
        assertSuggestions("foo", multiContextBoostSuggest, "suggestion9", "suggestion6", "suggestion5", "suggestion2", "suggestion1");
    }

    public void testMissingContextValue() throws Exception {
        LinkedHashMap<String, ContextMapping> map = new LinkedHashMap<>();
        map.put("cat", ContextBuilder.category("cat").field("cat").build());
        map.put("type", ContextBuilder.category("type").field("type").build());
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder().context(map);
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 0; i < numDocs; i++) {
            XContentBuilder source = jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", "suggestion" + i)
                    .field("weight", i + 1)
                    .endObject();
            if (randomBoolean()) {
                source.field("cat", "cat" + i % 2);
            }
            if (randomBoolean()) {
                source.field("type", "type" + i % 4);
            }
            source.endObject();
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(source));
        }
        indexRandom(true, indexRequestBuilders);
        ensureYellow(INDEX);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg");
        assertSuggestions("foo", prefix, "suggestion9", "suggestion8", "suggestion7", "suggestion6", "suggestion5");
    }

    public void testSeveralContexts() throws Exception {
        LinkedHashMap<String, ContextMapping> map = new LinkedHashMap<>();
        final int numContexts = randomIntBetween(2, 5);
        for (int i = 0; i < numContexts; i++) {
            map.put("type" + i, ContextBuilder.category("type" + i).field("type" + i).build());
        }
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder().context(map);
        createIndexAndMapping(mapping);
        int numDocs = randomIntBetween(10, 200);
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 0; i < numDocs; i++) {
            XContentBuilder source = jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", "suggestion" + i)
                    .field("weight", numDocs - i)
                    .endObject();
            for (int c = 0; c < numContexts; c++) {
                source.field("type"+c, "type" + c +i % 4);
            }
            source.endObject();
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(source));
        }
        indexRandom(true, indexRequestBuilders);
        ensureYellow(INDEX);

        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg");
        assertSuggestions("foo", prefix, "suggestion0", "suggestion1", "suggestion2", "suggestion3", "suggestion4");
    }

    public void testSimpleGeoPrefix() throws Exception {
        LinkedHashMap<String, ContextMapping> map = new LinkedHashMap<>();
        map.put("geo", ContextBuilder.geo("geo").build());
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder().context(map);
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 0; i < numDocs; i++) {
            XContentBuilder source = jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", "suggestion" + i)
                    .field("weight", i + 1)
                    .startObject("contexts")
                    .field("geo", GeoHashUtils.stringEncode(1.2, 1.3))
                    .endObject()
                    .endObject().endObject();
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(source));
        }
        indexRandom(true, indexRequestBuilders);
        ensureYellow(INDEX);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg");
        assertSuggestions("foo", prefix, "suggestion9", "suggestion8", "suggestion7", "suggestion6", "suggestion5");
    }

    public void testGeoFiltering() throws Exception {
        LinkedHashMap<String, ContextMapping> map = new LinkedHashMap<>();
        map.put("geo", ContextBuilder.geo("geo").build());
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder().context(map);
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        GeoPoint[] geoPoints = new GeoPoint[] {new GeoPoint("ezs42e44yx96"), new GeoPoint("u4pruydqqvj8")};
        for (int i = 0; i < numDocs; i++) {
            XContentBuilder source = jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", "suggestion" + i)
                    .field("weight", i + 1)
                    .startObject("contexts")
                    .field("geo", (i % 2 == 0) ? geoPoints[0].getGeohash() : geoPoints[1].getGeohash())
                    .endObject()
                    .endObject().endObject();
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(source));
        }
        indexRandom(true, indexRequestBuilders);
        ensureYellow(INDEX);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg");
        assertSuggestions("foo", prefix, "suggestion9", "suggestion8", "suggestion7", "suggestion6", "suggestion5");

        CompletionSuggestionBuilder geoFilteringPrefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg")
                .contexts(Collections.singletonMap("geo", Collections.singletonList(
                    GeoQueryContext.builder().setGeoPoint(new GeoPoint(geoPoints[0])).build())));

        assertSuggestions("foo", geoFilteringPrefix, "suggestion8", "suggestion6", "suggestion4", "suggestion2", "suggestion0");
    }

    public void testGeoBoosting() throws Exception {
        LinkedHashMap<String, ContextMapping> map = new LinkedHashMap<>();
        map.put("geo", ContextBuilder.geo("geo").build());
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder().context(map);
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        GeoPoint[] geoPoints = new GeoPoint[] {new GeoPoint("ezs42e44yx96"), new GeoPoint("u4pruydqqvj8")};
        for (int i = 0; i < numDocs; i++) {
            XContentBuilder source = jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", "suggestion" + i)
                    .field("weight", i + 1)
                    .startObject("contexts")
                    .field("geo", (i % 2 == 0) ? geoPoints[0].getGeohash() : geoPoints[1].getGeohash())
                    .endObject()
                    .endObject().endObject();
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(source));
        }
        indexRandom(true, indexRequestBuilders);
        ensureYellow(INDEX);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg");
        assertSuggestions("foo", prefix, "suggestion9", "suggestion8", "suggestion7", "suggestion6", "suggestion5");

        GeoQueryContext context1 = GeoQueryContext.builder().setGeoPoint(geoPoints[0]).setBoost(2).build();
        GeoQueryContext context2 = GeoQueryContext.builder().setGeoPoint(geoPoints[1]).build();
        CompletionSuggestionBuilder geoBoostingPrefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg")
                .contexts(Collections.singletonMap("geo", Arrays.asList(context1, context2)));

        assertSuggestions("foo", geoBoostingPrefix, "suggestion8", "suggestion6", "suggestion4", "suggestion9", "suggestion7");
    }

    public void testGeoPointContext() throws Exception {
        LinkedHashMap<String, ContextMapping> map = new LinkedHashMap<>();
        map.put("geo", ContextBuilder.geo("geo").build());
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder().context(map);
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 0; i < numDocs; i++) {
            XContentBuilder source = jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", "suggestion" + i)
                    .field("weight", i + 1)
                    .startObject("contexts")
                    .startObject("geo")
                        .field("lat", 52.22)
                        .field("lon", 4.53)
                    .endObject()
                    .endObject()
                    .endObject().endObject();
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(source));
        }
        indexRandom(true, indexRequestBuilders);
        ensureYellow(INDEX);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg")
                .contexts(Collections.singletonMap("geo", Collections.singletonList(GeoQueryContext.builder().setGeoPoint(new GeoPoint(52.2263, 4.543)).build())));
        assertSuggestions("foo", prefix, "suggestion9", "suggestion8", "suggestion7", "suggestion6", "suggestion5");
    }

    public void testGeoNeighbours() throws Exception {
        String geohash = "gcpv";
        List<String> neighbours = new ArrayList<>();
        neighbours.add("gcpw");
        neighbours.add("gcpy");
        neighbours.add("u10n");
        neighbours.add("gcpt");
        neighbours.add("u10j");
        neighbours.add("gcps");
        neighbours.add("gcpu");
        neighbours.add("u10h");

        LinkedHashMap<String, ContextMapping> map = new LinkedHashMap<>();
        map.put("geo", ContextBuilder.geo("geo").precision(4).build());
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder().context(map);
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 0; i < numDocs; i++) {
            XContentBuilder source = jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", "suggestion" + i)
                    .field("weight", i + 1)
                    .startObject("contexts")
                    .field("geo", randomFrom(neighbours))
                    .endObject()
                    .endObject().endObject();
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(source));
        }
        indexRandom(true, indexRequestBuilders);
        ensureYellow(INDEX);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg");
        assertSuggestions("foo", prefix, "suggestion9", "suggestion8", "suggestion7", "suggestion6", "suggestion5");

        CompletionSuggestionBuilder geoNeighbourPrefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg")
                .contexts(Collections.singletonMap("geo", Collections.singletonList(GeoQueryContext.builder().setGeoPoint(GeoPoint.fromGeohash(geohash)).build())));

        assertSuggestions("foo", geoNeighbourPrefix, "suggestion9", "suggestion8", "suggestion7", "suggestion6", "suggestion5");
    }

    public void testGeoField() throws Exception {

        XContentBuilder mapping = jsonBuilder();
        mapping.startObject();
        mapping.startObject(TYPE);
        mapping.startObject("properties");
        mapping.startObject("pin");
        mapping.field("type", "geo_point");
        mapping.endObject();
        mapping.startObject(FIELD);
        mapping.field("type", "completion");
        mapping.field("analyzer", "simple");

        mapping.startArray("contexts");
        mapping.startObject();
        mapping.field("name", "st");
        mapping.field("type", "geo");
        mapping.field("path", "pin");
        mapping.field("precision", 5);
        mapping.endObject();
        mapping.endArray();

        mapping.endObject();
        mapping.endObject();
        mapping.endObject();
        mapping.endObject();

        assertAcked(prepareCreate(INDEX).addMapping(TYPE, mapping));
        ensureYellow();

        XContentBuilder source1 = jsonBuilder()
                .startObject()
                .latlon("pin", 52.529172, 13.407333)
                .startObject(FIELD)
                .array("input", "Hotel Amsterdam in Berlin")
                .endObject()
                .endObject();
        client().prepareIndex(INDEX, TYPE, "1").setSource(source1).execute().actionGet();

        XContentBuilder source2 = jsonBuilder()
                .startObject()
                .latlon("pin", 52.363389, 4.888695)
                .startObject(FIELD)
                .array("input", "Hotel Berlin in Amsterdam")
                .endObject()
                .endObject();
        client().prepareIndex(INDEX, TYPE, "2").setSource(source2).execute().actionGet();

        refresh();

        String suggestionName = randomAsciiOfLength(10);
        CompletionSuggestionBuilder context = SuggestBuilders.completionSuggestion(FIELD).text("h").size(10)
                .contexts(Collections.singletonMap("st", Collections.singletonList(GeoQueryContext.builder().setGeoPoint(new GeoPoint(52.52, 13.4)).build())));
        SearchResponse searchResponse = client().prepareSearch(INDEX).suggest(new SuggestBuilder().addSuggestion(suggestionName, context)).get();

        assertEquals(searchResponse.getSuggest().size(), 1);
        assertEquals("Hotel Amsterdam in Berlin", searchResponse.getSuggest().getSuggestion(suggestionName).iterator().next().getOptions().iterator().next().getText().string());
    }

    public void assertSuggestions(String suggestionName, SuggestionBuilder suggestBuilder, String... suggestions) {
        SearchResponse searchResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion(suggestionName, suggestBuilder)
        ).execute().actionGet();
        CompletionSuggestSearchIT.assertSuggestions(searchResponse, suggestionName, suggestions);
    }

    private void createIndexAndMapping(CompletionMappingBuilder completionMappingBuilder) throws IOException {
        createIndexAndMappingAndSettings(Settings.EMPTY, completionMappingBuilder);
    }
    private void createIndexAndMappingAndSettings(Settings settings, CompletionMappingBuilder completionMappingBuilder) throws IOException {
        XContentBuilder mapping = jsonBuilder().startObject()
                .startObject(TYPE).startObject("properties")
                .startObject(FIELD)
                .field("type", "completion")
                .field("analyzer", completionMappingBuilder.indexAnalyzer)
                .field("search_analyzer", completionMappingBuilder.searchAnalyzer)
                .field("preserve_separators", completionMappingBuilder.preserveSeparators)
                .field("preserve_position_increments", completionMappingBuilder.preservePositionIncrements);

        if (completionMappingBuilder.contextMappings != null) {
            mapping = mapping.startArray("contexts");
            for (Map.Entry<String, ContextMapping> contextMapping : completionMappingBuilder.contextMappings.entrySet()) {
                mapping = mapping.startObject()
                        .field("name", contextMapping.getValue().name())
                        .field("type", contextMapping.getValue().type().name());
                switch (contextMapping.getValue().type()) {
                    case CATEGORY:
                        final String fieldName = ((CategoryContextMapping) contextMapping.getValue()).getFieldName();
                        if (fieldName != null) {
                            mapping = mapping.field("path", fieldName);
                        }
                        break;
                    case GEO:
                        final String name = ((GeoContextMapping) contextMapping.getValue()).getFieldName();
                        mapping = mapping
                                .field("precision", ((GeoContextMapping) contextMapping.getValue()).getPrecision());
                        if (name != null) {
                            mapping.field("path", name);
                        }
                        break;
                }

                mapping = mapping.endObject();
            }

            mapping = mapping.endArray();
        }
        mapping = mapping.endObject()
                .endObject().endObject()
                .endObject();

        assertAcked(client().admin().indices().prepareCreate(INDEX)
                .setSettings(Settings.settingsBuilder().put(indexSettings()).put(settings))
                .addMapping(TYPE, mapping)
                .get());
        ensureYellow();
    }
}
