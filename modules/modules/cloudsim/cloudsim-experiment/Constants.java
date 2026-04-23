package org.cloudbus.cloudsim.experiment;

import org.cloudbus.cloudsim.power.models.*;

/**
 * If you are using any algorithms, policies or workload included in the power package, please cite
 * the following paper:
 * <p>
 * Anton Beloglazov, and Rajkumar Buyya, "Optimal Online Deterministic Algorithms and Adaptive
 * Heuristics for Energy and Performance Efficient Dynamic Consolidation of Virtual Machines in
 * Cloud Data Centers", Concurrency and Computation: Practice and Experience (CCPE), Volume 24,
 * Issue 13, Pages: 1397-1420, John Wiley & Sons, Ltd, New York, USA, 2012
 *
 * @author Anton Beloglazov
 * @since Jan 6, 2012
 */
public class Constants {

    public final static boolean ENABLE_OUTPUT = true;
    public final static boolean OUTPUT_CSV = false;//不输出日志
    public static boolean isHistoryRecordOutput = true;//是否记录每次调度中的“活跃服务器数量”,“Vm迁移次数”和“热点数”；
    public final static double SCHEDULING_INTERVAL = 300;//调度间隔为5min
    public final static double SIMULATION_LIMIT = 24 * 60 * 60;//以秒为单位，仿真时长为1天

    //数据集有两种，一种是PlanetLab，另一种是自定义的Mix（VM规模2000)，还有一种是Gan
    public final static String WORKLOAD_TYPE = "Azure-4000"; //Azure-5000,Mix都是2000，Azure-4000，PlanetLab
    //可填的workload：20110303(1052),20110306(1000),20110309(1060),20110322(1516),20110325(1078),20110403(1462),20110409(1358),20110411(1232),20110412(1054),20110420(1032)
    public static String workload = "20110420";//如果你用MultiAlgorRunInScale跑的话，这里的赋值会被覆盖

    public  static int NUMBER_OF_CLOUDLETS = 1600;//等于或小于所选数据集中包含的VM数
    public  static int NUMBER_OF_VMS = NUMBER_OF_CLOUDLETS;
    public final static int NUMBER_OF_HOSTS = 800;  //若修改，ThermalModel.java中的NUMBER_OF_HOSTS参数也要改

    //这两个仅仅是为了在打印结果时方便获取策略名，所以设置成static放在Constants里面
    public static String vmAllocationPolicy = "";
    public static String vmSelectionPolicy = "";
    public final static int THREAD_NUM = 10;//群智能算法Depso、De的线程数

    /* ========================================== Cloudlet attribute ==========================================
     *
     * Cloudlet的长度采用泊松分布获取，内核数默认为1
     * PlanetLab数据集中记录的是各个Cloudlet的单位
     * */
    //单位是Million Instruction，实际上算出来的就是一台30000MIPs的VM，满载运行一小时可完成的任务的长度
    //这个1000MIPs主要是通过VM满载取均值得出的
//    public final static int CLOUDLET_LENGTH_BASE = 50000 * (1 * 60 * 60);//俊祺的设置
    public final static int CLOUDLET_LENGTH_BASE = 3000 * (int)SIMULATION_LIMIT;//陈睿的设置
    // Cloudlet length multiplier: mul~ Poisson(LAMBDA), 0 <= mul < N
    public final static double POISSON_LAMBDA = 6.0;
    // maximum of 19 multipliers（最多19就超限了）
    public final static int POISSON_MAX = 19;

    //这个不能单纯设置了，因为改成VM多核的，应该设置成放置到哪个VM就有多少个PES
    public final static int CLOUDLET_PES = 1;

    /*
     * =============================================== VM types（毕设版） ===============================================
     *
     * 注：EC2 Compute Units = 1000 MIPs，不同类型的VM具有不同vCPU数目，而且每个vCPU所含的MIPs也不尽相同
     * Amazon Elastic Compute Cloud (Amazon EC2) is a web service that provides resizable computing capacity—literally, servers in Amazon's data centers—that you use to build and host your software systems.
     * https://docs.aws.amazon.com/ec2/
     * 通用型：• General purpose
     *   a1.m         1 vCPU x 2300 MHz     2.3 EC2 Compute Units,         2 GB RAM  1
     *   a1.l         2 vCPU x 2300 MHz     4.6 EC2 Compute Units,         4 GB RAM  1
     *   a1.xl        4 vCPU x 2300 MHz	    9.2 EC2 Compute Units,         8 GB RAM  1
     *   a1.2xl       8 vCPU x 2300 MHz    18.4 EC2 Compute Units,        16 GB RAM
     *   a1.4xl      16 vCPU x 2300 MHz    36.8 EC2 Compute Units,        32 GB RAM
     *   m5a.l        2 vCPU x 2500 MHz     5.0 EC2 Compute Units,         8 GB RAM
     *
     * 加速计算：• Accelerated computing
     *   g4dn.xl      4 vCPU x 2500 MHz    10.0 EC2 Compute Units,        16 GB RAM
     *   g4dn.2xl     8 vCPU x 2500 MHz    20.0 EC2 Compute Units,        32 GB RAM  1
     *
     */
    public final static int VM_TYPES =4;    //4种类型的VM （3种通用,1种加速计算）
    //设置各种类型的VM数量
    public final static double[] VM_TYPES_PERCENTAGE ={0.4,0.4,0.1,0.1};  // 总和为1.0

//    public final static int[] VM_MIPS = {2500, 2000, 1000, 500};
//    public final static int[] VM_PES = {1, 1, 1, 1};
//    public final static int[] VM_RAM = {870,  1740, 1740, 613};//单位：MB
    public final static int[] VM_MIPS = {2300, 2300, 2300, 2500};
    public final static int[] VM_PES = {1, 2, 4, 8};
    public final static int[] VM_RAM = {2048, 4096, 8192, 16384};//单位：MB
    public final static int VM_BW = 100000; // 100 Mbit/s
    public final static int VM_SIZE = 3000; // 3 GB Storage

    /*
     * =============================================== VM types ===============================================
     *
     *
     * 注：EC2 Compute Units = 1000 MIPs，不同类型的VM具有不同vCPU数目，而且每个vCPU所含的MIPs也不尽相同
     *
     *
     * 通用型：
     *   a1.m         1 vCPU x 2300 MHz     2.3 EC2 Compute Units,         2 GB RAM（资源太少去掉）
     *   a1.l         2 vCPU x 2300 MHz     4.6 EC2 Compute Units,         4 GB RAM（资源太少去掉）
     *   a1.xl        4 vCPU x 2300 MHz	    9.2 EC2 Compute Units,         8 GB RAM
     *   a1.2xl       8 vCPU x 2300 MHz    18.4 EC2 Compute Units,        16 GB RAM
     *   a1.4xl      16 vCPU x 2300 MHz    36.8 EC2 Compute Units,        32 GB RAM
     *   m5a.l        2 vCPU x 2500 MHz     5.0 EC2 Compute Units,         8 GB RAM
     *
     * 内存优化型：
     *   r4.l	      2 vCPU x 2300 MHz 	4.6 EC2 Compute Units,     15.25 GB RAM（新增）
     *   r4.xl        4 vCPU x 2300 MHz     9.2 EC2 Compute Units,      30.5 GB RAM（新增）
     *   r4.2xl       8 vCPU x 2300 MHz     18.4 EC2 Compute Units,       61 GB RAM（新增）
     *
     * 加速计算：
     *   g4dn.xl      4 vCPU x 2500 MHz    10.0 EC2 Compute Units,        16 GB RAM
     *   g4dn.2xl     8 vCPU x 2500 MHz    20.0 EC2 Compute Units,        32 GB RAM
     *


    public final static int VM_TYPES = 9;//9种类型的VM
    public final static int[] VM_MIPS = {2300, 2300, 2300, 2500, 2300, 2300, 2300, 2500, 2500};
    public final static int[] VM_PES = {4, 8, 16, 2, 2, 4, 8, 4, 8};
    public final static int[] VM_RAM = {8192, 16384, 32768, 8192, 15616, 31232, 62464, 16384, 32768};//单位：MB
    public final static int VM_BW = 100000; // 100 Mbit/s
    public final static int VM_SIZE = 3000; // 3 GB Storage
*/

    /* ============================================== Host types ===============================================
     *   Dell PowerEdge R630                  (2 x [Xeon E5-2699 v3     2300 MHz, 18 Cores], 64GB)
     *   power = { 51.2, 93.4, 111, 129, 146, 162, 176, 197, 223, 255, 287};
     *   peff = { 0, 3473, 5830, 7528, 8889, 10000, 11033, 11505, 11623, 11560, 11284};
     *
     *   Dell PowerEdge R640                  (2 x [Xeon Platinum 8180  2500 MHz, 28 Cores], 192GB)
     *   power = { 55, 125, 151, 176, 202, 232, 261, 301, 357, 421, 469};
     *   peff = { 0, 4614, 7651, 9850, 11429, 12454, 13271, 13420, 12926, 12352, 12284};
     *
     *   Huawei Fusion Server 5288 V5         (2 x [Xeon Platinum 8280  2700 MHz, 28 Cores], 192GB)
     *   power = { 51.5, 119, 142, 165, 188, 194, 236, 256, 283, 336, 422};
     *   peff = { 0, 5156, 8605, 11111, 13061, 15728, 15518, 16712, 17285, 16499, 14479};
     *
     *
     *   Acer AT350 F2                        (2 x [Xeon E5-2665        2400 MHz, 8 Cores],  128GB)<新增>
     *   power = { 103, 147, 167, 190, 218, 245, 273, 296, 324, 378, 409};
     *   peff = { 0, 919, 1618, 2138, 2478, 2758, 2983, 3187, 3352, 3221, 3298}
     *
     *   新增低性能低功耗服务器
     *   Hewlett-Packard Company ProLiant ML110 G3  (1 x [Intel Pentium D Processor 930, 3000 MHz, 2Cores], 16GB)<新增> 1
     * 	 power = { 105, 112, 118, 125, 131, 137, 147, 153, 157, 164, 169 };
     *   peff = { 0, 47.9, 89.4, 128, 160, 191, 218, 241, 268, 285, 309 };

     *   PowerEdge R240                         (1 x [Intel Xeon E-2176G, 3700 MHz, 6Cores], 32GB)<新增> 1
     *   power = { 20.0,25.1,28.5,32.3,36.6,41.7,47.5,55.9,67.2, 81.0, 93.1};
     *   peff = { 0, 3408, 6021, 7965, 9358, 10289, 10800, 10762, 10228, 9499, 9208}
     *
     *   ProLiant DL20 Gen10 Plus              (1 x [Intel(R) Xeon(R) E-2388G CPU 3200MHz, 8 cores], 16GB)<新增> 1
     *   power = { 21.5	,27.2,30.3,33.1	,36.5,40.7,45.4	,51.1,59.8,70.1,81.5};
     *   peff = { 0, 3104, 5575, 7662, 9240, 10400, 11196, 11586, 11348, 10845, 10430}
     *
     *   Acer AR380 F2                        (2 x [Xeon E5-2640        2500 MHz, 6 Cores],  32GB)<新增> 1
     *   power = { 81.9, 107, 117, 129, 146, 165, 184, 200, 212, 239, 254};
     *   peff = { 0, 932, 1701, 2306, 2730, 3000, 3241, 3482, 3772, 3737, 3904}
     *
     *   IBM System x3530 M4                  (2 x [Xeon E5-2470        2300 MHz, 8 Cores],  24GB)<新增>
     *   power = { 62.6, 83.6, 93.4, 103, 114, 129, 149, 173, 199, 232, 274};
     *   peff = { 0, 1765, 3146, 4260, 5145, 5676, 5923, 5942, 5897, 5708, 5348}
     */
    public final static int HOST_TYPES = 4;
//    public final static double[] HOST_TYPES_PERCENTAGE ={0.25,0.25,0.25,0.25};  // 总和为1.0
    public final static int[] HOST_MIPS = {3000,3700,3200,2500};
    public final static int[] HOST_PES = {2, 6, 8, 12};
    public final static int[] HOST_RAM = {16384, 32768,16384,32768};//单位：MB
    public final static int HOST_BW = 1000000; // 单位：Kbps, 10 Gbit/s
    public final static int HOST_STORAGE = 10000000; // 单位：MB，1000 GB

    public final static PowerModel[] HOST_POWER = {
//            new PowerModelSpecPowerDellPowerEdgeR630(),
//            new PowerModelSpecPowerDellPowerEdgeR640(),
//            new PowerModelSpecPowerHuaweiFusionServer5288V5(),
//            new PowerModelSpecPowerAcerAT350F2(),
            new PowerModelSpecPowerHpProLiantMl110G3PentiumD930(),
            new PowerModelSpecPowerDellPowerEdgeR240(),
            new PowerModelSpecPowerProLiantDL20(),
            new PowerModelSpecPowerAcerAR380F2(),
//            new PowerModelSpecPowerIBMx3530M4()
    };

}
