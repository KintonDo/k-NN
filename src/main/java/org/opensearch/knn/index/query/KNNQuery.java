/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query;

import lombok.Getter;
import lombok.Setter;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.opensearch.knn.index.KNNSettings;

import java.io.IOException;

/**
 * Custom KNN query. Query is used for KNNEngine's that create their own custom segment files. These files need to be
 * loaded and queried in a custom manner throughout the query path.
 */
public class KNNQuery extends Query {

    private final String field;
    private final float[] queryVector;
    private final int k;
    private final String indexName;

    @Getter
    @Setter
    private Query filterQuery;

    public KNNQuery(String field, float[] queryVector, int k, String indexName) {
        this.field = field;
        this.queryVector = queryVector;
        this.k = k;
        this.indexName = indexName;
    }

    public KNNQuery(String field, float[] queryVector, int k, String indexName, Query filterQuery) {
        this.field = field;
        this.queryVector = queryVector;
        this.k = k;
        this.indexName = indexName;
        this.filterQuery = filterQuery;
    }

    public String getField() {
        return this.field;
    }

    public float[] getQueryVector() {
        return this.queryVector;
    }

    public int getK() {
        return this.k;
    }

    public String getIndexName() {
        return this.indexName;
    }

    /**
     * Constructs Weight implementation for this query
     *
     * @param searcher  searcher for given segment
     * @param scoreMode  How the produced scorers will be consumed.
     * @param boost     The boost that is propagated by the parent queries.
     * @return Weight   For calculating scores
     */
    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        if (!KNNSettings.isKNNPluginEnabled()) {
            throw new IllegalStateException("KNN plugin is disabled. To enable update knn.plugin.enabled to true");
        }
        final Weight filterWeight = getFilterWeight(searcher);
        if (filterWeight != null) {
            return new KNNWeight(this, boost, filterWeight);
        }
        return new KNNWeight(this, boost);
    }

    private Weight getFilterWeight(IndexSearcher searcher) throws IOException {
        if (this.getFilterQuery() != null) {
            // Run the filter query
            final BooleanQuery booleanQuery = new BooleanQuery.Builder().add(this.getFilterQuery(), BooleanClause.Occur.FILTER)
                .add(new FieldExistsQuery(this.getField()), BooleanClause.Occur.FILTER)
                .build();
            final Query rewritten = searcher.rewrite(booleanQuery);
            return searcher.createWeight(rewritten, ScoreMode.COMPLETE_NO_SCORES, 1f);
        }
        return null;
    }

    @Override
    public void visit(QueryVisitor visitor) {

    }

    @Override
    public String toString(String field) {
        return field;
    }

    @Override
    public int hashCode() {
        return field.hashCode() ^ queryVector.hashCode() ^ k;
    }

    @Override
    public boolean equals(Object other) {
        return sameClassAs(other) && equalsTo(getClass().cast(other));
    }

    private boolean equalsTo(KNNQuery other) {
        return this.field.equals(other.getField()) && this.queryVector.equals(other.getQueryVector()) && this.k == other.getK();
    }
};
