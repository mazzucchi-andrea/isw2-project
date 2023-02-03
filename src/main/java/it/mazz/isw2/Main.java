package it.mazz.isw2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        if (args.length < 2) {
            LOGGER.error("project, username and token are mandatory");
            return;
        }
        String projName = args[0];
        String username = args[1];
        String token = args[2];
        try {
            String skipDatasetGeneration = args[3];
            if (!skipDatasetGeneration.equals("-skip"))
                DatasetGenerator.getInstance().generateDataset(projName, username, token);
        } catch (ArrayIndexOutOfBoundsException ignore) {
            DatasetGenerator.getInstance().generateDataset(projName, username, token);
        }

        Analysis.getInstance().analyzeDataset(projName);
    }
}
