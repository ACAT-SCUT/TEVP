package org.cloudbus.cloudsim.experiment;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * The Class RunnerAbstract.
 * <p>
 * If you are using any algorithms, policies or workload included in the power package, please cite
 * the following paper:
 * <p>
 * Anton Beloglazov, and Rajkumar Buyya, "Optimal Online Deterministic Algorithms and Adaptive
 * Heuristics for Energy and Performance Efficient Dynamic Consolidation of Virtual Machines in
 * Cloud Data Centers", Concurrency and Computation: Practice and Experience (CCPE), Volume 24,
 * Issue 13, Pages: 1397-1420, John Wiley & Sons, Ltd, New York, USA, 2012
 *
 * @author Anton Beloglazov
 */
public abstract class RunnerAbstract {

    /**
     * The enable output.
     */
    private static boolean enableOutput;

    /**
     * The broker.
     */
    protected static DatacenterBroker broker;

    /**
     * The cloudlet list.
     */
    protected static List<Cloudlet> cloudletList;

    /**
     * The vm list.
     */
    protected static List<Vm> vmList;

    /**
     * The host list.
     */
    protected static List<PowerHost> hostList;

    /**
     * Run.
     *
     * @param enableOutput       the enable output
     * @param outputToFile       the output to file
     * @param inputFolder        the input folder
     * @param outputFolder       the output folder
     * @param workload           the workload
     * @param vmAllocationPolicy the vm allocation policy
     * @param vmSelectionPolicy  the vm selection policy
     * @param parameter          the parameter
     */
    public RunnerAbstract(
            boolean enableOutput,
            boolean outputToFile,
            String inputFolder,
            String outputFolder,
            String workload,//这个串用来记录数据集的名字
            String vmAllocationPolicy,
            String vmSelectionPolicy,
            String parameter) {
        try {
            initLogOutput(
                    enableOutput,
                    outputToFile,
                    outputFolder,
                    workload,
                    vmAllocationPolicy,
                    vmSelectionPolicy,
                    parameter);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }

        init(inputFolder + "/" + workload, workload);
        start(
                getExperimentName(workload, vmAllocationPolicy, vmSelectionPolicy, parameter),
                outputFolder,
                getVmAllocationPolicy(vmAllocationPolicy, vmSelectionPolicy, parameter));
    }

    /**
     * Inits the log output.
     *
     * @param enableOutput       the enable output
     * @param outputToFile       the output to file
     * @param outputFolder       the output folder
     * @param workload           the workload
     * @param vmAllocationPolicy the vm allocation policy
     * @param vmSelectionPolicy  the vm selection policy
     * @param parameter          the parameter
     * @throws IOException           Signals that an I/O exception has occurred.
     * @throws FileNotFoundException the file not found exception
     */
    protected void initLogOutput(
            boolean enableOutput,
            boolean outputToFile,
            String outputFolder,
            String workload,
            String vmAllocationPolicy,
            String vmSelectionPolicy,
            String parameter) throws IOException, FileNotFoundException {
        setEnableOutput(enableOutput);
        Log.setDisabled(!isEnableOutput());
        if (isEnableOutput() && outputToFile) {
            File folder = new File(outputFolder);
            if (!folder.exists()) {
                folder.mkdir();
            }

            File folder2 = new File(outputFolder + "/log");
            if (!folder2.exists()) {
                folder2.mkdir();
            }
            File file = new File(outputFolder + "/log/"
                    + getExperimentName(workload, vmAllocationPolicy, vmSelectionPolicy, parameter) + ".txt");
            file.createNewFile();
            Log.setOutput(new FileOutputStream(file));
        }
    }

    /**
     * Inits the simulation.
     *
     * @param inputFolder the input folder
     */
    protected abstract void init(String inputFolder,String workload);

    /**
     * Starts the simulation.
     *
     * @param experimentName     the experiment name
     * @param outputFolder       the output folder
     * @param vmAllocationPolicy the vm allocation policy
     */
    protected void start(String experimentName, String outputFolder, VmAllocationPolicy vmAllocationPolicy) {
        System.out.println("Starting " + experimentName);
        try {
            //建立数据中心
            //DataCenter作为一个SimEntity，创建出来以后直接由CloudSim进行处理，并没有和broker进行绑定
            PowerDatacenter datacenter = (PowerDatacenter) Helper.createDatacenter(
                    "Datacenter",
                    PowerDatacenter.class,
                    hostList,
                    vmAllocationPolicy);

            datacenter.setDisableMigrations(false);

            //建立Broker，把之前PlanetLabRunner.init()处理好的VmList、cloudLetList传入到参数里面
            broker.submitVmList(vmList);
            broker.submitCloudletList(cloudletList);

            CloudSim.terminateSimulation(Constants.SIMULATION_LIMIT);//设置仿真时长
            double lastClock = CloudSim.startSimulation();//开始仿真，并统计仿真采用的总时间

            List<Cloudlet> newList = broker.getCloudletReceivedList();//获取完成的cloudlet列表
            Log.printLine("Received " + newList.size() + " cloudlets");

            CloudSim.stopSimulation();

            Helper.printResults(
                    datacenter,
                    vmList,
                    lastClock,
                    experimentName,
                    Constants.OUTPUT_CSV,
                    outputFolder);

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("The simulation has been terminated due to an unexpected error");
            System.exit(0);
        }

        Log.printLine("Finished " + experimentName);
    }

    /**
     * Gets the experiment name.
     *
     * @param args the args
     * @return the experiment name
     */
    protected String getExperimentName(String... args) {
        StringBuilder experimentName = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (args[i].isEmpty()) {
                continue;
            }
            if (i != 0) {
                experimentName.append("_");
            }
            experimentName.append(args[i]);
        }
        return experimentName.toString();
    }

    /**
     * Gets the vm allocation policy.
     *
     * @param vmAllocationPolicyName the vm allocation policy name
     * @param vmSelectionPolicyName the vm selection policy name
     * @param parameterName the parameter name
     * @return the vm allocation policy
     */
    /**
     * 该函数用于获取特定的待迁移VM选择策略和VM放置策略
     */
    protected VmAllocationPolicy getVmAllocationPolicy(String vmAllocationPolicyName,
            String vmSelectionPolicyName,
            String parameterName) {
        VmAllocationPolicy vmAllocationPolicy = null;
        PowerVmSelectionPolicy vmSelectionPolicy = null;
        //获取待迁移VM选择策略
        if (!vmSelectionPolicyName.isEmpty()) {
            vmSelectionPolicy = getVmSelectionPolicy(vmSelectionPolicyName);
        }
        double parameter = 0;
        if (!parameterName.isEmpty()) {
            parameter = Double.parseDouble(parameterName);
        }
        //还准备了后备的VM调度策略
        if (vmAllocationPolicyName.equals("iqr")) {
            PowerVmAllocationPolicyMigrationAbstract fallbackVmSelectionPolicy = new PowerVmAllocationPolicyMigrationStaticThreshold(
                    hostList,
                    vmSelectionPolicy,
                    0.7);
            vmAllocationPolicy = new PowerVmAllocationPolicyMigrationInterQuartileRange(
                    hostList,
                    vmSelectionPolicy,
                    parameter,
                    fallbackVmSelectionPolicy);
        } else if (vmAllocationPolicyName.equals("mad")) {
            PowerVmAllocationPolicyMigrationAbstract fallbackVmSelectionPolicy = new PowerVmAllocationPolicyMigrationStaticThreshold(
                    hostList,
                    vmSelectionPolicy,
                    0.7);
            vmAllocationPolicy = new PowerVmAllocationPolicyMigrationMedianAbsoluteDeviation(
                    hostList,
                    vmSelectionPolicy,
                    parameter,
                    fallbackVmSelectionPolicy);
        } else if (vmAllocationPolicyName.equals("lr")) {
            PowerVmAllocationPolicyMigrationAbstract fallbackVmSelectionPolicy = new PowerVmAllocationPolicyMigrationStaticThreshold(
                    hostList,
                    vmSelectionPolicy,
                    0.7);
            vmAllocationPolicy = new PowerVmAllocationPolicyMigrationLocalRegression(
                    hostList,
                    vmSelectionPolicy,
                    parameter,
                    Constants.SCHEDULING_INTERVAL,
                    fallbackVmSelectionPolicy);
        } else if (vmAllocationPolicyName.equals("lrr")) {
            PowerVmAllocationPolicyMigrationAbstract fallbackVmSelectionPolicy = new PowerVmAllocationPolicyMigrationStaticThreshold(
                    hostList,
                    vmSelectionPolicy,
                    0.7);
            vmAllocationPolicy = new PowerVmAllocationPolicyMigrationLocalRegressionRobust(
                    hostList,
                    vmSelectionPolicy,
                    parameter,
                    Constants.SCHEDULING_INTERVAL,
                    fallbackVmSelectionPolicy);
        } else if (vmAllocationPolicyName.equals("thr")) {
            vmAllocationPolicy = new PowerVmAllocationPolicyMigrationStaticThreshold(
                    hostList,
                    vmSelectionPolicy,
                    parameter);
        } else if (vmAllocationPolicyName.equals("dvfs")) {
            vmAllocationPolicy = new PowerVmAllocationPolicySimple(hostList);
        } else if (vmAllocationPolicyName.equals("depso")) {
            vmAllocationPolicy = new VmAllocationPolicyForDifferentAlgorithm(hostList, 0);
        } else if (vmAllocationPolicyName.equals("peap")) {
            vmAllocationPolicy = new VmAllocationPolicyForDifferentAlgorithm(hostList, 1);
        } else if (vmAllocationPolicyName.equals("mbfd")) {
            vmAllocationPolicy = new VmAllocationPolicyForDifferentAlgorithm(hostList, 2);
        } else if (vmAllocationPolicyName.equals("random")) {
            vmAllocationPolicy = new VmAllocationPolicyForDifferentAlgorithm(hostList, 3);
        } else if (vmAllocationPolicyName.equals("ffd")) {
            vmAllocationPolicy = new VmAllocationPolicyForDifferentAlgorithm(hostList, 4);
        } else if (vmAllocationPolicyName.equals("de")) {
            vmAllocationPolicy = new VmAllocationPolicyForDifferentAlgorithm(hostList, 5);
        } else if (vmAllocationPolicyName.equals("pso")) {
            vmAllocationPolicy = new VmAllocationPolicyForDifferentAlgorithm(hostList, 6);
        } else if (vmAllocationPolicyName.equals("ipso")) {
            vmAllocationPolicy = new VmAllocationPolicyForDifferentAlgorithm(hostList, 7);
        } else if (vmAllocationPolicyName.equals("gapso")) {
            vmAllocationPolicy = new VmAllocationPolicyForDifferentAlgorithm(hostList, 8);
        } else if (vmAllocationPolicyName.equals("PTACO")) { //新增
            PowerVmAllocationPolicyMigrationAbstract fallbackVmSelectionPolicy = new PowerVmAllocationPolicyMigrationStaticThreshold(
                    hostList,
                    vmSelectionPolicy,
                    0.9);
            vmAllocationPolicy = new PowerVmAllocationPolicyPTACO(
                    hostList,
                    vmSelectionPolicy,
                    parameter,
                    fallbackVmSelectionPolicy);
        } else if (vmAllocationPolicyName.equals("GRANITE")) {//新增
            vmAllocationPolicy = new PowerVmAllocationPolicyGRANITE(
                    hostList,
                    vmSelectionPolicy,
                    0.9,
                    0.1);
        } else if (vmAllocationPolicyName.equals("ETAS")) {//新增
            vmAllocationPolicy = new PowerVmAllocationPolicyETAS(
                    hostList,
                    vmSelectionPolicy,
                    parameter);
        } else {
            System.out.println("Unknown VM allocation policy: " + vmAllocationPolicyName);
            System.exit(0);
        }
        return vmAllocationPolicy;
    }

    /**
     * Gets the vm selection policy.
     *
     * @param vmSelectionPolicyName the vm selection policy name
     * @return the vm selection policy
     */
    protected PowerVmSelectionPolicy getVmSelectionPolicy(String vmSelectionPolicyName) {
        PowerVmSelectionPolicy vmSelectionPolicy = null;
        if (vmSelectionPolicyName.equals("mc")) {
            vmSelectionPolicy = new PowerVmSelectionPolicyMaximumCorrelation(
                    new PowerVmSelectionPolicyMinimumMigrationTime());
        } else if (vmSelectionPolicyName.equals("mmt")) {
            vmSelectionPolicy = new PowerVmSelectionPolicyMinimumMigrationTime();
        } else if (vmSelectionPolicyName.equals("mm")) {
            vmSelectionPolicy = new PowerVmSelectionPolicyMinimizationOfMigrations();
        } else if (vmSelectionPolicyName.equals("mu")) {
            vmSelectionPolicy = new PowerVmSelectionPolicyMinimumUtilization();
        } else if (vmSelectionPolicyName.equals("rs")) {
            vmSelectionPolicy = new PowerVmSelectionPolicyRandomSelection();
        } else if (vmSelectionPolicyName.equals("none")) {
            //这里就不用赋值了，因为DEPSO的所有策略都是封装在opitmizationAllocation里面的
            vmSelectionPolicy = null;
        } else {
            System.out.println("Unknown VM selection policy: " + vmSelectionPolicyName);
            System.exit(0);
        }
        return vmSelectionPolicy;
    }

    /**
     * Sets the enable output.
     *
     * @param enableOutput the new enable output
     */
    public void setEnableOutput(boolean enableOutput) {
        RunnerAbstract.enableOutput = enableOutput;
    }

    /**
     * Checks if is enable output.
     *
     * @return true, if is enable output
     */
    public boolean isEnableOutput() {
        return enableOutput;
    }

}
