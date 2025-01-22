package it.mazz.isw2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            LOGGER.error("project is mandatory");
            return;
        }
        String projName = args[0];
        try {
            String skipDatasetGeneration = args[1];
            if (!skipDatasetGeneration.equals("-skip"))
                DatasetGenerator.getInstance().generateDataset(projName);
        } catch (ArrayIndexOutOfBoundsException ignore) {
            DatasetGenerator.getInstance().generateDataset(projName);
        }

        Analysis.getInstance().analyzeDataset(projName);
    }
}
