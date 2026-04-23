package org.cloudbus.cloudsim.power;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

/**
 * VM放置策略，传入"目标服务器初始的VM分配策略"（一个"集合向量"），还有待分配VM
 * 返回新的 "目标服务器VM分配策略"，传到该算法中的目标服务器的MIPs_offer均大于0，
 * 因为如果MIPs_offer<0的话，肯定超载了
 * <p>
 * PS：每次算法中会进行迁移的 Vm 都是在 AllocationVmList 里面的 Vm ，Host上的
 * 其他 Vm 不会发生迁移
 */
public class DiscreteDEPSO {
    /**
     * ===================================================基本数据===================================================
     */
    //数据中心的服务器列表
    private ArrayList<DDPHost> HostList;
    //目标服务器列表，只有在最终映射关系建立完成后才对Vm列表等属性进行更新
    public ArrayList<DDPHost> TargetHostList;
    //下面这个Map用来记录HostList里面的Host是否在TargetHost里面<HostId,bool>
    public HashMap<Integer, Boolean> IsInTargetHostList = new HashMap<>();
    //待分配VM列表，只有在最终映射关系建立完成后才更新Vm
    public ArrayList<DDPVm> AllocationVmList;
    //是否已经开启所有服务器，且仍然欠载（决定是否要执行群智能算法）
    private boolean isAllHostOverload;

    //static变量（由于getFitness是static函数，又要用到下面这5个变量，只能把它们变成static了）
    //适应度函数的三个权重值
    private static double w1;//CPU利用率的权重
    private static double w2;//能耗因子的权重
    private static double w3;//效能比因子的权重
    private static double w4;//热梯度的权重（新增）
    //所有目标服务器范围内的最大峰值能耗
    private static double peak_power_max;
    // 所有目标服务器范围内的最高温度
    private static double peak_temperature_max;
    //所有目标服务器的平均温度
    private static double average_temperature;

    //所有目标服务器范围内的最大峰值效能比
    private static double peak_peff_max;
    //全局线程池（注意实验做完以后我没有shutdown，因为我一次要跑多个算法，实验过程中会多次new该类，所以设置成静态会更合理一些）
    //这个只能让De、Pso、DePso几个来管理了
    public static ExecutorService fixedThreadPool;

    //采用何种放置方法(type==0:depso, type==1:peap, type==2:mbfd, type==3:random,
    // type==4:ffd, type==5:de, type==6:pso)
    private int AlgorType;
    //采用何种服务器开启策略
    private boolean IsAddTargetHostSequential;
    //用于记录DEPSO、DE、PSO在一次迭代中的收敛性
    public static ArrayList<Double> FitnessConvergenceHistory;

    /**
     * ==================================================群智能数据==================================================
     */

    //算法迭代次数
    private int G;
    private DDPSv OriginSV;//用于记录迁移各个Host的负载，算法中initial要用到
    private ArrayList<DDPSv> Swarm;//作为群体（解向量的集合），同时也是PSO中的位置向量的集合
    private ArrayList<DDPSv> MutaSwarm;//变异群体，每次迭代后由多个变异向量组成
    private ArrayList<DDPSv> CrossSwarm;//交叉群体，每次迭代后由多个交叉向量组成

    public DDPSv globalBest;        //群体最优解
    public int globalBest_age;      //全局最优粒子的年龄
    public int globalBest_lifespan; //全局最优粒子的寿命

    private ArrayList<DDPSv> localBest;//个体最优解
    //变异中DF1_A的概率
    private double MR;
    //交叉的概率
    private double CR;
    //对群体中较优的0.X进行PSO更新，剩余的较差的个体则用DE更新
    private double P;
    //PSO算法位置更新的百分比（只更新解向量中百分之几的维数）
    private double Y;
    //PSO算法各解向量的“速度”（与Swarm的位置一一对应）
    private ArrayList<DDPSv> Velocity = new ArrayList<>();
    // PSO算法
    private int initialLifespan;
    //total融合了worseHalf、MutationSwarm、CrossSwarm的SV所构成的ArrayList，在RouletteSelection里面按照Fitness进行
    //了升序排序（Fitness越小的SV越优）
    private ArrayList<DDPSv> totalSwarm = new ArrayList<>();
    private ArrayList<Integer> totalIndex = new ArrayList<>();

    //因为Swarm里面的个体不能移动，否则和MutasionSwarm、CrossSwarm，localBest，globalBest相应维数的host对不上
    //SwarmIndex是对Swarm进行Fitness升序排序的下标数组，自定义Comparator对象，
    //用于Fitness对解向量的ID（下标）进行升序排序，return 1表示o1排在o2后面
    private ArrayList<Integer> SwarmIndex = new ArrayList<>();

    /**
     * ==================================================public方法==================================================
     */
    public DiscreteDEPSO(ArrayList<DDPHost> HostList, ArrayList<DDPHost> TargetHostList,
                         ArrayList<DDPVm> AllocationVmList, int SwarmSize, double w1,
                         double w2, double w3,double w4, int G, double MR, double CR, double P,
                         double Y,int iLifespan, int type, boolean IsAddTargetHostSequential) {
        //step1: 初始化基本数据
        this.HostList = HostList;
        this.TargetHostList = TargetHostList;
        this.AllocationVmList = AllocationVmList;
        this.isAllHostOverload = false;
        DiscreteDEPSO.w1 = w1;
        DiscreteDEPSO.w2 = w2;
        DiscreteDEPSO.w3 = w3;
        DiscreteDEPSO.w4 = w4;
        peak_power_max = 0;
        peak_temperature_max=0; // 新增，所有目标主机的最高温度
        average_temperature=0; // 新增，所有目标主机的平均温度
        peak_peff_max = 0;

        //step2:统计最大峰值能耗和峰值效能比，getFitness要用到
        this.G = G;
        this.MR = MR;
        this.CR = CR;
        this.P = P;
        this.Y = Y;
        this.initialLifespan=iLifespan;
        this.AlgorType = type;

        FitnessConvergenceHistory = new ArrayList<>();

        this.IsAddTargetHostSequential = IsAddTargetHostSequential;
        double sum_temperature=0.0;
        for (DDPHost host : TargetHostList) {
            IsInTargetHostList.put(host.HostId, true);
            if (host.peak_power > peak_power_max) peak_power_max = host.peak_power;
            if (host.Temperature > peak_temperature_max) peak_temperature_max = host.Temperature;
            if (host.peak_peff > peak_peff_max) peak_peff_max = host.peak_peff;
            sum_temperature+=host.Temperature;
        }
        if(TargetHostList.size()>0){
            average_temperature=sum_temperature/TargetHostList.size();
        }

        //step3: 初始化群智能数据
        //初始化“origin向量”和“群体中的解向量”（最初状态下Pm是一样的）
        OriginSV = new DDPSv(TargetHostList, AllocationVmList, false);//浅拷贝
        Swarm = new ArrayList<>();
        for (int i = 0; i < SwarmSize; i++) {
            //此时AllocationVmList中的Vm还没有被放置到TargetHostList的Host里面
            Swarm.add(new DDPSv(TargetHostList, AllocationVmList, true));//深拷贝
        }
        localBest = new ArrayList<>();
    }

    //执行DiscreteDEPSO函数
    public void runAlgorithm() throws InterruptedException {
        /**step1:初始化群体中的解向量和各Fitness数组，如果只有一个VM待分配，那直接采用PEAP分配策略*/
        Swarm_Initialize();
        //如果所有服务器资源不足，则结束算法流程
        if (isAllHostOverload) return;
        //下面这几个type分支和DiscreteDEPSO没有什么关系，只是我懒得再另外开类写算法了，
        //所以直接用这里的PEAP、MBFD、RANDOM-FFD、FFD来作比较（type==0）的时候自然是不管这些的
        if (AlgorType == 1) {
            finalUpdate(Swarm.get(0));// PEAP
            return;
        }
        if (AlgorType == 2) {
            finalUpdate(Swarm.get(1));//MBFD
            return;
        }
        if (AlgorType == 3) {
            finalUpdate(Swarm.get(2));//RANDOM-FFD
            return;
        }
        if (AlgorType == 4) {
            finalUpdate(Swarm.get(3));//FFD
            return;
        }

        //如果放置的Vm只有一台，那就直接返回PEAP算法的结果算了（毕竟群智能算法只有在多台VM同时放置时才有更优的结果）
        if (AllocationVmList.size() <= 2) {
            finalUpdate(Swarm.get(0));
            return;
        }
        //初始化Swarm的Fitness，用于记录每次迭代以后解向量的Fitness
        for (DDPSv sv : Swarm) {
            sv.Fitness = getFitness(sv);
        }

        /**step2:初始化全局最优解和局部最优解，以及MutaSwarm和CrossSwarm*/
        //注意，此时Swarm中各Sv中VmList的待放置Vm已经全部放置到Host上了
        MutaSwarm = new ArrayList<>();
        CrossSwarm = new ArrayList<>();
        //初始化全局最优粒子的解向量，年龄和寿命
        globalBest = Swarm.get(0); // PEAP
        globalBest_age=0;
        globalBest_lifespan=this.initialLifespan;
        for (int i = 0; i < Swarm.size(); i++) {
            //初始化全局最优解和局部最优解
            DDPSv sv = new DDPSv(Swarm.get(i), true);//深拷贝
            localBest.add(sv);
            if (globalBest.Fitness > localBest.get(i).Fitness) {
                globalBest = localBest.get(i);
            }
            //初始化MutaSwarm和CrossSwarm（这个没辙，必须深拷贝）
            DDPSv tmpMutaSv = new DDPSv(Swarm.get(i), true);
            DDPSv tmpCrossSv = new DDPSv(Swarm.get(i), true);
            MutaSwarm.add(tmpMutaSv);
            CrossSwarm.add(tmpCrossSv);
        }
        if (VmAllocationPolicyForDifferentAlgorithm.IsFitnessConvergenceCheck) {
            FitnessConvergenceHistory.add(globalBest.Fitness);
        }

        /**step3:令Swarm按照Fitness升序排序（记录的是SV的下标位置）*/
        for (int i = 0; i < Swarm.size(); i++) {
            SwarmIndex.add(i);
        }

        /**开始进行G次算法迭代*/
        // 需要注意，除PSO算法的PositionUpdate()外，算法中的每一个步骤都要建立相应SV的副本（为了后续选出localBest）
        for (int i = 0; i < G; i++) {
            //System.out.println("DiscreteDEPSO：第" + i + "次迭代");//Debug
            /**step4:每次算法迭代先对所有解向量索引进行排序（用于在算法中获取较优的一半解向量和较差的一半解向量）*/
            SwarmIndex.sort(new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    double o1Fitness = Swarm.get(o1).Fitness;
                    double o2Fitness = Swarm.get(o2).Fitness;
                    if (o1Fitness < o2Fitness) return -1;
                    else if (o1Fitness == o2Fitness) return 0;
                    else return 1;
                }
            });

            //执行DE算法
            if (AlgorType != 6) {
                /**step5:所有解向量执行变异操作得到变异向量（旧的变异群体可以抛弃<反正localBest和globalBest都是独立记录的>）*/
                MutaSwarm = Mutation();
                /**step6:所有向量执行交叉操作得到交叉向量（旧的交叉群体可以抛弃<反正localBest和globalBest都是独立记录的>）*/
                CrossSwarm = Crossover();
                /**step7:每次“变异”“交叉”后更新一下globalBest和localBest*/
                globalAndLocalUpdateAfterDE();
                /**step8:worsePart,MutationSwarm,CrossSwarm采用轮盘赌的方式进行选择*/
                //首先选出较差的DP*Swarm.size()个解向量
                ArrayList<DDPSv> worsePart = new ArrayList<>();
                for (int j = (int) (SwarmIndex.size() * P); j < SwarmIndex.size(); j++) {
                    worsePart.add(Swarm.get(SwarmIndex.get(j)));
                }
                //构造本次迭代totalSwarm（按照Fitness升序排序）
                sortTotal(worsePart);
                //depso算法的话，需要在totalSwarm的前1/2段中进行轮盘赌
                //直接覆盖Swarm后半段的SV即可，无需建立Swarm副本，因为localBest和globalBest都是通过深拷贝记录的
                if (AlgorType == 0) RouletteSelection();
                else SimpleSelection();//de算法的话，采用普通的选择策略更新Swarm的所有SV
            } else {//AlgorType==6只执行PSO算法，首次算法迭代需要单独用RANDOM_Placement算法对初始速度进行初始化
                if (i == 0) {
                    for (int j = 0; j < Swarm.size(); j++) {
                        //创建RND类，构建解向量
                        DDPSv tmpSv = new DDPSv(OriginSV, false);
                        RANDOM_Placement RND = new RANDOM_Placement(tmpSv);
                        //Vm没放置完，就不断开启新的服务器，直到服务器不足为止
                        DDPHost tH2 = null;
                        if (!RND.placement(tH2)) {
                            //这里如果出现服务器资源不足的情况就不执行PSO了，直接取Swarm_Initialize的初始解返回
                            finalUpdate(globalBest);
                            System.out.println("DiscreteDEPSO<VelocityCreate>：执行RANDOM算法生成PSO初始速度向量" + "失败，这里就不再新增服务器了，返回当前globalBest");
                            return;
                        }
                        Velocity.add(tmpSv);
                    }
                }
            }

            //AlgorType==0时，执行depso才会用到这种PSO速度初始化方法
            if (AlgorType == 0) {
                /**step9:算法首次迭代需要先进行PSO速度初始化（直接取totalSwarm里面最优的SV作为初始速度）*/
                if (i == 0) {
                    for (int j = 0; j < Swarm.size(); j++) {
                        DDPSv sv = new DDPSv(totalSwarm.get(totalIndex.get(j)), true);
                        Velocity.add(sv);
                    }
                }
            }

            //执行PSO算法
            if (AlgorType != 5) {
                //记录未更新前globalBest.Fitness和所有localBest.Fitness用于更新全局最优粒子的寿命
                double lastGlobalBest_Fitness=globalBest.Fitness;
                double lastAllLocalBest_Fitness=0.0;
                for (DDPSv lb: localBest) {
                    lastAllLocalBest_Fitness+=lb.Fitness;
                }
                /**step10:Swarm执行PSO算法的“速度更新”操作（只更新Swarm前1/2的SV的速度）*/
                //如果更新中途中出现资源不足的情况，则马上返回当前的全局最优解
                VelocityUpdate();

                /**step11:Swarm执行PSO算法的“位置更新”操作（只更新Swarm前1/2的SV的位置）*/
                //如果更新中途中出现资源不足的情况，则马上返回当前的全局最优解
                PositionUpdate();
                /**step12:更新localBest和globalBest*/
                globalAndLocalUpdateAfterPSO();

                /**step13:最后检查一下Velocity的SV能不能刷新globalBest（反正都生成出Velocity.size()个SV了，别浪费了）*/
                for (DDPSv sv : Velocity) {
                    if (sv.Fitness < globalBest.Fitness) {
                        globalBest = new DDPSv(sv, true);
                    }
                }
                /**step14:老化机制(更新年龄和寿命)*/
                updateAgeandLifespan(lastGlobalBest_Fitness,lastAllLocalBest_Fitness);
                /**step15:精英重选机制,若全局最优粒子的年龄大于寿命，则生成一个新的挑战者*/
                if(globalBest_age>globalBest_lifespan){
                    //System.out.println("globalBest_age:"+globalBest_age+",globalBest_lifespan:"+globalBest_lifespan);
                    EliteReelection();
                }
            }
            //至此，localBest、globalBest更新完毕，Swarm更新完毕，我们记录一下Fitness的收敛性
            if (VmAllocationPolicyForDifferentAlgorithm.optimizeAllocationCounter == 1
                    && (AlgorType == 0 || AlgorType == 5 || AlgorType == 6)) {
                FitnessConvergenceHistory.add(globalBest.Fitness);
            }
        }
        //用globalBest更新TargetHostList和AllocationVmList
        finalUpdate(globalBest);
        //如果检查收敛性
        if (VmAllocationPolicyForDifferentAlgorithm.IsFitnessConvergenceCheck) {
            //执行完第一次调度查看收敛性并退出
            for (Double fitness : FitnessConvergenceHistory) {
                System.out.print(fitness + ",");

            }
            System.out.println();
            System.exit(1);
        }
        System.out.println("DiscreteDEPSO<runAlgorithm()> Successful：算法完整流程执行完毕");
    }

    /**
     * ==================================================辅助方法==================================================
     */
    // 加入老化机制(更新年龄和寿命)
    private void updateAgeandLifespan(double lastGlobalBest_Fitness, double lastAllLocalBest_Fitness) {
        //计算当前时刻的全局最优粒子的fitness与上一时刻全局最优粒子的fitness的差值
        double gb_fitness_dif=globalBest.Fitness-lastGlobalBest_Fitness;
        // 计算当前时刻的所有局部最优粒子的fitness之和与上一时刻所有局部最优粒子的fitness之和的差值
        double alllocalBest_Fitness=0.0;
        for (DDPSv lb: localBest) {alllocalBest_Fitness+=lb.Fitness;}
        double lb_fitness_dif=alllocalBest_Fitness-lastAllLocalBest_Fitness;
        //寿命更新策略
        if(gb_fitness_dif<0.0){
            globalBest_lifespan+=2;
        } else if (gb_fitness_dif==0.0 && lb_fitness_dif<0.0) {
            globalBest_lifespan+=1;
        }
        //增加年龄
        globalBest_age+=1;
    }
    // 重选精英
    private void EliteReelection() {
        //System.out.println("EliteReelection.........");
        /**step1:在全局最优粒子的基础上生成一个（或几个）新的挑战者(还没想清楚怎么弄，先不管了)*/
        DDPSv challenger=new DDPSv(globalBest,true);

        //对挑战者进行变异操作(随机选择一个VM迁移到随机一台启动的主机上)
        Set<Integer> vm_ids = challenger.VmList.keySet();
        Integer[] vm_ids_array = new Integer[vm_ids.size()];
        vm_ids.toArray(vm_ids_array);
        Random r = new Random();
        int random_vm_id=vm_ids_array[r.nextInt(vm_ids_array.length)];
        DDPVm ddpVm = challenger.VmList.get(random_vm_id);//随机选择的VM

        Set<Integer> host_ids = challenger.HostList.keySet();
        Integer[] host_ids_array = new Integer[host_ids.size()];
        host_ids.toArray(host_ids_array);

        while (true){// 选择一台CPU利用率不为0的主机进行放置
            int random_host_id=host_ids_array[r.nextInt(host_ids_array.length)];
            if (challenger.HostList.get(random_host_id).CPU_util>0.0){
                DDPHost ddpHost = challenger.HostList.get(random_host_id);//随机选择的主机
                // Debug
//                System.out.println("未修改前VmId:"+ddpVm.VmId+"，VmList.size():"+challenger.VmList.size());
//                System.out.println("未修改前迁出的HostId:"+ddpVm.Host_Belongs+",ddpHost.VmList.size():"+challenger.HostList.get(ddpVm.Host_Belongs).VmList.size());
//                System.out.println("未修改前迁入的HostId:"+ddpHost.HostId+",ddpHost.VmList.size():"+ddpHost.VmList.size());
                // 将选择VM从原来的主机中删除
                challenger.HostList.get(ddpVm.Host_Belongs).VmList.remove(random_vm_id);
//                System.out.println("----------------------------------------------------------------------");
//                System.out.println("修改后迁出的HostId:"+ddpVm.Host_Belongs+",ddpHost.VmList.size():"+challenger.HostList.get(ddpVm.Host_Belongs).VmList.size());
                //将选择的VM的主机归属修改成新选的主机
                ddpVm.Host_Belongs=ddpHost.HostId;
                // 将选择的VM新增到对应的主机的VM列表中
                ddpHost.VmList.put(ddpHost.HostId,ddpVm);

                // Debug
//                System.out.println("修改后VmId:"+ddpVm.VmId+"，VmList.size():"+challenger.VmList.size());
//                System.out.println("修改后迁入的HostId:"+ddpHost.HostId+",ddpHost.VmList.size():"+ddpHost.VmList.size());
                break;
            }
        }
        /**step2:判断该挑战者是否优于原有的领导者*/
        challenger.Fitness=DiscreteDEPSO.getFitness(challenger);
//        System.out.println("challenger_fitness:"+challenger.Fitness+",globalBest.Fitness:"+globalBest.Fitness);
        if(challenger.Fitness<globalBest.Fitness){//若新的挑战者的Fitness小于全局最优粒子，则接受为新的领导者。
            globalBest = new DDPSv(challenger, true);
            //重置新领导者的年龄和寿命
            globalBest_age=0;
            globalBest_lifespan=this.initialLifespan;
//            System.out.println("挑战者成为新的领导者");
        }else{
            globalBest_age=globalBest_lifespan-1;
//            System.out.println("原领导者");
        }
    }

    //位置和速度更新完以后，则更新globalBest和localBest
    private void globalAndLocalUpdateAfterPSO() {
        //将原来的localBest(G-1)和Swarm的各个解向量对应比较
        for (int i = 0; i < Swarm.size(); i++) {
            DDPSv tmpSV = Swarm.get(i);
            //将Swarm解向量和local原来的解向量相比
            if (tmpSV.Fitness < localBest.get(i).Fitness) {
                //SV构造函数里面的Host和Vm都是新构建出来的
                DDPSv sv = new DDPSv(tmpSV, true);
                localBest.set(i, sv);
                //由于globalBest一定是从localBest里面选出来的，所以只有localBest需要刷新时才需要检查globalBest是否刷新
                if (tmpSV.Fitness < globalBest.Fitness)
                    globalBest = sv;
            }
        }

    }

    //PSO“位置更新”函数（depso的话只更新Swarm中较优的1/2的Velocity）
    private void PositionUpdate() throws InterruptedException {
        //这个变量和depso算法无关，只是pso算法需要更新Swarm中全部解向量的Position，所以才要用到该变量
        int PositionUpdateSize;
        if (AlgorType == 6) PositionUpdateSize = Swarm.size();
        else PositionUpdateSize = (int) (Swarm.size() * P);

        //用于存放Swarm.size()次变异操作后得到的Swarm.size()个“变异向量”
        ConcurrentHashMap<Integer, DDPSv> PositionUpdateMap = new ConcurrentHashMap<>();
        int[] isSvSelected = new int[PositionUpdateSize];//这个是必须的，因为多线程对MutationSwarmMap的操作线程不安全
        CountDownLatch latch = new CountDownLatch(PositionUpdateSize);
        PositionUpdateThread t = new PositionUpdateThread(Swarm, OriginSV, AlgorType, Y,
                SwarmIndex, Velocity, PositionUpdateMap, isSvSelected, latch);
        //利用多线程处理Mutation，线程可以通过HashMap对应号码上的Sv是否为空来决定处理哪个个体
        for (int i = 0; i < PositionUpdateSize; i++) {
            fixedThreadPool.execute(t);
        }
        latch.await();
        //根据HashMap来复原出MutationSwarm
        for (int i = 0; i < PositionUpdateSize; i++) {
            Swarm.set(SwarmIndex.get(i), PositionUpdateMap.get(SwarmIndex.get(i)));
        }
    }

    //PSO“速度更新”函数（只更新Swarm中较优的1/2的Velocity）
    private void VelocityUpdate() throws InterruptedException {
        //这个变量和depso算法无关，只是pso算法需要更新Swarm中全部解向量的Velocity，所以才要用到该变量
        //depso算法只需要更新Fitness排名前 Swarm.size()*P 的向量即可
        int VelocityUpdateSize;
        if (AlgorType == 6) VelocityUpdateSize = Swarm.size();
        else VelocityUpdateSize = (int) (Swarm.size() * P);

        //用于存放Swarm.size()次变异操作后得到的Swarm.size()个“变异向量”
        ConcurrentHashMap<Integer, DDPSv> VelocityUpdateMap = new ConcurrentHashMap<>();
        int[] isSvSelected = new int[VelocityUpdateSize];//这个是必须的，因为多线程对MutationSwarmMap的操作线程不安全
        CountDownLatch latch = new CountDownLatch(VelocityUpdateSize);
        VelocityUpdateThread t = new VelocityUpdateThread(localBest, globalBest, OriginSV, AlgorType, SwarmIndex, Velocity, VelocityUpdateMap, isSvSelected, latch);
        //利用多线程处理Mutation，线程可以通过HashMap对应号码上的Sv是否为空来决定处理哪个个体
        for (int i = 0; i < VelocityUpdateSize; i++) {
            fixedThreadPool.execute(t);
        }
        latch.await();
        //根据HashMap来更新相应SV的Velocity
        for (int i = 0; i < VelocityUpdateSize; i++) {
            Velocity.set(SwarmIndex.get(i), VelocityUpdateMap.get(SwarmIndex.get(i)));
        }
    }

    //利用轮盘赌决定下一代Swarm的后半段SV（速度初始化主要是因为这里有个排好序的totalSwarm所以希望直接在这里取）
    private void RouletteSelection() {
        //在较优的1/2个totalSwarm里面进行轮盘赌，并直接作为Swarm的后半段SV
        //先构建轮盘，由于Fitness越小越好，所以现在我们采用1/Fitness的形式作为轮盘间隔
        ArrayList<Double> Roulette = new ArrayList<>();
        double FitnessSum = 0;
        for (int i = 0; i < (totalIndex.size() + 1) / 2; i++) {
            FitnessSum += 1 / totalSwarm.get(totalIndex.get(i)).Fitness;
            Roulette.add(FitnessSum);
        }
        HashMap<Integer, Boolean> isSelected = new HashMap<>();
        for (int i = (int) (Swarm.size() * P), j = 0; i < Swarm.size(); i++, j++) {
            double tmp = VmAllocationPolicyForDifferentAlgorithm.rand.nextDouble() * FitnessSum;
            //二分查找定位比自身大的元素的下标 <范围一定在[0,Roulette.size())之间>，其实就是获取排名第几的SV
            //因为轮盘也是以Fitness为顺序构造起来的，所以获得的下标即代表排名第几
            int res = rouletteBinSearch(Roulette, 0, Roulette.size(), tmp);
            if (isSelected.get(res) == null) {
                isSelected.put(res, true);
                Swarm.set(i, totalSwarm.get(totalIndex.get(res)));
            }
        }
    }

    //普通的DE贪心选择策略，只用在de算法的运行中
    private void SimpleSelection() {
        for (int i = 0; i < Swarm.size(); i++) {
            DDPSv tmpSv;
            //先选出MutaSwarm和CrossSwarm中相应位最优的Sv
            if (MutaSwarm.get(i).Fitness < CrossSwarm.get(i).Fitness) tmpSv = MutaSwarm.get(i);
            else tmpSv = CrossSwarm.get(i);
            //再和Swarm原来的Sv去比，如果更优，则更新Swarm相应位的Sv
            if (tmpSv.Fitness < Swarm.get(i).Fitness) {
                //MutaSwarm和CrossSwarm里面的Sv本来就是new出来的，进入下一次Crossover也不会被覆盖
                Swarm.set(i, tmpSv);
            }
        }
    }

    //将worseHalf、MutationSwarm，CrossSwarm集成为一个totalSwarm并按照Fitness进行升序排序
    private void sortTotal(ArrayList<DDPSv> worsePart) {
        totalSwarm.clear();
        totalIndex.clear();
        int index = 0;
        //排序并取出1/2个较优的解向量作为Swarm的后半段SV，同时更新SwarmFitness
        for (int i = 0; i < worsePart.size(); i++, index++) {
            totalSwarm.add(worsePart.get(i));
            totalIndex.add(index);
        }
        for (int i = 0; i < MutaSwarm.size(); i++, index++) {
            totalSwarm.add(MutaSwarm.get(i));
            totalIndex.add(index);
        }
        for (int i = 0; i < CrossSwarm.size(); i++, index++) {
            totalSwarm.add(CrossSwarm.get(i));
            totalIndex.add(index);
        }
        //升序排序
        totalIndex.sort(new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                double o1Fitness = totalSwarm.get(o1).Fitness;
                double o2Fitness = totalSwarm.get(o2).Fitness;
                return Double.compare(o1Fitness, o2Fitness);
            }
        });
    }

    //返回的一定是比tar大的或等于tar的元素下表
    public static int rouletteBinSearch(ArrayList<Double> Roulette, int l, int r, double tar) {
        if (l >= r) return l;
        int k = (l + r) / 2;
        if (Roulette.get(k) == tar) return k;
        else if (Roulette.get(k) < tar) return rouletteBinSearch(Roulette, k + 1, r, tar);
        else return rouletteBinSearch(Roulette, l, k, tar);
    }

    //用于DE后更新localBest和globalBest（注意要直接建立SV副本，不能浅拷贝）
    private void globalAndLocalUpdateAfterDE() {
        //将原来的localBest(G-1)和MutationSwarm的各个解向量对应比较
        for (int i = 0; i < MutaSwarm.size(); i++) {
            DDPSv tmpSV1 = MutaSwarm.get(i);
            DDPSv tmpSV2 = CrossSwarm.get(i);
            DDPSv tmpSV = tmpSV1;

            //先选出第i个“变异向量”和“交叉向量”哪个很好一些
            if (tmpSV2.Fitness < tmpSV1.Fitness) tmpSV = tmpSV2;

            //再将较优的一个解向量和Swarm原来的解向量相比
            if (tmpSV.Fitness < localBest.get(i).Fitness) {
                //SV构造函数里面的Host和Vm都是新构建出来的
                DDPSv sv = new DDPSv(tmpSV, true);
                localBest.set(i, sv);
                //由于globalBest一定是从localBest里面选出来的，所以只有localBest需要刷新时才需要检查globalBest是否刷新
                if (tmpSV.Fitness < globalBest.Fitness) globalBest = sv;
            }
        }
    }

    /**交叉操作*/
    // <在X(t,G)与M(t,G)之间取VM集合，构造出C(t,G)>（此时X(t,G)还是X(t,G-1)的值，因为DE算法没完成，不会更新X(t,G)）
    //返回Swarm.size()个交叉向量
    private ArrayList<DDPSv> Crossover() throws InterruptedException {

        //用于存放Swarm.size()次交叉操作后得到的Swarm.size()个“交叉向量”
        ArrayList<DDPSv> CrossoverSwarm = new ArrayList<>();
        ConcurrentHashMap<Integer, DDPSv> CrossoverSwarmMap = new ConcurrentHashMap<>();
        int[] isSvSelected = new int[Swarm.size()];//这个是必须的，因为多线程对MutationSwarmMap的操作线程不安全
        CountDownLatch latch = new CountDownLatch(Swarm.size());
        CrossoverThread t = new CrossoverThread(Swarm, MutaSwarm, CrossSwarm, OriginSV, CR, AlgorType,
                CrossoverSwarmMap, isSvSelected, latch);
        //利用多线程处理Crossover，线程可以通过HashMap对应号码上的Sv是否为空来决定处理哪个个体
        for (int i = 0; i < Swarm.size(); i++) {
            fixedThreadPool.execute(t);
        }
        latch.await();
        //根据HashMap来复原出CrossoverSwarm
        for (int i = 0; i < Swarm.size(); i++) {
            CrossoverSwarm.add(CrossoverSwarmMap.get(i));
        }
        return CrossoverSwarm;
    }

    /**变异操作*/
    // M(t,G)=X(Remain,G)⊕F·(X(r1,G)⊙(r2,G)) ,(t=1,2,……,S)
    //需要注意，构造的变异向量必须全部是新的，复制构造函数全部要采用深拷贝，其余属性也要新建
    public ArrayList<DDPSv> Mutation() throws InterruptedException {
        //用于存放Swarm.size()次变异操作后得到的Swarm.size()个“变异向量”
        ArrayList<DDPSv> MutationSwarm = new ArrayList<>();
        ConcurrentHashMap<Integer, DDPSv> MutationSwarmMap = new ConcurrentHashMap<>();
        int[] isSvSelected = new int[Swarm.size()];//这个是必须的，因为多线程对MutationSwarmMap的操作线程不安全
        CountDownLatch latch = new CountDownLatch(Swarm.size());
        MutationThread t = new MutationThread(Swarm, MutaSwarm, OriginSV, MR, AlgorType,
                MutationSwarmMap, isSvSelected, latch);
        //利用多线程处理Mutation，线程可以通过HashMap对应号码上的Sv是否为空来决定处理哪个个体
        for (int i = 0; i < Swarm.size(); i++) {
            fixedThreadPool.execute(t);
        }
        latch.await();
        //根据HashMap来复原出MutationSwarm
        for (int i = 0; i < Swarm.size(); i++) {
            MutationSwarm.add(MutationSwarmMap.get(i));
        }
        return MutationSwarm;
    }

    //初始群体构造函数，只有当所有初始解都成功放置的时候才isAllOverload才为false，否则会被置为true
    private void Swarm_Initialize() {
        /* 构造初始解（PEAP算法一个解向量，MBFD一个解向量，其他随机生成）（新修改，DEPSO的初始解也全部是随机生成；）
         * 注意：这里要考虑溢出情况，可能出现所有服务器都无法成功放置Vm，这时候只能退出程序*/
        //创建PEAP_placement类，构造第一个初始解向量
//        if ((AlgorType == 0 || AlgorType == 1) && !VmAllocationPolicyForDifferentAlgorithm.IsFitnessConvergenceCheck) {
        if (AlgorType == 1 && !VmAllocationPolicyForDifferentAlgorithm.IsFitnessConvergenceCheck) {
            PEAP_Placement peap = new PEAP_Placement(Swarm.get(0));
            DDPHost tH = null;
            //Vm没放置完，就不断开启新的服务器，直到服务器不足为止
            while (!peap.placement(tH)) {
                if (AddTargetHost(peap.UnderAllocatedDDPVmWithMaxVcpuCapicity.MipsCapacityOfSingleVcpu,
                        peap.UnderAllocatedCPUReq, peap.UnderAllocatedRAMReq, peap.UnderAllocatedBandReq)) {
                    //开启新服务器成功，更新tH给X_placement更新自身的两张表
                    tH = Swarm.get(0).HostList.get(TargetHostList.get(TargetHostList.size() - 1).HostId);

                    //Debug：考虑一种特殊情况，把一台服务器加进去了，仍然没有成功放置任何Vm到新Host上时该怎么办？
                    //答：事实上不可能出现新增了服务器还加不进Vm的情况，因为实验中
                    //    所选Host的任意一项资源都大于单个Vm的任意一项资源的请求量
                    if (TargetHostList.get(TargetHostList.size() - 2).HostId == tH.HostId) {
                        System.out.println("同一台服务器被AddTargetHost两次，程序出错！");
                        System.exit(1);
                    }
                } else {
                    System.out.println("DiscreteDEPSO<SV_Initialize>：构建第一个初始解失败，数据中心的服务器不足，" +
                            "无法开启新的服务器，程序退出");
                    System.exit(1);
                }
            }
        }
        //这句话和DEPSO算法无关，只是因为Peap算法运行完就可以直接结束了
        if (AlgorType == 1) return;
        //创建MBFD类，构造第二个初始解向量（新修改，DEPSO的初始解也全部是随机生成；）
//        if ((AlgorType == 0 || AlgorType == 2) && !VmAllocationPolicyForDifferentAlgorithm.IsFitnessConvergenceCheck) {
        if (AlgorType == 2 && !VmAllocationPolicyForDifferentAlgorithm.IsFitnessConvergenceCheck) {
            MBFD_Placement mbfd = new MBFD_Placement(Swarm.get(1));
            //Vm没放置完，就不断开启新的服务器，直到服务器不足为止
            while (!mbfd.placement()) {//MBFD_SV（Swarm.get(1)）会通过AddTargetHost更新，直接被算法内部使用
                //注意，这里AddTargetHost必须同步维护整个Swarm的SV的HostList
                if (AddTargetHost(mbfd.UnderAllocatedDDPVmWithMaxVcpuCapicity.MipsCapacityOfSingleVcpu,
                        mbfd.UnderAllocatedCPUReq, mbfd.UnderAllocatedRAMReq, mbfd.UnderAllocatedBandReq)) {
                    //Debug：考虑一种特殊情况，把一台服务器加进去了，仍然没有成功放置任何Vm到新Host上时该怎么办？
                    //答：事实上不可能出现新增了服务器还加不进Vm的情况，因为实验中
                    //    所选Host的任意一项资源都大于单个Vm的任意一项资源的请求量
                    if (TargetHostList.get(TargetHostList.size() - 2).HostId ==
                            TargetHostList.get(TargetHostList.size() - 1).HostId) {
                        System.out.println("同一台服务器被AddTargetHost两次，程序出错！");
                        System.exit(1);
                    }
                } else {
                    if (AlgorType == 0) {
                        System.out.println("DiscreteDEPSO<SV_Initialize>：构建第二个初始解失败，数据中心的服务器不足，" +
                                "无法开启新的服务器，直接返回PEAP的解作为结果");
                        isAllHostOverload = true;
                        break;
                    } else {//这个分支说明只有mbfd在执行，一旦mbfd执行失败整个程序就崩了
                        System.out.println("DiscreteDEPSO<SV_Initialize>：执行MBFD算法失败，数据中心的服务器不足，" +
                                "无法开启新的服务器，程序退出");
                        System.exit(1);
                    }
                }
            }
            if (isAllHostOverload) {//服务器的资源不足
                finalUpdate(Swarm.get(0));//以Swarm.get(0)作为最终解
                return;
            }
        }
        if (AlgorType == 2) return;
        //随机算法生成剩余的Swarm.size()-2个解向量（depso要这样做，rnd的话只需生成一个，de和pso的话要生成Swarm.size()个解向量）
        // 新修改，DEPSO的初始解也全部是随机生成；
        if (AlgorType == 0 || AlgorType == 3 || AlgorType == 5 || AlgorType == 6) {
            //因为de和pso算法需要完全通过rnd来生成初始解向量，所以要设置这个rndBeginPos变量
            int rndBeginPos=0;
//            if (AlgorType == 5 || AlgorType == 6 ||
//                    VmAllocationPolicyForDifferentAlgorithm.IsFitnessConvergenceCheck) rndBeginPos = 0;
//            else rndBeginPos = 2;
            for (int i = rndBeginPos; i < Swarm.size(); i++) {
                //创建RND类，构建解向量
                RANDOM_Placement RND = new RANDOM_Placement(Swarm.get(i));
                //Vm没放置完，就不断开启新的服务器，直到服务器不足为止
                DDPHost tH2 = null;
                while (!RND.placement(tH2)) {//RND_SV（Swarm.get(i)）会通过AddTargetHost更新，直接被算法内部使用
                    //注意，这里AddTargetHost必须同步维护整个Swarm的SV的HostList
                    if (AddTargetHost(RND.UnderAllocatedDDPVmWithMaxVcpuCapicity.MipsCapacityOfSingleVcpu,
                            RND.UnderAllocatedCPUReq, RND.UnderAllocatedRAMReq, RND.UnderAllocatedBandReq)) {

                        //开启新服务器成功，更新tH给RANDOM_placement，用于更新里面的TargetHostList
                        tH2 = Swarm.get(i).HostList.get(TargetHostList.get(TargetHostList.size() - 1).HostId);

                        //Debug：考虑一种特殊情况，把一台服务器加进去了，仍然没有成功放置任何Vm到新Host上时该怎么办？
                        //答：事实上不可能出现新增了服务器还加不进Vm的情况，因为实验中
                        //    所选Host的任意一项资源都大于单个Vm的任意一项资源的请求量
                        if (TargetHostList.get(TargetHostList.size() - 2).HostId ==
                                TargetHostList.get(TargetHostList.size() - 1).HostId) {
                            System.out.println("同一台服务器被AddTargetHost两次，程序出错！");
                            System.exit(1);
                        }
                    } else {
                        if (AlgorType == 0) {
                            System.out.println("DiscreteDEPSO<构造函数>：构建第" + i + "个初始解失败，数据中心的服务器不足，" +
                                    "无法开启新的服务器，直接返回Swarm.get(1)或Swarm.get(0)作为结果");
                            isAllHostOverload = true;
                            break;
                        } else {//这个分支说明只有random算法在执行，一旦random执行失败整个程序就崩了
                            System.out.println("DiscreteDEPSO<SV_Initialize>：执行RANDOM算法失败，数据中心的服务器不足，" +
                                    "无法开启新的服务器，程序退出");
                            System.exit(1);
                        }
                    }
                }
                /*//Debug
                for(Integer HostId:Swarm.get(i).HostList.keySet()){
                    for(Integer VmId:Swarm.get(i).HostList.get(HostId).VmList.keySet()){
                        if(OriginSV.VmList.get(VmId)!=null){
                            if(Swarm.get(i).HostList.get(HostId).VmList.get(VmId).Host_Belongs!=HostId){
                                System.out.println("Swarm_Initialize<RANDOM_Placement>，有Sv-HostList中的Vm的Host_Belongs，" +
                                        "根本和自己所在的HostId对不上，算法有误！！！");
                                System.exit(1);
                            }
                        }
                    }
                }*/

                if (isAllHostOverload) {//全部服务器的MIPs_offer为0，直接结束算法
                    //选出前两个解中适应度函数值最小的解（最优解），用以更新TargetHostList和AllocateVmList
                    if (getFitness(Swarm.get(0)) >= getFitness(Swarm.get(1))) {
                        finalUpdate(Swarm.get(0));//以Swarm.get(0)作为最终解
                    } else {
                        finalUpdate(Swarm.get(1));//以Swarm.get(1)作为最终解
                    }
                    return;
                }

                if (AlgorType == 3) return;

            /*System.out.println("\n打印一下RND的结果：\n");
            for (Integer hostId : mbfd.MBFD_SV.HostList.keySet()) {
                DDPHost host = mbfd.MBFD_SV.HostList.get(hostId);
                System.out.println("HostId：" + host.HostId
                        + ", CPU_util=" + host.CPU_util
                        + ", RAM_util=" + host.RAM_util
                        + ", Band_util=" + host.Band_util
                        + ", peak_peff=" + host.peak_peff
                        + ", MIPs_offer=" + host.MIPs_offer
                        + ", RAM_avail=" + host.RAM_avail
                        + ", Band_avail=" + host.Band_avail
                        + ", Pe_Mips_Cap=" + host.PeList.get(0).MIPs_Cap);
            }*/
            }
        }

        //下面这段和初始化群体无关，仅仅是为了调用FFD
        if (AlgorType == 4) {
            //创建RND类，构建解向量
            FFD_Placement FFD = new FFD_Placement(Swarm.get(3), 0);
            //Vm没放置完，就不断开启新的服务器，直到服务器不足为止
            DDPHost tH2 = null;
            while (!FFD.placement(tH2)) {//RND_SV（Swarm.get(i)）会通过AddTargetHost更新，直接被算法内部使用
                //注意，这里AddTargetHost必须同步维护整个Swarm的SV的HostList
                if (AddTargetHost(FFD.UnderAllocatedDDPVmWithMaxVcpuCapicity.MipsCapacityOfSingleVcpu,
                        FFD.UnderAllocatedCPUReq, FFD.UnderAllocatedRAMReq, FFD.UnderAllocatedBandReq)) {

                    //开启新服务器成功，更新tH给RANDOM_placement，用于更新里面的TargetHostList
                    tH2 = Swarm.get(3).HostList.get(TargetHostList.get(TargetHostList.size() - 1).HostId);

                    //Debug：考虑一种特殊情况，把一台服务器加进去了，仍然没有成功放置任何Vm到新Host上时该怎么办？
                    //答：事实上不可能出现新增了服务器还加不进Vm的情况，因为实验中
                    //    所选Host的任意一项资源都大于单个Vm的任意一项资源的请求量
                    if (TargetHostList.get(TargetHostList.size() - 2).HostId ==
                            TargetHostList.get(TargetHostList.size() - 1).HostId) {
                        System.out.println("同一台服务器被AddTargetHost两次，程序出错！");
                        System.exit(1);
                    }
                } else {//这个分支说明只有ffd在执行，一旦ffd执行失败整个程序就崩了
                    System.out.println("DiscreteDEPSO<SV_Initialize>：执行FFD算法失败，数据中心的服务器不足，" + "无法开启新的服务器，程序退出");
                    System.exit(1);
                }
            }

            if (isAllHostOverload) {//全部服务器的MIPs_offer为0，直接结束算法
                System.exit(1);
            }
        }

    }

    //服务器开启策略
    //通过检查标志位决定使用什么开启策略
    private boolean AddTargetHost(double UnderAllocatedMaxMipsCapacity, double UnderAllocatedCPUReq,
                                  double UnderAllocatedRAMReq, double UnderAllocatedBandReq) {
        //这是我自创的“服务器开启策略”，希望开启CPU容量最符合UnderAllocatedCPUReq的服务器
        if (!IsAddTargetHostSequential || AlgorType == 0) {
            /* 记录情况1~3最适合的Host，后续将该Host加入到所有解向量中，优先取情况1和2的结果
             * 情况1就是找全属性最适合的，情况2找MIPs_offer最适合的，情况3找MIPs_offer最大的*/
            int bestHostPos1 = -1;
            int bestHostPos2 = -1;
            int bestHostPos3 = -1;

            //情况1：首先尝试只开启一台服务器就能满足所有UnderAllocatedReq
            for (int i = 0; i < HostList.size(); i++) {
                //寻找未开启，且资源能够一次性符合UnderAllocateReq的服务器（CloudSim里面似乎没有设置服务器当前是否活跃的字段）
                if (IsInTargetHostList.get(HostList.get(i).HostId) == null//必须加这一句，否则同一个Host有可能会被Add两遍
                        && HostList.get(i).CPU_util == 0
                        && HostList.get(i).PeList.get(0).MIPs_Cap >= UnderAllocatedMaxMipsCapacity
                        && HostList.get(i).UP_THR * HostList.get(i).CPU_Cap >= UnderAllocatedCPUReq
                        && HostList.get(i).RAM_avail >= UnderAllocatedRAMReq
                        && HostList.get(i).BW_avail >= UnderAllocatedBandReq) {
                    if (bestHostPos1 == -1) bestHostPos1 = i;
                    else {//资源都满足的情况下，尽量选择MIPs_offer最接近UnderAllocatedCPUReq的服务器
                        if (HostList.get(bestHostPos1).UP_THR * HostList.get(bestHostPos1).CPU_Cap >
                                HostList.get(i).UP_THR * HostList.get(i).CPU_Cap) {
                            bestHostPos1 = i;
                        }
                    }
                }
            }
            //情况2：如果没有找到，则寻找最合适的UnderAllocatedCPUReq的方式开启新的服务器
            for (int i = 0; i < HostList.size(); i++) {
                if (IsInTargetHostList.get(HostList.get(i).HostId) == null//必须加这一句，否则同一个Host有可能会被Add两遍
                        && HostList.get(i).CPU_util == 0
                        && HostList.get(i).PeList.get(0).MIPs_Cap >= UnderAllocatedMaxMipsCapacity
                        && HostList.get(i).UP_THR * HostList.get(i).CPU_Cap >= UnderAllocatedCPUReq) {
                    if (bestHostPos2 == -1) bestHostPos2 = i;
                    else {
                        if (HostList.get(bestHostPos2).UP_THR * HostList.get(bestHostPos2).CPU_Cap >
                                HostList.get(i).UP_THR * HostList.get(i).CPU_Cap) {
                            bestHostPos2 = i;
                        }
                    }
                }
            }
            //情况3：上述两种情况都没能找到服务器，则直接返回MIPs_offer最大的服务器
            for (int i = 0; i < HostList.size(); i++) {
                if (IsInTargetHostList.get(HostList.get(i).HostId) == null//必须加这一句，否则同一个Host有可能会被Add两遍
                        && HostList.get(i).CPU_util == 0
                        && HostList.get(i).PeList.get(0).MIPs_Cap >= UnderAllocatedMaxMipsCapacity) {
                    if (bestHostPos3 == -1) bestHostPos3 = i;
                    else {
                        if (HostList.get(bestHostPos3).UP_THR * HostList.get(bestHostPos3).CPU_Cap <
                                HostList.get(i).UP_THR * HostList.get(i).CPU_Cap) {
                            bestHostPos3 = i;
                        }
                    }
                }
            }

            //选出最终要添加的目标服务器
            int bestHostPos;
            if (bestHostPos1 != -1) {
                //Debug:检查触发了哪种情况
                System.out.println("DiscreteDEPSO<AddTargetHost>：触发了第1种情况");
                bestHostPos = bestHostPos1;
            } else if (bestHostPos2 != -1) {
                System.out.println("DiscreteDEPSO<AddTargetHost>：触发了第2种情况");
                bestHostPos = bestHostPos2;
            } else {
                System.out.println("DiscreteDEPSO<AddTargetHost>：触发了第3种情况");
                bestHostPos = bestHostPos3;
            }

            //将该服务器加入到“最终TargetHostList”和群体各个解向量的TargetHostList中
            if (bestHostPos != -1) {
                DDPHost tmp = HostList.get(bestHostPos);
                TargetHostList.add(tmp);
                IsInTargetHostList.put(tmp.HostId, true);
                //同步维护整个Swarm的SV和OriginSV的HostList
                for (DDPSv sv : Swarm) {
                    //这里注意一定要深拷贝服务器
                    DDPHost tmpHost = new DDPHost(tmp, true);
                    sv.HostList.put(tmpHost.HostId, tmpHost);
                }
                OriginSV.HostList.put(tmp.HostId, tmp);
                return true;
            } else {
                System.out.println("\nDiscreteDEPSO<AddTargetHost>：服务器资源不足，玩蛋");
                return false;
            }
        } else {//顺序开启服务器
            int bestHostPos = -1;

            for (int i = 0; i < HostList.size(); i++) {
                //寻找未开启，且资源能够一次性符合UnderAllocateReq的服务器（CloudSim里面似乎没有设置服务器当前是否活跃的字段）
                if (IsInTargetHostList.get(HostList.get(i).HostId) == null//必须加这一句，否则同一个Host有可能会被Add两遍
                        && HostList.get(i).CPU_util == 0) {
                    bestHostPos = i;
                    break;
                }
            }

            //将该服务器加入到“最终TargetHostList”和群体各个解向量的TargetHostList中
            if (bestHostPos != -1) {
                DDPHost tmp = HostList.get(bestHostPos);
                TargetHostList.add(tmp);
                IsInTargetHostList.put(tmp.HostId, true);
                //同步维护整个Swarm的SV和OriginSV的HostList
                for (DDPSv sv : Swarm) {
                    //这里注意一定要深拷贝服务器
                    DDPHost tmpHost = new DDPHost(tmp, true);
                    sv.HostList.put(tmpHost.HostId, tmpHost);
                }
                OriginSV.HostList.put(tmp.HostId, tmp);
                return true;
            } else {
                System.out.println("\nDiscreteDEPSO<AddTargetHost>：服务器资源不足，玩蛋");
                return false;
            }
        }

    }

    //将最终结果更新到TargetHostList和AllocationVmList中
    private void finalUpdate(DDPSv res_SV) {
        //更新所有目标服务器的数据
        for (DDPHost host : TargetHostList) {
            host.CopyUpdate(res_SV.HostList.get(host.HostId));
        }

        //更新所有待分配Vm的数据（CloudSim构造migrationMap要用到）
        /*//Debug：检查globalBest的Vm列表和AllocationVmList的Vm
        System.out.println("\nAllocationVmList的Vm：");
        for(DDPVm vm:AllocationVmList){
            System.out.println(vm.VmId);
        }
        System.out.println("\nglobalBest的VmList：");
        for(Integer VmId:res_SV.VmList.keySet()){
            System.out.println(VmId);
        }*/
        for (DDPVm vm : AllocationVmList) {
            /*//Debug：res_SV里面不存在某台Vm
            if (res_SV.VmList.get(vm.VmId) == null) {
                System.out.println("出错了，res_SV里面缺失了Vm #" + vm.VmId);
                System.exit(1);
            }*/
            vm.CopyUpdate(res_SV.VmList.get(vm.VmId));
        }
    }

    //计算解向量的适应度函数值f(x)=求和(根号下(w1*utilFactor+w2*powerFactor+w3*peffFactor))
    public static double getFitness(DDPSv a) {
        double utilFactor;
        double powerFactor;
        double thermalFactor;
        double peffFactor;
        double Fitness = 0;
        for (Integer HostId : a.HostList.keySet()) {
            DDPHost tmp = a.HostList.get(HostId);
            //这里之所以要这样判断是为了排除有些Host虽然作为目标服务器，但却没有任何Vm在上面
            if (tmp.CPU_util != 0) {
                utilFactor = Math.pow(tmp.CPU_util - tmp.pp_CPU_util, 2) * 100;
                peffFactor = Math.pow((1 - tmp.Peff / peak_peff_max), 2) * 100;  // 能效指标，暂未使用
                /**能耗*/
                powerFactor = Math.pow((tmp.Power / peak_power_max), 2) * 100; // (p1)peak_power_max：所有目标服务器范围内的最大峰值功耗
//                powerFactor = Math.pow((tmp.Power / tmp.peak_power), 2) * 100; // (p2)peak_power：服务器本身的最大峰值功耗
//                powerFactor = tmp.Power / tmp.peak_power; // (p3)服务器的功耗比例，其中peak_power：服务器本身的最大峰值功耗
                /**温度*/
                thermalFactor = Math.pow((tmp.Temperature / peak_temperature_max), 2) * 100; // (t1)peak_temperature_max：所有目标服务器范围内的最大峰值温度
//                thermalFactor = Math.pow((tmp.Temperature / 75.0), 2) * 100; //(t2)新增（75表示主机的红线温度，目标是主机温度越低于红线温度越好）
//                thermalFactor = tmp.Temperature / 75.0; //(t3)新增（75表示主机的红线温度，目标是主机温度越低于红线温度越好）
//                thermalFactor = Math.pow((tmp.temperature-average_temperature), 2); //(t4)新增，目标是使得所有主机的温度接近，也就是减少温度梯度；
                Fitness += Math.sqrt(w1 * utilFactor + w2 * powerFactor + w3 * peffFactor+ w4*thermalFactor);
            }
        }
        return Fitness;
    }
}

class MutationThread implements Runnable {
    private ArrayList<DDPSv> Swarm;
    private ArrayList<DDPSv> MutaSwarm;
    private DDPSv OriginSV;
    private double MR;
    private int AlgorType;
    private ConcurrentHashMap<Integer, DDPSv> MutationSwarmMap;
    private int[] isSvSelected;
    private CountDownLatch latch;

    public MutationThread(ArrayList<DDPSv> Swarm, ArrayList<DDPSv> MutaSwarm, DDPSv OriginSV,
                          double MR, int AlgorType, ConcurrentHashMap<Integer, DDPSv> MutationSwarmMap,
                          int[] isSvSelected, CountDownLatch latch) {
        this.Swarm = Swarm;
        this.MutaSwarm = MutaSwarm;
        this.OriginSV = OriginSV;
        this.MR = MR;
        this.AlgorType = AlgorType;
        this.MutationSwarmMap = MutationSwarmMap;
        this.isSvSelected = isSvSelected;
        this.latch = latch;
    }

    public void run() {
        //通过isSvSelected先选出Swarm的一个SV
        int SvNo = -1;
        synchronized (this) {
            for (int i = 0; i < Swarm.size(); i++) {
                if (isSvSelected[i] == 0) {
                    isSvSelected[i] = 1;
                    SvNo = i;
                    //System.out.println(Thread.currentThread().getName()+"："+"我选了 "+SvNo);
                    break;
                }
            }
            if (SvNo == -1) {
                System.out.println("群体全部变异完毕，告辞~！");//不应该出现这句话
                return;//没选上的话说明已经结束了（保险起见还是加上这一句）
            }
        }

        //创建一个用于记录Mutation操作后的解向量
        DDPSv SvAfterMutation = new DDPSv();
        //用于X_placement，直接对HostList进行浅拷贝，VmList初始化为空（VmList用于存放需要再分配的Vm）
        DDPSv Conflict_SV = new DDPSv();
        Conflict_SV.HostList = SvAfterMutation.HostList;

        //step1：先随机选出r2向量，r1向量直接取Swarm[i]，r1,r2用于执行“⊙”运算
        int r1 = SvNo, r2 = 0;
        //synchronized (this){
        while (r1 == r2) {
            r2 = VmAllocationPolicyForDifferentAlgorithm.rand.nextInt(Swarm.size());
        }
        //}

        //step2：对选出的SV解向量r1和r2里面的每一维执行“⊙”运算，得到VmReAllocation
        //       和X(Remain,G)=DF1''（DF1_A）<相同和相异的都加到了DF1_A中>
        for (Integer HostId : Swarm.get(r1).HostList.keySet()) {
            //用于记录一维（一个目标服务器）变异后的结果，先初始化为OriginSV相应的Host状态（不含任何待放置Vm）
            DDPHost hostAfterMutation = new DDPHost(OriginSV.HostList.get(HostId),
                    true);//复制该Host在OriginSV中的状态

            //先求出DF1
            DDPHost host_r1 = Swarm.get(r1).HostList.get(HostId);
            DDPHost host_r2 = Swarm.get(r2).HostList.get(HostId);

            //注意！！！为什么在算法中我不需要统计哪些VM已经放置了？这是因为，我只会把host_r1异于host_r2的Vm
            //加入到hostAfterMutation中，而不会把host_r2异于host_r1的Vm加入到里面来，我是以host_r1作为基准的。
            //所以不存在一个Vm放置到两台Host上的情况（取决于getDifferAndSameVm的实现）

            //下面函数返回的数组中[0]为相异的Vm，[1]为相同的Vm（这些Vm全部都是在函数中深拷贝的）
            //结果SV_r1的所有Vm都会深拷贝一遍
            ArrayList<ArrayList<DDPVm>> res = getDifferAndSameVm(host_r1, host_r2, OriginSV.VmList);
            //注意获得的res的Differ和Sime数组都有可能为空（毕竟有的Host维上可能就没有待迁移Vm）

            //DF1分为DF1_A（保留到X(Remain,G)）和DF1_B（需要重新放置的VM）
            ArrayList<DDPVm> DF1_A = new ArrayList<>();
            ArrayList<DDPVm> DF1_B = new ArrayList<>();
            //用轮盘赌的方式，将（相异的Vm）按概率加入到DF1_A和DF1_B中
            //synchronized (this){
            for (DDPVm vm : res.get(0)) {
                if (VmAllocationPolicyForDifferentAlgorithm.rand.nextInt(1000) / 1000.0 <= MR) DF1_A.add(vm);
                else DF1_B.add(vm);
            }
            //}
            //把res[1]的Vm（相同的Vm）也加入到DF1_A中
            DF1_A.addAll(res.get(1));

            //DF1_A即为X(Remain,G) HostId维的VM集合，接下来需要将这部分Vm放置到host中，因为这部分VM已经确定保留到“变异向量”中
            //需要注意，这里Vm的放置要放置回原来的Core里面。另外还需要更新VmList相应Vm的属性
            //DF1_A里面的host还未必就能全部放置成功（放置的顺序不同可能会出现资源碎片），
            // 所以要用一个数组记录没有放置成功的Vm
            ArrayList<DDPVm> UnAllocatedVmInDF1_A = new ArrayList<>();
            for (int j = 0; j < DF1_A.size(); j++) {
                //VM未必放得下，因为这是两台Host上的VM，所以放置前要先检查一下出现放不下的情况，则剩余的VM都加入到DF1_B
                //深拷贝然后尝试放置Vm，放置失败的话说明这个host的负载程度已经比较满了，让DF1_A中剩余的VM重新放置
                DDPVm vm = DF1_A.get(j);//不用深拷贝，因为在getDifferAndSameVm函数中已经深拷贝了
                HashMap<String, Object> res2 = CheckVmPlacement.isSuitableForVm(hostAfterMutation, vm);
                //检查是否放置成功
                if (res2 != null) {
                    //先更新Vm的动态数据
                    vm.placementUpdate(hostAfterMutation.HostId, (ArrayList<DDPPeMipsMapPair>) res2.get("PeMipsMap"),
                            (double) res2.get("total_cpu"), (double) res2.get("ram"), (double) res2.get("bw"));
                    //然后更新Host的动态数据
                    hostAfterMutation.placementUpdate(null, vm);
                    //将getDifferAndSameVm中新建的DF1_A中的vm加入到MutationSV的VmList中
                    SvAfterMutation.VmList.put(vm.VmId, vm);
                } else {
                    UnAllocatedVmInDF1_A.add(vm);
                }
            }
            //没放置成功的Vm都老老实实加进DF1_B里面，用启发式算法重新放置
            DF1_B.addAll(UnAllocatedVmInDF1_A);

            //DF1_B的Vm加入到Conflict_SV中，用于后续X_placement
            for (DDPVm vm : DF1_B) {
                Conflict_SV.VmList.put(vm.VmId, vm);
            }

            //至此hostAfterMutation已经成为了X(Remain,G)中一维的Host状态（VmList只包含保留的Vm）
            SvAfterMutation.HostList.put(hostAfterMutation.HostId, hostAfterMutation);
        }

        //step3：执行“⊕”运算(X_placement)
        //创建一个SV，专门用于X_placement，其中VmList只存放要重分配的Vm
        if (Conflict_SV.VmList.size() > 0) {//确保真的有待分配Vm是要
            int failFlag = 0;
            if (AlgorType == 5) {
                    /*RANDOM_Placement rnd = new RANDOM_Placement(Conflict_SV);
                    if (!rnd.placement(null)) {
                        //return null;
                        //要是发生冲突，那就不更新这一维的MutationSV呗
                        MutationSwarm.add(MutaSwarm.get(i));
                        continue;
                    }*/
                MBFD_Placement mbfd = new MBFD_Placement(Conflict_SV);
                if (!mbfd.placement()) {
                    //return null;
                    //要是发生冲突，那就不更新这一维的MutationSV呗
                    failFlag = 1;
                }
            } else {
                MBFD_Placement mbfd = new MBFD_Placement(Conflict_SV);
                if (!mbfd.placement()) {
                    //return null;
                    //要是发生冲突，那就不更新这一维的MutationSV呗
                    failFlag = 1;
                }
                    /*PEAP_Placement peap = new PEAP_Placement(Conflict_SV);
                    if (!peap.placement(null)) {
                        MutationSwarm.add(MutaSwarm.get(i));
                        continue;//如果出现服务器资源不足，那说明这次位置更新失败了，那就不更新这一维的位置咯
                    }*/
            }
            if (failFlag == 0) {//如果未放置的Vm放置成功，则Mutation结束
                //添加Conflict_SV的VmList到MutationSV里面的Vm列表里面（HostList的话已经在Conflict_SV中更新了）
                for (Integer VmId : Conflict_SV.VmList.keySet()) {
                    SvAfterMutation.VmList.put(VmId, Conflict_SV.VmList.get(VmId));
                }
            } else {//如果Vm未放置成功，则Mutation失败，将原来的MutaSwarm相应Sv重新赋值进来
                SvAfterMutation = MutaSwarm.get(SvNo);
            }
        }
        //step4：将“变异向量”加入到MutationSwarm
        SvAfterMutation.Fitness = DiscreteDEPSO.getFitness(SvAfterMutation);
        //保证MutationSwarmMap同步
        synchronized (this) {
            MutationSwarmMap.put(SvNo, SvAfterMutation);
            /*//Debug：确定新构建的Sv没有弄丢Host，也没有因为赋值而弄丢Host
            DDPSv checkSv = MutationSwarmMap.get(SvNo);
            System.out.println(Thread.currentThread().getName()+": Sv #"+SvNo+" 处理完毕，结果如下：");
            for (Integer HostId : Swarm.get(SvNo).HostList.keySet()) {
                if (SvAfterMutation.HostList.get(HostId) == null) {
                    System.out.println("变异过程中弄丢了服务器！！！");
                    System.exit(1);
                }
            }
            System.out.println("\n");
            for (Integer HostId : SvAfterMutation.HostList.keySet()) {
                if (checkSv.HostList.get(HostId) != SvAfterMutation.HostList.get(HostId)) {
                    System.out.println("出问题了，checkSv里面的HostId和SvAfterMutation的对不上");
                    System.exit(1);
                }
            }
            System.out.println("处理成功")*/
        }
        latch.countDown();
    }

    //获取host1相异于host2的待迁移Vm，和host1与host2相同的待迁移Vm（用于Mutation）
    //return：一个数组，[0]表示相异的Vm，[1]表示相同的Vm
    private ArrayList<ArrayList<DDPVm>> getDifferAndSameVm
    (DDPHost host1, DDPHost host2, HashMap<Integer, DDPVm> VmInMigra) {
        ArrayList<ArrayList<DDPVm>> res = new ArrayList<ArrayList<DDPVm>>();
        ArrayList<DDPVm> Differ = new ArrayList<>();
        ArrayList<DDPVm> Same = new ArrayList<>();
        //Host2不同于Host1的Vm不用管，因为我以Sv1的Vm作为基准，遍历完Sv1的Host以后，
        // 所有AllocationVmList的Vm是全部都有判断过一遍的
        for (Integer VmId : host1.VmList.keySet()) {
            if (VmInMigra.get(VmId) != null) {//只有在待迁移Vm列表中的Vm才进行这一操作
                DDPVm vm = new DDPVm(host1.VmList.get(VmId));//为了避免影响到Swarm，还是直接new一个DDPVm吧
                if (host2.VmList.get(VmId) == null) Differ.add(vm);
                else Same.add(vm);
            }
        }
        res.add(Differ);
        res.add(Same);
        return res;
    }
}

class CrossoverThread implements Runnable {
    private ArrayList<DDPSv> Swarm;
    private ArrayList<DDPSv> MutaSwarm;
    private ArrayList<DDPSv> CrossSwarm;
    private DDPSv OriginSV;
    private double CR;
    private int AlgorType;
    private ConcurrentHashMap<Integer, DDPSv> CrossoverSwarmMap;
    private int[] isSvSelected;
    private CountDownLatch latch;

    public CrossoverThread(ArrayList<DDPSv> Swarm, ArrayList<DDPSv> MutaSwarm, ArrayList<DDPSv> CrossSwarm,
                           DDPSv OriginSV, double CR, int AlgorType,
                           ConcurrentHashMap<Integer, DDPSv> CrossoverSwarmMap,
                           int[] isSvSelected, CountDownLatch latch) {
        this.Swarm = Swarm;
        this.MutaSwarm = MutaSwarm;
        this.CrossSwarm = CrossSwarm;
        this.OriginSV = OriginSV;
        this.CR = CR;
        this.AlgorType = AlgorType;
        this.CrossoverSwarmMap = CrossoverSwarmMap;
        this.isSvSelected = isSvSelected;
        this.latch = latch;
    }

    public void run() {
        //通过isSvSelected先选出Swarm的一个SV
        int SvNo = -1;
        synchronized (this) {
            for (int i = 0; i < Swarm.size(); i++) {
                if (isSvSelected[i] == 0) {
                    isSvSelected[i] = 1;
                    SvNo = i;
                    //System.out.println(Thread.currentThread().getName()+"："+"我选了 "+SvNo);
                    break;
                }
            }
            if (SvNo == -1) {
                System.out.println("群体全部交叉完毕，告辞~！");//不应该出现这句话
                return;//没选上的话说明已经结束了（保险起见还是加上这一句）
            }
        }

        //用于记录解向量（VmList需要从旧的Sv的Host中深拷贝过来，之后再更新Belongs属性，HostList新建）
        DDPSv SvAfterCrossover = new DDPSv();
        //用于X_placement，直接对HostList进行浅拷贝，VmList初始化为空（VmList用于存放需要再分配的Vm）
        DDPSv Conflict_SV = new DDPSv();
        Conflict_SV.HostList = SvAfterCrossover.HostList;
        //用于检查Vm是否经过放置
        HashMap<Integer, Boolean> VmIsAllocate = new HashMap<>();

        //对所选SV中的每一维Host执行交叉操作
        for (Integer HostId : Swarm.get(SvNo).HostList.keySet()) {
            //用于记录一维Host交叉后的结果，此时所有待放置Vm并不在Host中
            //我们需要通过检查Host中的Vm，找到相关的待迁移Vm，然后加入到SvAfterCrossover的VmList中
            DDPHost HostAfterCrossover = new DDPHost(OriginSV.HostList.get(HostId),
                    true);//复制OriginSV相应维数的Host
            DDPHost Xhost = Swarm.get(SvNo).HostList.get(HostId);//X(t,G) HostId维的host
            DDPHost Mhost = MutaSwarm.get(SvNo).HostList.get(HostId);//M(t,G) HostId维的host

            //注意，有可能Xhost和Mhost上都没有待迁移Vm放在上面，那就只能让它保持原样了
            //（因为如果没待迁移Vm在上面的话，就没什么好交叉的了）

            //step1:按概率决定该Host取哪个VmList（注意VmList的Vm要新建）
            //      请注意，这里有可能HostId维的Xhost和Mhost上都没有分配到待迁移的VM
            DDPHost selectedHost;
            //小于等于CR就取X(t,G)
            if (VmAllocationPolicyForDifferentAlgorithm.rand.nextInt(1000) / 1000.0 <= CR) {
                selectedHost = Xhost;
            } else {
                selectedHost = Mhost;
            }
            //更新hostAfterCrossover（将X(t,G)的VM逐个放置到里面即可）（注意去重）
            for (Integer VmId : selectedHost.VmList.keySet()) {
                if (OriginSV.VmList.get(VmId) != null) {//OriginSV里面原有的VM不动，只针对待迁移VM执行交叉操作
                    DDPVm vm = new DDPVm(selectedHost.VmList.get(VmId));
                    //检查Vm是否重复，若没有重复就进行放置，VM肯定能放得下（只会因重复而减少，不会增多）
                    if (VmIsAllocate.get(vm.VmId) == null) {
                        VmIsAllocate.put(vm.VmId, true);
                                /*//Debug
                                if(vm.Host_Belongs!=HostAfterCrossover.HostId){
                                    System.out.println("Crossover从该服务器中拿出的Vm的Host_Belongs不是该服务器？？");
                                    System.exit(1);
                                }*/
                        //更新Host的动态数据（Vm的动态数据由于没有挪过位置，所以不用更新）
                        HostAfterCrossover.placementUpdate(vm.PeMipsMap, vm);//Vm放置
                        SvAfterCrossover.VmList.put(vm.VmId, vm);
                    }
                }
            }
            //将hostAfterCrossover加入到CrossoverSV的HostList中（step2要根据这个HostList的host状态，对剩余的VM进行放置
            SvAfterCrossover.HostList.put(HostAfterCrossover.HostId, HostAfterCrossover);
        }
        //step2:现在利用Conflict_SV，通过X_placement将剩余的Vm也放置到里面
        //统计出待迁移Vm中剩余哪些VM没有得到放置，并将它们加入到Conflict_SV里面
        for (Integer VmId : OriginSV.VmList.keySet()) {
            if (VmIsAllocate.get(VmId) == null) {
                DDPVm vm = new DDPVm(OriginSV.VmList.get(VmId));
                Conflict_SV.VmList.put(vm.VmId, vm);
            }
        }
        //调用X_placement将剩余Vm放置到上面
        if (Conflict_SV.VmList.size() > 0) {
            int failFlag = 0;
            if (AlgorType == 5) {//传统DE算法的话，冲突只用RND_Placement
                    /*RANDOM_Placement rnd = new RANDOM_Placement(Conflict_SV);
                    if (!rnd.placement(null)) {
                        //return null;
                        //要是发生冲突，那就不更新这一维的CrossoverSV
                        CrossoverSwarm.add(CrossSwarm.get(i));
                        continue;
                    }*/
                MBFD_Placement mbfd = new MBFD_Placement(Conflict_SV);
                if (!mbfd.placement()) {
                    //return null;
                    //要是发生冲突，那就不更新这一维的CrossoverSV
                    failFlag = 1;
                }
            } else {//depso
                MBFD_Placement mbfd = new MBFD_Placement(Conflict_SV);
                if (!mbfd.placement()) {
                    //return null;
                    //要是发生冲突，那就不更新这一维的CrossoverSV
                    failFlag = 1;
                }
                    /*PEAP_Placement peap = new PEAP_Placement(Conflict_SV);
                    if (!peap.placement(null)) {
                        CrossoverSwarm.add(CrossSwarm.get(i));
                        continue;//如果出现服务器资源不足，那说明这次位置更新失败了，那就不更新这一维的位置咯
                    }*/
            }
            if (failFlag == 0) {
                //添加Conflict_SV的VmList到MutationSV里面的Vm列表里面（HostList的话已经在Conflict_SV中更新了）
                //此外，Host和Vm都已经在mbfd.placement中得到更新，这里仅仅需要维护List就可以了
                for (Integer VmId : Conflict_SV.VmList.keySet()) {
                    SvAfterCrossover.VmList.put(VmId, Conflict_SV.VmList.get(VmId));
                }
            } else {
                SvAfterCrossover = CrossSwarm.get(SvNo);
            }
        }
        //step3：将“交叉向量”加入到CrossSwarm
        SvAfterCrossover.Fitness = DiscreteDEPSO.getFitness(SvAfterCrossover);
        //保证CrossoverSwarmMap同步
        synchronized (this) {
            CrossoverSwarmMap.put(SvNo, SvAfterCrossover);
        }
        latch.countDown();
        /*//Debug：检查每一个Sv个体会不会缺了某一台Vm
        for (Integer VmId : OriginSV.VmList.keySet()) {
            if (SvAfterCrossover.VmList.get(VmId) == null) {
                System.out.println("Crossover中某个sv的VmList少了一台待迁移Vm，算法有误！");
                System.exit(1);
            }
            if (SvAfterCrossover.HostList.get(SvAfterCrossover.VmList.get(VmId).Host_Belongs).VmList.get(VmId) == null) {
                System.out.println("Crossover有问题，待放置Vm的Host_Belongs上找不到相应的Vm");
                System.exit(1);
            }
        }*/
    }
}

class VelocityUpdateThread implements Runnable {
    private ArrayList<DDPSv> localBest;
    private DDPSv globalBest;
    private DDPSv OriginSV;
    private int AlgorType;
    private ArrayList<Integer> SwarmIndex;
    private ArrayList<DDPSv> Velocity;
    private ConcurrentHashMap<Integer, DDPSv> VelocityUpdateMap;
    private int[] isSvSelected;
    private CountDownLatch latch;

    public VelocityUpdateThread(ArrayList<DDPSv> localBest, DDPSv globalBest, DDPSv OriginSV,
                                int AlgorType, ArrayList<Integer> SwarmIndex, ArrayList<DDPSv> Velocity,
                                ConcurrentHashMap<Integer, DDPSv> VelocityUpdateMap,
                                int[] isSvSelected, CountDownLatch latch) {
        this.localBest = localBest;
        this.globalBest = globalBest;
        this.OriginSV = OriginSV;
        this.AlgorType = AlgorType;
        this.SwarmIndex = SwarmIndex;
        this.Velocity = Velocity;
        this.VelocityUpdateMap = VelocityUpdateMap;
        this.isSvSelected = isSvSelected;
        this.latch = latch;
    }

    public void run() {
        //通过isSvSelected先选出Swarm中排名第SvNo的SV，表示该线程要更新该SV的Velocity
        int SvNo = -1;
        synchronized (this) {
            for (int i = 0; i < isSvSelected.length; i++) {
                if (isSvSelected[i] == 0) {
                    isSvSelected[i] = 1;
                    SvNo = i;
                    //System.out.println(Thread.currentThread().getName()+"："+"我选了 "+SvNo);
                    break;
                }
            }
            if (SvNo == -1) {
                System.out.println("要求的速度更新完毕，告辞~！");//不应该出现这句话
                return;//没选上的话说明已经结束了（保险起见还是加上这一句）
            }
        }
        /*//Debug
        System.out.println("DiscreteDEPSO <VelocityUpdate> 正在进行第" + i + "个VelocitySV的更新");*/
        //因为“速度更新”中途可能会出现服务器资源不足的情况，这种情况下，我们就不更新该SV的“速度向量”
        //然后让算法继续运行
        //记录Vm是否已经放置到Velocity的某一维Host中
        HashMap<Integer, Boolean> VmIsAllocated = new HashMap<>();
        //取出Fitness排名第i的速度SV
        //将Velocity、localBest第i个SV以及globalBest取出
        DDPSv velocitySV = Velocity.get(SwarmIndex.get(SvNo));
        DDPSv localBestSV = localBest.get(SwarmIndex.get(SvNo));
        DDPSv globalBestSV = globalBest;
        //用于记录新Velocity的临时SV状态
        DDPSv SvAfterVelocityUpdate = new DDPSv();
        DDPSv Conflict_SV = new DDPSv();//HostList方面，Conflict_SV和SvAfterVelocityUpdate指向同一个HostList
        Conflict_SV.HostList = SvAfterVelocityUpdate.HostList;
        //step1:先构造出P1、P2、P3
        double P1 = 1 / Velocity.get(SwarmIndex.get(SvNo)).Fitness;
        double P2 = 1 / localBest.get(SwarmIndex.get(SvNo)).Fitness;
        double P3 = 1 / globalBest.Fitness;
        double PSum = P1 + P2 + P3;
        P1 /= PSum;
        P2 /= PSum;
        P3 /= PSum;
        //step2:接下来开始对所选SV的新一代速度的各HostId维进行Vm的放置（每维代表一个Host）（可能会存在Vm没有得到放置）
        for (Integer HostId : velocitySV.HostList.keySet()) {
            //System.out.println("DiscreteDEPSO <VelocityUpdate> VelocitySV中第" + HostId + "维的更新");
            //遍历三个解向量该维的Vm列表，统计Vm放置在该Host（该维）上的概率
            ArrayList<DDPVm> VmList = new ArrayList<>();//记录该维更新中可能进行放置的待迁移Vm
            ArrayList<Double> Roulette = new ArrayList<>();//Vm的概率轮盘
            HashMap<Integer, Double> VmProbability = new HashMap<>();//key为VmId，Double为放置到当前服务器的概率
            DDPHost VeloHost = velocitySV.HostList.get(HostId);//v(t,G-1)的一维
            DDPHost LocalHost = localBestSV.HostList.get(HostId);//local(t,G)的一维
            DDPHost GlobalHost = globalBestSV.HostList.get(HostId);//global(t,G)的一维
            //用于记录“速度更新”后该HostId维的状态
            DDPHost HostAfterVelocityUpdate = new DDPHost(OriginSV.HostList.get(HostId),
                    true);//复制构造函数

            for (Integer VmId : VeloHost.VmList.keySet()) {
                if (OriginSV.VmList.get(VmId) != null) {//只对待迁移VM进行操作
                    if (VmIsAllocated.get(VmId) == null) {//该SV之前HostId维数放置过的Vm，这里不会再次进行放置
                        VmProbability.put(VmId, P1);
                        VmList.add(VeloHost.VmList.get(VmId));
                    }
                }
            }
            for (Integer VmId : LocalHost.VmList.keySet()) {//如果没有该key，则直接设为P2，如果有该key，则设为OldVal+P2
                if (OriginSV.VmList.get(VmId) != null) {//只对待迁移VM进行操作
                    if (VmIsAllocated.get(VmId) == null) {//该SV之前HostId维数放置过的Vm，这里不会再次进行放置
                        if (VmProbability.get(VmId) == null) {
                            VmProbability.put(VmId, P2);
                            VmList.add(LocalHost.VmList.get(VmId));
                        } else {
                            VmProbability.merge(VmId, P2, Double::sum);
                        }
                    }
                }
            }
            for (Integer VmId : GlobalHost.VmList.keySet()) {//如果没有该key，则直接设为P3，如果有该key，则设为OldVal+P3
                if (OriginSV.VmList.get(VmId) != null) {//只对待迁移VM进行操作
                    if (VmIsAllocated.get(VmId) == null) {//该SV之前HostId维数放置过的Vm，这里不会再次进行放置
                        if (VmProbability.get(VmId) == null) {
                            VmProbability.put(VmId, P3);
                            VmList.add(GlobalHost.VmList.get(VmId));
                        } else {
                            VmProbability.merge(VmId, P3, Double::sum);
                        }
                    }
                }
            }

            //接下来是待迁移Vm按照概率进行放置，当然只有该HostId维上有待迁移Vm才能放置，没待迁移Vm的话直接跳过
            if (VmList.size() > 0) {
                    /*//Debug
                    System.out.println("VmProbability.size()=" + VmProbability.size());
                    System.out.println("VmList.size()=" + VmList.size());*/
                //每个Vm利用轮盘赌决定Vm是否放置到服务器中，贪心放置直到无法继续放置为止
                //构造Vm概率轮盘
                double probSum = 0;
                for (DDPVm vm : VmList) {
                    probSum += VmProbability.get(vm.VmId);
                    Roulette.add(probSum);//Vm概率轮盘的下标顺序对应VmList中vm的下标顺序
                }
                int cnt = 0;//判断tmpVm的尝试放置有没有成功：如果放置失败，则需要计数，如果放置成功，则计数清零

                //（连续三次轮盘赌都无法放置任何的Vm到Host上就结束该维Host上的Vm放置，
                // 这样有可能导致有部分Vm没得到放置，后续要进行处理）
                while (cnt < 3) {
                    double prob = VmAllocationPolicyForDifferentAlgorithm.rand.nextDouble() * probSum;
                    //获取相应概率在Roulette的下标，就相当于获取到VmList相应Vm的下标
                    int pos = DiscreteDEPSO.rouletteBinSearch(Roulette, 0, Roulette.size(), prob);
                        /*//Debug：打印一下probSum，prob，pos
                        System.out.println("probSum=" + probSum);
                        System.out.println("prob=" + prob);
                        System.out.println("pos=" + pos);*/

                    DDPVm vm = new DDPVm(VmList.get(pos));//创建新Vm，避免影响到Velocity、localBest、globalBest的Vm
                    if (VmIsAllocated.get(vm.VmId) == null) {//只对未放置过的待迁移Vm进行放置尝试
                        HashMap<String, Object> res = CheckVmPlacement.isSuitableForVm(HostAfterVelocityUpdate, vm);
                        if (res != null) {
                            //更新VmIsAllocated（避免Vm在另一台Host上重复放置）
                            VmIsAllocated.put(vm.VmId, true);
                            //更新Vm的动态数据
                            //Vm的Host_Belongs不用更新，但是PeMipsMap还是要更新一下
                            // 因为你无法保证三个Sv的Vm混合后，Vm申请到的Pe还是相同的
                            vm.placementUpdate(HostAfterVelocityUpdate.HostId,
                                    (ArrayList<DDPPeMipsMapPair>) res.get("PeMipsMap"),
                                    (double) res.get("total_cpu"), (double) res.get("ram"), (double) res.get("bw"));
                            //更新Host的动态数据
                            HostAfterVelocityUpdate.placementUpdate(null, vm);//PeList无需再更新
                            //确定要放置的Vm后需要同步更新SvAfterVelocityUpdate的VmList
                            SvAfterVelocityUpdate.VmList.put(vm.VmId, vm);
                            //Vm放置成功，cnt清零
                            cnt = 0;
                        } else {//Vm放置失败，cnt计数
                            cnt++;
                        }
                    } else {//如果VmIsAllocated不为null，说明该Vm之前已经放置过了，不需要再放置，cnt计数
                        cnt++;
                    }
                }
            }
            //此时SvAfterVelocityUpdate的一维基本构造完成（还剩下一些Vm没有放置，后续用启发式算法放置）
            SvAfterVelocityUpdate.HostList.put(HostAfterVelocityUpdate.HostId, HostAfterVelocityUpdate);
        }
        //step3:对该SV没有得到放置的Vm进行放置（利用X_placement）
        //先统计未得到放置的待迁移Vm，并加入到PeapSV里面
        for (Integer VmId : OriginSV.VmList.keySet()) {
            if (VmIsAllocated.get(VmId) == null) {
                DDPVm vm = new DDPVm(OriginSV.VmList.get(VmId));
                Conflict_SV.VmList.put(VmId, vm);
            }
        }
        //Conflict_SV的VmList可能为空（在前面的步骤中所有待迁移Vm都得到了妥善的放置，又或者该HostId维上没有待迁移VM）
        if (Conflict_SV.VmList.size() > 0) {
            int failFlag = 0;
            if (AlgorType == 6) {
                    /*RANDOM_Placement rnd = new RANDOM_Placement(Conflict_SV);
                    if (!rnd.placement(null)) {
                        //System.out.println("DiscreteDEPSO<VelocityUpdate()>：变异过程中出现Host资源不足的情况，" +
                        //        "群智能算法结束，立即返回全局最优解");
                        //return false;
                        continue;//如果出现服务器资源不足，那说明这次速度更新失败了，那就不更新这一维的速度咯

                    }*/
                MBFD_Placement mbfd = new MBFD_Placement(Conflict_SV);
                if (!mbfd.placement()) {
                    //System.out.println("DiscreteDEPSO<VelocityUpdate()>：变异过程中出现Host资源不足的情况，" +
                    //        "群智能算法结束，立即返回全局最优解");
                    //return false;
                    //如果出现服务器资源不足，那说明这次速度更新失败了，那就不更新这一维的速度咯
                    failFlag = 1;
                }
            } else {
                MBFD_Placement mbfd = new MBFD_Placement(Conflict_SV);
                if (!mbfd.placement()) {
                    //System.out.println("DiscreteDEPSO<VelocityUpdate()>：变异过程中出现Host资源不足的情况，" +
                    //        "群智能算法结束，立即返回全局最优解");
                    //return false;
                    //如果出现服务器资源不足，那说明这次速度更新失败了，那就不更新这一维的速度咯
                    failFlag = 1;
                }
                    /*PEAP_Placement peap = new PEAP_Placement(Conflict_SV);
                    if (!peap.placement(null)) {
                        //System.out.println("DiscreteDEPSO<PositionUpdate()>：“位置更新”过程中出现Host资源不足的情况，" +
                        //        "群智能算法结束，立即返回全局最优解");
                        //return false;
                        continue;//如果出现服务器资源不足，那说明这次位置更新失败了，那就不更新这一维的位置咯
                    }*/
            }
            if (failFlag == 0) {
                //添加Conflict_SV的VmList到SvAfterVelocityUpdate里面的Vm列表中（HostList的话已经在Conflict_SV中更新了）
                for (Integer VmId : Conflict_SV.VmList.keySet()) {
                    SvAfterVelocityUpdate.VmList.put(VmId, Conflict_SV.VmList.get(VmId));
                }
            } else {//放置失败就不更新该SV的速度咯
                SvAfterVelocityUpdate = Velocity.get(SwarmIndex.get(SvNo));
            }
        }
        SvAfterVelocityUpdate.Fitness = DiscreteDEPSO.getFitness(SvAfterVelocityUpdate);
            /*//Debug：检查VmList是否有遗漏Vm
            for (Integer VmId : OriginSV.VmList.keySet()) {
                if (SvAfterVelocityUpdate.VmList.get(VmId) == null) {
                    System.out.println("VelocityUpdate更新过程中有Vm没有得到放置，算法有误！");
                    System.exit(1);
                }
            }*/
        //step4:用构造好的SvAfterVelocityUpdate放到HashMap里面，然后外部会对Velocity作出相应更新
        synchronized (this) {
            VelocityUpdateMap.put(SwarmIndex.get(SvNo), SvAfterVelocityUpdate);
        }
        latch.countDown();
    }
}

class PositionUpdateThread implements Runnable {
    private ArrayList<DDPSv> Swarm;
    private DDPSv OriginSV;
    private int AlgorType;
    private double Y;
    private ArrayList<Integer> SwarmIndex;
    private ArrayList<DDPSv> Velocity;
    private ConcurrentHashMap<Integer, DDPSv> PositionUpdateMap;
    private int[] isSvSelected;
    private CountDownLatch latch;

    public PositionUpdateThread(ArrayList<DDPSv> Swarm, DDPSv OriginSV, int AlgorType, double Y,
                                ArrayList<Integer> SwarmIndex, ArrayList<DDPSv> Velocity,
                                ConcurrentHashMap<Integer, DDPSv> PositionUpdateMap,
                                int[] isSvSelected, CountDownLatch latch) {
        this.Swarm = Swarm;
        this.OriginSV = OriginSV;
        this.AlgorType = AlgorType;
        this.Y = Y;
        this.SwarmIndex = SwarmIndex;
        this.Velocity = Velocity;
        this.PositionUpdateMap = PositionUpdateMap;
        this.isSvSelected = isSvSelected;
        this.latch = latch;
    }

    public void run() {
        //通过isSvSelected先选出Swarm中排名第SvNo的SV，表示该线程要更新该SV的Velocity
        int SvNo = -1;
        synchronized (this) {
            for (int i = 0; i < isSvSelected.length; i++) {
                if (isSvSelected[i] == 0) {
                    isSvSelected[i] = 1;
                    SvNo = i;
                    //System.out.println(Thread.currentThread().getName()+"："+"我选了 "+SvNo);
                    break;
                }
            }
            if (SvNo == -1) {
                System.out.println("要求的速度更新完毕，告辞~！");//不应该出现这句话
                return;//没选上的话说明已经结束了（保险起见还是加上这一句）
            }
        }

        //用于记录更新好的解向量
        DDPSv SvAfterPositionUpdate = new DDPSv();
        //用于X_placement，直接对HostList进行浅拷贝，VmList初始化为空（VmList用于存放需要再分配的Vm）
        DDPSv Conflict_SV = new DDPSv();
        Conflict_SV.HostList = SvAfterPositionUpdate.HostList;
        //用于检查Vm是否经过放置
        HashMap<Integer, Boolean> VmIsAllocate = new HashMap<>();

        //step1:通过轮盘赌选取解向量中的P*Swarm.get(SwarmIndex.get(i)).HostList.size()维，更新为V(t,G)
        ArrayList<DDPHost> RouletteHostList = new ArrayList<>();//存放着X(t,G-1)各维
        //每一维被选中的概率都相等，各占1，所以不需要轮盘了，直接下标随机生成，然后访问即可
        HashMap<Integer, Boolean> HostIsUpdate = new HashMap<>();//用于判断Host是否已经更新了
        int UpdateSize = (int) (Y * Swarm.get(SwarmIndex.get(SvNo)).HostList.size());//只扰动每个位置向量SV中P%的Host
        if (UpdateSize == 0) UpdateSize = 1;//至少更新一维的Host
        //构造该SV的Host轮盘
        for (Integer HostId : Swarm.get(SwarmIndex.get(SvNo)).HostList.keySet()) {
            RouletteHostList.add(Swarm.get(SwarmIndex.get(SvNo)).HostList.get(HostId));
        }
        //这里需要注意，更新过的维数不再进行更新
        for (int j = 0; j < UpdateSize; j++) {
            int pos = VmAllocationPolicyForDifferentAlgorithm.rand.nextInt(RouletteHostList.size());
            //如果选出的pos对应的Host已经更新过了，则重新选择pos，否则进行更新，并将pos对应Host的HostId加入到HostIsUpdate中
            if (HostIsUpdate.get(RouletteHostList.get(pos).HostId) != null) {
                j--;
                continue;
            }
            HostIsUpdate.put(RouletteHostList.get(pos).HostId, true);

            //用于记录一维（一个目标服务器）交叉后的结果
            //直接复制V(t,G)中 HostId维的host（包括里面的Vm和Pe都是深拷贝的）<不用一个个VM重新放置>
            DDPHost hostAfterPositionUpdate = new DDPHost
                    (Velocity.get(SwarmIndex.get(SvNo)).HostList.get(RouletteHostList.get(pos).HostId),
                            true);
            //被 V(t,G)中 HostId维的host（VHost）所包含的待迁移Vm 影响的变量要更新一下（VmIsAllocate和SV的VmList）
            for (Integer VmId : hostAfterPositionUpdate.VmList.keySet()) {
                if (OriginSV.VmList.get(VmId) != null) {//只处理待迁移Vm，其它的VM不动
                    //这里无需检查Vm是否重复，因为取的Host都是单纯V(t,G)的，不可能重复，而且Vm肯定放得下
                    VmIsAllocate.put(VmId, true);
                    SvAfterPositionUpdate.VmList.put(VmId, hostAfterPositionUpdate.VmList.get(VmId));
                }
            }
            //将hostAfterPositionUpdate加入到SvAfterPositionUpdate的HostList中
            // （后面要根据这个HostList的host状态，对剩余的VM进行放置）
            SvAfterPositionUpdate.HostList.put(hostAfterPositionUpdate.HostId, hostAfterPositionUpdate);
        }

        //step2:将X(t,G)剩余的HostId维数保持为X(t,G-1)，注意检查Vm是否已经放置了
        //（这里之所以V(t,G)和X(t,G-1)的放置要先后分开写，是为了X(t,G)让Vm更新为V(t,G)时不会出现Vm冲突的情况
        //  完整地保留了V(t,G)相应维数的分配情况）
        for (Integer HostId : Swarm.get(SwarmIndex.get(SvNo)).HostList.keySet()) {
            if (HostIsUpdate.get(HostId) == null) {
                //用于记录一维（一个目标服务器）交叉后的结果
                //创建Host，且使Host处于默认状态
                DDPHost hostAfterPositionUpdate = new DDPHost(OriginSV.HostList.get(HostId),
                        true);

                DDPHost XHost = Swarm.get(SwarmIndex.get(SvNo)).HostList.get(HostId);//X(t,G-1) HostId维的host
                //将XHost中的Vm逐个放置到X(t,G)中
                for (Integer VmId : XHost.VmList.keySet()) {
                    if (OriginSV.VmList.get(VmId) != null) {//只处理待迁移VM，其他VM不动
                        //检查Vm是否重复，若没有重复就进行放置（肯定能放得下，因为VM只会由于重复而减少，不会增多）
                        if (VmIsAllocate.get(VmId) == null) {
                            DDPVm vm = new DDPVm(XHost.VmList.get(VmId));
                            VmIsAllocate.put(vm.VmId, true);
                            //更新Host的动态数据（Vm的动态数据不用更新，因为Vm就没有移动过）
                            hostAfterPositionUpdate.placementUpdate(vm.PeMipsMap, vm);//Vm重新放置
                            SvAfterPositionUpdate.VmList.put(vm.VmId, vm);
                        }
                    }
                }
                //将构造好的hostAfterPositionUpdate加入到SvAfterPositionUpdate的HostList中
                SvAfterPositionUpdate.HostList.put(hostAfterPositionUpdate.HostId, hostAfterPositionUpdate);
            }
        }
        //step3:现在利用Conflict_SV，通过X_placement将剩余的Vm也放置到里面
        //先统计出剩余未得到放置的待迁移Vm并加入到Conflict_SV的待放置Vm列表里面
        for (Integer VmId : OriginSV.VmList.keySet()) {
            if (VmIsAllocate.get(VmId) == null) {
                DDPVm vm = new DDPVm(OriginSV.VmList.get(VmId));//深拷贝
                Conflict_SV.VmList.put(vm.VmId, vm);
            }
        }
        //若确实有没有得到放置的VM，就用MBFD算法进行放置
        if (Conflict_SV.VmList.size() > 0) {
            //调用X_placement将剩余Vm放置到上面
            int failFlag = 0;
            if (AlgorType == 6) {//pso
                    /*RANDOM_Placement rnd = new RANDOM_Placement(Conflict_SV);
                    if (!rnd.placement(null)) {
                        //System.out.println("DiscreteDEPSO<PositionUpdate()>：“位置更新”过程中出现Host资源不足的情况，" +
                        //        "群智能算法结束，立即返回全局最优解");
                        //return false;
                        continue;//如果出现服务器资源不足，那说明这次位置更新失败了，那就不更新这一维的位置咯
                    }*/
                MBFD_Placement mbfd = new MBFD_Placement(Conflict_SV);
                if (!mbfd.placement()) {
                    //System.out.println("DiscreteDEPSO<PositionUpdate()>：“位置更新”过程中出现Host资源不足的情况，" +
                    //        "群智能算法结束，立即返回全局最优解");
                    //return false;
                    failFlag = 1;//如果出现服务器资源不足，那说明这次位置更新失败了，那就不更新这一维的位置
                }
            } else {//depso
                MBFD_Placement mbfd = new MBFD_Placement(Conflict_SV);
                if (!mbfd.placement()) {
                    //System.out.println("DiscreteDEPSO<PositionUpdate()>：“位置更新”过程中出现Host资源不足的情况，" +
                    //        "群智能算法结束，立即返回全局最优解");
                    //return false;
                    failFlag = 1;//如果出现服务器资源不足，那说明这次位置更新失败了，那就不更新这一维的位置咯
                }
                    /*PEAP_Placement peap = new PEAP_Placement(Conflict_SV);
                    if (!peap.placement(null)) {
                        //System.out.println("DiscreteDEPSO<PositionUpdate()>：“位置更新”过程中出现Host资源不足的情况，" +
                        //        "群智能算法结束，立即返回全局最优解");
                        //return false;
                        continue;//如果出现服务器资源不足，那说明这次位置更新失败了，那就不更新这一维的位置咯
                    }*/
            }
            if (failFlag == 0) {
                //添加Conflict_SV的VmList到MutationSV里面的Vm列表里面（HostList的话已经在Conflict_SV中更新了）
                for (Integer VmId : Conflict_SV.VmList.keySet()) {
                    SvAfterPositionUpdate.VmList.put(VmId, Conflict_SV.VmList.get(VmId));
                }
            } else {
                SvAfterPositionUpdate = Swarm.get(SwarmIndex.get(SvNo));
            }
        }
        //step4：将“交叉向量”加入到CrossSwarm
        SvAfterPositionUpdate.Fitness = DiscreteDEPSO.getFitness(SvAfterPositionUpdate);
        synchronized (this){
            PositionUpdateMap.put(SwarmIndex.get(SvNo), SvAfterPositionUpdate);
        }
        latch.countDown();
    }
}