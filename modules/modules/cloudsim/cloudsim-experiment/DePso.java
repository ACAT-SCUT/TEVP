package org.cloudbus.cloudsim.experiment;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.power.DiscreteDEPSO;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.concurrent.Executors;

public class DePso {
    /**
     * The main method.
     *
     * @param args the arguments
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public static void main(String[] args) throws IOException {
        boolean enableOutput = true;   // 打印日志
        boolean outputToFile = true;    // 保存日志到文件中
        String inputFolder = null;
        if (Constants.WORKLOAD_TYPE == "PlanetLab") {
            inputFolder = DePso.class.getClassLoader().getResource("workload/planetlab").getPath();
        } else if (Constants.WORKLOAD_TYPE == "Mix") {
            inputFolder = DePso.class.getClassLoader().getResource("workload/Mix").getPath();
        } else if (Constants.WORKLOAD_TYPE == "Gan") {
            inputFolder = DePso.class.getClassLoader().getResource("workload/Gan").getPath();
        } else if (Constants.WORKLOAD_TYPE == "Azure-4000") {
            inputFolder = DePso.class.getClassLoader().getResource("workload/Azure-4000").getPath();
        } else if (Constants.WORKLOAD_TYPE == "Azure-5000") {
            inputFolder = DePso.class.getClassLoader().getResource("workload/Azure-5000").getPath();
        }
        inputFolder = URLDecoder.decode(inputFolder, "UTF-8");//解决中文路径问题
        String outputFolder = "output";

        Constants.vmAllocationPolicy="depso";
        //none:表示采用我自创的Vm选择策略（包括四部分“超载服务器选择策略”、“待迁移Vm选择策略”、“欠载Vm选择策略”、“服务器开启策略”）
        Constants.vmSelectionPolicy = "none";
        String parameter = ""; // the safety parameter of the IQR policy
        // 固定线程数
        DiscreteDEPSO.fixedThreadPool = Executors.newFixedThreadPool(Constants.THREAD_NUM);
        long time1=System.currentTimeMillis();
        new PlanetLabRunner(
                enableOutput,
                outputToFile,
                inputFolder,
                outputFolder,
                Constants.workload,
                Constants.vmAllocationPolicy,
                Constants.vmSelectionPolicy,
                parameter);
        long time2=System.currentTimeMillis();
        DiscreteDEPSO.fixedThreadPool.shutdown();
        System.out.println("The program is finished. The consumption time is:"+(double)(time2-time1)/1000+" s");

    }
}
