// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.path.Path;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.integration.ml.XGBoostImporter;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Replaces instances of the xgboost(model-path)
 * pseudofeature with the native Vespa ranking expression implementing
 * the same computation.
 *
 * @author grace-lam
 * @author bratseth
 */
public class XgboostFeatureConverter extends ExpressionTransformer<RankProfileTransformContext> {

    /** A cache of imported models indexed by model path. This avoids importing the same model multiple times. */
    private final Map<Path, ConvertedModel> convertedXGBoostModels = new HashMap<>();

    @Override
    public ExpressionNode transform(ExpressionNode node, RankProfileTransformContext context) {
        if (node instanceof ReferenceNode)
            return transformFeature((ReferenceNode) node, context);
        else if (node instanceof CompositeNode)
            return super.transformChildren((CompositeNode) node, context);
        else
            return node;
    }

    private ExpressionNode transformFeature(ReferenceNode feature, RankProfileTransformContext context) {
        if ( ! feature.getName().equals("xgboost")) return feature;

        try {
            Path modelPath = Path.fromString(ConvertedModel.FeatureArguments.asString(feature.getArguments().expressions().get(0)));
            ConvertedModel convertedModel =
                    convertedXGBoostModels.computeIfAbsent(modelPath, __ -> ConvertedModel.fromSourceOrStore(modelPath, context));
            return convertedModel.expression(asFeatureArguments(feature.getArguments()), context);
        } catch (IllegalArgumentException | UncheckedIOException e) {
            throw new IllegalArgumentException("Could not use XGBoost model from " + feature, e);
        }
    }

    private ConvertedModel.FeatureArguments asFeatureArguments(Arguments arguments) {
        if (arguments.size() != 1)
            throw new IllegalArgumentException("An xgboost node must take a single argument pointing to " +
                                               "the xgboost model directory under [application]/models");
        return new ConvertedModel.FeatureArguments(arguments);
    }

}
