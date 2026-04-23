package org.cloudbus.cloudsim.experiment;

import java.io.IOException;
import java.net.URLDecoder;

public class Ipso {
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
            inputFolder = Ipso.class.getClassLoader().getResource("workload/planetlab").getPath();
        } else if (Constants.WORKLOAD_TYPE == "Mix") {
            inputFolder = Ipso.class.getClassLoader().getResource("workload/Mix").getPath();
        } else if (Constants.WORKLOAD_TYPE == "Gan") {
            inputFolder = Ipso.class.getClassLoader().getResource("workload/Gan").getPath();
        } else if (Constants.WORKLOAD_TYPE == "Azure-4000") {
            inputFolder = Ipso.class.getClassLoader().getResource("workload/Azure-4000").getPath();
        } else if (Constants.WORKLOAD_TYPE == "Azure-5000") {
            inputFolder = Ipso.class.getClassLoader().getResource("workload/Azure-5000").getPath();
        }
        inputFolder = URLDecoder.decode(inputFolder, "UTF-8");//解决中文路径问题
        //System.out.println(inputFolder);
        String outputFolder = "output";

        Constants.vmAllocationPolicy="ipso";
        //none表示采用我自创的Vm选择策略（其实包括四部分“超载服务器选择策略”、“待迁移Vm选择策略”、“欠载Vm选择策略”、“服务器开启策略”）
        Constants.vmSelectionPolicy = "none";
        String parameter = ""; // the safety parameter of the IQR policy

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
