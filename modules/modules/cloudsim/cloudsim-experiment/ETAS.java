package org.cloudbus.cloudsim.experiment;

import java.io.IOException;
import java.net.URLDecoder;

public class ETAS {
    /**
     * The main method.
     *
     * @param args the arguments
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public static void main(String[] args) throws IOException {
        boolean enableOutput = false;
        boolean outputToFile = false;
        String inputFolder = null;
        if (Constants.WORKLOAD_TYPE == "PlanetLab") {
            inputFolder = ETAS.class.getClassLoader().getResource("workload/planetlab").getPath();
        } else if (Constants.WORKLOAD_TYPE == "Mix") {
            inputFolder = ETAS.class.getClassLoader().getResource("workload/Mix").getPath();
        } else if (Constants.WORKLOAD_TYPE == "Gan") {
            inputFolder = ETAS.class.getClassLoader().getResource("workload/Gan").getPath();
        } else if (Constants.WORKLOAD_TYPE == "Azure-4000") {
            inputFolder = ETAS.class.getClassLoader().getResource("workload/Azure-4000").getPath();
        } else if (Constants.WORKLOAD_TYPE == "Azure-5000") {
            inputFolder = ETAS.class.getClassLoader().getResource("workload/Azure-5000").getPath();
        }
        inputFolder = URLDecoder.decode(inputFolder, "UTF-8");//解决中文路径问题
        String outputFolder = "output";

        Constants.vmAllocationPolicy="ETAS";
        Constants.vmSelectionPolicy = "mmt";
        String parameter = "0.8"; // the safety parameter of the IQR policy

        new PlanetLabRunner(
                enableOutput,
                outputToFile,
                inputFolder,
                outputFolder,
                Constants.workload,
                Constants.vmAllocationPolicy,
                Constants.vmSelectionPolicy,
                parameter);
    }
}

