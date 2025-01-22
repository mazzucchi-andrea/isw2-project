package it.mazz.isw2.ml.filters;

import weka.attributeSelection.BestFirst;
import weka.attributeSelection.CfsSubsetEval;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;

public class BestFirstFilter {
    public Filter getFilter() {
        AttributeSelection filter = new AttributeSelection();
        CfsSubsetEval evalFs = new CfsSubsetEval();
        BestFirst search = new BestFirst();
        filter.setEvaluator(evalFs);
        filter.setSearch(search);
        return filter;
    }
}

