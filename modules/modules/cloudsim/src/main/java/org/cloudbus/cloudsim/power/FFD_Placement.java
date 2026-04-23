package org.cloudbus.cloudsim.power;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Random放置算法及其所需的数据
 * <p>
 * 暂时不考虑VM的vCPU数目对所选Host的Pe数目的限制（也即一个VM的多个vCPU可以同时放置在同一个Host里面）
 * "/*" 注释掉的代码就是该限制对算法产生影响的部分
 */

//打乱Vm和Host列表，再用FFD进行放置
public class FFD_Placement {
    /**
     * ===================================================基本数据===================================================
     */
    private DDPSv FFD_SV;//解向量
    private ArrayList<DDPHost> TargetHostList = new ArrayList<>();//目标服务器列表（乱序）
    private ArrayList<DDPVm> VmAllocateList = new ArrayList<>();//用于存放待迁移Vm（乱序）
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

    //ShuffleMode：0表示HostList和VmList都不动，1表示HostList和VmList都要shuffle，2表示对HostList按照CPU利用率降序排序
    public FFD_Placement(DDPSv FFD_SV,int ShuffleMode) {
        this.FFD_SV = FFD_SV;
        //构造待迁移Vm列表
        VmToAllocate = 0;
        for (Integer VmId : FFD_SV.VmList.keySet()) {
            VmAllocateList.add(FFD_SV.VmList.get(VmId));
            /*VmOrderedByCoreNum.add(RND_SV.VmList.get(VmId));*/
            VmOrderedByVcpuCapacity.add(FFD_SV.VmList.get(VmId));
            UnderAllocatedCPUReq += FFD_SV.VmList.get(VmId).Total_CPU_Request;
            UnderAllocatedRAMReq += FFD_SV.VmList.get(VmId).RAM_Request;
            UnderAllocatedBandReq += FFD_SV.VmList.get(VmId).BW_Request;
        }
        for (Integer HostId : FFD_SV.HostList.keySet()) {
            TargetHostList.add(FFD_SV.HostList.get(HostId));
        }
        //打乱目标服务器列表和待分配Vm列表
        if(ShuffleMode==1) {
            Collections.shuffle(TargetHostList,VmAllocationPolicyForDifferentAlgorithm.rand);
            Collections.shuffle(VmAllocateList,VmAllocationPolicyForDifferentAlgorithm.rand);
        }else if(ShuffleMode==2){
            TargetHostList.sort(new Comparator<DDPHost>() {//按照CPU利用率降序排序
                @Override
                public int compare(DDPHost o1, DDPHost o2) {
                    return Double.compare(o2.CPU_util, o1.CPU_util);
                }
            });
        }

        /*//构造VmOrderedByCoreNum
        //按照Core_Belongs.size()对VmOrderedByCoreNum进行降序排序
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

        //System.out.println("FFD_placement<构造函数> Successful\n");
    }

    //采用FFD算法对Vm进行放置
    public boolean placement(DDPHost tH) {
        //考虑特殊情况，Vm列表为空（所有服务器都没有超载），目标服务器列表为空（所有服务器都超载），则需要直接return false
        if (FFD_SV.HostList.size() == 0) return false;
        if (FFD_SV.VmList.size() == 0) return true;

        //如果遇到服务器资源不足而开启服务器，FFD算法需要把Host加到TargetHost里面
        if (tH != null) {
            TargetHostList.add(tH);
        }

        //遍历VmAllocateList，逐个Vm进行分配
        for (int i = VmToAllocate; i < VmAllocateList.size(); i++, VmToAllocate++) {
            boolean isSuccess = SingleVmPlacement(VmAllocateList.get(i));
            if (!isSuccess) {
                System.out.println("FFD_placement<placement>:Resources are insufficient. Start a new server\n");
                /*for (DDPVm value : VmOrderedByCoreNum) {
                    if (VmIsAllocate.get(value.VmId) == null) {//取出没放值过的排名最前（Core数目最多）的Vm
                        UnderAllocatedDDPVmWithMaxCoreNum = value;
                    }
                }*/
                for (DDPVm value : VmOrderedByVcpuCapacity) {
                    if (VmIsAllocate.get(value.VmId) == null) {//取出没放值过的排名最前（MIPsCapacity数目最多）的Vm
                        UnderAllocatedDDPVmWithMaxVcpuCapicity = value;
                    }
                }
                return false;
            }
        }

        //System.out.println("FFD_placement<placement> Successful");
        return true;
    }

    /**
     * ===================================================辅助方法===================================================
     */

    private boolean SingleVmPlacement(DDPVm vm) {
        //step1:遍历整个HostMap，逐个计算能耗增量，选出最佳的Host
        for (DDPHost host : TargetHostList) {
            if (Check_and_Place_Vm(host, vm)) return true;//一旦放置成功，就return true
        }
        //如果欠缺资源，遍历后没有放置成功，则return false
        return false;
    }

    //Vm的放置操作，返回原有的MIPs_offer值
    //return：Vm能放置的则返回该host在Vm放置前的MIPs_offer（一定为正，因为MIPsLink里面的结点都为正），如果放置不下，则返回一个负数
    private boolean Check_and_Place_Vm(DDPHost host, DDPVm vm) {
        HashMap<String, Object> res = CheckVmPlacement.isSuitableForVm(host, vm);

        if (res != null) {
            //先更新Vm的数据，调度过程中不考虑由于超载导致的性能退化（所以CPU,RAM,BW的Allocate量和request值相等）
            vm.placementUpdate(host.HostId, (ArrayList<DDPPeMipsMapPair>) res.get("PeMipsMap"),
                    (double) res.get("total_cpu"), (double) res.get("ram"), (double) res.get("bw"));
            //然后更新Host的动态数据（必须先更新vm的数据，因为host的更新是根据vm的动态数据进行更新的）
            host.placementUpdate(null, vm);
            //维护VmIsAllocate
            VmIsAllocate.put(vm.VmId, true);
            //更新UnderAllocated的数据
            UnderAllocatedCPUReq -= vm.Total_CPU_Request;
            UnderAllocatedRAMReq -= vm.RAM_Request;
            UnderAllocatedBandReq -= vm.BW_Request;
            return true;
        } else {
            return false;
        }
    }

}

