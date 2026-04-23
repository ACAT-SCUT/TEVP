package org.cloudbus.cloudsim.power;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

/**
 * MBFD放置算法及其所需的数据
 * <p>
 * 暂时不考虑VM的vCPU数目对所选Host的Pe数目的限制（也即一个VM的多个vCPU可以同时放置在同一个Host里面）
 * "/*" 注释掉的代码就是该限制对算法产生影响的部分
 */

public class MBFD_Placement {
    /**
     * ===================================================基本数据===================================================
     */
    public DDPSv MBFD_SV;//解向量
    private ArrayList<DDPVm> VmAllocateList = new ArrayList<>();//用于存放待迁移Vm（按CPU_Req进行排序）
    private int VmToAllocate;//放置到VmAllocateList的第几台Vm

    //下面这三个是用来更新UnderAllocatedDDPVmWithMaxCoreNum和UnderAllocatedDDPVmWithMaxVcpuCapacity，
    // 将所有待分配Vm分别按照requiredNumOfPes和MipsCapacityOfSingleVcpu降序排序，一旦出现需要
    // AddTargetHost的情况，就遍历该表，找到还未被分配且requiredNumOfPes和MipsCapacityOfSingleVcpu数目最大的两个Vm，
    /*public ArrayList<DDPVm> VmOrderedByCoreNum = new ArrayList<>();*/
    public ArrayList<DDPVm> VmOrderedByVcpuCapacity = new ArrayList<>();
    public HashMap<Integer, Boolean> VmIsAllocate = new HashMap<>();

    //这个只记录剩余待分配Vm中vCPU数目最大的Vm，只有在服务器资源数量不足时才需要更新
    /*public DDPVm UnderAllocatedDDPVmWithMaxCoreNum;*/
    //这个只记录剩余待分配Vm中MipsCapacityOfSingleVcpu数目最大的Vm，只有在服务器资源数量不足时才需要更新
    public DDPVm UnderAllocatedDDPVmWithMaxVcpuCapicity;

    //待分配Vm还有多少资源请求量
    public double UnderAllocatedCPUReq = 0;
    public double UnderAllocatedRAMReq = 0;
    public double UnderAllocatedBandReq = 0;


    /**
     * ==================================================public方法==================================================
     */

    public MBFD_Placement(DDPSv MBFD_SV) {
        this.MBFD_SV = MBFD_SV;//浅拷贝
        //构造待迁移Vm列表
        VmToAllocate = 0;
        if (MBFD_SV.VmList.size() != 0) {
            for (Integer VmId : MBFD_SV.VmList.keySet()) {
                VmAllocateList.add(MBFD_SV.VmList.get(VmId));//同样是直接浅拷贝进来
                /*VmOrderedByCoreNum.add(MBFD_SV.VmList.get(VmId));*/
                VmOrderedByVcpuCapacity.add(MBFD_SV.VmList.get(VmId));
                UnderAllocatedCPUReq += MBFD_SV.VmList.get(VmId).Total_CPU_Request;
                UnderAllocatedRAMReq += MBFD_SV.VmList.get(VmId).RAM_Request;
                UnderAllocatedBandReq += MBFD_SV.VmList.get(VmId).BW_Request;
            }
            //自定义Comparator对象，并进行按照Total_CPU_Req对VmAllocateList进行降序排序，return 1表示o1排在o2后面
            VmAllocateList.sort(new Comparator<DDPVm>() {
                @Override
                public int compare(DDPVm o1, DDPVm o2) {
                    return Double.compare(o2.Total_CPU_Request, o1.Total_CPU_Request);
                }
            });

            /*//按照Core_Belongs.size()对VmOrderedByCoreNum进行降序排序
            VmOrderedByCoreNum.sort(new Comparator<DDPVm>() {
                @Override
                public int compare(DDPVm o1, DDPVm o2) {
                    return Integer.compare(o2.requiredNumOfPes, o1.requiredNumOfPes);
                }
            });
            UnderAllocatedDDPVmWithMaxCoreNum = VmOrderedByCoreNum.get(0);*/

            //按照Core_Belongs.size()对VmOrderedByCoreNum进行降序排序
            VmOrderedByVcpuCapacity.sort(new Comparator<DDPVm>() {
                @Override
                public int compare(DDPVm o1, DDPVm o2) {
                    return Double.compare(o2.MipsCapacityOfSingleVcpu, o1.MipsCapacityOfSingleVcpu);
                }
            });
            UnderAllocatedDDPVmWithMaxVcpuCapicity = VmOrderedByVcpuCapacity.get(0);
        }

        //System.out.println("MBFD_placement<构造函数> Successful\n");
    }

    public boolean placement() {
        //考虑特殊情况，Vm列表为空（所有服务器都没有超载），目标服务器列表为空（所有服务器都超载），则需要直接return false
        if (MBFD_SV.HostList.size() == 0) return false;
        if (MBFD_SV.VmList.size() == 0) return true;

        //如果遇到服务器资源不足而开启服务器，MBFD也没什么好维护的，所以继续尝试Vm放置就完事了

        //遍历VmAllocateList，逐个Vm进行分配
        for (int i = VmToAllocate; i < VmAllocateList.size(); i++, VmToAllocate++) {
            if (!SingleVmPlacement(VmAllocateList.get(i))) {
                System.out.println("MBFD_placement<placement>：资源不足，准备开启新的服务器");
                /*for (DDPVm value : VmOrderedByCoreNum) {
                    if (VmIsAllocate.get(value.VmId) == null) {//取出没放置过的排名最前（所需Pe数目最多）的Vm
                        UnderAllocatedDDPVmWithMaxCoreNum = value;
                    }
                }*/

                /*//Debug
                System.out.println("打印VmIsAllocate和VmOrderedByVcpuCapacity的情况：");
                for (DDPVm val : VmOrderedByVcpuCapacity) {
                    System.out.println("Vm #" + val.VmId
                            + ", VcpuCapacity=" + val.MipsCapacityOfSingleVcpu
                            + ", VmIsAllocate=" + VmIsAllocate.get(val.VmId));
                }*/

                for (DDPVm value : VmOrderedByVcpuCapacity) {
                    if (VmIsAllocate.get(value.VmId) == null) {//取出没放值过的排名最前（MIPsCapacity数目最多）的Vm
                        UnderAllocatedDDPVmWithMaxVcpuCapicity = value;
                    }
                    break;
                }

                /*//Debug：看看到底是不是真的没有资源可以放置
                System.out.println("准备触发AddTargetHost函数，先检查是不是真的没有资源可以放置");
                System.out.println("导致资源不足的Vm为：#" + VmAllocateList.get(i).VmId + ", Total_CPU_Req="
                        + VmAllocateList.get(i).Total_CPU_Req + ", RAM_Req=" + VmAllocateList.get(i).RAM_Req +
                        ", Band_Req=" + VmAllocateList.get(i).Band_Req + ", MipsCapacityOfSingleVcpu="
                        + VmAllocateList.get(i).MipsCapacityOfSingleVcpu + "\n");

                System.out.println("再把各种UnderAllocated数据打印一下：");
                System.out.println("UnderAllocatedCPUReq=" + UnderAllocatedCPUReq
                        + "\nUnderAllocatedRAMReq=" + UnderAllocatedRAMReq
                        + "\nUnderAllocatedBandReq=" + UnderAllocatedBandReq
                        +"\nUnderAllocatedMaxVcpuCapacity的Vm=Vm #"+UnderAllocatedDDPVmWithMaxVcpuCapicity.VmId
                        + "\nUnderAllocatedMaxVcpuCapacity=" + UnderAllocatedDDPVmWithMaxVcpuCapicity.MipsCapacityOfSingleVcpu
                        + "\n");

                System.out.println("TargetHostList的现状：\n");
                System.out.println("MBFD_SV.HostList.size()=" + MBFD_SV.HostList.size());
                for (Integer hostId : MBFD_SV.HostList.keySet()) {
                    DDPHost sh = MBFD_SV.HostList.get(hostId);
                    System.out.println("HostId：" + sh.HostId
                            + ", peak_peff=" + sh.peak_peff
                            + ", MIPs_offer=" + sh.MIPs_offer
                            + ", RAM_avail=" + sh.RAM_avail
                            + ", Band_avail=" + sh.Band_avail
                            + ", Pe_Mips_Cap=" + sh.PeList.get(0).MIPs_Cap);
                }
                System.out.println();*/

                return false;
            }
        }

        //System.out.println("MBFD_placement<placement> Successful");
        return true;
    }

    /**
     * ===================================================辅助方法===================================================
     */

    private boolean SingleVmPlacement(DDPVm vm) {
        //step1:遍历整个HostMap，逐个计算能耗增量，选出最佳的Host
        int bestHostId = -1;
        double bestHostPowerIncreasement = Double.MAX_VALUE - 1;
        for (Integer HostId : MBFD_SV.HostList.keySet()) {
            /*//Debug
            if(VmToAllocate==26){
                System.out.println();
            }*/
            //直接浅拷贝MBFD_SV的Host（MBFD_SV是浅拷贝，所以影响的是传进来的Host）
            DDPHost tmp = MBFD_SV.HostList.get(HostId);
            double tmpPowerIncreasement = Check_and_Cal_Vm(tmp, vm);
            if (bestHostPowerIncreasement > tmpPowerIncreasement) {
                bestHostId = HostId;
                bestHostPowerIncreasement = tmpPowerIncreasement;
            }
        }
        //如果欠缺资源，没有选出Host，直接退出
        if (bestHostId == -1) return false;

        //step2:放置Vm并更新数据
        place_Vm(MBFD_SV.HostList.get(bestHostId), vm);
        return true;
    }

    //检查Vm是否能放置，并计算相应的能耗增量
    //return：如果能放置，则返回能耗增量，如果不能放置，则返回Double.MAX_VALUE
    private double Check_and_Cal_Vm(DDPHost host, DDPVm vm) {
        HashMap<String, Object> res = CheckVmPlacement.isSuitableForVm(host, vm);
        //先保证各项资源满足要求
        if (res != null) {
            //检查并计算能耗增量
            double CPU_availAfterPlacement = host.CPU_avail - (double)res.get("total_cpu");
            double CPU_utilAfterPlacement = 1 - CPU_availAfterPlacement / host.CPU_Cap;//一定小于UP_THR
            double PowerAfterPlacement = host.updatePower(CPU_utilAfterPlacement);//插值获取放置后的能耗
            double PowerIncreasement = PowerAfterPlacement - host.Power;
            //回滚资源PeMipsMap的资源
            ArrayList<DDPPeMipsMapPair> PeMipsMap=(ArrayList<DDPPeMipsMapPair>) res.get("PeMipsMap");
            //host的PeList回滚
            for (DDPPeMipsMapPair pair : PeMipsMap) {
                host.PeList.get(pair.PeId).MIPs_available += pair.PeMipsReq;
            }
            return PowerIncreasement;

        } else {
            return Double.MAX_VALUE;
        }
    }

    //选出bestHost后放置Vm，更新数据（100% 会成功，因为之前进行过检查）
    private void place_Vm(DDPHost host, DDPVm vm) {
        //没办法，重新放置一次，在相应的Pe上面放置
        HashMap<String, Object> res = CheckVmPlacement.isSuitableForVm(host, vm);

        //之前已经检查过了，肯定可以放置得下（无需判断res是否为null和回滚）
        //先更新Vm的数据（这里更新的直接就是MBFD_SV里vm的数据了）
        vm.placementUpdate(host.HostId,(ArrayList<DDPPeMipsMapPair>) res.get("PeMipsMap"),
                (double) res.get("total_cpu"), (double) res.get("ram"), (double) res.get("bw"));
        //然后更新Host的动态数据（更新的也是MBFD_SV里host的数据）
        host.placementUpdate(null, vm);
        //维护VmIsAllocate
        VmIsAllocate.put(vm.VmId, true);
        //更新UnderAllocated的数据
        UnderAllocatedCPUReq -= vm.Total_CPU_Request;
        UnderAllocatedRAMReq -= vm.RAM_Request;
        UnderAllocatedBandReq -= vm.BW_Request;
    }
}

