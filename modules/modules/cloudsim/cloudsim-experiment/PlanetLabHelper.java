package org.cloudbus.cloudsim.experiment;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelNull;
import org.cloudbus.cloudsim.UtilizationModelPlanetLabInMemory;
import org.cloudbus.cloudsim.util.MathUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A helper class for the running examples for the PlanetLab workload.
 * <p>
 * If you are using any algorithms, policies or workload included in the power package please cite
 * the following paper:
 * <p>
 * Anton Beloglazov, and Rajkumar Buyya, "Optimal Online Deterministic Algorithms and Adaptive
 * Heuristics for Energy and Performance Efficient Dynamic Consolidation of Virtual Machines in
 * Cloud Data Centers", Concurrency and Computation: Practice and Experience (CCPE), Volume 24,
 * Issue 13, Pages: 1397-1420, John Wiley & Sons, Ltd, New York, USA, 2012
 *
 * @author Anton Beloglazov
 * @since Jan 5, 2012
 */
public class PlanetLabHelper {

    /**
     * Creates the cloudlet list planet lab.
     *
     * @param brokerId        the broker id
     * @param inputFolderName the input folder name
     * @return the list
     * @throws FileNotFoundException the file not found exception
     */
    public static List<Cloudlet> createCloudletListPlanetLab(int brokerId, String inputFolderName,String workload)
            throws IOException {
        List<Cloudlet> list = new ArrayList<Cloudlet>();
        //这个数据列表用于统计数据集的平均值和标准差
        ArrayList<Double> record=new ArrayList<>();

        long fileSize = 300;
        long outputSize = 300;
        UtilizationModel utilizationModelNull = new UtilizationModelNull();

        File inputFolder = new File(inputFolderName);
        File[] files = inputFolder.listFiles();

        // eg. workload/20110303 目录下共1052个文件，说明最多创建1052个Cloudlet
        //System.out.println(Constants.NUMBER_OF_CLOUDLETS);
        for (int i = 0; i < Constants.NUMBER_OF_CLOUDLETS ; i++) {
            assert files != null;
            if(i >= files.length){//不要超限了
                System.out.println("The number of loads is less than NUMBER_OF_CLOUDLET. Please check the data set Settings. The program is finished");
                System.exit(1);
            }
            Cloudlet cloudlet = null;
            // mul ~ Poisson(LAMBDA)
//            int mul = getLengthMultiplierPoisson(Constants.POISSON_LAMBDA);
            try {
//                UtilizationModelPlanetLabInMemory utilModel = new UtilizationModelPlanetLabInMemory(
//                        files[i].getAbsolutePath(),
//                        Constants.SCHEDULING_INTERVAL,
//                        record);
                cloudlet = new Cloudlet(
                        i,
//                        Constants.CLOUDLET_LENGTH_BASE * mul,//表示每个任务的长度，单位：Million Instruction
                        Constants.CLOUDLET_LENGTH_BASE,//表示每个任务的长度，单位：Million Instruction
                        Constants.CLOUDLET_PES,//每个任务需要占用几个核
                        fileSize,//fileSize和outputSize是用来计算cost的
                        outputSize,
                        new UtilizationModelPlanetLabInMemory( //这个资源利用率变化模型是通过读文件得到的，并且后续会用来管理Cloudlet的资源变化
                                files[i].getAbsolutePath(),
                                Constants.SCHEDULING_INTERVAL,
                                record), //300s（5min）一次VM调度
                        utilizationModelNull,
                        utilizationModelNull);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(0);
            }
            cloudlet.setUserId(brokerId);
            cloudlet.setVmId(i);
            list.add(cloudlet);
        }

        //这里顺便统计一下数据集的属性（平均值和标准差），并输出到相应路径中
        File getPath=new File("");
        String outputPath=getPath.getCanonicalPath();
        outputPath+="/output/Cloudlet";
        File outputFile=new File(outputPath);
        if(!outputFile.exists()) outputFile.mkdir();//如果文件夹不存在，则创建文件夹
        BufferedWriter bW=new BufferedWriter(new FileWriter(outputFile+"/"+workload
                +"(CloudletSize="+Constants.NUMBER_OF_CLOUDLETS+")"+".txt"));//"cloudlet-experiment/20110303"
        bW.write("Means: ");
        bW.write(""+MathUtil.mean(record)+"\n");
        bW.write("StDev: ");
        bW.write(""+MathUtil.stDev(record)+"\n");
        bW.close();

        return list;
    }

    /**
     * @param lambda expectation
     * @return get a random poisson var X with prob(X=K) ~ P(lambda)
     * @author wwt
     */
    public static int getLengthMultiplierPoisson(double lambda) {
        int x = 0;
        Random rand = new Random(3);
        // avoid returning zero
        while (x == 0) {
            // the pointer on a roulette
            double roll = rand.nextDouble();

            // accumulated probability
            double cdf = getPoissonProbability(0, lambda);
            while (cdf < roll) {
                x++;
                cdf += getPoissonProbability(x, lambda);
            }

        }

        // the limit of x
        if (x > Constants.POISSON_MAX)
            x = Constants.POISSON_MAX;

        return x;
    }

    /**
     * @param k      variable
     * @param lambda expectation
     * @return P(X = k) = (lambda^k / k!) *e^-lambda
     * @author wwt
     */
    public static double getPoissonProbability(int k, double lambda) {
        double prob;
        double c;

        c = Math.exp(-1 * lambda);
        // k = 0
        prob = c;
        // k >= 1
        for (int i = 1; i <= k; i++)
            prob *= (lambda / i);

        return prob;
    }

}


