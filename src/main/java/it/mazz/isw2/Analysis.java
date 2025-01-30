package it.mazz.isw2;

import com.opencsv.CSVWriter;
import it.mazz.isw2.exceptions.HeadersEqualException;
import it.mazz.isw2.ml.filters.BestFirstFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.CostSensitiveClassifier;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.AttributeStats;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.supervised.instance.SpreadSubsample;

import java.io.File;
import java.io.FileWriter;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class Analysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(Analysis.class);
    private static final String RANDOM_FOREST = "RandomForest";
    private static final String NAIVE_BAYES = "NaiveBayes";
    private static final String IBK = "IBk";
    private static final String NO_SAMPLING = "No sampling";
    private static final String OVERSAMPLING = "Oversampling";
    private static final String UNDERSAMPLING = "Undersampling";
    private static final String SMOTE = "SMOTE";
    private static final String NO_COST_SENSITIVITY = "No cost sensitive ";
    private static final String SENSITIVE_THRESHOLD = "Sensitive Threshold";
    private static final String SENSITIVE_LEARNING = "Sensitive Learning";
    private static final String NO_SELECTION = "No selection";
    private static final String BEST_FIRST = "Best first";
    private static final double CFP = 1;
    private static final double CFN = 10 * CFP;
    private static Analysis instance = null;

    private Analysis() {
    }

    public static Analysis getInstance() {
        if (instance == null)
            instance = new Analysis();
        return instance;
    }

    public void analyzeDataset(String projName) {
        Util util = Util.getInstance();
        List<String> trainPaths = new ArrayList<>();
        List<String> testPaths = new ArrayList<>();

        List<File> files = new LinkedList<>();
        util.listFiles("./output/" + projName + "/" + projName + "-datasets", files);

        for (File file : files) {
            if (file.getPath().contains("test")) {
                testPaths.add(file.getPath());
            } else {
                trainPaths.add(file.getPath());
            }
        }

        trainPaths.sort(Collator.getInstance());
        testPaths.sort(Collator.getInstance());

        Filter oversampling = new Resample(); //-B 1.0 -Z 130.3
        String[] optionsO = {"-B", "1.0", "-Z", "130.3"};

        Filter undersampling = new SpreadSubsample(); //-B 1.0 -Z 130.3
        String[] optionsU = {"-M", "1.0"};

        Filter smote = new SMOTE();

        try {
            oversampling.setOptions(optionsO);
            undersampling.setOptions(optionsU);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return;
        }

        File analysisResults = new File(String.format("./output/%s/%s-results.csv", projName, projName));
        String header = "dataset,#TrainingRelease,%Training,%DefectiveTraining,%DefectiveTesting,classifier," +
                "balancing,FeatureSelection,Sensitivity,TP,FP,TN,FN,Precision,Recall,AUC,Kappa\n";

        String[] classifiers = {RANDOM_FOREST, NAIVE_BAYES, IBK};
        String[] samplings = {NO_SAMPLING, OVERSAMPLING, UNDERSAMPLING, SMOTE};
        String[] costSensitivity = {NO_COST_SENSITIVITY, SENSITIVE_THRESHOLD, SENSITIVE_LEARNING};
        String[] featureSelection = {NO_SELECTION, BEST_FIRST};

        List<String[]> specs = generateClassifierSpecs(classifiers, featureSelection, samplings, costSensitivity);

        try (FileWriter outputFile = new FileWriter(analysisResults)) {
            outputFile.write(header);
            try (CSVWriter writer = new CSVWriter(outputFile)) {
                for (String[] spec : specs) {
                    LOGGER.info("Starting analysis with {}, {}, {}, {}", spec[0], spec[1], spec[2], spec[3]);

                    FilteredClassifier fc = new FilteredClassifier();

                    fc.setClassifier(getClassifier(spec[0]));

                    switch (spec[1]) {
                        case OVERSAMPLING:
                            fc.setFilter(oversampling);
                            break;
                        case UNDERSAMPLING:
                            fc.setFilter(undersampling);
                            break;
                        case SMOTE:
                            fc.setFilter(smote);
                            break;
                        default:
                    }

                    Classifier c;
                    CostSensitiveClassifier csc;
                    switch (spec[3]) {
                        case SENSITIVE_THRESHOLD:
                            csc = new CostSensitiveClassifier();
                            csc.setClassifier(fc);
                            csc.setCostMatrix(createCostMatrix());
                            csc.setMinimizeExpectedCost(true);
                            c = csc;
                            break;
                        case SENSITIVE_LEARNING:
                            csc = new CostSensitiveClassifier();
                            csc.setClassifier(fc);
                            csc.setCostMatrix(createCostMatrix());
                            csc.setMinimizeExpectedCost(false);
                            c = csc;
                            break;
                        default:
                            c = fc;
                    }

                    analyze(projName, c, spec, trainPaths, testPaths, writer);
                }
            }
        } catch (Exception e) {
            LOGGER.warn(e.getMessage());
        }
    }

    private void analyze(String projName, Classifier classifier, String[] specs, List<String> trainPaths,
                         List<String> testPaths, CSVWriter writer) throws Exception {

        for (int i = 1; i < testPaths.size(); i++) {
            DataSource trainSource = new DataSource(trainPaths.get(i));
            Instances training = trainSource.getDataSet();

            DataSource testSource = new DataSource(testPaths.get(i));
            Instances testing = testSource.getDataSet();

            String msg = training.equalHeadersMsg(testing);
            if (msg != null)
                throw new HeadersEqualException(msg);

            int numAttr = training.numAttributes();

            AttributeStats buggedTrainingStats = training.attributeStats(numAttr - 1);
            double percDefectiveTraining = ((double) buggedTrainingStats.nominalCounts[1] / buggedTrainingStats.totalCount) * 100;

            AttributeStats buggedTestingStats = testing.attributeStats(numAttr - 1);
            double percDefectiveTesting = ((double) buggedTestingStats.nominalCounts[1] / buggedTestingStats.totalCount) * 100;

            training.setClassIndex(numAttr - 1);
            testing.setClassIndex(numAttr - 1);

            int numTraining = training.numInstances();
            int numTesting = testing.numInstances();
            double percTraining = (double) numTraining / (double) (numTesting + numTraining) * 100;

            if (Objects.equals(specs[1], BEST_FIRST)) {
                Filter bf = new BestFirstFilter().getFilter();
                bf.setInputFormat(training);
                training = Filter.useFilter(training, bf);
                testing = Filter.useFilter(testing, bf);
            }

            if (numTraining > 0)
                classifier.buildClassifier(training);

            Evaluation eval = new Evaluation(testing);

            eval.evaluateModel(classifier, testing);

            String[] line = {
                    projName,
                    Integer.toString(i),
                    Double.toString(percTraining), Double.toString(percDefectiveTraining), Double.toString(percDefectiveTesting),
                    specs[0], specs[2], specs[1], specs[3],
                    Double.toString(eval.numTruePositives(1)), Double.toString(eval.numFalsePositives(1)),
                    Double.toString(eval.numTrueNegatives(1)), Double.toString(eval.numFalseNegatives(1)),
                    Double.toString(eval.precision(1)), Double.toString(eval.recall(1)),
                    Double.toString(eval.areaUnderROC(1)), Double.toString(eval.kappa())};

            writer.writeNext(line);
        }
    }

    private CostMatrix createCostMatrix() {
        CostMatrix costMatrix = new CostMatrix(2);
        costMatrix.setCell(0, 0, 0.0);
        costMatrix.setCell(0, 1, CFN);
        costMatrix.setCell(1, 0, CFP);
        costMatrix.setCell(1, 1, 0.0);
        return costMatrix;
    }

    private Classifier getClassifier(String classifierName) {
        if (classifierName.equals(RANDOM_FOREST)) {
            return new RandomForest();
        } else if (classifierName.equals(NAIVE_BAYES)) {
            return new NaiveBayes();
        } else {
            return new IBk();
        }
    }

    private List<String[]> generateClassifierSpecs(String[] classifiers, String[] featureSelections, String[] samplings,
                                                   String[] costSensitivity) {
        List<String[]> specs = new ArrayList<>();
        for (String c : classifiers) {
            String[] spec = new String[4];
            spec[0] = c;
            for (String f : featureSelections) {
                spec[1] = f;
                for (String s : samplings) {
                    spec[2] = s;
                    for (String cs : costSensitivity) {
                        spec[3] = cs;
                        specs.add(spec.clone());
                    }
                }

            }
        }
        return specs;
    }
}