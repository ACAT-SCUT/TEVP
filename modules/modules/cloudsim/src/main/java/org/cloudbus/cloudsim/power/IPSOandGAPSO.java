package org.cloudbus.cloudsim.power;
import java.util.*;

/**
 * 该类包含两种算法，一种是改进的IPSO，一种是在IPSO基础上增加GA算法的交叉和变异操作。
 */
public class IPSOandGAPSO {
    //数据中心的服务器列表
    private ArrayList<DDPHost> HostList;
    //待分配VM列表，只有在最终映射关系建立完成后才更新Vm
    public ArrayList<DDPVm> VmList;

    /**
     * ==================================================群智能数据==================================================
     */

    //算法迭代次数
    private int G;
    private ArrayList<IPSv> Swarm = new ArrayList<>();//作为群体（解向量的集合），同时也是PSO中的位置向量的集合

    /**
     * IPSO用到的数据
     * */
    //群体最优解
    public IPSv globalBest;
    //个体最优解
    private ArrayList<IPSv> localBest = new ArrayList<>();
    //PSO算法各解向量的“速度”（与Swarm的位置一一对应）
    private ArrayList<HashMap<Integer, Integer>> Velocity = new ArrayList<>();

    /**
     * GAPSO用到的数据
     * */
    //服务器集群中最大的峰值能耗
    private double peak_power_max;
    //该集合用于记录每次迭代中GAPSO选出的一半群体在Swarm中的下标位置
    private HashSet<Integer> SelectedSwarmPos = new HashSet<>();

    /**
     * ==================================================算法函数==================================================
     */
    //构造函数：传进来的必须是一个Vm都没有进行放置的HostList，数据中心中所有Vm都要进行重新放置
    public IPSOandGAPSO(ArrayList<DDPHost> HostList, ArrayList<DDPVm> VmList, int G, int SwarmSize) {
        this.HostList = HostList;
        this.VmList = VmList;
        this.G = G;
        //初始化“群体中的解向量<个体>”（最初状态下Host的状态是一样的）
        Swarm = new ArrayList<>();
        for (int i = 0; i < SwarmSize; i++) {
            Swarm.add(new IPSv(new DDPSv(HostList, VmList, true)));//深拷贝
        }
        //GA-PSO算法需要用到服务区集群中最大的峰值能耗
        if (VmAllocationPolicyForDifferentAlgorithm.PlacementAlgorType == 8) {
            peak_power_max = HostList.get(0).peak_power;
            for (int i = 1; i < HostList.size(); i++) {
                if (HostList.get(i).peak_power > peak_power_max) peak_power_max = HostList.get(i).peak_power;
            }
        }
    }

    //执行IPSO或GAPSO算法
    public void runAlgorithm() {
        //初始化群体（群体本身就是位置向量，速度向量，localBest，globalBest也在该函数中更新）
        Swarm_Initialize();
        //Debug：检查Vm资源是否正确
        SwarmReasonableCheck();
        //Debug
        System.out.println("正式开始进入IPSO或GAPSO迭代流程：");
        /**
         * IPSO，PlacementAlgorType == 7
         * */
        if (VmAllocationPolicyForDifferentAlgorithm.PlacementAlgorType == 7) {
            //算法总共需要经过G次迭代
            for (int i = 0; i < G; i++) {
                VelocityUpdate();//速度更新
                PositionUpdate();//位置更新
                GlobalBestAndLocalBestUpdate();//群体最优解和个体最优解更新
            }
            finalUpdate();//将最优解的VM分配状况更新到HostList和AllocationVmList中
        } else {
            /**
             * GAPSO，PlacementAlgorType == 8
             * */
            for (int i = 0; i < G; i++) {
                SelectedSwarmPos.clear();//每次迭代前都清空
                /**Step1：以“fitness的倒数”为概率，用轮盘赌选出较优的一半Swarm（没选上的个体就舍弃掉）*/
                ArrayList<Double> Roulette = new ArrayList<>();//Roulette.size()等于Swarm.size()
                double probSum = 0;
                for (IPSv sv : Swarm) {
                    probSum += 1 / sv.SecondDimension.Fitness;
                    Roulette.add(probSum);
                }
                HashMap<Integer, Boolean> previousPos = new HashMap<>();//记录已经选出的pos
                for (int j = 0; j < Swarm.size() / 2; j++) {
                    double prob = VmAllocationPolicyForDifferentAlgorithm.rand.nextDouble() * probSum;
                    int pos = rouletteBinSearch(Roulette, 0, Roulette.size(), prob);//找到刚好比prob大的位置
                    if (previousPos.get(pos) != null) {
                        j--;
                        continue;
                    }
                    previousPos.put(pos, true);
                    SelectedSwarmPos.add(pos);
                }

                /**Step2：针对SelectedSwarmPos的个体，调用IPSO*/
                VelocityUpdate();       //速度更新
                SwarmReasonableCheck(); //Debug：检查Vm资源是否正确
                PositionUpdate();       //位置更新
                SwarmReasonableCheck(); //Debug：检查Vm资源是否正确
                FirstDimensionCheck();  //Debug：检查PositionUpdate之后，FirstDimension是否正确

                /**Step3：复制一份SelectedSwarmPos经过IPSO更新后的个体（复制到Swarm剩余的个体的位置）*/
                //另外再把复制的个体构造成一个ArrayList(ChromosomeArray)，方便GA算法的操作
                ArrayList<IPSv> ChromosomeArray = new ArrayList<>();
                //构建Map，便于更新Swarm的个体：<在ChromosomeArray中的下标位置,在Swarm中的下标位置>
                HashMap<Integer, Integer> ChromosomeMap = new HashMap<>();
                int j, k = 0;
                for (j = 0; j < Swarm.size(); j++) {
                    if (SelectedSwarmPos.contains(j)) {//先找到一个SelectedSwarmPos里面的个体，复制到没被选上的Swarm[k]中
                        for (; k < Swarm.size(); k++) {//复制过的Swarm的位置就不用重新去搜索了，所以k不用重置为0
                            if (!SelectedSwarmPos.contains(k)) {//找到没被选上的个体在Swarm中的位置
                                //复制个体给ChromosomeArray，之后所有对ChromosomeArray的操作都是Swarm的另外一半Sv进行操作
                                IPSv sv = new IPSv(Swarm.get(j), true);
                                Swarm.set(k, sv);//替换掉原来没被选上的个体
                                ChromosomeArray.add(sv);//记录到ArrayList中
                                ChromosomeMap.put(ChromosomeArray.size() - 1, k);//建立映射关系
                                k++;
                                break;
                            }
                        }
                    }
                }

                /**Step4：利用GA算法优化Chromosome*/
                Crossover(ChromosomeArray, ChromosomeMap);  //GAPSO算法的交叉操作
                SwarmReasonableCheck();                     //Debug：检查Vm资源是否正确
                Mutation(ChromosomeArray);                  //GAPSO算法的变异操作
                SwarmReasonableCheck();                     //Debug：检查Vm资源是否正确

                /**Step5：统一更新一下Swarm中个体的FirstDimension*/
                for (IPSv sv : Swarm) {
                    sv.FirstDimension.clear();
                    for (Integer HostId : sv.SecondDimension.HostList.keySet()) {
                        if (sv.SecondDimension.HostList.get(HostId).VmList.size() > 0) {
                            sv.FirstDimension.put(HostId, 1);
                        } else {
                            sv.FirstDimension.put(HostId, 0);
                        }
                    }
                }

                /**Step6：更新全局最优解和局部最优解*/
                GlobalBestAndLocalBestUpdate();//群体最优解和个体最优解更新
            }
            finalUpdate();
        }
    }

    //Debug专用：检查PositionUpdate之后，FirstDimension是否正确
    private int FirstDimensionCheck() {
        for (int i = 0; i < Swarm.size(); i++) {
            IPSv sv = Swarm.get(i);
            for (Integer HostId : sv.FirstDimension.keySet()) {
                if (sv.FirstDimension.get(HostId) == 1) {
                    if (sv.SecondDimension.HostList.get(HostId).VmList.size() == 0) {
                        System.out.println("IPSOandGAPSO<FirstDimensionCheck>：FirstDimension显示为1，而实际上Host上没有Vm");
                        System.exit(1);
                    }
                } else {
                    if (sv.SecondDimension.HostList.get(HostId).VmList.size() > 0) {
                        System.out.println("IPSOandGAPSO<FirstDimensionCheck>：FirstDimension显示为0，而实际上Host上有Vm在运行");
                        System.exit(1);
                    }
                }
            }
        }
        return 0;
    }

    //Debug专用：检查是否有Vm放置在了两台Host上或者有Vm没有得到放置
    //return：1、表示有Vm没有得到放置；2、表示有Vm放置在两台Host上；
    private int SwarmReasonableCheck() {
        for (int i = 0; i < Swarm.size(); i++) {
            HashMap<Integer, Boolean> IsVmAllocate = new HashMap<>();
            IPSv sv = Swarm.get(i);
            for (Integer HostId : sv.SecondDimension.HostList.keySet()) {
                DDPHost host = sv.SecondDimension.HostList.get(HostId);
                for (Integer VmId : host.VmList.keySet()) {
                    if (IsVmAllocate.get(VmId) == null) {
                        IsVmAllocate.put(VmId, true);
                    } else {
                        System.out.println("VmId重复放置了，算法有误");
                        System.exit(1);
                    }
                }
                if ((host.UP_THR - host.CPU_util) * host.CPU_Cap < -10) {//允许少量的舍入误差
                    System.out.println("出现Host超载的情况，算法有误");
                    System.out.println("MIPs_avail=" + ((host.UP_THR - host.CPU_util) * host.CPU_Cap));
                    System.exit(1);
                }
            }
            for (Integer VmId : sv.SecondDimension.VmList.keySet()) {
                DDPVm vm = sv.SecondDimension.VmList.get(VmId);
                if (sv.SecondDimension.HostList.get(vm.Host_Belongs).VmList.get(VmId) != vm) {
                    System.out.println("VmList中的vm不在自己的Host_Belongs上，算法有误");
                    System.exit(1);
                }
            }
            if (VmList.size() > IsVmAllocate.size()) {
                System.out.println("有Vm没有得到放置，算法有误");
                System.exit(1);
            }
        }
        return 0;//没检查出问题
    }

    //按globalBest的IPSv的情况更新HostList中的Host和VmList中的Vm
    private void finalUpdate() {
        for (DDPHost host : HostList) {
            host.CopyUpdate(globalBest.SecondDimension.HostList.get(host.HostId));
        }

        //更新所有待分配Vm的数据（CloudSim构造migrationMap要用到）
        for (DDPVm vm : VmList) {
            vm.CopyUpdate(globalBest.SecondDimension.VmList.get(vm.VmId));
        }
    }

    //每次迭代后更新PSO的个体最优解和群体最优解
    private void GlobalBestAndLocalBestUpdate() {
        for (int i = 0; i < Swarm.size(); i++) {
            if (Swarm.get(i).SecondDimension.Fitness < localBest.get(i).SecondDimension.Fitness) {
                IPSv tmpSv = new IPSv(Swarm.get(i), true);//深拷贝
                localBest.set(i, tmpSv);
                if (Swarm.get(i).SecondDimension.Fitness < globalBest.SecondDimension.Fitness) {
                    globalBest = tmpSv;
                }
            }
        }
    }

    /**
     * GAPSO算法的变异操作
     * 随机选择两台活跃的Host，然后从两台Host中各取一台Vm进行互换
     * */
    private void Mutation(ArrayList<IPSv> ChromosomeArray) {
        //对每个个体进行变异操作
        Random rand=new Random(System.currentTimeMillis());
        for (IPSv sv : ChromosomeArray) {
            //Step1：构造一个活跃服务器列表
            ArrayList<DDPHost> ActiveHostList = new ArrayList<>();//浅拷贝
            for (Integer HostId : sv.SecondDimension.HostList.keySet()) {
                if (sv.SecondDimension.HostList.get(HostId).VmList.size() > 0) {
                    ActiveHostList.add(sv.SecondDimension.HostList.get(HostId));
                }
            }
            if(ActiveHostList.size()==1) return;//只有一台Host的时候就没什么可以交换的了
            //Step2：随机选择两台Host和上面的一台Vm
            DDPHost MutaHost1 = ActiveHostList.get(
                    rand.nextInt(ActiveHostList.size()));
            DDPHost MutaHost2 = ActiveHostList.get(
                    rand.nextInt(ActiveHostList.size()));
            while (MutaHost1.HostId == MutaHost2.HostId) {//要选出不同于MutaHost1的Host
                MutaHost2 = ActiveHostList.get(
                        rand.nextInt(ActiveHostList.size()));
            }
            int VmNo1 = rand.nextInt(MutaHost1.VmList.size());
            int VmNo2 = rand.nextInt(MutaHost2.VmList.size());
            int cnt = 0;
            DDPVm randVm1 = null, randVm2 = null;
            for (Integer VmId : MutaHost1.VmList.keySet()) {
                if (cnt == VmNo1) {
                    randVm1 = MutaHost1.VmList.get(VmId);
                    break;
                }
                cnt++;
            }
            cnt = 0;
            for (Integer VmId : MutaHost2.VmList.keySet()) {
                if (cnt == VmNo2) {
                    randVm2 = MutaHost2.VmList.get(VmId);
                    break;
                }
                cnt++;
            }

            //Step3：复制两台Host，得到tmpHost,然后
            //在复制的Host中移除被选中的randVm1和randVm2
            //只有在复制的Host上Swap成功了，才在真实的Host上进行迁移
            DDPHost tmpMutaHost1 = new DDPHost(MutaHost1, true);
            DDPHost tmpMutaHost2 = new DDPHost(MutaHost2, true);
            if (randVm1 == null || randVm2 == null) {
                System.out.println("IPSOandGAPSO<Mutation>：randVm为null，算法有问题，程序退出");
                System.exit(1);
            }
            tmpMutaHost1.removeUpdate(randVm1);
            /*//Debug
            System.out.println("MutaHost2 remove前的状态：");
            System.out.println("Host的数据：MIPs_avail=" + ((MutaHost2.UP_THR - MutaHost2.CPU_util) * MutaHost2.CPU_Cap)
                    + "，RAM_avail=" + MutaHost2.RAM_avail
                    + "，Bw_avail=" + MutaHost2.BW_avail);
            System.out.println("Vm的数据：MIPS_Request=" + randVm2.Total_CPU_Request + "，RAM_Request="
                    + randVm2.RAM_Request + "，Bw_Request=" + randVm2.BW_Request + "，Total_CPU_Allocate="
                    + randVm2.Total_CPU_Allocate + "，RAM_Allocate=" + randVm2.RAM_Allocate + "，BW_Allocate="
                    + randVm2.BW_Allocate);*/
            tmpMutaHost2.removeUpdate(randVm2);

            HashMap<String, Object> res1 = CheckVmPlacement.isSuitableForVm(tmpMutaHost2, randVm1);
            HashMap<String, Object> res2 = CheckVmPlacement.isSuitableForVm(tmpMutaHost1, randVm2);
            if (res1 != null && res2 != null) {
                MutaHost1.removeUpdate(randVm1);//移除Vm
                MutaHost2.removeUpdate(randVm2);//移除Vm
                res1 = CheckVmPlacement.isSuitableForVmTest(MutaHost2, randVm1);//更新PeMipsMap
                res2 = CheckVmPlacement.isSuitableForVmTest(MutaHost1, randVm2);//更新PeMipsMap
                //更新Vm的动态数据
                randVm1.placementUpdate(MutaHost2.HostId, (ArrayList<DDPPeMipsMapPair>) res1.get("PeMipsMap"),
                        (double) res1.get("total_cpu"), (double) res1.get("ram"), (double) res1.get("bw"));
                randVm2.placementUpdate(MutaHost1.HostId, (ArrayList<DDPPeMipsMapPair>) res2.get("PeMipsMap"),
                        (double) res2.get("total_cpu"), (double) res2.get("ram"), (double) res2.get("bw"));
                //更新Host的动态数据
                MutaHost2.placementUpdate(null, randVm1);
                MutaHost1.placementUpdate(null, randVm2);
            }
        }
    }


    /**
     * GAPSO算法的交叉操作
     * 从ChromosomeArray中选出两个个体进行单点交叉操作,然后采用贪心选择策略，从4条染色体中选出子代
     */
    private void Crossover(ArrayList<IPSv> ChromosomeArray, HashMap<Integer, Integer> ChromosomeMap) {
        //Step1：选出两个个体（轮盘赌）
        ArrayList<Double> Roulette = new ArrayList<>();
        double probSum = 0;
        for (IPSv sv : ChromosomeArray) {
            probSum += 1 / sv.SecondDimension.Fitness;
            Roulette.add(probSum);
        }
        double randProb = VmAllocationPolicyForDifferentAlgorithm.rand.nextDouble() * probSum;
        int pos1 = rouletteBinSearch(Roulette, 0, Roulette.size(), randProb);//用于记录，在ChromosomeArray中的下标位置
        //Debug：检查pos
        //System.out.println("Roulette.size()=="+Roulette.size()+"，pos=="+pos);
        IPSv parent1 = ChromosomeArray.get(pos1);//父代个体1
        IPSv offSpring1 = new IPSv(parent1, true);//深拷贝，子代个体1
        int pos2 = pos1;//用于记录，在ChromosomeArray中的下标位置
        while (pos2 == pos1) {//必须取两个不同的Sv
            randProb = VmAllocationPolicyForDifferentAlgorithm.rand.nextDouble() * probSum;
            pos2 = rouletteBinSearch(Roulette, 0, Roulette.size(), randProb);
        }
        //Debug：检查pos
        //System.out.println("Roulette.size()=="+Roulette.size()+"，pos=="+pos);
        IPSv parent2 = ChromosomeArray.get(pos2);//父代个体2
        IPSv offSpring2 = new IPSv(parent2, true);//深拷贝，子代个体2

        //Step2：构造Vm列表（按照Host的顺序构造，详见论文Fig.6）
        //交叉点直接从VmList1和VmList2的下标中取，VmPosInVmList2则是
        // 为了记录sv2中各Vm在VmList2中的下标位置，方便后续检查Vm是否在交叉点之后
        ArrayList<DDPVm> VmList1 = new ArrayList<>();
        ArrayList<DDPVm> VmList2 = new ArrayList<>();
        HashMap<Integer, Integer> VmPosInVmList2 = new HashMap<>();//<VmId,在VmList2中的下标>
        int VmPos = 0;
        //由于不同Sv的Host列表是相同的，所以可以同时遍历
        for (Integer HostId : offSpring1.SecondDimension.HostList.keySet()) {
            DDPHost hostInSv1 = offSpring1.SecondDimension.HostList.get(HostId);
            DDPHost hostInSv2 = offSpring2.SecondDimension.HostList.get(HostId);
            if (hostInSv1.VmList.size() > 0) {
                for (Integer VmId : hostInSv1.VmList.keySet()) {
                    VmList1.add(hostInSv1.VmList.get(VmId));
                }
            }
            if (hostInSv2.VmList.size() > 0) {
                for (Integer VmId : hostInSv2.VmList.keySet()) {
                    VmList2.add(hostInSv2.VmList.get(VmId));
                    VmPosInVmList2.put(VmId, VmPos);
                    VmPos++;
                }
            }
        }

        //Step3：开始交叉，先选出交叉点，然后开始交叉组合
        int CrossPointInVmList = VmAllocationPolicyForDifferentAlgorithm.rand.nextInt(VmList1.size());
        //先同时检查后半段，找出共同的Vm并互换，并同步对两个个体公共Vm相应的Host进行维护
        ArrayList<Integer> publicVmList = new ArrayList<>();//记录公共Vm的位置<VmId>，之后通过遍历该列表来进行Vm放置
        //之所以要全部互换完再放置，是因为全部公共Vm互换以后Host空出来的资源会更多
        for (int i = CrossPointInVmList; i < VmList1.size(); i++) {
            DDPVm vmInList1 = VmList1.get(i);
            if (VmPosInVmList2.get(vmInList1.VmId) >= CrossPointInVmList) {
                DDPVm vmInList2 = VmList2.get(VmPosInVmList2.get(vmInList1.VmId));
                publicVmList.add(vmInList1.VmId);
                //维护公共Vm相应的Host信息（移除公共Vm）
                offSpring1.SecondDimension.HostList.get(vmInList1.Host_Belongs).removeUpdate(vmInList1);
                offSpring2.SecondDimension.HostList.get(vmInList2.Host_Belongs).removeUpdate(vmInList2);
                //互换公共Vm记录的HostBelongs（PeMipsMap和其他动态数据不用管，因为Vm互换Host以后进行放置时会对动态数据进行更新）
                int tmpHostBelongs = vmInList1.Host_Belongs;
                vmInList1.Host_Belongs = vmInList2.Host_Belongs;
                vmInList2.Host_Belongs = tmpHostBelongs;
            }
        }

        //Step4：根据交换后的Vm-Host映射关系对公共Vm进行放置（遇到放不下的Vm就记录下来）
        DDPSv reallocateFailVmInSv1 = new DDPSv();
        reallocateFailVmInSv1.HostList = offSpring1.SecondDimension.HostList;//这样，冲突Vm放置后无须再更新HostList
        DDPSv reallocateFailVmInSv2 = new DDPSv();
        reallocateFailVmInSv2.HostList = offSpring2.SecondDimension.HostList;//这样，冲突Vm放置后无须再更新HostList
        for (Integer VmId : publicVmList) {
            //尝试在交换HostBelongs后分别对两个个体的Host放置Vm
            DDPVm vmInSv1 = offSpring1.SecondDimension.VmList.get(VmId);
            DDPHost hostInSv1 = offSpring1.SecondDimension.HostList.get(vmInSv1.Host_Belongs);
            DDPVm vmInSv2 = offSpring2.SecondDimension.VmList.get(VmId);
            DDPHost hostInSv2 = offSpring2.SecondDimension.HostList.get(vmInSv2.Host_Belongs);

            HashMap<String, Object> res1 = CheckVmPlacement.isSuitableForVm(hostInSv1, vmInSv1);
            if (res1 != null) {//能够成功放置，则更新相关的数据
                //更新Vm的动态数据
                vmInSv1.placementUpdate(hostInSv1.HostId, (ArrayList<DDPPeMipsMapPair>) res1.get("PeMipsMap"),
                        (double) res1.get("total_cpu"), (double) res1.get("ram"), (double) res1.get("bw"));
                //然后更新Host的动态数据（必须先更新vm的数据，因为host的更新是根据vm的动态数据进行更新的）
                hostInSv1.placementUpdate(null, vmInSv1);
            } else {
                reallocateFailVmInSv1.VmList.put(VmId, vmInSv1);
            }

            HashMap<String, Object> res2 = CheckVmPlacement.isSuitableForVm(hostInSv2, vmInSv2);
            if (res2 != null) {//能够成功放置，则更新相关的数据
                //更新Vm的动态数据
                vmInSv2.placementUpdate(hostInSv2.HostId, (ArrayList<DDPPeMipsMapPair>) res2.get("PeMipsMap"),
                        (double) res2.get("total_cpu"), (double) res2.get("ram"), (double) res2.get("bw"));
                //然后更新Host的动态数据（必须先更新vm的数据，因为host的更新是根据vm的动态数据进行更新的）
                hostInSv2.placementUpdate(null, vmInSv2);
            } else {
                reallocateFailVmInSv2.VmList.put(VmId, vmInSv2);
            }
        }

        //Step5：将交叉后放置失败的Vm用FFD算法回填（现在ActiveHost中回填，没资源了再开启其他服务器）
        if (reallocateFailVmInSv1.VmList.size() > 0) {
            FFD_Placement ffd1 = new FFD_Placement(reallocateFailVmInSv1, 2);
            if (!ffd1.placement(null)) {
                System.out.println("IPSOandGAPSO<Crossover>：子代1冲突Vm回填失败，数据中心的服务器资源不足！");
                System.exit(1);
            }
        }
        if (reallocateFailVmInSv2.VmList.size() > 0) {
            FFD_Placement ffd2 = new FFD_Placement(reallocateFailVmInSv2, 2);
            if (!ffd2.placement(null)) {
                System.out.println("IPSOandGAPSO<Crossover>：子代2冲突Vm回填失败，数据中心的服务器资源不足！");
                System.exit(1);
            }
        }

        //Step6：计算offSpring的适应度值并根据适应度值选出最好的两个来替换掉Swarm中的个体
        offSpring1.SecondDimension.Fitness = getFitness(offSpring1);
        offSpring2.SecondDimension.Fitness = getFitness(offSpring2);
        IPSv BestChromosome, SecondBestChromosome;
        ArrayList<IPSv> tmpChromosome = new ArrayList<>();
        tmpChromosome.add(parent1);
        tmpChromosome.add(parent2);
        tmpChromosome.add(offSpring1);
        tmpChromosome.add(offSpring2);
        tmpChromosome.sort(new Comparator<IPSv>() {//升序排序
            @Override
            public int compare(IPSv o1, IPSv o2) {
                return Double.compare(o1.SecondDimension.Fitness, o2.SecondDimension.Fitness);
            }
        });
        BestChromosome = tmpChromosome.get(0);
        SecondBestChromosome = tmpChromosome.get(1);

        //将Swarm中对应的两个个体替换成选出的最优的两个个体，ChromosomeArray也要跟着更新
        ChromosomeArray.set(pos1, BestChromosome);
        ChromosomeArray.set(pos2, SecondBestChromosome);
        Swarm.set(ChromosomeMap.get(pos1), BestChromosome);
        Swarm.set(ChromosomeMap.get(pos2), SecondBestChromosome);
    }

    //位置向量更新函数
    //我对这个步骤的理解是：
    //（1）先根据速度向量确定位置向量（Swarm.get(i)）中需要更新的Host
    // 速度向量相应位的值为1，则位置向量FirstDimension相应位的值及其对应的Host状态不变（即开关状态和VM放置情况不变）
    // 速度向量相应位的值为0，则位置向量FirstDimension相应位的值及其对应的Host状态进行Update（之后会确定这些Host哪些开哪些关）
    //（2）整个位置向量的第一维更新完以后，UpdateHostList中CPU利用率较高的Host更有可能被选上，
    // CPU利用率为0的也有一定的几率被选上，不过几率更小（意思就是原来开着的服务器有更大的概率继续开着）；
    //（3）统计没有得到放置的Vm，组成VmReallocateList，并维护关闭的服务器（只要被选中要Update的Host，里面的Vm都要重新放置）；
    //（4）FFD回填VmReallocateList中所有的Vm到活跃的服务器上；
    private void PositionUpdate() {
        //对每个个体的Position（Swarm）进行更新
        Random rand=new Random(System.currentTimeMillis());
        for (int i = 0; i < Swarm.size(); i++) {
            //GAPSO中只更新一半的Swarm，如果i不在SelectedSwarmPos中，直接跳过个体Swarm[i]
            if (VmAllocationPolicyForDifferentAlgorithm.PlacementAlgorType == 8 && !SelectedSwarmPos.contains(i)) {
                continue;
            }

            //Step1：根据速度向量确定（Swarm.get(i)）中需要更新的Host（逐bit更新<即逐个Host更新>）
            //       并统计构造轮盘所需的相关信息

            //CPU利用率较高的Host保留的概率会大一些，因此我们设每一台原先有负载的Host的概率是
            //没有负载的Host的概率的2倍，然后有负载的Host集群多出来的这一倍概率(ExtraProb)单独取出来，
            // 再根据这部分Host各自的CPU利用率的程度叠加到这些Host上，CPU利用率越高的Host
            // 从这一倍概率中获得的Extra概率就越多
            IPSv sv = Swarm.get(i);
            ArrayList<Double> Roulette = new ArrayList<>();
            ArrayList<DDPHost> UpdateHostList = new ArrayList<>();
            int ActiveUpdateHostNum = 0;//统计活跃Host的数量
            double CpuUtilSum = 0;//后续构造轮盘要用到该值来确定ActiveUpdateHost各自的ExtraProb
            HashMap<Integer, DDPVm> VmReAllocateMap = new HashMap<>();//<VmId,DDPVm>
            ArrayList<DDPHost> ActiveHostList = new ArrayList<>();//用于记录位置更新之后仍然活跃的服务器
            ArrayList<DDPVm> VmReAllocateList = new ArrayList<>();//构造DDPSv要用到（将Map的内容转到List这里）

            for (Integer HostId : Velocity.get(i).keySet()) {
                if (Velocity.get(i).get(HostId) == 0) {//将需要更新的Host记录下来
                    DDPHost host = sv.SecondDimension.HostList.get(HostId);
                    UpdateHostList.add(host);
                    if (host.VmList.size() > 0) {//统计活跃的Host的数量和活跃Host集群的总CPU利用率及可能要进行Reallocate的Vm
                        ActiveUpdateHostNum++;
                        CpuUtilSum += host.CPU_util;
                        for (Integer VmId : host.VmList.keySet()) {
                            //先记录着，等一下如果Host Update以后还是开启状态，就从Map中remove Vm出来
                            VmReAllocateMap.put(VmId, host.VmList.get(VmId));
                        }
                        //FirstDimension相应位数先设置为0
                        sv.FirstDimension.put(HostId, 0);
                    }
                } else {//不需要更新的Host直接加入到ActiveHostList中
                    ActiveHostList.add(sv.SecondDimension.HostList.get(HostId));
                }
            }

            //统计构造轮盘所需的相关信息
            double AvgPro = 1 / (double) ActiveUpdateHostNum + (double) UpdateHostList.size();
            double ExtraProb = (double) ActiveUpdateHostNum * AvgPro;//多出来

            //Step2：构建轮盘
            double ProbSum = 0;//0~j的概率之和
            for (DDPHost host : UpdateHostList) {
                if (host.VmList.size() > 0) {
                    ProbSum += AvgPro + ExtraProb * (host.CPU_util / CpuUtilSum);
                } else {
                    ProbSum += AvgPro;
                }
                Roulette.add(ProbSum);
            }

            /*//Debug：检查Roulette
            System.out.println("ActiveUpdateHostNum="+ActiveUpdateHostNum);
            System.out.println("UpdateHostList.size()="+UpdateHostList.size());
            for (Double val : Roulette) {
                System.out.print(val + " ");
            }
            System.out.println();
            System.exit(1);*/

            //Step3：随机选定ActiveUpdateHostNum台服务器进行开启（原来已经开启的服务器则保留），
            // 并统计没有得到放置的Vm（维护VmReallocateList），同步维护关闭的Host的状态
            int maximumIterationTime = 10;//迭代10次还是没找到应该开启的Host，就直接跳出循环
            int failCounter = 0;
            for (int j = 0; j < ActiveUpdateHostNum; j++) {
                double prob = rand.nextDouble() * Roulette.get(Roulette.size() - 1);
                int pos = rouletteBinSearch(Roulette, 0, Roulette.size(), prob);//找到刚好大于prob的元素
                //System.out.println(pos);
                if (sv.FirstDimension.get(UpdateHostList.get(pos).HostId) != null) {
                    j--;
                    failCounter++;
                    //随机了10次都没找到未开启的Host，则直接跳出循环（后面大不了从HostList中新增其他的Host）
                    if (failCounter == maximumIterationTime) break;
                } else {
                    failCounter = 0;//寻找未开启的Host失败的记录次数归0
                    //若选出的Host是未开启的，则直接开启即可；
                    //若选出的Host原本就是开启的，则里面的Vm无需重新放置，从VmReallocateList中remove这部分Vm；
                    DDPHost StartUpHost = UpdateHostList.get(pos);//在本轮调度中要开启的Host
                    //更新sv.FirstDimension相应位数的值
                    sv.FirstDimension.put(StartUpHost.HostId, 1);
                    //将新开启的Host加入到ActiveHostList里面
                    ActiveHostList.add(StartUpHost);
                }
            }

            //维护相应的Host的数据（UpdateHostList中FirstDimension设置为0且原先是有Vm在里面的Host需要initial）
            for (DDPHost host : UpdateHostList) {
                host.initial();
            }

            //Step4：将没有得到放置的Vm，利用FF策略回填到ActiveHostList的服务器中
            for (Integer VmId : VmReAllocateMap.keySet()) {
                VmReAllocateList.add(VmReAllocateMap.get(VmId));
            }
            //构造ddpsv供FFD_Placement调用
            //ddpsv里面的Host和Vm就是Swarm.get(i)里面的Host和Vm对象（因为是浅拷贝），
            // 不过范围缩小了而已（只有“未得到放置的Vm”和“ActiveHost”）
            // 放置完成后不用另外更新HostList和VmList
            if (VmReAllocateList.size() > 0) {//极端的情况下，有可能出现没有Vm需要重新放置
                DDPSv ddpsv = new DDPSv(ActiveHostList, VmReAllocateList, false);
                FFD_Placement ffd = new FFD_Placement(ddpsv, 2);
                DDPHost tH = null;
                while (!ffd.placement(tH)) {
                    //新增Host到ddpsv的HostList中（ActiveHostList不用管）
                    for (Integer HostId : sv.SecondDimension.HostList.keySet()) {
                        if (sv.FirstDimension.get(HostId) == 0) {
                            sv.FirstDimension.put(HostId, 1);
                            DDPHost host = sv.SecondDimension.HostList.get(HostId);
                            ddpsv.HostList.put(HostId, host);
                            tH = host;
                            break;
                        }
                    }
                    //这里我们的资源是一定充足的，所以不存在说开启所有服务器仍然装不下Vm的情况
                }

                //Step5：更新Fitness值
                sv.SecondDimension.Fitness = getFitness(sv);
            }
            //统一更新一下FirstDimension，有多余的FirstDimension设置为1，但实际上没有Vm放置在上面的Host需要设置为0
            sv.FirstDimension.clear();
            for (Integer HostId : sv.SecondDimension.HostList.keySet()) {
                if (sv.SecondDimension.HostList.get(HostId).VmList.size() > 0) {
                    sv.FirstDimension.put(HostId, 1);
                } else {
                    sv.FirstDimension.put(HostId, 0);
                }
            }
        }
    }

    //速度向量更新函数
    private void VelocityUpdate() {
        Random rand=new Random(System.currentTimeMillis());
        //对每个个体的Velocity进行更新
        for (int i = 0; i < Swarm.size(); i++) {
            //GAPSO中只更新一半的Swarm，如果i不在SelectedSwarmPos中，直接跳过个体Swarm[i]
            if (VmAllocationPolicyForDifferentAlgorithm.PlacementAlgorType == 8 && !SelectedSwarmPos.contains(i)) {
                continue;
            }

            IPSv SwarmSv = Swarm.get(i);
            IPSv LocalBestSv = localBest.get(i);
            IPSv GlobalBestSv = globalBest;

            //Step1：构造出localBest_Velocity和globalBest_Velocity
            HashMap<Integer, Integer> LB_Velocity = new HashMap<>();
            HashMap<Integer, Integer> GB_Velocity = new HashMap<>();

            //逐个bit计算可得LB_Velocity和GB_Velocity
            for (Integer HostId : SwarmSv.FirstDimension.keySet()) {
                if (LocalBestSv.FirstDimension.get(HostId).equals(SwarmSv.FirstDimension.get(HostId))) {
                    LB_Velocity.put(HostId, 1);
                } else {
                    LB_Velocity.put(HostId, 0);
                }

                if (GlobalBestSv.FirstDimension.get(HostId).equals(SwarmSv.FirstDimension.get(HostId))) {
                    GB_Velocity.put(HostId, 1);
                } else {
                    GB_Velocity.put(HostId, 0);
                }
            }

            //Step2：计算出Swarm.get(i),localBest,globalBest的概率，并构造轮盘
            double[] Roulette = new double[3];
            double denominatorSum = 1 / SwarmSv.SecondDimension.Fitness
                    + 1 / LocalBestSv.SecondDimension.Fitness
                    + 1 / GlobalBestSv.SecondDimension.Fitness;
            Roulette[0] = (1 / SwarmSv.SecondDimension.Fitness) / denominatorSum;
            Roulette[1] = Roulette[0] + (1 / LocalBestSv.SecondDimension.Fitness) / denominatorSum;
            Roulette[2] = 1;

            //更新后的第i个个体的速度
            HashMap<Integer, Integer> velo = Velocity.get(i);
            //逐个bit更新，每个bit为0或1，轮盘赌按概率决定该位取哪个Velocity相应位的值即可
            for (Integer HostId : velo.keySet()) {
                double val = rand.nextDouble();//0~1之间的随机数
                if (val >= 0 && val <= Roulette[0]) continue;//不用更新，相应位沿用Vi(t)
                else if (val > Roulette[0] && val <= Roulette[1]) velo.put(HostId, LB_Velocity.get(HostId));
                else velo.put(HostId, GB_Velocity.get(HostId));
            }
        }
    }

    //群体初始化
    private void Swarm_Initialize() {
        double minFitnessVal = Double.MIN_VALUE;
        IPSv tmpBest = null;//暂时记录全局最优解，等全部解生成完毕以后再深拷贝到globalBest上
        //IPSO的初始群体使用FF构造而成的
        for (IPSv sv : Swarm) {
            FFD_Placement FFD = new FFD_Placement(sv.SecondDimension, 1);
            //由于IPSO的服务器范围是全局，因此无需新增服务器，放置失败了就说明数据中心的服务器资源不足
            DDPHost tH = null;
            if (!FFD.placement(tH)) {
                System.out.println("DiscreteDEPSO<Swarm_Initialize>：执行FFD算法失败，数据中心的服务器不足，无法开启新的服务器，程序退出");
                System.exit(1);
            }
            //更新FirstDimension（同步更新Velocity，因为最初的Velocity值就是Swarm的FirstDimension的值）
            HashMap<Integer, Integer> FD = sv.FirstDimension;
            HashMap<Integer, Integer> VelocityOfSwarmI = new HashMap<>();
            FD.clear();//虽然传进来的Host应该就是空的，但是姑且还是clear一下
            for (Integer HostId : sv.SecondDimension.HostList.keySet()) {
                DDPHost host = sv.SecondDimension.HostList.get(HostId);
                if (host.VmList.size() > 0) {
                    FD.put(HostId, 1);
                    VelocityOfSwarmI.put(HostId, 1);
                } else {
                    FD.put(HostId, 0);
                    VelocityOfSwarmI.put(HostId, 0);
                }
            }
            Velocity.add(VelocityOfSwarmI);

            //计算Fitness（更新SecondDimension里面的Fitness）
            sv.SecondDimension.Fitness = getFitness(sv);
            if (sv.SecondDimension.Fitness > minFitnessVal) {
                tmpBest = sv;//浅拷贝
                minFitnessVal = sv.SecondDimension.Fitness;
            }
        }
        //统计localBest和globalBest
        for (IPSv sv : Swarm) {
            IPSv tmpSv = new IPSv(sv, true);
            localBest.add(tmpSv);
        }
        if (tmpBest != null) globalBest = new IPSv(tmpBest, true);
        else System.out.println("IPSO<Swarm_Initialize>：算法有误，globalBest不可能为null");
    }

    //fitness值就是该个体中Host集群的总功耗，两种fitness都是越小越好
    private double getFitness(IPSv sv) {
        if (VmAllocationPolicyForDifferentAlgorithm.PlacementAlgorType == 7) {//IPSO
            double fitness = 0;
            for (Integer HostId : sv.SecondDimension.HostList.keySet()) {
                fitness += sv.SecondDimension.HostList.get(HostId).Power;
            }
            return fitness;
        } else if (VmAllocationPolicyForDifferentAlgorithm.PlacementAlgorType == 8) {//GAPSO
            double fitness = 0;
            double CPUutilFactor;
            double RAMutilFactor;
            double powerFactor;
            for (Integer HostId : sv.SecondDimension.HostList.keySet()) {
                DDPHost host = sv.SecondDimension.HostList.get(HostId);
                if (host.CPU_util > 0) {
                    CPUutilFactor = Math.pow(host.CPU_util - 1.0, 2) * 100;
                    RAMutilFactor = Math.pow(host.RAM_util - 1.0, 2) * 100;
                    powerFactor = Math.pow((host.Power / peak_power_max), 2) * 100;
                    fitness += Math.sqrt(CPUutilFactor + RAMutilFactor + powerFactor);
                }
            }
            return fitness;
        } else {
            System.out.println("IPSOandGAPSO<getFitness>：算法逻辑有误，不应该出现除IPSO和GAPSO之外的第三种算法");
            System.exit(1);
            return -1;
        }
    }

    //升序Roulette找到tar（返回的一定是比tar大的或等于tar的元素下标）
    private int rouletteBinSearch(ArrayList<Double> Roulette, int l, int r, double tar) {
        if (l >= r) return l;
        int k = (l + r) / 2;
        if (Roulette.get(k) == tar) return k;
        else if (Roulette.get(k) < tar) return rouletteBinSearch(Roulette, k + 1, r, tar);
        else return rouletteBinSearch(Roulette, l, k, tar);
    }
}
